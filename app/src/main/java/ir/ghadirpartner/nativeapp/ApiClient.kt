package ir.ghadirpartner.nativeapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class ApiException(val status: Int, override val message: String) : Exception(message)

private class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("ghadir_native_cookies", Context.MODE_PRIVATE)
    private val key = "cookies_${BuildConfig.APP_MODE}"
    private val cache = mutableMapOf<String, Cookie>()

    init {
        val root = HttpUrl.Builder().scheme("https").host("ghadirpartner.ir").build()
        prefs.getStringSet(key, emptySet())?.forEach { raw ->
            Cookie.parse(root, raw)?.let { cache[it.name + "|" + it.path] = it }
        }
        cleanup()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie -> cache[cookie.name + "|" + cookie.path] = cookie }
        cleanup()
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cleanup()
        return cache.values.filter { it.matches(url) }
    }

    fun clear() {
        cache.clear()
        prefs.edit().remove(key).apply()
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val expired = cache.filterValues { it.expiresAt < now }.keys
        expired.forEach(cache::remove)
    }

    private fun persist() {
        prefs.edit().putStringSet(key, cache.values.map { it.toString() }.toSet()).apply()
    }
}

class ApiClient(private val context: Context) {
    private val cookieJar = PersistentCookieJar(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val isPortal: Boolean get() = BuildConfig.APP_MODE == "portal"
    val base: String get() = BuildConfig.API_BASE.trimEnd('/')

    private data class MemoryEntry(val savedAt: Long, val json: String)
    private val memoryCache = ConcurrentHashMap<String, MemoryEntry>()
    private val cacheablePrefixes = listOf("/api/catalog", "/api/offers", "/api/orders", "/api/reports/customer-summary")
    private fun isCacheable(path: String): Boolean = cacheablePrefixes.any { path.startsWith(it) }
    private fun clonePayload(text: String): Any = when {
        text.startsWith("{") -> JSONObject(text)
        text.startsWith("[") -> JSONArray(text)
        else -> text
    }
    private fun payloadText(value: Any): String = when (value) {
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        else -> value.toString()
    }

    suspend fun get(path: String): Any {
        if (isCacheable(path)) {
            val hit = memoryCache[path]
            if (hit != null && System.currentTimeMillis() - hit.savedAt < 15_000L) return clonePayload(hit.json)
        }
        val result = request("GET", path, null)
        if (isCacheable(path)) memoryCache[path] = MemoryEntry(System.currentTimeMillis(), payloadText(result))
        return result
    }
    suspend fun post(path: String, body: JSONObject = JSONObject()): Any {
        val result = request("POST", path, body)
        memoryCache.clear()
        return result
    }

    private fun urlFor(path: String): String = if (path.startsWith("http")) path else base + if (path.startsWith('/')) path else "/$path"

    private fun publicError(text: String, fallback: String): String {
        if (text.isBlank()) return fallback
        return try {
            val json = JSONObject(text)
            json.optString("message").ifBlank { json.optString("error") }.ifBlank { fallback }
        } catch (_: Exception) {
            text.take(300).ifBlank { fallback }
        }
    }

    suspend fun request(method: String, path: String, body: JSONObject?): Any = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(urlFor(path))
            .header("Accept", "application/json, text/plain;q=0.9")
            .header("User-Agent", "GhadirPartnerNative/${BuildConfig.VERSION_NAME} Android")
        if (method == "POST") {
            val payload = (body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.post(payload)
        } else builder.get()

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw ApiException(response.code, publicError(text, "خطای ارتباط با سرور (${response.code})"))
            }
            if (text.isBlank()) return@use JSONObject()
            when {
                text.startsWith("{") -> JSONObject(text)
                text.startsWith("[") -> JSONArray(text)
                else -> text
            }
        }
    }

    fun modeAllowed(me: JSONObject): Boolean {
        val roles = me.arr("roles").strings().toSet()
        return if (isPortal) {
            "customer" in roles
        } else {
            roles.any { it in setOf("admin", "sales", "finance", "prep", "viewer") }
        }
    }

    fun modeError(): String = if (isPortal) "این حساب برای پرتال مشتریان مجاز نیست." else "حساب مشتری اجازه ورود به اتوماسیون را ندارد."

    suspend fun login(identity: String, password: String) {
        val body = JSONObject()
            .put("Username", identity.trim())
            .put("Password", password)
            .put("app_mode", BuildConfig.APP_MODE)
        post("/api/login", body)
        val session = me()
        if (!modeAllowed(session)) {
            logout()
            throw ApiException(403, modeError())
        }
    }

    suspend fun logout() {
        try { get("/api/logout") } catch (_: Exception) {}
        cookieJar.clear()
        memoryCache.clear()
    }

    suspend fun me(): JSONObject = get("/api/me") as JSONObject

    suspend fun downloadFile(path: String, fileName: String, mimeType: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor(path))
            .header("Accept", mimeType)
            .header("User-Agent", "GhadirPartnerNative/${BuildConfig.VERSION_NAME} Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                throw ApiException(response.code, publicError(text, "دانلود فایل ناموفق بود (${response.code})"))
            }
            val body = response.body ?: throw ApiException(500, "فایل دریافتی خالی است")
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "-")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GhadirPartner")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw ApiException(500, "امکان ایجاد فایل دانلود وجود ندارد")
                try {
                    resolver.openOutputStream(uri)?.use { output -> body.byteStream().use { it.copyTo(output) } }
                        ?: throw ApiException(500, "امکان ذخیره فایل وجود ندارد")
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/GhadirPartner/$safeName"
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, safeName)
                file.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
                file.absolutePath
            }
        }
    }
    suspend fun saveSerialsPdf(order: JSONObject): String = withContext(Dispatchers.IO) {
        val rows = mutableListOf<Pair<String, String>>()
        val items = order.optJSONArray("items") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val product = shortProductName(item.optString("product"))
            val serials = item.optJSONArray("serials") ?: JSONArray()
            for (j in 0 until serials.length()) {
                val raw = serials.opt(j)
                val serial = when (raw) {
                    is JSONObject -> raw.optString("serial")
                    else -> raw?.toString().orEmpty()
                }.trim()
                if (serial.isNotBlank()) rows += product to serial
            }
        }
        if (rows.isEmpty()) throw ApiException(404, "برای این سفارش هنوز سریالی ثبت نشده است")

        val doc = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(16, 42, 67)
                textAlign = Paint.Align.RIGHT
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            fun drawText(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, size: Float = 11f, bold: Boolean = false) {
                paint.textSize = size
                paint.typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                canvas.drawText(text, x, y, paint)
            }

            val perPage = 24
            val pages = (rows.size + perPage - 1) / perPage
            for (pageIndex in 0 until pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
                val canvas = page.canvas
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                canvas.drawRoundRect(28f, 28f, 567f, 814f, 12f, 12f, paint)
                paint.style = Paint.Style.FILL

                drawText(canvas, "لیست سریال‌های مشتری", 540f, 62f, 20f, true)
                drawText(canvas, "شماره سفارش: ${order.optString("number")}", 540f, 90f, 12f, true)
                val customer = order.optString("customer_name")
                if (customer.isNotBlank()) drawText(canvas, "مشتری: $customer", 540f, 112f, 11f)
                drawText(canvas, "تاریخ: ${formatDateFa(order.optString("created_at"))}", 540f, 134f, 11f)
                drawText(canvas, "صفحه ${pageIndex + 1} از $pages", 540f, 156f, 10f)

                var y = 194f
                drawText(canvas, "ردیف", 540f, y, 11f, true)
                drawText(canvas, "مدل دستگاه", 455f, y, 11f, true)
                drawText(canvas, "سریال", 205f, y, 11f, true)
                y += 14f
                paint.color = android.graphics.Color.rgb(220, 227, 235)
                canvas.drawLine(45f, y, 545f, y, paint)
                paint.color = android.graphics.Color.rgb(16, 42, 67)
                y += 22f

                val from = pageIndex * perPage
                val to = minOf(rows.size, from + perPage)
                for (idx in from until to) {
                    val (product, serial) = rows[idx]
                    drawText(canvas, (idx + 1).toString(), 540f, y, 10f)
                    drawText(canvas, product.take(28), 455f, y, 10f)
                    paint.textAlign = Paint.Align.LEFT
                    drawText(canvas, serial, 55f, y, 10f, true)
                    paint.textAlign = Paint.Align.RIGHT
                    y += 25f
                    paint.color = android.graphics.Color.rgb(235, 239, 244)
                    canvas.drawLine(45f, y - 12f, 545f, y - 12f, paint)
                    paint.color = android.graphics.Color.rgb(16, 42, 67)
                }
                drawText(canvas, "قدیر پارتنر • پشتیبانی 09981638272", 540f, 790f, 9f)
                doc.finishPage(page)
            }

            val safe = order.optString("number").replace(Regex("[^A-Za-z0-9_-]"), "-")
            val fileName = "serials-${safe.ifBlank { order.optInt("id").toString() }}.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GhadirPartner")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw ApiException(500, "امکان ایجاد فایل PDF سریال وجود ندارد")
                try {
                    resolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                        ?: throw ApiException(500, "امکان ذخیره PDF سریال وجود ندارد")
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/GhadirPartner/$fileName"
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { doc.writeTo(it) }
                file.absolutePath
            }
        } finally {
            doc.close()
        }
    }

    suspend fun saveProformaPdf(order: JSONObject): String = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(16, 42, 67)
                textAlign = Paint.Align.RIGHT
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            fun line(text: String, y: Float, size: Float = 14f, bold: Boolean = false) {
                paint.textSize = size
                paint.typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                canvas.drawText(text, 545f, y, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRoundRect(35f, 35f, 560f, 805f, 14f, 14f, paint)
            paint.style = Paint.Style.FILL
            line("پیش‌فاکتور رسمی قدیر پارتنر", 75f, 21f, true)
            line("شماره سفارش: ${order.optString("number")}", 112f, 13f, true)
            val customer = order.optString("customer_name")
            if (customer.isNotBlank()) line("مشتری: $customer", 136f, 12f)
            line("تاریخ ثبت: ${formatDateFa(order.optString("created_at"))}", 160f, 12f)
            line("وضعیت: ${order.optString("status")}", 184f, 12f)
            val invoiceType = order.optString("invoice_type")
            if (invoiceType.isNotBlank()) line("نوع فاکتور: $invoiceType", 208f, 12f)
            val payment = order.optString("requested_payment_method_label").ifBlank { order.optString("payment_method_label") }
            if (payment.isNotBlank()) line("روش تسویه: $payment", 232f, 12f)
            var y = 272f
            line("شرح اقلام", y, 15f, true); y += 28f
            val items = order.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val product = shortProductName(item.optString("product"))
                val qty = item.optInt("qty")
                val unit = item.optLong("unit_price")
                val total = item.optLong("line_total")
                line("${i + 1}. $product × $qty", y, 12f, true); y += 20f
                if (unit > 0) { line("قیمت واحد: ${formatMoney(unit)} تومان", y, 11f); y += 18f }
                if (total > 0) { line("جمع قلم: ${formatMoney(total)} تومان", y, 11f); y += 20f }
                if (y > 680f) break
            }
            val subtotal = order.optLong("subtotal_before_discount")
            val discount = order.optLong("discount_amount")
            val total = if (order.optLong("approved_total") > 0) order.optLong("approved_total") else order.optLong("estimated_total")
            y = maxOf(y + 20f, 650f)
            if (subtotal > 0) { line("جمع قبل از تخفیف: ${formatMoney(subtotal)} تومان", y, 12f); y += 22f }
            if (discount > 0) { line("تخفیف: ${formatMoney(discount)} تومان", y, 12f); y += 22f }
            line("مبلغ نهایی: ${formatMoney(total)} تومان", y, 16f, true); y += 28f
            val notes = order.optString("notes").trim()
            if (notes.isNotBlank() && y < 735f) {
                line("توضیحات: ${notes.take(70)}", y, 10f); y += 22f
            }
            line("پشتیبانی: 09981638272", y, 11f)
            line("این پیش‌فاکتور بر اساس همان اطلاعات سفارش ثبت‌شده در سامانه قدیر پارتنر صادر شده است.", 780f, 9f)
            doc.finishPage(page)

            val safe = order.optString("number").replace(Regex("[^A-Za-z0-9_-]"), "-")
            val fileName = "proforma-${safe.ifBlank { order.optInt("id").toString() }}.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GhadirPartner")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw ApiException(500, "امکان ایجاد فایل پیش‌فاکتور وجود ندارد")
                try {
                    resolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                        ?: throw ApiException(500, "امکان ذخیره PDF وجود ندارد")
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/GhadirPartner/$fileName"
                } catch (e: Exception) {
                    resolver.delete(uri, null, null); throw e
                }
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { doc.writeTo(it) }
                file.absolutePath
            }
        } finally {
            doc.close()
        }
    }

}

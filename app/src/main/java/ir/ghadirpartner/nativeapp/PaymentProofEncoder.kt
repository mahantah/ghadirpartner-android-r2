package ir.ghadirpartner.nativeapp

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepare payment documents for checkout or an existing order. */
internal class PaymentProofEncoder(private val resolver: ContentResolver) {
    /** Read the selection off the UI thread and enforce the request budget. */
    suspend fun encode(uris: List<Uri>, remaining: Int): List<String> = withContext(Dispatchers.IO) {
        require(uris.size <= remaining) { "حداکثر ${faNumber(remaining)} فایل دیگر انتخاب کنید." }
        val files = uris.distinct().map { uri ->
            if (resolver.getType(uri) == "application/pdf") encodePdf(uri) else encodeImage(uri)
        }
        PaymentProofSelection.requireBatchSize(files)
        files
    }

    private fun encodePdf(uri: Uri): String {
        val output = ByteArrayOutputStream()
        val input = requireNotNull(resolver.openInputStream(uri)) { "فایل PDF خوانده نشد." }
        input.use {
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= BuildConfig.PROOF_MAX_RAW_FILE_BYTES) {
                    "حجم هر فایل PDF باید حداکثر ۱ مگابایت باشد."
                }
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        require(bytes.take(PDF_HEADER.length).toByteArray().decodeToString() == PDF_HEADER) { "فایل PDF معتبر نیست." }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun encodeImage(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تصویر معتبر نیست" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            val dimension = BuildConfig.PROOF_IMAGE_DIMENSION
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > dimension) inSampleSize *= 2
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("تصویر خوانده نشد")
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, BuildConfig.PROOF_JPEG_QUALITY, stream)
            require(stream.size() in 1..BuildConfig.PROOF_MAX_RAW_FILE_BYTES) { "حجم تصویر زیاد است؛ تصویر کوچک‌تری انتخاب کنید" }
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }
    companion object {
        private const val PDF_HEADER = "%PDF-"
    }
}

/** Keep checkout selections and request limits in one place. */
internal object PaymentProofSelection {
    val mimeTypes: Array<String> get() = arrayOf("image/*", "application/pdf")
    val kinds: List<String> = listOf("cash", "check")
    const val field = "payment_files"
    private val legacyFields = listOf("cash_image_base64", "check_image_base64", "image_base64")

    /** Read selected files for a payment kind, including drafts from the previous app. */
    fun read(settlement: JSONObject, kind: String): List<String> {
        val files = settlement.optJSONObject(field)?.optJSONArray(kind)
        if (files != null) return List(files.length()) { files.getString(it) }
        val legacyKey = "${kind}_image_base64"
        val value = settlement.optString(legacyKey).ifBlank {
            if (kind == "check") settlement.optString("image_base64") else ""
        }
        return listOf(value).filter(String::isNotBlank)
    }

    /** Update one kind without losing the selection for the other. */
    fun set(settlement: JSONObject, kind: String, files: List<String>) {
        val selection = settlement.optJSONObject(field) ?: JSONObject().put(
            "cash", JSONArray(read(settlement, "cash"))
        ).put("check", JSONArray(read(settlement, "check")))
        selection.put(kind, JSONArray(files))
        requireBatchSize(kinds.flatMap { read(JSONObject().put(field, selection), it) })
        settlement.put(field, selection)
        legacyFields.forEach { settlement.remove(it) }
    }

    /** Include documents for the payment methods submitted with the order. */
    fun prepare(settlement: JSONObject, method: String) {
        val selection = JSONObject()
        if (!settlement.b("upload_proof_later")) {
            for (kind in kinds) {
                val files = read(settlement, kind)
                if (files.isNotEmpty() && (method == kind || (method == "mixed" && settlement.l("${kind}_amount") > 0))) {
                    selection.put(kind, JSONArray(files))
                }
            }
        }
        clear(settlement)
        if (selection.length() > 0) settlement.put(field, selection)
    }

    /** Keep documents out of the lightweight abandoned-cart data. */
    fun clear(settlement: JSONObject) {
        legacyFields.forEach { settlement.remove(it) }
        settlement.remove(field)
    }

    /** Reject oversized selections before creating a network request. */
    fun requireBatchSize(files: List<String>) {
        require(files.sumOf(String::length) <= BuildConfig.PROOF_MAX_ENCODED_BATCH_BYTES) {
            "حجم مجموع فایل‌ها زیاد است. فایل‌های کوچک‌تری انتخاب کنید."
        }
    }
}

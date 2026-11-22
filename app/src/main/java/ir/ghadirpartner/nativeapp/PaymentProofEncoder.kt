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

/** One evidence file with the customer's optional explanation. */
internal data class SelectedProof(val data: String, val note: String = "", val depositAmount: Long? = null) {
    /** Encode the file and its explanation together for the server. */
    fun payload(): JSONObject {
        val amount = depositAmount?.let { "مبلغ این واریز: ${formatMoney(it)} تومان" }.orEmpty()
        val description = listOf(amount, note).filter(String::isNotBlank).joinToString("\n")
        return JSONObject().put("data", data).put("note", description.take(BuildConfig.PROOF_MAX_NOTE_CHARS))
    }
}

/** Keep checkout selections and request limits in one place. */
internal object PaymentProofSelection {
    val mimeTypes: Array<String> get() = arrayOf("image/*", "application/pdf")
    val kinds: List<String> = listOf("cash", "check")
    const val field = "payment_files"
    private const val notesField = "payment_file_notes"
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

    /** Join each attachment with its explanation in its selection order. */
    fun documents(settlement: JSONObject, kind: String): List<SelectedProof> {
        val notes = settlement.optJSONObject(notesField)?.optJSONArray(kind)
        val amounts = if (kind == "cash" && settlement.has(CashDepositRows.field)) {
            CashDepositRows.read(settlement).filter { it.image.isNotBlank() }.associate { it.image to it.amount }
        } else emptyMap()
        return read(settlement, kind).mapIndexed { index, data -> SelectedProof(data, notes?.optString(index).orEmpty(), amounts[data]) }
    }

    /** Set a note without changing the original bytes or other selections. */
    fun note(settlement: JSONObject, kind: String, index: Int, note: String): JSONObject {
        val next = JSONObject(settlement.toString())
        val notes = next.optJSONObject(notesField) ?: JSONObject()
        val revised = documents(next, kind).mapIndexed { i, file -> if (i == index) note else file.note }
        notes.put(kind, JSONArray(revised))
        return next.put(notesField, notes)
    }

    /** Return the selected evidence that belongs to the final payment method. */
    fun active(settlement: JSONObject, method: String): List<Pair<String, List<SelectedProof>>> =
        if (settlement.b("upload_proof_later")) emptyList() else kinds.mapNotNull { kind ->
            val enabled = (method == kind || (method == "mixed" && settlement.l("${kind}_amount") > 0)) &&
                (kind != "cash" || CashDepositRows.depositEnabled(settlement))
            val files = documents(settlement, kind)
            if (enabled && files.isNotEmpty()) kind to files else null
        }

    /** Update one kind without losing the selection for the other. */
    fun set(settlement: JSONObject, kind: String, files: List<String>) {
        val selection = settlement.optJSONObject(field) ?: JSONObject().put(
            "cash", JSONArray(read(settlement, "cash"))
        ).put("check", JSONArray(read(settlement, "check")))
        val priorNotes = documents(settlement, kind).associate { it.data to it.note }
        val notes = settlement.optJSONObject(notesField) ?: JSONObject()
        notes.put(kind, JSONArray(files.map { priorNotes[it].orEmpty() }))
        selection.put(kind, JSONArray(files))
        requireBatchSize(kinds.flatMap { read(JSONObject().put(field, selection), it) })
        settlement.put(field, selection).put(notesField, notes)
        legacyFields.forEach { settlement.remove(it) }
    }

    /** Include documents for the payment methods submitted with the order. */
    fun prepare(settlement: JSONObject, method: String) {
        val selection = JSONObject()
        active(settlement, method).forEach { (kind, files) ->
            selection.put(kind, JSONArray(files.map(SelectedProof::payload)))
        }
        clear(settlement)
        if (selection.length() > 0) settlement.put(field, selection)
    }

    /** Keep documents out of the lightweight abandoned-cart data. */
    fun clear(settlement: JSONObject) {
        legacyFields.forEach { settlement.remove(it) }
        settlement.remove(field)
        settlement.remove(notesField)
        settlement.remove(CashDepositRows.field)
    }

    /** Reject oversized selections before creating a network request. */
    fun requireBatchSize(files: List<String>) {
        require(files.sumOf(String::length) <= BuildConfig.PROOF_MAX_ENCODED_BATCH_BYTES) {
            "حجم مجموع فایل‌ها زیاد است. فایل‌های کوچک‌تری انتخاب کنید."
        }
    }
}

package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Exchange shipping attachments through the order-scoped authenticated endpoint. */
internal class OrderShippingReceiptRepository(private val api: ApiClient) {
    private val path = "/api/order-shipping-receipts"

    /** Fetch metadata without downloading receipt images. */
    suspend fun read(orderId: Int): JSONObject = api.get("$path?order_id=$orderId") as JSONObject

    /** Save one retry-identifiable receipt, retaining all previous shipments. */
    suspend fun append(orderId: Int, fields: JSONObject): JSONObject =
        api.post(path, fields.put("order_id", orderId)) as JSONObject

    /** Retrieve a private attachment only through its owning order. */
    suspend fun document(orderId: Int, token: String): String {
        require(token.matches(Regex("[a-f0-9]{48}")))
        return (api.get("$path/document?order_id=$orderId&token=$token") as JSONObject).s("data")
    }
}

/** Show receipt history to customers and expose uploads to authorized staff. */
@Composable
internal fun OrderShippingReceipts(api: ApiClient, orderId: Int, allowUpload: Boolean = false) {
    val repository = remember(api) { OrderShippingReceiptRepository(api) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val encoder = remember(context) { PaymentProofEncoder(context.contentResolver) }
    var summary by remember(orderId) { mutableStateOf<JSONObject?>(null) }
    var error by remember(orderId) { mutableStateOf("") }
    var busy by remember(orderId) { mutableStateOf(false) }
    var carrier by rememberSaveable(orderId) { mutableStateOf("") }
    var tracking by rememberSaveable(orderId) { mutableStateOf("") }
    var note by rememberSaveable(orderId) { mutableStateOf("") }
    var files by remember(orderId) { mutableStateOf(emptyList<SelectedProof>()) }
    var requestId by rememberSaveable(orderId) { mutableStateOf("") }

    suspend fun load() {
        busy = true
        error = ""
        try {
            summary = repository.read(orderId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "رسیدهای ارسال دریافت نشد."
        } finally {
            busy = false
        }
    }

    LaunchedEffect(api, orderId) { load() }
    fun receiveImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            busy = true
            error = ""
            try {
                val encoded = encoder.encode(uris, BuildConfig.PROOF_MAX_FILES_PER_KIND - files.size)
                val merged = (files + encoded.map { SelectedProof(it) }).distinctBy { it.data }
                PaymentProofSelection.requireBatchSize(merged.map { it.data })
                files = merged
                requestId = ""
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = failure.message ?: "رسید انتخاب نشد."
            } finally {
                busy = false
            }
        }
    }

    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("رسیدهای ارسال", color = Ink, fontWeight = FontWeight.Bold)
            if (error.isNotBlank()) Text(error, color = Danger)
            val data = summary
            if (data == null) {
                Text(if (busy) "در حال دریافت رسیدهای ارسال…" else "دریافت رسیدها ناموفق بود.", color = Muted)
                if (!busy) GhadirButton("تلاش دوباره", { scope.launch { load() } }, secondary = true)
                return@Column
            }
            val receipts = data.arr("receipts").objects()
            if (receipts.isEmpty()) Text("هنوز رسید ارسالی برای این سفارش ثبت نشده است.", color = Muted)
            receipts.forEach { receipt ->
                Text(receipt.s("carrier"), color = Ink, fontWeight = FontWeight.Bold)
                Text(formatDateFa(receipt.s("created_at")), color = Muted)
                if (receipt.s("tracking_code").isNotBlank()) DetailLine("کد رهگیری", faDigits(receipt.s("tracking_code")))
                if (receipt.s("note").isNotBlank()) Text(receipt.s("note"), color = Ink)
                receipt.arr("files").objects().forEachIndexed { index, proof ->
                    ShippingReceiptPreview(repository, orderId, proof.s("proof_token"), "رسید ارسال ${faNumber(index + 1)}")
                    if (proof.s("note").isNotBlank()) Text(proof.s("note"), color = Ink)
                }
            }
            GhadirButton("به‌روزرسانی رسیدها", { scope.launch { load() } }, secondary = true, enabled = !busy)
            if (allowUpload) {
                val policy = data.obj("policy")
                DesignField("نام پیک یا باربری", carrier, {
                    carrier = it.take(policy.i("max_carrier_chars")); requestId = ""
                }, "پیک، کالارسان یا نام باربری", enabled = !busy)
                DesignField("کد رهگیری یا شماره بارنامه", tracking, {
                    tracking = it.take(policy.i("max_tracking_chars")); requestId = ""
                }, "اختیاری", enabled = !busy)
                DesignField("توضیحات ارسال", note, {
                    note = it.take(policy.i("max_note_chars")); requestId = ""
                }, "اختیاری", singleLine = false, enabled = !busy)
                ImageUploadButton("بارگذاری رسید ارسال",enabled=!busy&&files.size<BuildConfig.PROOF_MAX_FILES_PER_KIND,mimeTypes=PaymentProofSelection.mimeTypes) {receiveImages(it)}
                SelectedProofReview("رسید ارسال", files, onNote = { index, value ->
                    files = files.mapIndexed { i, file -> if (i == index) file.copy(note = value) else file }
                    requestId = ""
                }, onRemove = { index ->
                    files = files.filterIndexed { i, _ -> i != index }; requestId = ""
                }, enabled = !busy)
                GhadirButton(if (busy) "در حال ثبت…" else "ثبت رسید ارسال", {
                    if (busy) return@GhadirButton
                    busy = true
                    error = ""
                    if (requestId.isEmpty()) requestId = OrderFollowupRepository.newId()
                    scope.launch {
                        try {
                            summary = repository.append(orderId, JSONObject()
                                .put("request_id", requestId).put("carrier", carrier)
                                .put("tracking_code", tracking).put("note", note)
                                .put("files", JSONArray(files.map { it.payload() })))
                            files = emptyList()
                            carrier = ""; tracking = ""; note = ""; requestId = ""
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = failure.message ?: "ثبت رسید ارسال ناموفق بود."
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy && carrier.isNotBlank() && files.isNotEmpty())
            }
        }
    }
}

/** Download each shipping document on demand and reuse the image/PDF preview. */
@Composable
private fun ShippingReceiptPreview(repository: OrderShippingReceiptRepository, orderId: Int, token: String, title: String) {
    val scope = rememberCoroutineScope()
    var data by remember(orderId, token) { mutableStateOf("") }
    var error by remember(orderId, token) { mutableStateOf("") }
    var busy by remember(orderId, token) { mutableStateOf(false) }
    if (data.isNotBlank()) {
        ProofThumbnail(data, title)
    } else {
        GhadirButton(if (busy) "در حال دریافت…" else "مشاهده $title", {
            busy = true
            error = ""
            scope.launch {
                try {
                    data = repository.document(orderId, token)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    error = failure.message ?: "رسید دریافت نشد."
                } finally {
                    busy = false
                }
            }
        }, enabled = !busy, secondary = true)
    }
    if (error.isNotBlank()) Text(error, color = Danger)
}

package ir.ghadirpartner.nativeapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Exchange authenticated order updates, alerts, and protected documents. */
internal class OrderFollowupRepository(private val api: ApiClient) {
    /** Return a fresh financial and correction summary for the selected order. */
    suspend fun read(id: Int): JSONObject = api.get("/api/order-followup?order_id=$id") as JSONObject

    /** Read the staff queue including the badge count. */
    suspend fun alerts(): JSONObject = api.get("/api/order-followup/alerts") as JSONObject

    /** Apply one checked workflow action and invalidate stale order reads. */
    suspend fun send(id: Int, action: String, fields: JSONObject): JSONObject =
        api.post("/api/order-followup", fields.put("action", action).put("order_id", id)) as JSONObject

    /** Resolve one proof via order ownership and finance permissions. */
    suspend fun proof(id: Int, token: String): String {
        require(token.matches(Regex("[a-f0-9]{48}")))
        return (api.get("/api/order-followup/document?order_id=$id&token=$token") as JSONObject).s("data")
    }

    companion object {
        /** Keep an operation identifiable across retries. */
        fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    }
}

/** Render a stored attachment on demand and signal successful preview to its reviewer. */
@Composable
internal fun StoredOrderProof(repository: OrderFollowupRepository, orderId: Int, proof: JSONObject, number: Int, onViewed: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val token = proof.s("proof_token")
    var data by remember(token) { mutableStateOf("") }
    var message by remember(token) { mutableStateOf("") }
    var loading by remember(token) { mutableStateOf(false) }
    val title = "مدرک ${faNumber(number)}"
    if (data.isNotEmpty()) {
        ProofThumbnail(data, title, onViewed)
    } else {
        GhadirButton(
            if (loading) "در حال دریافت…" else "مشاهده $title",
            onClick = {
                scope.launch {
                    loading = true
                    try {
                        data = repository.proof(orderId, token)
                        message = ""
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        message = error.message ?: "مدرک دریافت نشد."
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading, secondary = true,
        )
    }
    if (proof.s("note").isNotBlank()) Text(proof.s("note"), color = Ink)
    if (message.isNotBlank()) Text(message, color = Danger)
}

/** Let the customer send photographed repayment evidence after delivery. */
@Composable
internal fun CustomerCreditRepayment(api: ApiClient, order: JSONObject) {
    val id = order.i("id")
    val relevant = order.s("requested_payment_method") in listOf("credit", "mixed") ||
        order.s("payment_method") == "credit" || order.s("payment_status") == "اعتباری"
    if (!relevant || id < 1) return
    val repository = remember(api) { OrderFollowupRepository(api) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val encoder = remember(context) { PaymentProofEncoder(context.contentResolver) }
    var summary by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var busy by remember(id) { mutableStateOf(false) }
    var amounts by remember(id) { mutableStateOf(emptyMap<String, String>()) }
    var key by rememberSaveable(id) { mutableStateOf("") }
    var files by remember(id) { mutableStateOf(emptyList<SelectedProof>()) }
    suspend fun load() {
        try {
            summary = repository.read(id)
            error = ""
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "اطلاعات تسویه دریافت نشد."
        }
    }
    LaunchedEffect(id, order.s("status")) { load() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = ""
            try {
                val encoded = encoder.encode(uris, BuildConfig.PROOF_MAX_FILES_PER_KIND - files.size)
                val merged = (files + encoded.map { SelectedProof(it) }).distinctBy { it.data }
                PaymentProofSelection.requireBatchSize(merged.map { it.data })
                files = merged
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = failure.message ?: "انتخاب مدارک انجام نشد."
            } finally {
                busy = false
            }
        }
    }
    val data = summary
    if (data != null && data.l("credit_amount") <= 0L && data.arr("repayments").length() == 0) return
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تسویهٔ اعتبار سفارش", color = Ink)
            if (error.isNotBlank()) Text(error, color = Danger)
            if (data == null) {
                if (error.isNotBlank()) GhadirButton("تلاش دوباره", { scope.launch { load() } }, secondary = true)
                else Text("در حال دریافت وضعیت تسویه…", color = Muted)
                return@Column
            }
            DetailLine("ماندهٔ اعتباری", formatMoney(data.l("outstanding")) + " تومان")
            val payments = data.arr("repayments").objects()
            val pending = data.l("pending_amount")
            val remaining = if (data.has("submittable_amount")) data.l("submittable_amount") else data.l("outstanding")
            if (pending > 0) {
                DetailLine("در انتظار بررسی", formatMoney(pending) + " تومان")
            }
            for (payment in payments.reversed()) {
                val status = payment.s("status")
                val label = when (status) {
                    "approved" -> "تأییدشده و قفل‌شده"
                    "rejected" -> "ردشده"
                    else -> "در انتظار بررسی"
                }
                DetailLine(label, formatMoney(payment.l(if (status == "approved") "amount" else "claimed_amount")) + " تومان")
                if (payment.s("review_note").isNotBlank()) Text(payment.s("review_note"), color = Muted)
                payment.arr("proofs").objects().forEachIndexed { i, proof -> StoredOrderProof(repository, id, proof, i + 1) }
            }
            if (data.b("can_submit")) {
                var expanded by rememberSaveable(id) { mutableStateOf(false) }
                if (!expanded) {
                    GhadirButton("تسویه اعتبار", { expanded = true }, enabled = !busy)
                    return@Column
                }
                DetailLine("مبلغ قابل ثبت", formatMoney(remaining) + " تومان")
                GhadirButton("انتخاب عکس یا PDF فیش", { picker.launch(PaymentProofSelection.mimeTypes) },
                    enabled = !busy && files.size < BuildConfig.PROOF_MAX_FILES_PER_KIND, secondary = true)
                SelectedProofReview("فیش", files, { index, note ->
                    files = files.mapIndexed { i, file -> if (i == index) file.copy(note = note) else file }
                }, onRemove = { index ->
                    amounts = amounts - files[index].data
                    files = files.filterIndexed { i, _ -> i != index }
                }, enabled = !busy, details = { index ->
                    val document = files[index].data
                    DesignField("مبلغ این فیش (تومان)", amounts[document].orEmpty(), { value ->
                        val digits = asciiDigits(value).filter { it in '0'..'9' }.take(13)
                        amounts = amounts + (document to digits)
                    }, keyboardType = KeyboardType.Number, transformation = SettlementAmountTransformation, enabled = !busy)
                })
                val submittedAmounts = files.map { amounts[it.data]?.toLongOrNull() ?: 0L }
                val total = submittedAmounts.sum()
                if (files.isNotEmpty()) DetailLine("جمع فیش‌های انتخاب‌شده", formatMoney(total) + " تومان")
                if (total > remaining) Text("جمع فیش‌ها از مبلغ قابل ثبت بیشتر است.", color = Danger)
                GhadirButton(if (busy) "در حال ارسال…" else "ارسال فیش‌ها برای بررسی", {
                    if (busy) return@GhadirButton
                    busy = true
                    scope.launch {
                        if (key.isEmpty()) key = OrderFollowupRepository.newId()
                        error = ""
                        try {
                            val documents = files.mapIndexed { index, file ->
                                file.payload().put("amount", submittedAmounts[index])
                            }
                            summary = repository.send(id, "submit", JSONObject().put("request_id", key)
                                .put("files", JSONArray(documents)))
                            key = ""
                            amounts = emptyMap()
                            files = emptyList()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            error = failure.message ?: "ارسال فیش انجام نشد."
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy && files.isNotEmpty() && submittedAmounts.all { it > 0L } && total <= remaining)
                Text("مبلغ پس از تأیید واحد مالی به اعتبار قابل استفاده برمی‌گردد.", color = Muted)
            }
        }
    }
}

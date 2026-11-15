package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Show staff correction and credit actions in the selected order. */
@Composable
internal fun StaffOrderFollowup(api: ApiClient, order: JSONObject, roles: Set<String>, onChanged: () -> Unit) {
    val finance = roles.any { it == "finance" || it == "admin" }
    val sales = roles.any { it == "sales" || it == "admin" }
    if (!finance && !sales) return
    val id = order.i("id")
    val repository = remember(api) { OrderFollowupRepository(api) }
    val scope = rememberCoroutineScope()
    var summary by remember(id) { mutableStateOf<JSONObject?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var busy by remember(id) { mutableStateOf(false) }
    var reason by rememberSaveable(id) { mutableStateOf("") }
    var returnId by rememberSaveable(id) { mutableStateOf("") }

    fun send(action: String, request: JSONObject) {
        scope.launch {
            busy = true
            error = ""
            try {
                val result = repository.send(id, action, request)
                summary = if (result.has("order_id")) result else repository.read(id)
                onChanged()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = failure.message ?: "عملیات انجام نشد."
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(id) {
        try {
            summary = repository.read(id)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "اطلاعات سفارش دریافت نشد."
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (error.isNotBlank()) Text(error, color = Danger)
        val details = summary ?: return@Column
        val correction = details.obj("correction")
        if (correction.s("reason").isNotBlank()) Text(correction.s("reason"), color = Ink)
        if (sales && correction.s("status") == "resubmitted") {
            GhadirButton("اعلان اصلاح سفارش بررسی شد", {
                send("ack_revision", JSONObject().put("version", correction.i("version")))
            }, enabled = !busy, secondary = true)
        }
        if (sales && details.b("can_return")) {
            DesignField("دلیل بازگشت سفارش", reason, { reason = it.take(BuildConfig.PROOF_MAX_NOTE_CHARS) },
                "موردی که مشتری باید اصلاح کند", singleLine = false, enabled = !busy)
            GhadirButton("بازگشت سفارش برای اصلاح", {
                if (returnId.isEmpty()) returnId = OrderFollowupRepository.newId()
                send("return", JSONObject().put("request_id", returnId).put("note", reason))
            }, enabled = !busy && reason.isNotBlank(), secondary = true)
        }
        if (finance) {
            order.obj("native_settlement").arr("proofs").objects().forEachIndexed { i, proof ->
                StoredOrderProof(repository, id, proof, i + 1)
            }
            val payments = details.arr("repayments").objects()
            if (details.l("credit_amount") > 0 || payments.isNotEmpty()) {
                DetailLine("ماندهٔ اعتباری سفارش", formatMoney(details.l("outstanding")) + " تومان")
                payments.reversed().forEach { record ->
                    key(record.s("id")) {
                        StaffRepaymentEntry(repository, id, record, busy, ::send)
                    }
                }
                if (details.l("outstanding") > 0L) key(payments.size) { StaffRepaymentEntry(repository, id, null, busy, ::send) }
            }
        }
    }
}

@Composable
private fun StaffRepaymentEntry(
    repository: OrderFollowupRepository,
    orderId: Int,
    payment: JSONObject?,
    busy: Boolean,
    send: (String, JSONObject) -> Unit,
) {
    val status = payment?.s("status").orEmpty()
    val original = payment?.l(if (status == "approved") "amount" else "claimed_amount") ?: 0L
    var amount by rememberSaveable(original) { mutableStateOf(original.takeIf { it > 0 }?.toString().orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    var manualId by rememberSaveable { mutableStateOf("") }
    val proofs = payment?.arr("proofs")?.objects().orEmpty()
    val viewed = remember { mutableStateListOf<String>() }
    val seen = proofs.isNotEmpty() && proofs.all { it.s("proof_token") in viewed }
    fun action(kind: String) {
        if (payment == null && manualId.isEmpty()) manualId = OrderFollowupRepository.newId()
        send(kind, JSONObject().put("request_id", payment?.s("id") ?: manualId)
            .put("amount", amount.toLongOrNull() ?: 0L).put("previous_amount", original)
            .put("note", note).put("seen", seen))
    }
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (status) {
                "approved" -> "تسویهٔ تأییدشده"
                "pending" -> "فیش در انتظار تأیید"
                "rejected" -> "فیش ردشده"
                else -> "ثبت دستی تسویه"
            }, color = Ink)
            if (original > 0) Text(formatMoney(original) + " تومان", color = Ink)
            val reviewNote = payment?.s("review_note").orEmpty()
            if (reviewNote.isNotBlank()) Text(reviewNote, color = Muted)
            proofs.forEachIndexed { index, proof ->
                StoredOrderProof(repository, orderId, proof, index + 1) {
                    val token = proof.s("proof_token")
                    if (token !in viewed) viewed.add(token)
                }
            }
            if (status == "rejected") return@Column
            DesignField("مبلغ تومان", amount, { amount = asciiDigits(it).filter { digit -> digit in '0'..'9' }.take(13) },
                "", keyboardType = KeyboardType.Number, transformation = SettlementAmountTransformation, enabled = !busy)
            DesignField("توضیحات بررسی", note, { note = it.take(BuildConfig.PROOF_MAX_NOTE_CHARS) }, "",
                singleLine = false, enabled = !busy)
            val valid = !busy && (amount.toLongOrNull() ?: 0L) > 0L
            when (status) {
                "pending" -> {
                    GhadirButton("تأیید فیش و ثبت تسویه", { action("approve") }, enabled = valid && seen)
                    GhadirButton("رد فیش", { action("reject") }, enabled = !busy && note.isNotBlank(), secondary = true)
                }
                "approved" -> GhadirButton("اصلاح مبلغ تسویه", { action("adjust") }, enabled = valid && note.isNotBlank(), secondary = true)
                else -> GhadirButton("ثبت تسویه دستی", { action("manual") }, enabled = valid && note.isNotBlank())
            }
        }
    }
}

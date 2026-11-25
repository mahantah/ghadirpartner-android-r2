package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Let the customer review and respond to the latest amendment independently of the checkout. */
@Composable
internal fun OrderAmendmentPanel(api: ApiClient, orderId: Int, orderUpdatedAt: String) {
    val repository = remember(api) { OrderFollowupRepository(api) }
    val scope = rememberCoroutineScope()
    var amendment by remember(orderId) { mutableStateOf<JSONObject?>(null) }
    var decision by rememberSaveable(orderId) { mutableStateOf("") }
    var busy by remember(orderId) { mutableStateOf(false) }
    var error by remember(orderId) { mutableStateOf("") }
    var refresh by remember(orderId) { mutableIntStateOf(0) }
    LaunchedEffect(repository, orderId, orderUpdatedAt, refresh) {
        try {
            amendment = repository.read(orderId).optJSONObject("amendment")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (amendment != null) error = "اصلاحیهٔ تازه دریافت نشد. دوباره تلاش کنید."
        }
    }
    val entry = amendment ?: return
    val status = when (entry.s("status")) {
        "pending" -> "در انتظار پاسخ شما"
        "accepted" -> "تأیید شما ثبت شد. شرکت رسیدگی مالی و ارسال را پیگیری می‌کند."
        "rejected" -> "شما این اصلاحیه را رد کردید."
        else -> return
    }
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("اصلاحیهٔ سفارش · نسخهٔ " + faNumber(entry.i("version")), color = Ink, fontWeight = FontWeight.Bold)
            Text(status, color = Ink)
            Text(entry.s("reason"), color = Ink)
            DetailLine("مبلغ قبلی", formatMoney(entry.l("previous_total")) + " تومان")
            DetailLine("مبلغ پیشنهادی", formatMoney(entry.l("total")) + " تومان")
            val difference = entry.l("difference")
            DetailLine("تغییر مبلغ فاکتور", if (difference == 0L) "بدون تغییر" else
                (if (difference > 0L) "افزایش " else "کاهش ") + formatMoney(kotlin.math.abs(difference)) + " تومان")
            DetailLine("نوع فاکتور", entry.s("invoice"))
            DetailLine("روش ارسال", entry.s("shipping"))
            if (entry.s("address").isNotBlank()) DetailLine("نشانی ارسال", entry.s("address"))
            HorizontalDivider()
            for (row in entry.arr("items").objects()) {
                val type = when (row.s("type")) {
                    "serial" -> "سریال آزاد"
                    "panel" -> "ثبت در پنل"
                    "agent" -> "عامل فروش"
                    else -> ""
                }
                val label = listOf(type, row.s("switch")).filter(String::isNotBlank).joinToString(" · ")
                DetailLine(row.s("product"), faNumber(row.i("quantity")) + " عدد × " + formatMoney(row.l("unit")) + " تومان")
                if (label.isNotBlank()) Text(label, color = Muted)
            }
            for ((key, label) in listOf("cash_amount" to "نقد", "check_amount" to "چک", "credit_amount" to "اعتبار", "wallet_amount" to "کیف پول")) {
                val amount = entry.obj("settlement").l(key)
                if (amount > 0L) DetailLine(label, formatMoney(amount) + " تومان")
            }
            if (error.isNotBlank()) {
                Text(error, color = Danger)
                TextButton(onClick = { error = ""; refresh++ }, enabled = !busy) { Text("به‌روزرسانی اصلاحیه") }
            }
            if (entry.s("status") == "pending") {
                GhadirButton("تأیید اصلاحیه", { decision = "accept_amendment:" + entry.s("id") }, enabled = !busy)
                GhadirButton("رد اصلاحیه", { decision = "reject_amendment:" + entry.s("id") }, secondary = true, enabled = !busy)
            }
        }
    }
    val decisionId = decision.substringAfter(':', "")
    if (decisionId.isNotEmpty() && decisionId == entry.s("id") && entry.s("status") == "pending") {
        val action = decision.substringBefore(':')
        val accept = action == "accept_amendment"
        AlertDialog(
            onDismissRequest = { if (!busy) decision = "" },
            title = { Text(if (accept) "تأیید اصلاحیهٔ سفارش" else "رد اصلاحیهٔ سفارش") },
            text = { Text(if (accept) "تأیید شما برای رسیدگی مالی و ارسال به شرکت اعلام می‌شود." else "رد اصلاحیه به شرکت اعلام می‌شود.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val result = repository.send(orderId, action, JSONObject().put("amendment_id", decisionId))
                            amendment = result.optJSONObject("amendment")
                            error = ""
                            decision = ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "ثبت پاسخ انجام نشد."
                            decision = ""
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (accept) "ثبت تأیید" else "ثبت رد") }
            },
            dismissButton = { TextButton(onClick = { decision = "" }, enabled = !busy) { Text("انصراف") } },
        )
    }
}

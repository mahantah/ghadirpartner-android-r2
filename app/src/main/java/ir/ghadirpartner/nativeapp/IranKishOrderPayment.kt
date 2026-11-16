package ir.ghadirpartner.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Bridge the order's authenticated API to the bank in the system browser. */
internal class IranKishOrderPayment(private val api: ApiClient) {
    private val path = "/api/orders/irankish"

    /** Read the server's configured availability before offering the checkout method. */
    suspend fun available(): Boolean = (api.request("GET", path, null) as JSONObject).b("enabled")

    /** Start the saved server-priced installment and return its browser handoff if still unpaid. */
    suspend fun start(orderId: Int): Uri? {
        val response = action("start", orderId)
        if (response.b("confirmed")) return null
        val id = response.s("id")
        require(id.matches(Regex("[a-f0-9]{20}"))) { "لینک پرداخت سفارش معتبر نیست." }
        return Uri.parse("${api.base}$path/launch").buildUpon().appendQueryParameter("id", id).build()
    }

    /** Reconcile the bank's confirmation with the original order. */
    suspend fun recheck(orderId: Int): JSONObject = action("recheck", orderId)

    private suspend fun action(name: String, orderId: Int): JSONObject =
        api.post(path, JSONObject().put("action", name).put("order_id", orderId)) as JSONObject
}

/** Resume a payment from order details without submitting another order. */
@Composable
internal fun IranKishOrderPaymentActions(
    api: ApiClient,
    order: JSONObject,
    autoOpen: Boolean = false,
    onConfirmed: () -> Unit,
) {
    val saved = order.obj("irankish_order_payment")
    val amount = saved.l("amount")
    if (amount <= 0L) return
    val receipt = saved.obj("receipt")
    if (receipt.l("amount") > 0L) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailLine("پرداخت تأییدشده ایران‌کیش", formatMoney(receipt.l("amount")) + " تومان")
            DetailLine("شماره پیگیری", faDigits(receipt.s("reference")))
        }
        return
    }
    if (order.s("status") !in listOf("در انتظار تأیید سفارش", "ثبت شده")) return

    val orderId = order.i("id")
    val service = remember(api) { IranKishOrderPayment(api) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoOpened by rememberSaveable(orderId) { mutableStateOf(false) }
    var busy by remember(orderId) { mutableStateOf(false) }
    var message by remember(orderId) { mutableStateOf("") }
    suspend fun perform(open: Boolean) {
        if (busy) return
        busy = true
        message = ""
        try {
            if (open) {
                val uri = service.start(orderId)
                if (uri == null) onConfirmed() else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                }
            } else {
                val result = service.recheck(orderId)
                if (result.obj("receipt").l("amount") > 0L) onConfirmed()
                else message = "پرداخت هنوز تأیید نشده است."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = error.message ?: "دریافت وضعیت پرداخت ممکن نشد."
        } finally {
            busy = false
        }
    }
    LaunchedEffect(orderId, autoOpen) {
        if (autoOpen && !autoOpened) {
            autoOpened = true
            perform(true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailLine("مبلغ پرداخت آنلاین", formatMoney(amount) + " تومان")
        GhadirButton("پرداخت با ایران‌کیش", { scope.launch { perform(true) } }, enabled = !busy)
        GhadirButton("بررسی نتیجه پرداخت", { scope.launch { perform(false) } }, enabled = !busy, secondary = true)
        if (message.isNotBlank()) DesignNotice(message)
    }
}

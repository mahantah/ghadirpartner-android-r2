package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Display wallet funds and credit exposure independently for the signed-in account. */
@Composable
internal fun CustomerWallet(api: ApiClient) {
    val context = LocalContext.current
    var account by remember(api) { mutableStateOf<JSONObject?>(null) }
    var error by remember(api) { mutableStateOf("") }
    var paymentError by remember(api) { mutableStateOf("") }
    var topUpDialog by rememberSaveable { mutableStateOf(false) }
    var topUpAmount by rememberSaveable { mutableStateOf("") }
    var attemptId by rememberSaveable { mutableStateOf("") }
    var paymentBusy by remember { mutableStateOf(false) }
    var paymentMessage by remember { mutableStateOf("") }
    val topUps = remember(api) { IranKishWalletPayment(api, IranKishOrderPayment(api)) }
    val scope = rememberCoroutineScope()
    var onlineTopUp by remember(api) { mutableStateOf(false) }
    var retry by remember(api) { mutableIntStateOf(0) }

    suspend fun refresh() {
        error = ""
        onlineTopUp = false
        try {
            account = api.request("GET", "/api/native/credit", null) as JSONObject
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "کیف پول دریافت نشد."
        }
        try {
            val availability = api.get("/api/wallet/irankish") as? JSONObject
            onlineTopUp = availability?.optBoolean("enabled") == true
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // The balance remains available when online payment is temporarily unavailable.
        }
    }
    LaunchedEffect(api, context, retry) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        if (lifecycle == null) {
            refresh()
        } else {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh() }
        }
    }
    fun refreshPayment() {
        if (attemptId.isBlank() || paymentBusy) return
        scope.launch {
            paymentBusy = true
            paymentError = ""
            try {
                val result = topUps.recheck(attemptId)
                paymentMessage = when {
                    result.b("credited") -> "مبلغ به کیف پول اضافه شد."
                    result.b("failed") -> "شارژ کیف پول ناموفق بود."
                    else -> "شارژ کیف پول هنوز تأیید نشده است."
                }
                if (result.b("credited") || result.b("failed") || result.b("expired")) attemptId = ""
                refresh()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                paymentError = failure.message ?: "بررسی پرداخت ممکن نشد."
            } finally {
                paymentBusy = false
            }
        }
    }

    val gateway = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { refreshPayment() }
    if (topUpDialog) {
        val amount = topUpAmount.toLongOrNull() ?: 0L
        AlertDialog(
            onDismissRequest = { if (!paymentBusy) topUpDialog = false },
            title = { Text("شارژ کیف پول") },
            text = {
                DesignField(
                    "مبلغ به تومان", topUpAmount,
                    { topUpAmount = asciiDigits(it).filter { digit -> digit in '0'..'9' }.take(15) },
                    "مبلغ شارژ", enabled = !paymentBusy,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
            },
            confirmButton = {
                TextButton(enabled = !paymentBusy && amount > 0L, onClick = {
                    scope.launch {
                        paymentBusy = true
                        paymentError = ""
                        paymentMessage = ""
                        try {
                            val id = topUps.start(amount)
                            val uri = topUps.launchUri(id)
                            attemptId = id
                            topUpDialog = false
                            gateway.launch(IranKishPaymentActivity.intent(context, uri))
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            paymentError = failure.message ?: "درگاه پرداخت باز نشد."
                        } finally {
                            topUpDialog = false
                            paymentBusy = false
                        }
                    }
                }) { Text("ورود به درگاه") }
            },
            dismissButton = { TextButton(enabled = !paymentBusy, onClick = { topUpDialog = false }) { Text("انصراف") } },
        )
    }
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val snapshot = account
            if (snapshot == null) {
                Text(error.ifBlank { "در حال دریافت کیف پول…" }, color = if (error.isBlank()) Muted else Danger)
                if (error.isNotBlank()) GhadirButton("تلاش دوباره", { retry++ }, secondary = true)
            } else {
                val wallet = snapshot.obj("wallet")
                DetailLine("موجودی کیف پول", formatMoney(wallet.l("balance")) + " تومان")
                DetailLine("موجودی قابل استفاده کیف پول", formatMoney(wallet.l("available")) + " تومان")
                DetailLine("موجودی رزروشده کیف پول", formatMoney(wallet.l("reserved")) + " تومان")
                if (onlineTopUp) {
                    GhadirButton("شارژ کیف پول با ایران‌کیش", { topUpDialog = true },
                        enabled = !paymentBusy, modifier = Modifier.fillMaxWidth())
                    if (attemptId.isNotBlank()) {
                        GhadirButton("بررسی وضعیت شارژ", { refreshPayment() }, enabled = !paymentBusy, secondary = true)
                    }
                }
                if (paymentMessage.isNotBlank()) Text(paymentMessage, color = Ink)
                if (paymentError.isNotBlank()) Text(paymentError, color = Danger)
                if (error.isNotBlank()) {
                    Text(error, color = Danger)
                    GhadirButton("به‌روزرسانی موجودی", { retry++ }, secondary = true)
                }
                DetailLine("سقف اعتبار", formatMoney(snapshot.l("limit")) + " تومان")
                DetailLine("اعتبار قابل استفاده", formatMoney(snapshot.l("available")) + " تومان")
                DetailLine("اعتبار رزروشده", formatMoney(snapshot.l("reserved")) + " تومان")
                DetailLine("بدهی اعتباری", formatMoney(snapshot.l("outstanding")) + " تومان")
                for (entry in wallet.arr("transactions").objects()) {
                    val label =
                        when (entry.s("source")) {
                            "order_cancellation" -> "بازگشت وجه"
                            "order_purchase" -> "پرداخت سفارش"
                            "credit_repayment" -> "بازپرداخت اعتبار"
                            "manual_credit" -> "شارژ کیف پول"
                            "irankish_credit" -> "شارژ آنلاین ایران‌کیش"
                            else -> "گردش کیف پول"
                        }
                    val reference = entry.s("number").ifBlank { entry.s("reference") }
                    val title = if (reference.isBlank()) label else "$label · $reference"
                    DetailLine(title, formatMoney(entry.l("amount")) + " تومان")
                }
            }
        }
    }
}

/** Start and reconcile a native top-up using the customer's existing portal session. */
internal class IranKishWalletPayment(
    private val api: ApiClient,
    private val gateway: IranKishOrderPayment,
) {
    private val path = "/api/wallet/irankish"

    /** Return the server-issued top-up ID. */
    suspend fun start(amount: Long): String =
        (api.post(path, JSONObject().put("action", "start_native").put("amount", amount)) as JSONObject).s("id")

    /** Build the protected merchant handoff for the bank page. */
    fun launchUri(id: String): android.net.Uri = gateway.launchUri(id)

    /** Confirm the transfer before refreshing the wallet balance. */
    suspend fun recheck(id: String): JSONObject =
        api.post(path, JSONObject().put("action", "recheck").put("id", id)) as JSONObject
}

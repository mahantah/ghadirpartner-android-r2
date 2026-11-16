package ir.ghadirpartner.nativeapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Display wallet funds and credit exposure independently for the signed-in account. */
@Composable
internal fun CustomerWallet(api: ApiClient) {
    val context = LocalContext.current
    var account by remember(api) { mutableStateOf<JSONObject?>(null) }
    var error by remember(api) { mutableStateOf("") }
    var paymentError by remember(api) { mutableStateOf("") }
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
                    GhadirButton("شارژ آنلاین با ایران‌کیش", {
                        val portal = Uri.parse("${api.base}/#gpIranKishWallet")
                        val intent = Intent(Intent.ACTION_VIEW, portal).addCategory(Intent.CATEGORY_BROWSABLE)
                        try {
                            context.startActivity(intent)
                            paymentError = ""
                        } catch (_: ActivityNotFoundException) {
                            paymentError = "مرورگری برای باز کردن صفحهٔ شارژ پیدا نشد."
                        }
                    }, modifier = Modifier.fillMaxWidth())
                    Text("برای پرداخت، پرتال در مرورگر باز می‌شود و ممکن است ورود لازم باشد.", color = Muted)
                    if (paymentError.isNotBlank()) Text(paymentError, color = Danger)
                }
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

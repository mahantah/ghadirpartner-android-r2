package ir.ghadirpartner.nativeapp

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Display wallet funds and credit exposure independently for the signed-in account. */
@Composable
internal fun CustomerWallet(api: ApiClient) {
    var account by remember(api) { mutableStateOf<JSONObject?>(null) }
    var error by remember(api) { mutableStateOf("") }
    var retry by remember(api) { mutableIntStateOf(0) }
    LaunchedEffect(api, retry) {
        error = ""
        try {
            account = api.request("GET", "/api/native/credit", null) as JSONObject
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "کیف پول دریافت نشد."
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
                            else -> "گردش کیف پول"
                        }
                    DetailLine("$label · ${entry.s("number")}", formatMoney(entry.l("amount")) + " تومان")
                }
            }
        }
    }
}

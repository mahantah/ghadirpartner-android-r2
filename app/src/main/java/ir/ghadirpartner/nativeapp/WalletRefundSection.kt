package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Keep account-owned refund I/O behind a single application service. */
internal class WalletRefundService(private val api: ApiClient) {
    /** Read only requests belonging to the server-authenticated customer. */
    suspend fun list(): List<JSONObject> = (api.request("GET","/api/wallet/refunds",null) as JSONArray).objects()
    /** Reuse the attempt identity so a timeout cannot create another request. */
    suspend fun request(amount: Long, destination: String, requestId: String): JSONObject =
        api.post("/api/wallet/refunds",JSONObject().put("amount",amount).put("destination",destination).put("request_id",requestId)) as JSONObject
}

/** Request a bank refund while retaining funds until finance records the completed transfer. */
@Composable
internal fun WalletRefundSection(api: ApiClient, available: Long) {
    val service=remember(api){WalletRefundService(api)}
    val scope=rememberCoroutineScope()
    var requests by remember {mutableStateOf(emptyList<JSONObject>())}
    var expanded by rememberSaveable {mutableStateOf(false)}
    var amount by rememberSaveable {mutableStateOf("")}
    var destination by rememberSaveable {mutableStateOf("")}
    var attempt by rememberSaveable {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    LaunchedEffect(service) {
        try {requests=service.list()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(failure:Exception){error=failure.message?:"درخواست‌های عودت دریافت نشد."}
    }
    val pending=requests.any {it.s("status") in listOf("pending","approved")}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        GhadirButton("درخواست عودت وجه",{expanded=!expanded},enabled=!busy&&!pending&&available>0L,secondary=true)
        if(pending) Text("درخواست عودت شما در انتظار بررسی مالی است.",color=Muted)
        if(expanded) {
            DesignNotice("پس از تأیید مالی، مبلغ از کیف پول کسر و برای واریز به حساب شما پیگیری می‌شود.")
            DesignField("مبلغ عودت (تومان)",amount,{amount=asciiDigits(it).filter(Char::isDigit).take(15);attempt=""},keyboardType=KeyboardType.Number,enabled=!busy)
            DesignField("شماره شبای حساب خودتان",destination,{destination=asciiDigits(it).uppercase().replace(" ","").take(26);attempt=""},"IR…",enabled=!busy)
            GhadirButton(if(busy)"در حال ارسال…" else "ارسال درخواست به مالی",{
                if(busy)return@GhadirButton
                if(attempt.isBlank())attempt=UUID.randomUUID().toString().replace("-","")
                scope.launch {
                    busy=true;error=""
                    try {service.request(amount.toLong(),destination,attempt);requests=service.list();expanded=false;amount="";destination="";attempt=""}
                    catch(cancelled:CancellationException){throw cancelled}
                    catch(failure:Exception){error=failure.message?:"ثبت درخواست انجام نشد."}
                    finally {busy=false}
                }
            },enabled=!busy&&(amount.toLongOrNull()?:0L) in 1..available&&destination.matches(Regex("IR[0-9]{24}")))
        }
        requests.reversed().forEach {request ->
            DetailLine(when(request.s("status")){"approved"->"تأیید مالی؛ در انتظار واریز";"paid"->"عودت واریزشده";"rejected"->"عودت ردشده";else->"عودت در انتظار بررسی"},formatMoney(request.l("amount"))+" تومان")
            if(request.s("reference").isNotBlank())Text("پیگیری: "+faDigits(request.s("reference")),color=Muted)
        }
        if(error.isNotBlank())ErrorBanner(error){error=""}
    }
}

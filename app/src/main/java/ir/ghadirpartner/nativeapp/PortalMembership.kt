package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Submit a request for staff review using the existing customer registration endpoint. */
@Composable
internal fun PortalMembership(api: ApiClient, initialMobile: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf(asciiDigits(initialMobile).filter { it in '0'..'9' }.take(11)) }
    var code by remember { mutableStateOf("") }
    var verifiedTarget by remember { mutableStateOf("") }
    var retryAfter by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(retryAfter) {
        if (retryAfter > 0) {
            kotlinx.coroutines.delay(1000)
            retryAfter--
        }
    }
    var nationalId by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PortalBackHeader("درخواست عضویت", onBack)
        if (submitted) {
            DesignNotice("درخواست ثبت شد. پس از تأیید مدیر می‌توانید وارد حساب شوید.")
            GhadirButton("بازگشت به ورود", onBack)
        } else {
            DesignField("نام و نام خانوادگی", name, { name = it }, enabled = !busy)
            DesignField("نام فروشگاه یا شرکت", company, { company = it }, enabled = !busy)
            DesignField(
                "شماره همراه", mobile, {
                    mobile = asciiDigits(it).filter { digit -> digit in '0'..'9' }.take(11)
                    code = ""
                },
                enabled = !busy, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            )
            GhadirButton(
                if (retryAfter > 0) "ارسال مجدد پس از ${faDigits(retryAfter.toString())} ثانیه" else "ارسال کد تأیید",
                {
                    if (!busy && retryAfter == 0) scope.launch {
                        busy = true
                        error = ""
                        try {
                            val response = api.post("/api/native/membership/code", JSONObject().put("mobile", mobile)) as JSONObject
                            verifiedTarget = mobile
                            code = ""
                            retryAfter = response.optInt("retry_after", 60).coerceAtLeast(1)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            error = exception.message ?: "ارسال کد ناموفق بود."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && retryAfter == 0 && mobile.matches(Regex("09[0-9]{9}")),
                secondary = true,
            )
            if (verifiedTarget == mobile) {
                DesignField(
                    "کد شش‌رقمی پیامک", code,
                    { code = asciiDigits(it).filter { digit -> digit in '0'..'9' }.take(6) },
                    enabled = !busy, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
            }
            DesignField(
                "کد ملی", nationalId, { nationalId = asciiDigits(it).filter { digit -> digit in '0'..'9' }.take(10) },
                enabled = !busy, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            DesignField("شهر", city, { city = it }, enabled = !busy)
            DesignField("نشانی", address, { address = it }, enabled = !busy, singleLine = false)
            OutlinedTextField(
                password, { password = it.take(72) }, label = { Text("رمز ورود") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
            )
            if (error.isNotEmpty()) ErrorBanner(error) { error = "" }
            GhadirButton(
                if (busy) "در حال ارسال…" else "ارسال درخواست عضویت",
                {
                    if (!busy) scope.launch {
                        busy = true
                        error = ""
                        try {
                            val request = JSONObject()
                                .put("name", name.trim()).put("company", company.trim()).put("mobile", mobile)
                                .put("otp", code).put("national_id", nationalId).put("city", city.trim()).put("address", address.trim()).put("password", password)
                            api.post("/api/native/membership/register", request)
                            password = ""
                            submitted = true
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            error = exception.message ?: "ارسال درخواست ناموفق بود."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && name.trim().length >= 3 && mobile.matches(Regex("09[0-9]{9}")) &&
                    verifiedTarget == mobile && code.length == 6 && nationalId.length == 10 && password.toByteArray().size in 8..72 && password.length >= 8,
            )
        }
        PortalPrivacyLink()
    }
}

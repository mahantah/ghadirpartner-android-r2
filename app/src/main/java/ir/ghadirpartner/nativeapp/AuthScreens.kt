package ir.ghadirpartner.nativeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().portalBackdrop(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ghadir_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(126.dp).clip(RoundedCornerShape(34.dp))
            )
            Spacer(Modifier.height(22.dp))
            Text("قدیر پارتنر", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("راهکار یکپارچه فروش و همکاری", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(color = Orange, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun LoginScreen(api: ApiClient, onLoggedIn: () -> Unit) {
    val scope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    var loginPrompt by remember {mutableStateOf<androidx.biometric.BiometricPrompt?>(null)}
    DisposableEffect(Unit){onDispose{loginPrompt?.cancelAuthentication()}}
    var identity by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
    var visible by remember {mutableStateOf(false)};var loading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")};var resetOpen by remember {mutableStateOf(false)}
    var otpOpen by remember {mutableStateOf(false)};var otp by remember {mutableStateOf("")}
    var countdown by remember {mutableIntStateOf(0)}
    LaunchedEffect(countdown){if(countdown>0){kotlinx.coroutines.delay(1000);countdown--}}
    fun requestOtp(){scope.launch {loading=true;error="";try {
        val response=api.post("/api/native/login-otp/request",JSONObject().put("mobile",identity)) as JSONObject
        countdown=response.i("retry_after").coerceAtLeast(60);otpOpen=true
    }catch(e:Exception){error=e.message?:"ارسال کد ناموفق بود"}finally{loading=false}}}
    Column(Modifier.fillMaxSize().portalBackdrop().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=84.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){Text(if(otpOpen) "کد تأیید" else "ورود به قدیر پارتنر",color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("قدیر پارتنر",color=Muted,fontSize=12.sp)}
            TextButton(onClick={if(otpOpen)otpOpen=false else resetOpen=false}){Text("بازگشت",color=Ink)}
        }
        Image(painterResource(R.drawable.ghadir_logo),"قدیر پارتنر",Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
        Text(if(otpOpen) "کد تأیید را وارد کنید" else "خوش آمدید",color=Ink,fontSize=26.sp,fontWeight=FontWeight.Bold)
        Text(if(otpOpen) "کد ارسال‌شده به شماره "+faDigits(identity) else "برای پیگیری سفارش‌ها وارد حساب شوید.",color=Muted,fontSize=14.sp)
        if(!otpOpen) {
            OutlinedTextField(identity,{identity=asciiDigits(it);error=""},label={Text(if(api.isPortal)"شماره همراه" else "نام کاربری")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
            OutlinedTextField(password,{password=it;error=""},label={Text("رمز عبور")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),visualTransformation=if(visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={TextButton(onClick={visible=!visible}){Text(if(visible)"پنهان" else "نمایش")}})
            GhadirButton(if(loading)"در حال ورود…" else "ورود",{scope.launch{loading=true;error="";try{api.login(identity,password);onLoggedIn()}catch(e:Exception){error=e.message?:"ورود ناموفق بود"}finally{loading=false}}},enabled=!loading&&identity.isNotBlank()&&password.isNotBlank())
            if(api.isPortal)GhadirButton("ورود با کد یک‌بارمصرف",{requestOtp()},enabled=!loading&&identity.matches(Regex("09[0-9]{9}")),secondary=true)
            if(api.isPortal)GhadirButton("ورود با اثر انگشت",{
                val activity=context as? androidx.fragment.app.FragmentActivity
                val allowed=androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                if(activity==null||androidx.biometric.BiometricManager.from(context).canAuthenticate(allowed)!=androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                    error="اثر انگشت یا تشخیص چهره روی این دستگاه فعال نیست."
                } else {
                    val prompt=androidx.biometric.BiometricPrompt(activity,androidx.core.content.ContextCompat.getMainExecutor(context),object:androidx.biometric.BiometricPrompt.AuthenticationCallback(){
                        override fun onAuthenticationSucceeded(result:androidx.biometric.BiometricPrompt.AuthenticationResult){scope.launch{try{api.me();onLoggedIn()}catch(_:Exception){error="نشست شما منقضی شده است؛ با رمز یا کد پیامکی وارد شوید."}}}
                        override fun onAuthenticationError(code:Int,message:CharSequence){error="تأیید هویت انجام نشد؛ دوباره تلاش کنید یا با رمز وارد شوید."}
                    })
                    loginPrompt=prompt
                    prompt.authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("ورود امن قدیر پارتنر").setAllowedAuthenticators(allowed).setNegativeButtonText("استفاده از رمز عبور").build())
                }
            },secondary=true)
            GhadirButton("فراموشی رمز عبور",{resetOpen=true},secondary=true)
            GlassSurface {Text("فعال‌سازی ورود بیومتریک پس از یک ورود موفق انجام می‌شود.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(16.dp))}
        } else {
            OutlinedTextField(otp,{otp=asciiDigits(it).filter(Char::isDigit).take(6)},label={Text("کد یک‌بارمصرف")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(16.dp))
            GhadirButton(if(loading)"در حال بررسی…" else "تأیید و ورود",{scope.launch{loading=true;error="";try{api.post("/api/native/login-otp/confirm",JSONObject().put("mobile",identity).put("otp",otp));onLoggedIn()}catch(e:Exception){error=e.message?:"کد تأیید نشد"}finally{loading=false}}},enabled=otp.length==6&&!loading)
            GhadirButton(if(countdown>0)"ارسال مجدد پس از ${faNumber(countdown)} ثانیه" else "ارسال مجدد کد",{requestOtp()},enabled=countdown==0&&!loading,secondary=true)
            GhadirButton("ویرایش شماره همراه",{otpOpen=false;otp=""},enabled=!loading,secondary=true)
        }
        ErrorBanner(error){error=""}
    }
    if(resetOpen)PasswordResetDialog(api){resetOpen=false}
}

@Composable
private fun PasswordResetDialog(api: ApiClient, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var identity by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeated by remember {mutableStateOf("")}
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text("بازیابی رمز عبور", color = NavyDeep, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        },
        text = {
            Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),horizontalAlignment = Alignment.Start) {
                Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(74.dp).clip(CircleShape).background(Color(0xFFFFE4CF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, null, tint = Orange, modifier = Modifier.size(34.dp))
                    }
                }
                if (step == 1) {
                    OutlinedTextField(
                        value = identity,
                        onValueChange = { identity = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (api.isPortal) "شماره موبایل" else "نام کاربری / موبایل") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                    )
                } else {
                    Text("کد ۶ رقمی پیامک‌شده را وارد کنید.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = otp,
                        onValueChange = { if (it.length <= 6) otp = it.filter(Char::isDigit); error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("کد یکبارمصرف") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("رمز جدید – حداقل ۸ کاراکتر") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                    )
                }
                if(step!=1)OutlinedTextField(repeated,{repeated=it},label={Text("تکرار رمز عبور جدید")},modifier=Modifier.fillMaxWidth(),visualTransformation=PasswordVisualTransformation())
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp)); Text(message, color = Success, fontSize = 12.sp)
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp)); Text(error, color = Danger, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                GhadirButton(
                    text = if (loading) "لطفاً صبر کنید..." else if (step == 1) "ارسال کد" else "تغییر رمز",
                    enabled = !loading,
                    onClick = {
                        scope.launch {
                            loading = true; error = ""; message = ""
                            try {
                                if (step == 1) {
                                    val path = if (api.isPortal) "/api/password-reset/request" else "/api/staff-password-reset/request"
                                    val body = if (api.isPortal) JSONObject().put("mobile", identity) else JSONObject().put("identity", identity)
                                    val result = api.post(path, body) as JSONObject
                                    message = result.optString("message", "درخواست بازیابی بررسی شد.")
                                    val otpRequired = result.optBoolean("otp_required", true)
                                    val smsSent = result.optBoolean("sms_sent", false)
                                    val rateLimited = result.optBoolean("rate_limited", false)
                                    if (otpRequired && (smsSent || rateLimited)) step = 2
                                } else {
                                    require(password==repeated){"رمز جدید و تکرار آن یکسان نیست"}
                                    val path = if (api.isPortal) "/api/password-reset/confirm" else "/api/staff-password-reset/confirm"
                                    val body = JSONObject().put(if (api.isPortal) "mobile" else "identity", identity)
                                        .put("otp", otp).put("password", password)
                                    api.post(path, body)
                                    message = "رمز عبور تغییر کرد."
                                    kotlinx.coroutines.delay(700)
                                    onDismiss()
                                }
                            } catch (e: Exception) { error = e.message ?: "عملیات ناموفق بود" }
                            finally { loading = false }
                        }
                    }
                )
                if (step == 2) {
                    GlassTextButton(
                        onClick = {
                            scope.launch {
                                loading = true; error = ""; message = ""
                                try {
                                    val path = if (api.isPortal) "/api/password-reset/request" else "/api/staff-password-reset/request"
                                    val body = if (api.isPortal) JSONObject().put("mobile", identity) else JSONObject().put("identity", identity)
                                    val result = api.post(path, body) as JSONObject
                                    message = result.optString("message", "درخواست ارسال مجدد ثبت شد.")
                                } catch (e: Exception) { error = e.message ?: "ارسال مجدد ناموفق بود" }
                                finally { loading = false }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("ارسال مجدد کد", color = Ink) }
                }
                GlassTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("انصراف", color = Muted) }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = AppSurface
    )
}

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
                painter = painterResource(R.drawable.brand_logo),
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
fun LoginScreen(api:ApiClient,onLoggedIn:()->Unit) {
    val scope=rememberCoroutineScope()
    val pageScroll=rememberScrollState()
    val context=androidx.compose.ui.platform.LocalContext.current
    var view by remember {mutableStateOf("login")}
    LaunchedEffect(view){pageScroll.scrollTo(0)}
    val loginStore=remember(context){SecureLoginStore(context)}
    val savedLogin=remember {loginStore.load()}
    var rememberLogin by remember {mutableStateOf(savedLogin!=null)}
    var identity by remember {mutableStateOf(savedLogin?.identity.orEmpty())}
    var password by remember {mutableStateOf(savedLogin?.password.orEmpty())}
    var visible by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var otp by remember {mutableStateOf("")}
    var countdown by remember {mutableIntStateOf(0)}
    var biometricRetry by remember {mutableIntStateOf(0)}
    var biometricFailed by remember {mutableStateOf(false)}
    var loginPrompt by remember {mutableStateOf<androidx.biometric.BiometricPrompt?>(null)}
    DisposableEffect(Unit){onDispose{loginPrompt?.cancelAuthentication()}}
    androidx.activity.compose.BackHandler(view!="login"){loginPrompt?.cancelAuthentication();view="login";error=""}
    LaunchedEffect(countdown){if(countdown>0){kotlinx.coroutines.delay(1000);countdown--}}
    LaunchedEffect(view,biometricRetry) {
        if(view=="biometric") {
            val activity=context.fragmentActivity()
            val allowed=androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            if(activity==null||androidx.biometric.BiometricManager.from(context).canAuthenticate(allowed)!=androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                biometricFailed=true;error="اثر انگشت یا تشخیص چهره روی این دستگاه فعال نیست."
            } else {
                val prompt=androidx.biometric.BiometricPrompt(activity,androidx.core.content.ContextCompat.getMainExecutor(context),object:androidx.biometric.BiometricPrompt.AuthenticationCallback(){
                    override fun onAuthenticationSucceeded(result:androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        scope.launch {
                            try {
                                val account=try {api.me()}catch(e:ApiException){
                                    if(e.status!=401)throw e
                                    val saved=loginStore.load()?:throw IllegalStateException("ابتدا با رمز وارد شوید و گزینهٔ مرا به خاطر بسپار را فعال کنید.")
                                    require(biometricEnabled(context,saved.identity)){"ورود بیومتریک برای این حساب فعال نشده است."}
                                    api.login(saved.identity,saved.password)
                                    api.me()
                                }
                                require(api.modeAllowed(account)&&biometricEnabled(context,account.s("username"))){"ابتدا با رمز یا کد پیامکی وارد شوید و ورود بیومتریک را در پروفایل فعال کنید."}
                                onLoggedIn()
                            }catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(e:Exception){biometricFailed=true;error=e.message?:"نشست شما منقضی شده است؛ با رمز یا کد پیامکی وارد شوید."}
                        }
                    }
                    override fun onAuthenticationError(code:Int,message:CharSequence) {
                        if(code==androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON){view="login";error=""}
                        else {biometricFailed=true;error="دوباره تلاش کنید یا با رمز عبور وارد شوید."}
                    }
                })
                loginPrompt=prompt
                prompt.authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("ورود امن قدیر پارتنر").setAllowedAuthenticators(allowed).setNegativeButtonText("استفاده از رمز عبور").build())
            }
        }
    }
    fun requestOtp() {
        if(loading||countdown>0)return
        scope.launch {
            loading=true;error=""
            try {
                val response=api.post("/api/native/login-otp/request",JSONObject().put("mobile",identity)) as JSONObject
                countdown=response.i("retry_after").coerceAtLeast(60);otp="";view="otp"
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"ارسال کد ناموفق بود"}finally{loading=false}
        }
    }
    if(view=="reset") {
        PasswordResetScreen(api,identity){view="login";error=""}
        return
    }
    Column(Modifier.fillMaxSize().background(if(AppAppearance.dark)AppBackground else Color(0xFFF4F7FB)).navigationBarsPadding().imePadding().verticalScroll(pageScroll),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(view=="login")Spacer(Modifier.height(48.dp)) else PortalBackHeader(when(view){"otp"->"کد تأیید";"biometric"->"ورود امن";else->"ورود به قدیر پارتنر"}) {
            loginPrompt?.cancelAuthentication();view="login";error=""
        }
        Surface(modifier=Modifier.padding(horizontal=20.dp).widthIn(max=420.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp),
            color=if(view=="login")AppSurface else Color.Transparent,shadowElevation=if(view=="login")6.dp else 0.dp) {
        Column(Modifier.fillMaxWidth().padding(if(view=="login")20.dp else 0.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(view) {
                "otp"->{
                    DesignState("کد تأیید را وارد کنید","کد ارسال‌شده به شماره "+faDigits(identity),R.drawable.design_otp)
                    DesignOtp(otp,{otp=it})
                    Text("کد شش‌رقمی پیامک را وارد کنید.",color=Muted,fontSize=12.sp)
                    GhadirButton(if(loading)"در حال بررسی…" else "تأیید و ورود",{
                        scope.launch {
                            loading=true;error=""
                            try {
                                api.post("/api/native/login-otp/confirm",JSONObject().put("mobile",identity).put("otp",otp))
                                val account=api.me()
                                if(!api.modeAllowed(account)){api.logout();throw ApiException(403,api.modeError())}
                                onLoggedIn()
                            }catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(e:Exception){error=e.message?:"کد تأیید نشد"}finally{loading=false}
                        }
                    },enabled=otp.length==6&&!loading)
                    GhadirButton(if(countdown>0)"ارسال مجدد پس از "+faNumber(countdown)+" ثانیه" else "ارسال مجدد کد",{requestOtp()},enabled=countdown==0&&!loading,secondary=true)
                    GhadirButton("ویرایش شماره همراه",{view="login";otp=""},enabled=!loading,secondary=true)
                }
                "biometric"->{
                    DesignState(if(biometricFailed)"هویت تأیید نشد" else "تأیید هویت دستگاه",
                        if(biometricFailed)error.ifBlank {"دوباره تلاش کنید یا با رمز عبور وارد شوید."} else "اثر انگشت یا قفل امن دستگاه",
                        if(biometricFailed)R.drawable.design_fingerprint_error else R.drawable.design_fingerprint)
                    if(!biometricFailed)DesignNotice("ورود سریع و امن\n\nبرای تأیید هویت، انگشت خود را روی حسگر دستگاه قرار دهید.")
                    GhadirButton(if(biometricFailed)"تلاش مجدد با اثر انگشت" else "ادامه با بیومتریک",{biometricFailed=false;error="";biometricRetry++})
                    GhadirButton("ورود با رمز عبور",{loginPrompt?.cancelAuthentication();view="login";error=""},secondary=true)
                }
                else->{
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                        Image(painterResource(R.drawable.brand_logo),"قدیر پارتنر",Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("خوش آمدید",color=Ink,fontSize=24.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                    Text(if(api.isPortal)"پرتال همکاران قدیر پرداخت" else "سامانه اتوماسیون فروش عمده",color=Muted,fontSize=14.sp,lineHeight=23.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                    DesignField(if(api.isPortal)"شماره همراه" else "نام کاربری",identity,{identity=asciiDigits(it).take(100);error=""},
                        if(api.isPortal)"۰۹۱۲…" else "نام کاربری",R.drawable.r54_phone,
                        keyboardType=if(api.isPortal)androidx.compose.ui.text.input.KeyboardType.Phone else androidx.compose.ui.text.input.KeyboardType.Text)
                    DesignField("رمز عبور",password,{password=it;error=""},"رمز عبور",R.drawable.r54_eye,onIcon={visible=!visible},
                        transformation=if(visible)androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation())
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(rememberLogin,{rememberLogin=it;if(!it)loginStore.clear()},enabled=!loading)
                        Text("مرا به خاطر بسپار",color=Ink,fontSize=14.sp)
                    }
                    WebLoginButton(if(loading)"در حال ورود…" else "ورود",{
                        scope.launch {loading=true;error="";try{
                            api.login(identity,password)
                            if(rememberLogin)try{loginStore.save(identity,password)}catch(_:Exception){loginStore.clear();rememberLogin=false;android.widget.Toast.makeText(context,"ورود انجام شد؛ ذخیرهٔ رمز روی این دستگاه ممکن نبود.",android.widget.Toast.LENGTH_LONG).show()} else loginStore.clear()
                            onLoggedIn()
                        }
                            catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(e:Exception){error=e.message?:"ورود ناموفق بود"}finally{loading=false}}
                    },enabled=!loading&&identity.isNotBlank()&&password.isNotBlank())
                    if(api.isPortal) {
                        WebLoginButton("ورود با کد یک‌بارمصرف",{requestOtp()},enabled=!loading&&countdown==0&&identity.matches(Regex("09[0-9]{9}")),secondary=true)
                        WebLoginButton("ورود با اثر انگشت",{view="biometric";biometricFailed=false;error=""},enabled=!loading,secondary=true)
                    }
                    WebLoginButton("فراموشی رمز عبور",{view="reset"},enabled=!loading,secondary=true)
                    Text("فعال‌سازی بیومتریک پس از ورود، از بخش پروفایل.",color=Muted,fontSize=12.sp)
                }
            }
            if(error.isNotBlank()&&view!="biometric")ErrorBanner(error){error=""}
        }
        }
    }
}

@Composable
private fun PasswordResetScreen(api:ApiClient,initialIdentity:String,onBack:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val scope=rememberCoroutineScope()
    var identity by remember {mutableStateOf(initialIdentity)}
    var otp by remember {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var repeated by remember {mutableStateOf("")}
    var codeSent by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var message by remember {mutableStateOf("")}
    var countdown by remember {mutableIntStateOf(0)}
    LaunchedEffect(countdown){if(countdown>0){kotlinx.coroutines.delay(1000);countdown--}}
    fun request() {
        if(loading||countdown>0)return
        scope.launch {
            loading=true;error="";message=""
            try {
                val path=if(api.isPortal)"/api/password-reset/request" else "/api/staff-password-reset/request"
                val body=JSONObject().put(if(api.isPortal)"mobile" else "identity",identity)
                val result=api.post(path,body) as JSONObject
                message=result.s("message").ifBlank {"درخواست بازیابی بررسی شد."}
                if(result.optBoolean("otp_required",true)&&(result.b("sms_sent")||result.b("rate_limited"))){codeSent=true;countdown=result.i("retry_after").coerceAtLeast(60)}
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"ارسال کد ناموفق بود"}finally{loading=false}
        }
    }
    Column(Modifier.fillMaxSize().portalBackdrop().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PortalBackHeader("بازیابی رمز عبور",onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DesignNotice("شماره همراه حساب را تأیید کنید، سپس رمز جدید بسازید.")
            Text("۱. تأیید شماره همراه",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
            DesignField(if(api.isPortal)"شماره همراه" else "نام کاربری یا شماره همراه",identity,{identity=asciiDigits(it);codeSent=false;otp="";error=""},
                if(api.isPortal)"۰۹۱۲…" else "شناسه حساب",R.drawable.r54_phone,enabled=!loading)
            GhadirButton(if(loading)"لطفاً صبر کنید…" else if(countdown>0)"ارسال مجدد پس از "+faNumber(countdown)+" ثانیه" else if(codeSent)"ارسال مجدد کد" else "دریافت کد تأیید",{request()},
                enabled=!loading&&countdown==0&&identity.isNotBlank(),secondary=codeSent)
            if(message.isNotBlank())Text(message,color=Success,fontSize=12.sp)
            if(codeSent) {
                DesignOtp(otp,{otp=it},"کد یک‌بارمصرف • شماره "+faDigits(identity))
                Text("۲. تعیین رمز جدید",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                DesignField("رمز عبور جدید",password,{password=it},"حداقل ۸ کاراکتر",R.drawable.r54_eye,transformation=PasswordVisualTransformation())
                DesignField("تکرار رمز عبور",repeated,{repeated=it},"رمز جدید را دوباره وارد کنید",R.drawable.r54_eye,transformation=PasswordVisualTransformation())
                DesignNotice("هر دو رمز باید یکسان باشند. رمز جدید پس از تأیید کد پیامکی ثبت می‌شود.")
                GhadirButton(if(loading)"در حال ثبت…" else "ثبت رمز جدید",{
                    scope.launch {
                        loading=true;error="";message=""
                        try {
                            require(password==repeated){"رمز جدید و تکرار آن یکسان نیست"}
                            val path=if(api.isPortal)"/api/password-reset/confirm" else "/api/staff-password-reset/confirm"
                            api.post(path,JSONObject().put(if(api.isPortal)"mobile" else "identity",identity).put("otp",otp).put("password",password))
                            SecureLoginStore(context).clear();message="رمز عبور تغییر کرد.";kotlinx.coroutines.delay(700);onBack()
                        }catch(e:kotlinx.coroutines.CancellationException){throw e}
                        catch(e:Exception){error=e.message?:"تغییر رمز ناموفق بود"}finally{loading=false}
                    }
                },enabled=!loading&&otp.length==6&&password.length>=8&&password==repeated)
            }
            if(error.isNotBlank())ErrorBanner(error){error=""}
            GhadirButton("بازگشت به ورود",onBack,secondary=true)
        }
    }
}

@Composable
private fun WebLoginButton(text:String,onClick:()->Unit,enabled:Boolean=true,secondary:Boolean=false) {
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        shape=RoundedCornerShape(10.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(secondary) {
            if(AppAppearance.dark)AppSecondary else Color(0xFFEDF2F7)
        } else Color(0xFFF58220),contentColor=if(secondary)Ink else Color.White)) {
        Text(text,fontSize=14.sp,lineHeight=23.sp,fontWeight=FontWeight.Bold)
    }
}

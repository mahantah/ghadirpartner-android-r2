package ir.ghadirpartner.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Authenticated password change uses the existing server contract, not the reset flow. */
@Composable
internal fun AccountPasswordForm(api:ApiClient,account:String,onChanged:()->Unit,onBusyChanged:(Boolean)->Unit={}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    // Passwords are intentionally kept out of saved-instance state.
    var current by remember {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var repeated by remember {mutableStateOf("")}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    LaunchedEffect(saving){onBusyChanged(saving)}
    DisposableEffect(Unit){onDispose{onBusyChanged(false)}}
    BackHandler(saving){}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        DesignField("رمز فعلی",current,{current=it;error=""},transformation=PasswordVisualTransformation(),keyboardType=KeyboardType.Password,enabled=!saving)
        DesignField("رمز جدید",password,{password=it;error=""},"حداقل ۸ کاراکتر",transformation=PasswordVisualTransformation(),keyboardType=KeyboardType.Password,enabled=!saving)
        DesignField("تکرار رمز جدید",repeated,{repeated=it;error=""},transformation=PasswordVisualTransformation(),keyboardType=KeyboardType.Password,enabled=!saving)
        if(error.isNotBlank())ErrorBanner(error){error=""}
        GhadirButton(if(saving)"در حال تغییر رمز…" else "ثبت رمز جدید",{
            if(saving)return@GhadirButton
            if(password!=repeated){error="رمز جدید و تکرار آن یکسان نیست";return@GhadirButton}
            scope.launch {
                saving=true;error=""
                try {
                    val result=api.post("/api/password",JSONObject().put("current",current).put("new",password)) as JSONObject
                    require(result.b("ok")){"تغییر رمز تأیید نشد؛ دوباره تلاش کنید"}
                    val store=SecureLoginStore(context)
                    val saved=store.load()
                    if(saved?.identity==account) {
                        try {store.save(account,password)} catch(_:Exception){store.clear()}
                    }
                    current="";password="";repeated=""
                    onChanged()
                }catch(e:CancellationException){throw e}
                catch(e:Exception){error=e.message?:"تغییر رمز ناموفق بود"}
                finally{saving=false}
            }
        },enabled=!saving&&current.isNotBlank()&&password.length>=8&&repeated.isNotBlank())
    }
}

/** The verified profile API only allows editing alternate_mobile, never the login identity. */
@Composable
internal fun AccountContactForm(api:ApiClient,me:JSONObject,onUpdated:(JSONObject)->Unit,onBusyChanged:(Boolean)->Unit={}) {
    val scope=rememberCoroutineScope()
    var mobile by rememberSaveable(me.s("username")){mutableStateOf(me.s("alternate_mobile"))}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    LaunchedEffect(saving){onBusyChanged(saving)}
    DisposableEffect(Unit){onDispose{onBusyChanged(false)}}
    BackHandler(saving){}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        DesignField("شماره اصلی ورود",faDigits(me.s("username")),{},enabled=false)
        DesignNotice("برای تغییر شماره اصلی ورود با پشتیبانی تماس بگیرید. در این بخش شماره تماس جایگزین را ثبت یا حذف کنید.")
        DesignField("شماره تماس جایگزین",mobile,{mobile=asciiDigits(it).filter(Char::isDigit).take(11);error=""},"۰۹۱۲…",keyboardType=KeyboardType.Phone,enabled=!saving)
        if(error.isNotBlank())ErrorBanner(error){error=""}
        GhadirButton(if(saving)"در حال ذخیره…" else "ذخیره شماره تماس",{
            if(saving)return@GhadirButton
            scope.launch {
                saving=true;error=""
                try {
                    // Preserve unrelated saved customer fields. Unsaved profile edits are not submitted here.
                    val customer=me.obj("customer")
                    val body=JSONObject().put("alternate_mobile",mobile)
                    listOf("name","company","province","city","address","postal_code").forEach {body.put(it,customer.s(it))}
                    val result=api.post("/api/profile/update",body) as JSONObject
                    require(result.has("alternate_mobile")&&result.s("alternate_mobile")==mobile){"ذخیره شماره تماس تأیید نشد"}
                    onUpdated(result)
                }catch(e:CancellationException){throw e}
                catch(e:Exception){error=e.message?:"تغییر شماره تماس ناموفق بود"}
                finally{saving=false}
            }
        },enabled=!saving&&(mobile.isBlank()||mobile.matches(Regex("09[0-9]{9}")))&&mobile!=me.s("alternate_mobile"))
    }
}

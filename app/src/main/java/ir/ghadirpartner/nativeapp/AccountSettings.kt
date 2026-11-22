package ir.ghadirpartner.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var attempted by remember {mutableStateOf(false)}
    LaunchedEffect(saving){onBusyChanged(saving)}
    DisposableEffect(Unit){onDispose{onBusyChanged(false)}}
    BackHandler(saving){}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        DesignField("رمز فعلی",current,{current=it;error=""},transformation=PasswordVisualTransformation(),keyboardType=KeyboardType.Password,enabled=!saving,fieldError=if(attempted&&current.isBlank())"رمز فعلی را وارد کنید." else error)
        DesignField("رمز جدید",password,{password=it;error=""},"حداقل ۸ کاراکتر",transformation=PasswordVisualTransformation(),keyboardType=KeyboardType.Password,enabled=!saving,fieldError=if(attempted&&password.length<8)"حداقل ۸ کاراکتر وارد کنید." else "")
        DesignField("تکرار رمز جدید",repeated,{repeated=it;error=""},transformation=PasswordVisualTransformation(),keyboardType=KeyboardType.Password,enabled=!saving,fieldError=if(attempted&&(repeated.isBlank()||password!=repeated))"تکرار رمز با رمز جدید یکسان نیست." else "")
        GhadirButton(if(saving)"در حال تغییر رمز…" else "ثبت رمز جدید",{
            if(saving)return@GhadirButton
            attempted=true
            if(current.isBlank()||password.length<8||repeated.isBlank()||password!=repeated)return@GhadirButton
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
        },enabled=!saving)
    }
}

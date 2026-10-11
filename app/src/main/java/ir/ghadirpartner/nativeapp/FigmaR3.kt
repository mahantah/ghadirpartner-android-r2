package ir.ghadirpartner.nativeapp

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SECURITY_PREFS = "ghadir_security"
private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

internal fun biometricEnabled(context: Context, account: String): Boolean =
    context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE)
        .getBoolean("biometric:$account", false)

@Composable
internal fun PortalGlassHeader(title: String, dashboard: Boolean, unread: Int, onOffers: () -> Unit, onBell: () -> Unit) {
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min=84.dp).padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.brand_logo),"قدیر پارتنر",Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f)) {
            Text(title,color=Ink,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold,maxLines=2)
            Text("قدیر پارتنر",color=Muted,fontSize=12.sp,lineHeight=20.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FigmaSvg(if(AppAppearance.dark) "offers_night" else "offers_day","طرح‌ها و آفرها",Modifier.size(40.dp,44.dp).clickable(onClick=onOffers))
            FigmaSvg(if(AppAppearance.dark) "theme_night" else "theme_day",if(AppAppearance.dark) "حالت روز" else "حالت شب",Modifier.size(40.dp,44.dp).clickable {AppAppearance.toggle(context)})
            Box {
                FigmaSvg(if(AppAppearance.dark) "bell_night" else "bell_day","اعلان‌ها",Modifier.size(40.dp,44.dp).clickable(onClick=onBell))
                if(unread>0) Badge(Modifier.align(Alignment.TopEnd),containerColor=Orange,contentColor=Navy){Text(faNumber(unread),fontSize=9.sp)}
            }
        }
    }
}

@Composable
internal fun BiometricPreference(account: String) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE) }
    var enabled by remember(account) { mutableStateOf(biometricEnabled(context, account)) }
    var pending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val prompt = remember { mutableStateOf<BiometricPrompt?>(null) }
    DisposableEffect(Unit) { onDispose { prompt.value?.cancelAuthentication() } }
    GlassSurface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("قفل بیومتریک · ${if (enabled) "روشن" else "خاموش"}", color = Ink, fontWeight = FontWeight.Bold)
                Text(message.ifBlank { if (enabled) "ورود با اثر انگشت یا تشخیص چهره" else "درخواست بیومتریک هنگام ورود خاموش است" },
                    color = Muted, fontSize = 12.sp)
            }
            Switch(checked = enabled, enabled = !pending, onCheckedChange = { requested ->
                if (!requested) {
                    prefs.edit().putBoolean("biometric:$account", false).apply()
                    enabled = false
                    message = ""
                } else {
                    val availability = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
                    val activity = context.fragmentActivity()
                    if (availability != BiometricManager.BIOMETRIC_SUCCESS || activity == null) {
                        message = if (availability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED)
                            "ابتدا اثر انگشت یا چهره را در تنظیمات گوشی ثبت کنید."
                        else "بیومتریک اکنون روی این گوشی در دسترس نیست."
                    } else {
                        pending = true
                        val authentication = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    prefs.edit().putBoolean("biometric:$account", true).apply()
                                    enabled = true; pending = false; message = ""
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    pending = false; message = "فعال‌سازی انجام نشد؛ دوباره تلاش کنید."
                                }
                            })
                        prompt.value = authentication
                        authentication.authenticate(BiometricPrompt.PromptInfo.Builder()
                            .setTitle("فعال‌سازی قفل بیومتریک")
                            .setAllowedAuthenticators(AUTHENTICATORS)
                            .setNegativeButtonText("انصراف").build())
                    }
                }
            }, colors = SwitchDefaults.colors(checkedTrackColor = Orange, checkedThumbColor = Color.White))
        }
    }
}

package ir.ghadirpartner.nativeapp

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
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
        .getBoolean("biometric:$account", true) // Preserve the existing default for upgraded users.

@Composable
internal fun DashboardBrandHeader() {
    Surface(color = Navy, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Image(painterResource(R.drawable.figma_bell), null,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp).size(24.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 5.dp, color = Orange) {
                    Image(painterResource(R.drawable.ghadir_logo), "لوگوی قدیر پارتنر",
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)))
                }
                Text("پیشخوان", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("قدیر پارتنر", color = Color.White, fontSize = 12.sp)
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
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("قفل بیومتریک · ${if (enabled) "روشن" else "خاموش"}", color = Navy, fontWeight = FontWeight.Bold)
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
                    val activity = context as? FragmentActivity
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
            }, colors = SwitchDefaults.colors(checkedTrackColor = Navy, checkedThumbColor = Color.White))
        }
    }
}

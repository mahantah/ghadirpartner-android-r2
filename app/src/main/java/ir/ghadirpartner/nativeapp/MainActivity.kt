package ir.ghadirpartner.nativeapp

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhadirTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NativeAppRoot()
                }
            }
        }
    }
}

@Composable
private fun NativeAppRoot() {
    val context = LocalContext.current
    val api = remember { ApiClient(context) }
    var checking by remember { mutableStateOf(true) }
    var me by remember { mutableStateOf<JSONObject?>(null) }
    var authNonce by remember { mutableIntStateOf(0) }
    var biometricChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authNonce) {
        checking = true
        biometricChecked = false
        me = try {
            val current = api.me()
            if (api.modeAllowed(current)) current else {
                api.logout()
                null
            }
        } catch (_: Exception) { null }
        checking = false
    }

    when {
        checking -> SplashScreen()
        me == null -> LoginScreen(
            api = api,
            onLoggedIn = { authNonce++ }
        )
        api.isPortal && !biometricChecked && biometricEnabled(context, me!!.s("username")) -> BiometricGate(
            onUnlocked = { biometricChecked = true },
            onUnavailable = { biometricChecked = true },
            onUsePassword = {
                scope.launch {
                    api.logout()
                    me = null
                    authNonce++
                }
            }
        )
        api.isPortal -> PortalApp(
            api = api,
            me = me!!,
            onLogout = {
                scope.launch {
                    api.logout()
                    me = null
                    authNonce++
                }
            }
        )
        else -> AutomationApp(
            api = api,
            me = me!!,
            onLogout = {
                scope.launch {
                    api.logout()
                    me = null
                    authNonce++
                }
            }
        )
    }
}


@Composable
private fun BiometricGate(onUnlocked: () -> Unit, onUnavailable: () -> Unit, onUsePassword: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var message by remember { mutableStateOf("برای ورود سریع، هویت خود را با اثر انگشت یا چهره تأیید کنید.") }
    var retryNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(activity, retryNonce) {
        if (activity == null) { onUnavailable(); return@LaunchedEffect }
        val manager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnavailable()
            return@LaunchedEffect
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onUnlocked()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onUsePassword()
                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                        message = "بیومتریک موقتاً قفل شده است. کمی بعد دوباره تلاش کنید یا با رمز وارد شوید."
                    BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED ->
                        message = "ورود بیومتریک لغو شد. برای ادامه دوباره تلاش کنید."
                    else -> message = "تأیید بیومتریک انجام نشد: $errString"
                }
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                message = "اثر انگشت یا چهره شناسایی نشد؛ دوباره تلاش کنید."
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن قدیر پارتنر")
            .setSubtitle("اثر انگشت یا تشخیص چهره دستگاه")
            .setNegativeButtonText("ورود با رمز")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ورود بیومتریک", style = MaterialTheme.typography.headlineSmall, color = NavyDeep)
            Spacer(Modifier.height(12.dp))
            Text(message, textAlign = TextAlign.Center, color = Muted)
            Spacer(Modifier.height(18.dp))
            Button(onClick = {
                message = "برای ورود سریع، هویت خود را با اثر انگشت یا چهره تأیید کنید."
                retryNonce++
            }) { Text("تلاش دوباره") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUsePassword) { Text("ورود با شماره و رمز") }
        }
    }
}

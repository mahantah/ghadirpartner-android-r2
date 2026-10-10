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
        AppAppearance.restore(this)
        window.statusBarColor = android.graphics.Color.rgb(238,244,252)
        window.navigationBarColor = android.graphics.Color.rgb(255,245,233)
        androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            GhadirTheme {
                val dark = AppAppearance.dark
                SideEffect {
                    window.statusBarColor = if(dark) 0xFF10161F.toInt() else 0xFFFFF8F2.toInt()
                    window.navigationBarColor = if(dark) 0xFF1B2430.toInt() else 0xFFFFFFFF.toInt()
                    androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
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
    var passwordVerified by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        val activity = context as FragmentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if(event == androidx.lifecycle.Lifecycle.Event.ON_STOP) biometricChecked = false
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(authNonce) {
        checking = true
        biometricChecked = passwordVerified
        passwordVerified = false
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
            onLoggedIn = { passwordVerified = true; authNonce++ }
        )
        api.isPortal -> Box {
            PortalApp(
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
            if(!biometricChecked && biometricEnabled(context,me!!.s("username"))) {
                androidx.compose.ui.window.Dialog(onDismissRequest={},properties=androidx.compose.ui.window.DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false,usePlatformDefaultWidth=false)) {
                    BiometricGate(
            onUnlocked = { biometricChecked = true },
            onUnavailable = { scope.launch { api.logout(); me = null; authNonce++ } },
            onUsePassword = {
                scope.launch {
                    api.logout()
                    me = null
                    authNonce++
                }
            }
        )
                }
            }
        }
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
    var failed by remember {mutableStateOf(false)}
    var activePrompt by remember {mutableStateOf<BiometricPrompt?>(null)}
    DisposableEffect(Unit){onDispose{activePrompt?.cancelAuthentication()}}

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
                failed=true
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
                failed=true
                message = "اثر انگشت یا چهره شناسایی نشد؛ دوباره تلاش کنید."
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن قدیر پارتنر")
            .setSubtitle("اثر انگشت یا تشخیص چهره دستگاه")
            .setNegativeButtonText("ورود با رمز")
            .setAllowedAuthenticators(authenticators)
            .build()
        activePrompt=prompt
        prompt.authenticate(info)
    }

    Surface(modifier = Modifier.fillMaxSize().portalBackdrop(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal=20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PortalBackHeader("ورود امن",onUsePassword)
            DesignState(if(failed)"هویت تأیید نشد" else "تأیید هویت دستگاه",message,
                if(failed)R.drawable.design_fingerprint_error else R.drawable.design_fingerprint)
            GhadirButton(if(failed)"تلاش مجدد با اثر انگشت" else "ادامه با بیومتریک", onClick = {
                message = "برای ورود سریع، هویت خود را با اثر انگشت یا چهره تأیید کنید."
                failed=false
                retryNonce++
            })
            GhadirButton("ورود با رمز عبور",onUsePassword,secondary=true)
        }
    }
}

package ir.ghadirpartner.nativeapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NavyDeep, Navy, Color(0xFF102F5D)))),
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
            Text("قدیر پارتنر", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("راهکار یکپارچه فروش و همکاری", color = Color(0xFFC5D6EB), fontSize = 13.sp)
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(color = Orange, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun LoginScreen(api: ApiClient, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var resetOpen by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NavyDeep, Navy, Color(0xFFF2F5FA), Canvas)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Image(
                painter = painterResource(R.drawable.ghadir_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(108.dp).clip(RoundedCornerShape(30.dp))
            )
            Spacer(Modifier.height(18.dp))
            Text("قدیر پارتنر", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text(
                if (api.isPortal) "ورود مشتریان" else "ورود اتوماسیون",
                color = Color(0xFFC9D8EA), fontSize = 14.sp
            )
            Spacer(Modifier.height(34.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(30.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.Start) {
                    Text(
                        if (api.isPortal) "خوش آمدید" else "دسترسی امن اتوماسیون",
                        color = NavyDeep, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (api.isPortal) "شماره موبایل ثبت‌شده و رمز عبور را وارد کنید." else "نام کاربری یا شماره موبایل و رمز عبور را وارد کنید.",
                        color = Muted, fontSize = 12.sp, textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = identity,
                        onValueChange = { identity = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text(if (api.isPortal) "شماره موبایل" else "نام کاربری / موبایل") },
                        leadingIcon = { Icon(if (api.isPortal) Icons.Default.PhoneAndroid else Icons.Default.Person, null, tint = Orange) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("رمز عبور") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Orange) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                    )
                    Spacer(Modifier.height(10.dp))
                    ErrorBanner(error) { error = "" }
                    Spacer(Modifier.height(12.dp))
                    GhadirButton(
                        text = if (loading) "در حال ورود..." else "ورود",
                        enabled = !loading && identity.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            scope.launch {
                                loading = true; error = ""
                                try {
                                    api.login(identity, password)
                                    onLoggedIn()
                                } catch (e: Exception) {
                                    error = e.message ?: "ورود ناموفق بود"
                                } finally { loading = false }
                            }
                        }
                    )
                    TextButton(onClick = { resetOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("رمز عبور را فراموش کرده‌ام", color = Navy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (resetOpen) PasswordResetDialog(api = api, onDismiss = { resetOpen = false })
}

@Composable
private fun PasswordResetDialog(api: ApiClient, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var identity by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
            Column(horizontalAlignment = Alignment.Start) {
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
                    TextButton(
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
                    ) { Text("ارسال مجدد کد", color = Navy) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("انصراف", color = Muted) }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

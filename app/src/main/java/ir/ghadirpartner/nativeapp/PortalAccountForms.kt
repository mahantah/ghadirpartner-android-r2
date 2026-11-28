package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Edit the destination used for wallet refunds underneath the profile address. */
@Composable
internal fun AccountBankForm(api: ApiClient, onBusyChanged: (Boolean) -> Unit) {
    val service = remember(api) { PortalAccountService(api) }
    val scope = rememberCoroutineScope()
    var iban by rememberSaveable { mutableStateOf("IR") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(busy) { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    LaunchedEffect(service, retry) {
        if (loaded) return@LaunchedEffect
        try {
            val saved = service.bank()
            iban = saved.iban.ifBlank { "IR" }
            bankName = saved.name
            loaded = true
            error = ""
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            hidden = (failure as? ApiException)?.status == 403
            error = failure.message ?: "اطلاعات بانکی دریافت نشد."
        }
    }
    if (hidden) {
        DesignNotice("دسترسی به اطلاعات بانکی برای این حساب فعال نیست.")
        return
    }
    val bank = AccountBank(iban, bankName.trim())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DesignField("شماره شبا برای عودت وجه", iban, {
            iban = "IR" + asciiDigits(it).filter { digit -> digit in '0'..'9' }.take(24)
            message = ""
        }, "IR…", enabled = loaded && !busy,
            fieldError = if (attempted && !bank.validIban()) "شماره شبای معتبر وارد کنید." else "")
        BankSelectionField(bankName, enabled = loaded && !busy, allowLegacyIssuer = false) { bankName = it; message = "" }
        if (attempted && bank.name.isBlank()) Text("نام بانک را انتخاب کنید.", color = Danger)
        if (error.isNotBlank()) Text(error, color = Danger)
        if (message.isNotBlank()) Text(message, color = Ink)
        if (!loaded && error.isNotBlank()) TextButton(onClick = { retry++ }) { Text("تلاش دوباره") }
        GhadirButton("ذخیره اطلاعات بانکی", {
            attempted = true
            if (!bank.complete || !bank.validIban()) return@GhadirButton
            busy = true
            scope.launch {
                try {
                    val saved = service.saveBank(bank)
                    iban = saved.iban.ifBlank { "IR" }
                    bankName = saved.name
                    error = ""
                    message = "اطلاعات بانکی ذخیره شد."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "ذخیره اطلاعات بانکی انجام نشد."
                } finally {
                    busy = false
                }
            }
        }, secondary = true, enabled = loaded && !busy)
    }
}

/** Manage separately verified sign-ins within the owner's customer account. */
@Composable
internal fun AccountUsersForm(api: ApiClient, onBusyChanged: (Boolean) -> Unit) {
    val service = remember(api) { PortalAccountService(api) }
    val scope = rememberCoroutineScope()
    var access by rememberSaveable { mutableStateOf("viewer") }
    var users by remember { mutableStateOf(emptyList<JSONObject>()) }
    var available by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var issuedMobile by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var codeAttempted by rememberSaveable { mutableStateOf(false) }
    var revoke by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }
    LaunchedEffect(busy) { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }

    fun change(failureField: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        field = failureField
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "انجام درخواست ناموفق بود." }
            finally { busy = false }
        }
    }
    LaunchedEffect(service, retry) {
        try {
            if (service.accessLevel() != "manager") {
                available = false
                error = "مدیریت دسترسی فقط برای مدیر نمایندگی فعال است."
            } else {
                users = service.users()
                available = true
                error = ""
            }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            available = false
            error = if ((failure as? ApiException)?.status == 404) "سطح‌بندی کاربران پس از به‌روزرسانی سرور فعال می‌شود."
            else failure.message ?: "فهرست کاربران دریافت نشد."
        }
    }
    val validMobile = mobile.matches(Regex("09[0-9]{9}"))
    val validPassword = password.toByteArray().size in 8..72
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (available) {
            users.forEach { user ->
                DetailLine(user.s("name"), faDigits(user.s("mobile")) + " · " + DelegateAccess.label(user.s("access_level")))
                TextButton(onClick = { revoke = user }, enabled = !busy) { Text("لغو دسترسی " + user.s("name")) }
            }
            DesignNotice("کاربر با شماره و رمز جداگانه وارد همین پنل می‌شود. کد تأیید به شمارهٔ او ارسال می‌شود.")
            DropdownField("سطح دسترسی", DelegateAccess.label(access), DelegateAccess.levels.keys.toList(), DelegateAccess::label) { access = it }
            DetailLine("محدودیت‌ها", when (access) {
                "sales" -> "ثبت سفارش با واریز یا چک و مدیریت نشانی. اعتبار، کیف پول، پرداخت آنلاین و مدیریت کاربران مجاز نیست."
                "finance" -> "مشاهده و پیگیری مالی و ثبت فیش بازپرداخت. ثبت سفارش، برداشت و مدیریت کاربران در اختیار مدیر است."
                else -> "مشاهدهٔ کالاها، سفارش‌ها و سریال‌ها. امکان ثبت سفارش یا عملیات مالی ندارد."
            })
            DesignField("نام کاربر", name, { name = it.take(80) }, enabled = !busy,
                fieldError = if (attempted && name.trim().length < 3) "نام کاربر را وارد کنید." else "")
            DesignField("شماره همراه کاربر", mobile, {
                mobile = asciiDigits(it).filter(Char::isDigit).take(11)
                code = ""
                issuedMobile = ""
                error = ""
            }, keyboardType = KeyboardType.Phone, enabled = !busy,
                fieldError = if (codeAttempted && !validMobile) "شماره همراه معتبر وارد کنید." else if (field == "mobile") error else "")
            GhadirButton(if (issuedMobile == mobile && validMobile) "ارسال دوباره کد" else "ارسال کد تأیید", {
                codeAttempted = true
                if (validMobile) change("mobile") { service.sendCode(mobile); issuedMobile = mobile }
            }, secondary = true, enabled = !busy)
            if (issuedMobile.isNotBlank() && issuedMobile == mobile) {
                DesignField("کد پیامکی کاربر", code, { code = asciiDigits(it).filter(Char::isDigit).take(6); error = "" },
                    keyboardType = KeyboardType.Number, enabled = !busy,
                    fieldError = if (attempted && code.length != 6) "کد ۶ رقمی را وارد کنید." else if (field == "code") error else "")
                DesignField("رمز ورود کاربر", password, { password = it.take(72) }, transformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password, enabled = !busy,
                    fieldError = if (attempted && !validPassword) "رمز معتبر با حداقل ۸ کاراکتر وارد کنید." else "")
                GhadirButton("ثبت دسترسی کاربر", {
                    attempted = true
                    if (name.trim().length >= 3 && code.length == 6 && validPassword) change("code") {
                        val created = service.addUser(name.trim(), mobile, code, password, access)
                        users = users + created
                        name = ""; mobile = ""; code = ""; password = ""; issuedMobile = ""; attempted = false; codeAttempted = false
                    }
                }, enabled = !busy)
            }
        }
        if (error.isNotBlank() && field.isBlank()) {
            Text(error, color = Danger)
            if (!available) TextButton(onClick = { retry++ }) { Text("تلاش دوباره") }
        }
    }
    revoke?.let { user ->
        AlertDialog(
            onDismissRequest = { revoke = null }, title = { Text("لغو دسترسی " + user.s("name")) },
            confirmButton = { TextButton(onClick = {
                revoke = null
                change("") { service.revoke(user.i("id")); users = users.filterNot { it.i("id") == user.i("id") } }
            }) { Text("لغو دسترسی") } },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text("انصراف") } },
        )
    }
}

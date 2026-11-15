package ir.ghadirpartner.nativeapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

internal fun asciiDigits(value: String): String =
    value.map { if (it.isDigit()) Character.digit(it, 10).toString() else it.toString() }.joinToString("")

@Composable
internal fun SettlementForm(
    api: ApiClient,
    invoiceTotal: Long,
    invoiceType: String,
    value: JSONObject,
    onBusyChanged: (Boolean) -> Unit = {},
    onChange: (JSONObject) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTotal by rememberUpdatedState(invoiceTotal)
    val currentValue by rememberUpdatedState(value)
    val updateSettlement by rememberUpdatedState(onChange)
    fun patch(block: (JSONObject) -> Unit) {
        val next = JSONObject(currentValue.toString())
        block(next)
        updateSettlement(refreshSettlementTotals(next, currentTotal))
    }
    fun set(key: String, v: Any) = patch { it.put(key, v) }
    fun amountText(part: String): String = value.optLong("${part}_amount").takeIf { it > 0L }?.toString() ?: ""
    fun setAmount(part: String, raw: String) {
        val digits = asciiDigits(raw).filter(Char::isDigit).take(15)
        val amount = digits.toLongOrNull() ?: 0L
        patch { it.put("${part}_amount", amount) }
    }
    var credit by rememberSaveable(stateSaver = JsonSaver) { mutableStateOf(JSONObject()) }
    var creditLoaded by rememberSaveable { mutableStateOf(false) }
    var creditLoadedAt by rememberSaveable { mutableLongStateOf(0L) }
    var loadedRetry by rememberSaveable { mutableIntStateOf(-1) }
    var creditLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    val creditActive = value.b("credit_enabled") && value.optLong("credit_amount") > 0L
    LaunchedEffect(busy, creditLoading) { onBusyChanged(busy || creditLoading) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    LaunchedEffect(creditActive, retry) {
        error = ""
        if (!creditActive) return@LaunchedEffect
        if (creditLoaded && loadedRetry == retry && System.currentTimeMillis() - creditLoadedAt in 0L..60000L) return@LaunchedEffect
        creditLoading = true
        creditLoaded = false
        try {
            credit = api.get("/api/native/credit") as JSONObject
            creditLoaded = true
            creditLoadedAt = System.currentTimeMillis()
            loadedRetry = retry
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "دریافت اعتبار ناموفق بود"
        } finally {
            creditLoading = false
        }
    }
    var imageTarget by rememberSaveable { mutableStateOf("cash") }
    fun attachFiles(uris: List<Uri>, target: String, temporaryFile: java.io.File? = null) {
        scope.launch {
            busy = true
            error = ""
            try {
                val existing = PaymentProofSelection.read(currentValue, target)
                val encoded = PaymentProofEncoder(context.contentResolver).encode(
                    uris, BuildConfig.PROOF_MAX_FILES_PER_KIND - existing.size,
                )
                patch { PaymentProofSelection.set(it, target, (existing + encoded).distinct()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "انتخاب تصویر ناموفق بود"
            } finally {
                temporaryFile?.delete()
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) attachFiles(uris, imageTarget)
    }
    val chequePhoto = remember(context) { java.io.File(context.cacheDir, "cheque-capture/cheque.jpg") }
    val chequePhotoUri = remember(context, chequePhoto) {
        androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", chequePhoto)
    }
    val chequeCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        if (captured) attachFiles(listOf(chequePhotoUri), "check", chequePhoto) else chequePhoto.delete()
    }
    val cashFiles = PaymentProofSelection.read(value, "cash").size
    val checkFiles = PaymentProofSelection.read(value, "check").size
    val deferProof = value.b("upload_proof_later")
    val remaining = settlementRemaining(value, invoiceTotal)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesignNotice("روش تسویه را می‌توانید ترکیب کنید. مبلغ هر بخش را جداگانه وارد کنید.")
        DetailLine("مبلغ فاکتور", formatMoney(invoiceTotal) + " تومان")
        DetailLine("مانده فاکتور (برای واحد مالی)", formatMoney(remaining) + " تومان")
        if (invoiceType == "رسمی") {
            DesignNotice("فاکتور رسمی — فروشنده: هوشمند پرداز پویا تجارت قدیر — شناسه ملی ۱۴۰۱۰۳۶۶۳۹۵")
        }
        SplitMethodRow("نقد", value.b("cash_enabled"), amountText("cash"), { enabled ->
            patch { toggleSettlementPart(it, "cash", enabled, currentTotal) }
        }, { setAmount("cash", it) })
        SplitMethodRow("چک", value.b("check_enabled"), amountText("check"), { enabled ->
            patch { toggleSettlementPart(it, "check", enabled, currentTotal) }
        }, { setAmount("check", it) })
        SplitMethodRow("اعتباری", value.b("credit_enabled"), amountText("credit"), { enabled ->
            patch { toggleSettlementPart(it, "credit", enabled, currentTotal) }
        }, { setAmount("credit", it) })
        if (value.b("cash_enabled") && invoiceType == "غیررسمی") {
            DetailLine("صاحب حساب", BuildConfig.CHEQUE_INFORMAL_PAYEE)
            DetailLine("شماره کارت", "\u2066${BuildConfig.INFORMAL_CASH_CARD}\u2069")
            DetailLine("شماره شبا", "\u2066${BuildConfig.INFORMAL_CASH_IBAN}\u2069")
        }
        if (value.b("cash_enabled") && value.optLong("cash_amount") > 0L) {
            DesignNotice("تسویه نقدی\n\nثبت رسید پرداخت برای بررسی واحد مالی.")
            DesignField("شماره پیگیری", value.s("tracking_number"), { set("tracking_number", asciiDigits(it).take(100)) }, "شماره پیگیری انتقال — در صورت پرداخت", enabled = !busy)
            GhadirButton(
                if (busy) "در حال آماده‌سازی فایل‌ها…" else "افزودن عکس یا PDF رسید",
                { imageTarget = "cash"; picker.launch(PaymentProofSelection.mimeTypes) },
                enabled = !busy && !deferProof && cashFiles < BuildConfig.PROOF_MAX_FILES_PER_KIND,
                secondary = true,
            )
            if (cashFiles > 0) GhadirButton(
                "${faNumber(cashFiles)} فایل رسید انتخاب شد · پاک کردن انتخاب",
                { patch { PaymentProofSelection.set(it, "cash", emptyList()) } },
                enabled = !busy, secondary = true,
            )
        }
        if (value.b("check_enabled") && value.optLong("check_amount") > 0L) {
            val payeeNote = ChequePayeePolicy().instructionsFor(invoiceType)
            DesignNotice("تسویه با چک\n\n$payeeNote")
            DesignField("شناسه صیادی", value.s("check_number"), { set("check_number", asciiDigits(it).filter(Char::isDigit).take(16)) }, "شناسه ۱۶ رقمی", keyboardType = KeyboardType.Number, enabled = !busy)
            BankSelectionField(value.s("bank_name"), !busy) { set("bank_name", it) }
            PersianDateSelectionField("سررسید", value.s("due_date"), !busy) { set("due_date", it) }
            GhadirButton("گرفتن عکس چک", {
                error = ""
                val directory = chequePhoto.parentFile
                if (directory == null || (!directory.isDirectory && !directory.mkdirs())) {
                    error = "آماده‌سازی محل عکس ممکن نشد."
                } else {
                    try {
                        chequeCamera.launch(chequePhotoUri)
                    } catch (_: android.content.ActivityNotFoundException) {
                        error = "برنامهٔ دوربین در دسترس نیست. تصویر چک را از گالری انتخاب کنید."
                    }
                }
            }, enabled = !busy && !deferProof && checkFiles < BuildConfig.PROOF_MAX_FILES_PER_KIND, secondary = true)
            GhadirButton(
                if (busy) "در حال آماده‌سازی فایل‌ها…" else "افزودن عکس یا PDF چک",
                { imageTarget = "check"; picker.launch(PaymentProofSelection.mimeTypes) },
                enabled = !busy && !deferProof && checkFiles < BuildConfig.PROOF_MAX_FILES_PER_KIND,
                secondary = true,
            )
            if (checkFiles > 0) GhadirButton(
                "${faNumber(checkFiles)} فایل چک انتخاب شد · پاک کردن انتخاب",
                { patch { PaymentProofSelection.set(it, "check", emptyList()) } },
                enabled = !busy, secondary = true,
            )
        }
        if (listOf("cash", "check").any { value.b("${it}_enabled") && value.optLong("${it}_amount") > 0L }) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = deferProof,
                    onCheckedChange = { checked -> set("upload_proof_later", checked) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "ارسال تصویر بعداً" },
                )
                Text("عکس‌ها را بعداً در جزئیات سفارش ثبت می‌کنم.")
            }
        }
        if (creditActive) {
            GlassSurface {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اعتبار حساب شما", color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    when {
                        creditLoading -> Text("در حال دریافت اعتبار…", color = Muted)
                        creditLoaded && credit.b("configured") -> {
                            DetailLine("اعتبار قابل استفاده", formatMoney(credit.l("available")) + " تومان")
                            DetailLine("مبلغ اعتباری این سفارش", formatMoney(value.optLong("credit_amount")) + " تومان")
                        }
                        creditLoaded -> Text("سقف اعتبار برای این حساب ثبت نشده است؛ درخواست شما توسط واحد مالی بررسی می‌شود.", color = Muted)
                        else -> Text("اطلاعات اعتبار دریافت نشده است.", color = Muted)
                    }
                }
            }
            if (creditLoaded && credit.s("due_date").isNotBlank()) {
                DesignField("سررسید توافق‌شده", faDigits(credit.s("due_date")), {}, icon = R.drawable.design_calendar, enabled = false)
            }
            CreditCommitment(value, onChange)
            if (error.isNotBlank()) GhadirButton("دریافت دوباره اعتبار", { retry++ }, secondary = true, enabled = !creditLoading)
        }
        if (error.isNotBlank()) Text(error, color = Danger)
    }
}

@Composable
private fun SplitMethodRow(
    label: String,
    enabled: Boolean,
    amount: String,
    onEnabled: (Boolean) -> Unit,
    onAmount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = "فعال‌سازی $label" },
            )
            Text(label, color = Ink, modifier = Modifier.weight(1f))
        }
        if (enabled) {
            DesignField(
                "$label — مبلغ (تومان)",
                amount,
                onAmount,
                "مبلغ به تومان",
                keyboardType = KeyboardType.Number,
                transformation = SettlementAmountTransformation,
                textDirection = TextDirection.Ltr,
                textAlign = TextAlign.Right,
            )
        }
    }
}

/** Keep the displayed commitment and the submitted explicit consent in the same state. */
@Composable
internal fun CreditCommitment(value: JSONObject, onChange: (JSONObject) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesignNotice("تعهد تسویه\n\n" + BuildConfig.ORDER_CREDIT_COMMITMENT)
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(
                checked = value.b("terms_accepted"),
                onCheckedChange = { accepted ->
                    onChange(
                        JSONObject(value.toString()).put("terms_accepted", accepted)
                            .put("commitment_sha256", if (accepted) BuildConfig.ORDER_CREDIT_COMMITMENT_SHA256 else ""),
                    )
                },
                modifier = Modifier.semantics { contentDescription = "پذیرش تعهد اعتبار" },
            )
            Text("متن تعهد بالا را خواندم و تأیید می‌کنم.", color = Ink, modifier = Modifier.weight(1f))
        }
    }
}

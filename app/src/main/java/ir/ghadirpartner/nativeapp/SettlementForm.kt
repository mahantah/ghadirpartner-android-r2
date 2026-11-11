package ir.ghadirpartner.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

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
    fun patch(block: (JSONObject) -> Unit) {
        val next = JSONObject(value.toString())
        block(next)
        onChange(refreshSettlementTotals(next, currentTotal))
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
    var imageTarget by remember { mutableStateOf("cash") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = imageTarget
        scope.launch {
            busy = true
            error = ""
            try {
                val encoded = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تصویر معتبر نیست" }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 1600 || bounds.outHeight / inSampleSize > 1600) inSampleSize *= 2
                    }
                    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: throw IllegalArgumentException("تصویر خوانده نشد")
                    try {
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        require(stream.size() < 1_000_000) { "حجم تصویر زیاد است؛ تصویر کوچک‌تری انتخاب کنید" }
                        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    } finally {
                        bitmap.recycle()
                    }
                }
                patch {
                    if (target == "check") it.put("check_image_base64", encoded).put("image_base64", encoded)
                    else it.put("cash_image_base64", encoded)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "انتخاب تصویر ناموفق بود"
            } finally {
                busy = false
            }
        }
    }
    val remaining = settlementRemaining(value, invoiceTotal)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesignNotice("روش تسویه را می‌توانید ترکیب کنید. مبلغ هر بخش را جداگانه وارد کنید.")
        DetailLine("مبلغ فاکتور", formatMoney(invoiceTotal) + " تومان")
        DetailLine("مانده فاکتور (برای واحد مالی)", formatMoney(remaining) + " تومان")
        if (invoiceType == "رسمی") {
            DesignNotice("فاکتور رسمی — فروشنده: هوشمند پرداز پویا تجارت قدیر — شناسه ملی ۱۴۰۱۰۳۶۶۳۹۵")
        }
        SplitMethodRow("نقد", value.b("cash_enabled"), amountText("cash"), { enabled ->
            patch { it.put("cash_enabled", enabled); if (!enabled) it.put("cash_amount", 0L) }
        }, { setAmount("cash", it) })
        SplitMethodRow("چک", value.b("check_enabled"), amountText("check"), { enabled ->
            patch { it.put("check_enabled", enabled); if (!enabled) it.put("check_amount", 0L) }
        }, { setAmount("check", it) })
        SplitMethodRow("اعتباری", value.b("credit_enabled"), amountText("credit"), { enabled ->
            patch { it.put("credit_enabled", enabled); if (!enabled) it.put("credit_amount", 0L) }
        }, { setAmount("credit", it) })
        if (value.b("cash_enabled") && value.optLong("cash_amount") > 0L) {
            DesignNotice("تسویه نقدی\n\nثبت رسید پرداخت برای بررسی واحد مالی.")
            DesignField("شماره پیگیری", value.s("tracking_number"), { set("tracking_number", asciiDigits(it).take(100)) }, "شماره پیگیری انتقال — در صورت پرداخت", enabled = !busy)
            GhadirButton(
                if (busy) "در حال آماده‌سازی تصویر…" else if (value.s("cash_image_base64").isNotBlank()) "رسید انتخاب شد؛ تغییر تصویر" else "بارگذاری رسید پرداخت",
                { imageTarget = "cash"; picker.launch("image/*") },
                enabled = !busy,
                secondary = true,
            )
        }
        if (value.b("check_enabled") && value.optLong("check_amount") > 0L) {
            val payeeNote = ChequePayeePolicy().instructionsFor(invoiceType)
            DesignNotice("تسویه با چک\n\n$payeeNote")
            DesignField("شناسه صیادی", value.s("check_number"), { set("check_number", asciiDigits(it).filter(Char::isDigit).take(16)) }, "شناسه ۱۶ رقمی", keyboardType = KeyboardType.Number, enabled = !busy)
            BankSelectionField(value.s("bank_name"), !busy) { set("bank_name", it) }
            PersianDateSelectionField("سررسید", value.s("due_date"), !busy) { set("due_date", it) }
            GhadirButton(
                if (busy) "در حال آماده‌سازی تصویر…" else if (value.s("check_image_base64").isNotBlank() || value.s("image_base64").isNotBlank()) "تصویر چک انتخاب شد؛ تغییر" else "بارگذاری تصویر چک",
                { imageTarget = "check"; picker.launch("image/*") },
                enabled = !busy,
                secondary = true,
            )
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

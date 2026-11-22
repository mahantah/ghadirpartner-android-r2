package ir.ghadirpartner.nativeapp

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Normalize Persian and Arabic input digits before parsing payment details. */
internal fun asciiDigits(value: String): String =
    value.map { if (it.isDigit()) Character.digit(it, 10).toString() else it.toString() }.joinToString("")

/** Present cash and non-cash settlement with editable amounts for each selected method. */
@Composable
internal fun SettlementForm(
    api: ApiClient,
    invoiceTotal: Long,
    invoiceType: String,
    value: JSONObject,
    amountsLocked: Boolean = false,
    agentReviewRequired: Boolean = false,
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
    var credit by rememberSaveable(stateSaver = JsonSaver) { mutableStateOf(JSONObject()) }
    var creditLoaded by rememberSaveable { mutableStateOf(false) }
    var creditLoadedAt by rememberSaveable { mutableLongStateOf(0L) }
    var loadedRetry by rememberSaveable { mutableIntStateOf(-1) }
    var creditLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    val creditActive = value.b("credit_enabled")
    val checkActive = value.b("check_enabled")
    var nonCashOpen by rememberSaveable { mutableStateOf(creditActive || checkActive) }
    var awaitingCredit by rememberSaveable { mutableStateOf(false) }
    val walletAvailable = credit.obj("wallet").l("available").coerceAtLeast(0L)
    val creditAvailable = credit.l("available").coerceAtLeast(0L)
    fun set(key: String, v: Any) = patch { it.put(key, v) }
    fun amountText(part: String): String = value.optLong("${part}_amount").takeIf { it > 0L }?.toString() ?: ""
    fun setAmount(part: String, raw: String) {
        val digits = asciiDigits(raw).filter(Char::isDigit).take(15)
        val amount = digits.toLongOrNull() ?: 0L
        patch {
            when (part) {
                "wallet" -> SettlementSelection(currentTotal).setWallet(it, true, amount, walletAvailable)
                "credit" -> it.put("credit_amount", amount.coerceAtMost(creditAvailable))
                else -> it.put("${part}_amount", amount)
            }
        }
    }
    val orderPayment = remember(api) { IranKishOrderPayment(api) }
    var onlineAvailable by remember(api) { mutableStateOf(false) }
    LaunchedEffect(orderPayment) {
        try {
            onlineAvailable = orderPayment.available()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onlineAvailable = false
        }
    }
    val creditAmount = value.l("credit_amount")
    val creditOverLimit = creditActive && creditLoaded && creditAmount > creditAvailable
    val walletOverLimit = value.b("wallet_enabled") && creditLoaded && value.l("wallet_amount") > walletAvailable
    val walletNeedsBalance = value.b("wallet_enabled") && !creditLoaded
    LaunchedEffect(busy, awaitingCredit, creditActive, creditLoaded, creditOverLimit, walletOverLimit, walletNeedsBalance) {
        onBusyChanged(busy || awaitingCredit || (creditActive && !creditLoaded) || creditOverLimit || walletOverLimit || walletNeedsBalance)
    }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    LaunchedEffect(awaitingCredit, creditLoaded) {
        if (awaitingCredit && creditLoaded) {
            patch { SettlementSelection(currentTotal).selectNonCash(it, "credit", creditAvailable) }
            awaitingCredit = false
        }
    }
    LaunchedEffect(retry) {
        error = ""
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
    val deposits = CashDepositRows.read(value)
    fun editDeposit(index: Int, edit: (CashDeposit) -> CashDeposit) {
        patch { next ->
            val revised = CashDepositRows.read(next).mapIndexed { i, row -> if (i == index) edit(row) else row }
            CashDepositRows.set(next, revised, amountsLocked)
        }
    }
    fun attachCashReceipt(index: Int, uri: Uri) {
        scope.launch {
            busy = true
            error = ""
            try {
                val image = PaymentProofEncoder(context.contentResolver).encode(listOf(uri), 1).single()
                editDeposit(index) { it.copy(image = image) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "انتخاب تصویر فیش ناموفق بود"
            } finally {
                busy = false
            }
        }
    }
    val checkFiles = PaymentProofSelection.read(value, "check").size
    val deferProof = value.b("upload_proof_later")
    val remaining = settlementRemaining(value, invoiceTotal)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DetailLine("مبلغ فاکتور", formatMoney(invoiceTotal) + " تومان")
        DetailLine("مانده فاکتور (برای واحد مالی)", formatMoney(remaining) + " تومان")
        if (invoiceType == "رسمی") {
            DesignNotice("فاکتور رسمی — فروشنده: هوشمند پرداز پویا تجارت قدیر — شناسه ملی ۱۴۰۱۰۳۶۶۳۹۵")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettlementMethodCard("نقد", value.b("cash_enabled"), !busy && !amountsLocked, Modifier.weight(1f)) { enabled ->
                patch { toggleSettlementPart(it, "cash", enabled, currentTotal) }
            }
            SettlementMethodCard("غیر نقد", nonCashOpen, !busy && !amountsLocked, Modifier.weight(1f)) { opened ->
                nonCashOpen = opened
                if (!opened) {
                    awaitingCredit = false
                    patch {
                        it.put("check_enabled", false).put("check_amount", 0L)
                        it.put("credit_enabled", false).put("credit_amount", 0L)
                        if (it.b("cash_enabled") && it.l("cash_amount") == 0L) {
                            toggleSettlementPart(it, "cash", true, currentTotal)
                        }
                    }
                }
            }
        }
        if (nonCashOpen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettlementMethodCard("چک", checkActive && !awaitingCredit, !busy && !amountsLocked, Modifier.weight(1f)) {
                    if (!checkActive || awaitingCredit) {
                        awaitingCredit = false
                        patch { SettlementSelection(currentTotal).selectNonCash(it, "check") }
                    }
                }
                SettlementMethodCard("اعتباری", creditActive || awaitingCredit, !busy && !amountsLocked, Modifier.weight(1f)) {
                    if (!creditActive) awaitingCredit = true
                }
            }
            if (awaitingCredit && creditLoading) Text("در حال دریافت اعتبار…", color = Muted)
        }
        if (value.b("cash_enabled")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettlementMethodCard("واریز به حساب", !value.b("cash_online"), !busy, Modifier.weight(1f)) {
                    set("cash_online", false)
                }
                SettlementMethodCard("پرداخت آنلاین", value.b("cash_online"), onlineAvailable && !busy && !agentReviewRequired, Modifier.weight(1f)) {
                    patch {
                        it.put("cash_online", true).remove("tracking_number")
                        PaymentProofSelection.set(it, "cash", emptyList())
                        it.remove(CashDepositRows.field)
                    }
                }
            }
            val online = value.b("cash_online")
            if (online) {
                SettlementAmountField("پرداخت آنلاین", amountText("cash"), !amountsLocked) { setAmount("cash", it) }
                Text("بعد از ثبت سفارش می‌توانید این مبلغ را در درگاه ایران‌کیش پرداخت کنید.", color = Muted)
            } else {
                when (invoiceType) {
                    "رسمی" -> {
                        DetailLine("صاحب حساب", BuildConfig.CHEQUE_FORMAL_PAYEE)
                        Text("شمارهٔ حساب شرکت در اپ ثبت نشده است.", color = Muted)
                    }
                    "غیررسمی" -> {
                        DetailLine("صاحب حساب", BuildConfig.CHEQUE_INFORMAL_PAYEE)
                        DetailLine("شماره کارت", "\u2066${BuildConfig.INFORMAL_CASH_CARD}\u2069")
                        DetailLine("شماره شبا", "\u2066${BuildConfig.INFORMAL_CASH_IBAN}\u2069")
                    }
                }
                DesignField("شماره پیگیری", value.s("tracking_number"), { set("tracking_number", asciiDigits(it).take(100)) }, "شماره پیگیری انتقال — در صورت پرداخت", enabled = !busy)
                deposits.forEachIndexed { index, deposit ->
                    key(index) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(3f)) {
                                SettlementAmountField("فیش ${faNumber(index + 1)}", deposit.amount.takeIf { it > 0 }?.toString().orEmpty(), !busy) { raw ->
                                    val amount = asciiDigits(raw).filter(Char::isDigit).take(15).toLongOrNull() ?: 0L
                                    editDeposit(index) { it.copy(amount = amount) }
                                }
                            }
                            Box(Modifier.weight(2f)) {
                                ImageUploadButton(if (deposit.image.isBlank()) "بارگذاری عکس" else "تعویض عکس",
                                    enabled = !busy && !deferProof, singleSelection = true) { uris ->
                                    uris.firstOrNull()?.let { attachCashReceipt(index, it) }
                                }
                            }
                        }
                        if (deposit.image.isNotBlank()) ProofThumbnail(deposit.image, "فیش ${faNumber(index + 1)}")
                        if (deposits.size > 1 || deposit.image.isNotBlank()) TextButton(enabled = !busy, onClick = {
                            patch { next ->
                                val rows = CashDepositRows.read(next)
                                val revised = if (rows.size > 1) rows.filterIndexed { i, _ -> i != index }
                                    else rows.map { it.copy(image = "") }
                                CashDepositRows.set(next, revised, amountsLocked)
                            }
                        }) { Text(if (deposits.size > 1) "حذف این فیش" else "حذف عکس فیش") }
                    }
                }
                DetailLine("جمع مبلغ فیش‌ها", formatMoney(deposits.sumOf { it.amount }) + " تومان")
                OutlinedButton(enabled = !busy && deposits.size < BuildConfig.PROOF_MAX_FILES_PER_KIND, onClick = {
                    patch { next -> CashDepositRows.set(next, CashDepositRows.read(next) + CashDeposit(), amountsLocked) }
                }) { Text("+ افزودن فیش") }
            }
        }
        if (nonCashOpen && checkActive && !awaitingCredit) {
            val payeeNote = ChequePayeePolicy().instructionsFor(invoiceType)
            DesignNotice("تسویه با چک\n\n$payeeNote")
            SettlementAmountField("چک", amountText("check"), !amountsLocked) { setAmount("check", it) }
            DesignField("شناسه صیادی", value.s("check_number"), { set("check_number", asciiDigits(it).filter(Char::isDigit).take(16)) }, "شناسه ۱۶ رقمی", keyboardType = KeyboardType.Number, enabled = !busy)
            BankSelectionField(value.s("bank_name"), !busy) { set("bank_name", it) }
            PersianDateSelectionField("سررسید", value.s("due_date"), !busy) { set("due_date", it) }
            ImageUploadButton("بارگذاری چک",enabled=!busy&&!deferProof&&checkFiles<BuildConfig.PROOF_MAX_FILES_PER_KIND,mimeTypes=PaymentProofSelection.mimeTypes) { attachFiles(it,"check") }
            if (checkFiles > 0) GhadirButton(
                "${faNumber(checkFiles)} فایل چک انتخاب شد · پاک کردن انتخاب",
                { patch { PaymentProofSelection.set(it, "check", emptyList()) } },
                enabled = !busy, secondary = true,
            )
        }
        if (listOf("cash", "check").any { value.b("${it}_enabled") }) {
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
        if (nonCashOpen && creditActive) {
            SettlementAmountField("اعتباری", amountText("credit"), enabled = creditLoaded && !busy && !amountsLocked) { setAmount("credit", it) }
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
            if (creditLoaded && creditAvailable < (invoiceTotal - value.l("wallet_amount")).coerceAtLeast(0L)) {
                DesignNotice("اعتبار برای کل فاکتور کافی نیست. ابتدا قسمت غیر نقد را تکمیل کنید، سپس مانده را از قسمت نقد بپردازید.")
            }
            if (creditOverLimit) DesignNotice(if (amountsLocked) "اعتبار برای مبلغ اعلام‌شده کافی نیست. با شرکت تماس بگیرید." else "مبلغ اعتباری را تا سقف اعتبار قابل استفاده کاهش دهید.")
            if (creditAmount > 0L) CreditCommitment(value, onChange)
        }
        if (agentReviewRequired) Text("پرداخت آنلاین و کیف پول پس از بررسی تسویه عامل فروش در دسترس است.", color = Muted)
        if (!agentReviewRequired && creditLoaded && (walletAvailable > 0L || value.b("wallet_enabled"))) {
            DetailLine("موجودی قابل استفاده کیف پول", formatMoney(walletAvailable) + " تومان")
            if (checkActive && !value.b("wallet_enabled")) {
                Text("کیف پول با تسویه چکی ترکیب نمی‌شود.", color = Muted)
            } else if (amountsLocked) {
                if (value.b("wallet_enabled")) DetailLine("سهم کیف پول", formatMoney(value.l("wallet_amount")) + " تومان")
            } else SplitMethodRow("کیف پول", value.b("wallet_enabled"), amountText("wallet"), { enabled ->
                patch { SettlementSelection(currentTotal).setWallet(it, enabled, walletAvailable, walletAvailable) }
            }, { setAmount("wallet", it) })
            if (walletOverLimit) DesignNotice(if (amountsLocked) "موجودی کیف پول برای مبلغ اعلام‌شده کافی نیست. با شرکت تماس بگیرید." else "مبلغ کیف پول را تا سقف موجودی قابل استفاده کاهش دهید.")
        }
        if ((creditActive || awaitingCredit || walletNeedsBalance) && !creditLoaded && error.isNotBlank()) {
            GhadirButton("دریافت دوباره اعتبار و موجودی", { retry++ }, secondary = true, enabled = !creditLoading)
        }
        if (error.isNotBlank()) Text(error, color = Danger)
    }
}

@Composable
private fun SettlementMethodCard(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onSelected: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (selected) Orange.copy(alpha = 0.12f) else AppSurface,
        border = BorderStroke(1.dp, if (selected) Orange else Border),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelected)
                .semantics { contentDescription = "فعال‌سازی $label" }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Ink, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun SettlementAmountField(label: String, amount: String, enabled: Boolean = true, onAmount: (String) -> Unit) {
    DesignField(
        "$label — مبلغ (تومان)",
        amount,
        onAmount,
        "مبلغ به تومان",
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        transformation = SettlementAmountTransformation,
        textDirection = TextDirection.Ltr,
        textAlign = TextAlign.Right,
    )
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
            SettlementAmountField(label, amount, onAmount = onAmount)
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

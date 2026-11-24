package ir.ghadirpartner.nativeapp

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

internal enum class SettlementEditor { Deposit, Online, Check, Credit, Wallet }

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
    validationAttempt: Int = 0,
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
    var editor by rememberSaveable { mutableStateOf<SettlementEditor?>(null) }
    var validateEditor by rememberSaveable { mutableStateOf(false) }
    var amountWarning by rememberSaveable { mutableStateOf("") }
    var uploadError by remember { mutableStateOf("") }
    var uploadField by remember { mutableStateOf("") }

    fun selectNonCash(method: String, availableCredit: Long = 0L) {
        patch { next ->
            val savedAmounts = next.optJSONObject("draft_non_cash_amounts") ?: JSONObject()
            listOf("check", "credit").filter { next.b("${it}_enabled") }.forEach {
                savedAmounts.put(it, next.l("${it}_amount"))
            }
            SettlementSelection(currentTotal).selectNonCash(next, method, availableCredit)
            if (savedAmounts.has(method)) {
                val maximum = if (method == "credit") availableCredit else currentTotal
                next.put("${method}_amount", savedAmounts.l(method).coerceAtMost(maximum))
            }
            next.put("draft_non_cash_amounts", savedAmounts)
        }
    }

    fun openEditor(next: SettlementEditor) {
        validateEditor = false
        editor = next
    }
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
    var mixedCashAvailable by remember(api) { mutableStateOf(false) }
    LaunchedEffect(orderPayment) {
        try {
            val availability = orderPayment.availability()
            onlineAvailable = availability.b("enabled")
            mixedCashAvailable = availability.b("mixed_cash")
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
            selectNonCash("credit", creditAvailable)
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
    fun attachCheckFiles(uris: List<Uri>, registration: Boolean = false) {
        scope.launch {
            busy = true
            uploadError = ""
            uploadField = if (registration) "registration" else "check"
            try {
                val existing = PaymentProofSelection.documents(currentValue, "check")
                    .filterNot { registration && it.note == PaymentProofSelection.checkRegistration }
                    .map { it.data }
                val encoded = PaymentProofEncoder(context.contentResolver).encode(
                    uris, BuildConfig.PROOF_MAX_FILES_PER_KIND - existing.size,
                )
                require(encoded.none(existing::contains)) { "این تصویر چک تکراری است." }
                val notes = if (registration) encoded.associateWith { PaymentProofSelection.checkRegistration } else emptyMap()
                patch { PaymentProofSelection.set(it, "check", (existing + encoded).distinct(), notes) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                uploadError = e.message ?: "انتخاب تصویر ناموفق بود"
            } finally {
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
            uploadError = ""
            uploadField = "deposit:" + index
            try {
                val image = PaymentProofEncoder(context.contentResolver).encode(listOf(uri), 1).single()
                editDeposit(index) { it.copy(image = image) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                uploadError = failure.message ?: "انتخاب تصویر فیش ناموفق بود"
            } finally {
                busy = false
            }
        }
    }
    val checkDocuments = PaymentProofSelection.documents(value, "check")
    val checkFiles = checkDocuments.filterNot { it.note == PaymentProofSelection.checkRegistration }
    val registrationReceipt = checkDocuments.firstOrNull { it.note == PaymentProofSelection.checkRegistration }
    val checkErrors = if (validateEditor) SettlementProofRules.checkFields(value) else emptyMap()
    LaunchedEffect(validationAttempt) {
        if (validationAttempt == 0) return@LaunchedEffect
        val next = when {
            value.b("cash_enabled") && CashDepositRows.error(value).isNotBlank() ->
                if (value.b("cash_online") && CashDepositRows.onlineAmount(value) <= 0L) SettlementEditor.Online else SettlementEditor.Deposit
            checkActive && SettlementProofRules.checkError(value).isNotBlank() -> SettlementEditor.Check
            creditActive && !value.b("terms_accepted") -> SettlementEditor.Credit
            else -> null
        }
        if (next != null) {
            editor = next
            validateEditor = true
        }
    }
    val deferProof = value.b("upload_proof_later")
    val remaining = settlementRemaining(value, invoiceTotal)
    fun disableMethod(method: SettlementEditor) {
        patch {
            when (method) {
                SettlementEditor.Deposit -> {
                    if (it.b("cash_online")) CashDepositRows.setMethods(it, false, true, CashDepositRows.onlineAmount(it), false)
                    else {
                        CashDepositRows.setMethods(it, false, false, 0L, false)
                        toggleSettlementPart(it, "cash", false, currentTotal)
                    }
                }
                SettlementEditor.Online -> {
                    val keepDeposit = CashDepositRows.depositEnabled(it)
                    CashDepositRows.setMethods(it, keepDeposit, false, 0L, false)
                    if (!keepDeposit) toggleSettlementPart(it, "cash", false, currentTotal)
                }
                SettlementEditor.Wallet -> SettlementSelection(currentTotal).setWallet(it, false, 0L, walletAvailable)
                SettlementEditor.Check, SettlementEditor.Credit -> {
                    val key = if (method == SettlementEditor.Check) "check" else "credit"
                    val saved = it.optJSONObject("draft_non_cash_amounts") ?: JSONObject()
                    if (it.b("${key}_enabled")) saved.put(key, it.l("${key}_amount"))
                    it.put("draft_non_cash_amounts", saved).put("${key}_enabled", false).put("${key}_amount", 0L)
                }
            }
        }
        if (method == SettlementEditor.Credit) awaitingCredit = false
        if (editor == method) editor = null
    }
    val onlineSelected = value.b("cash_enabled") && value.b("cash_online")
    val online = CashDepositRows.requiresOnline(value)
    val deposit = CashDepositRows.depositEnabled(value)
    val depositSelected = value.b("cash_enabled") && deposit
    val walletSelected = value.b("wallet_enabled")
    val walletEditable = !busy && !amountsLocked && !agentReviewRequired && creditLoaded &&
        (!checkActive || walletSelected) && (walletAvailable > 0L || walletSelected)
    val onlineEditable = !busy && (onlineSelected || (onlineAvailable && !agentReviewRequired))
    fun openDeposit() {
        patch {
            if (!it.b("cash_enabled")) toggleSettlementPart(it, "cash", true, currentTotal)
            if (it.has(CashDepositRows.field) || !deposit)
                CashDepositRows.setMethods(it, true, onlineSelected && mixedCashAvailable, CashDepositRows.onlineAmount(it), amountsLocked)
        }
        openEditor(SettlementEditor.Deposit)
    }
    fun updateOnline(amount: Long) {
        patch {
            if (!it.b("cash_enabled")) toggleSettlementPart(it, "cash", true, currentTotal)
            val hasReceipts = it.has(CashDepositRows.field) || PaymentProofSelection.read(it, "cash").isNotEmpty()
            val keepDeposit = deposit && ((mixedCashAvailable && hasReceipts) || amount == 0L)
            CashDepositRows.setMethods(it, keepDeposit, true, amount, amountsLocked)
        }
    }
    fun openOnline() {
        if (!onlineSelected && !amountsLocked) updateOnline(remaining)
        openEditor(SettlementEditor.Online)
    }
    fun openCheck() {
        if (!checkActive) selectNonCash("check")
        awaitingCredit = false
        openEditor(SettlementEditor.Check)
    }
    fun openCredit() {
        if (!creditActive) awaitingCredit = true
        openEditor(SettlementEditor.Credit)
    }
    fun openWallet() {
        if (walletEditable && !walletSelected)
            patch { SettlementSelection(currentTotal).setWallet(it, true, walletAvailable, walletAvailable) }
        openEditor(SettlementEditor.Wallet)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CheckoutAmountHero(invoiceTotal, settlementAllocated(value), remaining)
        Text("انتخاب روش‌های تسویه", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaymentMethodCard(SettlementEditor.Wallet, walletSelected, !busy && !agentReviewRequired, walletEditable,
                Modifier.weight(1f), ::openWallet) { if (it) openWallet() else disableMethod(SettlementEditor.Wallet) }
            PaymentMethodCard(SettlementEditor.Online, onlineSelected, onlineEditable && (!amountsLocked || onlineSelected),
                onlineEditable && !amountsLocked, Modifier.weight(1f), ::openOnline) {
                if (it) openOnline() else disableMethod(SettlementEditor.Online)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaymentMethodCard(SettlementEditor.Credit, creditActive || awaitingCredit, !busy && (creditActive || !amountsLocked),
                !busy && !amountsLocked, Modifier.weight(1f), ::openCredit) { if (it) openCredit() else disableMethod(SettlementEditor.Credit) }
            PaymentMethodCard(SettlementEditor.Deposit, depositSelected, !busy && (!amountsLocked || depositSelected),
                !busy && !amountsLocked, Modifier.weight(1f), ::openDeposit) { if (it) openDeposit() else disableMethod(SettlementEditor.Deposit) }
        }
        PaymentMethodCard(SettlementEditor.Check, checkActive, !busy && (checkActive || !amountsLocked), !busy && !amountsLocked,
            Modifier.fillMaxWidth(), ::openCheck, horizontal = true) { if (it) openCheck() else disableMethod(SettlementEditor.Check) }
        if (agentReviewRequired) Text("پرداخت آنلاین و کیف پول پس از بررسی تسویه عامل فروش در دسترس است.", color = Muted)
        if (remaining > 0L && onlineEditable && !amountsLocked)
            TextButton(onClick = ::openOnline) { Text("پرداخت آنلاین مانده") }
        CheckoutAmountFooter(settlementAllocated(value), remaining)
    }

    val currentEditor = editor
    if (currentEditor != null) PaymentMethodDialog(currentEditor, busy, onClose = {
        awaitingCredit = false
        editor = null
    }) {
        if (editor == SettlementEditor.Online) {
            SettlementAmountField("پرداخت آنلاین", CashDepositRows.onlineAmount(value).takeIf { it > 0 }?.toString().orEmpty(),
                !busy && (!amountsLocked || deposit), fieldError = if (validateEditor && !online) "مبلغ پرداخت آنلاین را وارد کنید." else "") { raw ->
                updateOnline(asciiDigits(raw).filter(Char::isDigit).take(15).toLongOrNull() ?: 0L)
            }
            val maximum = remaining + CashDepositRows.onlineAmount(value)
            if (!amountsLocked && maximum > 0L) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(100, 75, 50, 25).forEach { percent ->
                    OutlinedButton(
                        { updateOnline(maximum / 100L * percent + maximum % 100L * percent / 100L) },
                        modifier = Modifier.weight(1f), enabled = !busy, contentPadding = PaddingValues(horizontal = 3.dp),
                    ) { Text(if (percent == 100) "کل مبلغ" else faNumber(percent) + "٪", fontSize = 12.sp) }
                }
            }
            Text("بعد از ثبت سفارش می‌توانید این مبلغ را در درگاه ایران‌کیش پرداخت کنید.", color = Muted)
        }
        if (deposit && editor == SettlementEditor.Deposit) {
            when (invoiceType) {
                "رسمی" -> {
                    DetailLine("شناسه ملی فروشنده", faDigits("۱۴۰۱۰۳۶۶۳۹۵"))
                    DetailLine("صاحب حساب", BuildConfig.CHEQUE_FORMAL_PAYEE)
                    DetailLine("شماره شبا", "\u2066${BuildConfig.FORMAL_CASH_IBAN}\u2069")
                }
                "غیررسمی" -> {
                    DetailLine("صاحب حساب", BuildConfig.CHEQUE_INFORMAL_PAYEE)
                    DetailLine("شماره کارت", "\u2066${BuildConfig.INFORMAL_CASH_CARD}\u2069")
                    DetailLine("شماره شبا", "\u2066${BuildConfig.INFORMAL_CASH_IBAN}\u2069")
                }
            }
            DesignField("شماره پیگیری", value.s("tracking_number"), { set("tracking_number", asciiDigits(it).take(100)) }, "شماره پیگیری انتقال — در صورت پرداخت", enabled = !busy)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                deposits.forEachIndexed { index, deposit ->
                    key(index) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom) {
                                Box(Modifier.weight(3f)) {
                                    SettlementAmountField("فیش ${faNumber(index + 1)}", deposit.amount.takeIf { it > 0 }?.toString().orEmpty(),
                                        enabled = !busy, compact = true,
                                        fieldError = if (validateEditor && deposit.amount <= 0L) "مبلغ فیش را وارد کنید." else "") { raw ->
                                        val amount = asciiDigits(raw).filter(Char::isDigit).take(15).toLongOrNull() ?: 0L
                                        editDeposit(index) { it.copy(amount = amount) }
                                    }
                                }
                                Box(Modifier.weight(2f)) {
                                    ImageUploadButton(if (deposit.image.isBlank()) "بارگذاری عکس" else "تعویض عکس",
                                        enabled = !busy, singleSelection = true) { uris ->
                                        uris.firstOrNull()?.let { attachCashReceipt(index, it) }
                                    }
                                }
                            }
                            if (uploadField == "deposit:" + index && uploadError.isNotBlank()) Text(uploadError, color = Danger)
                            if (validateEditor && !deferProof && deposit.image.isBlank())
                                Text("عکس فیش را بارگذاری کنید یا گزینهٔ ارسال بعداً را بزنید.", color = Danger)
                            if (deposit.image.isNotBlank()) ProofThumbnail(deposit.image, "فیش ${faNumber(index + 1)}")
                            if (deposits.size > 1 || deposit.image.isNotBlank()) {
                                IconButton(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    enabled = !busy,
                                    onClick = {
                                        patch { next ->
                                            val rows = CashDepositRows.read(next)
                                            val revised = if (rows.size > 1) rows.filterIndexed { i, _ -> i != index }
                                                else rows.map { it.copy(image = "") }
                                            CashDepositRows.set(next, revised, amountsLocked)
                                        }
                                    },
                                ) {
                                    val label = if (deposits.size > 1) "حذف فیش" else "حذف عکس فیش"
                                    Icon(Icons.Default.Close, contentDescription = "$label ${faNumber(index + 1)}",
                                        tint = if (busy) Muted else Danger)
                                }
                            }
                        }
                    }
                }
            }
            DetailLine("جمع مبلغ فیش‌ها", formatMoney(deposits.sumOf { it.amount }) + " تومان")
            OutlinedButton(enabled = !busy && deposits.size < BuildConfig.PROOF_MAX_FILES_PER_KIND, onClick = {
                patch { next -> CashDepositRows.set(next, CashDepositRows.read(next) + CashDeposit(), amountsLocked) }
            }) { Text("+ افزودن فیش") }
        }
        if (editor == SettlementEditor.Check && checkActive && !awaitingCredit) {
            val payeeNote = ChequePayeePolicy().instructionsFor(invoiceType)
            DesignNotice("تسویه با چک\n\n$payeeNote")
            SettlementAmountField("چک", amountText("check"), !amountsLocked, fieldError = checkErrors["amount"].orEmpty()) { setAmount("check", it) }
            DesignField("شناسه صیادی", value.s("check_number"), { set("check_number", asciiDigits(it).filter(Char::isDigit).take(16)) },
                "شناسه ۱۶ رقمی", keyboardType = KeyboardType.Number, enabled = !busy, fieldError = checkErrors["number"].orEmpty())
            BankSelectionField(value.s("bank_name"), !busy) { set("bank_name", it) }
            checkErrors["bank"]?.let { Text(it, color = Danger) }
            PersianDateSelectionField("سررسید", value.s("due_date"), !busy) { set("due_date", it) }
            checkErrors["date"]?.let { Text(it, color = Danger) }
            ImageUploadButton("بارگذاری عکس چک", enabled = !busy && checkDocuments.size < BuildConfig.PROOF_MAX_FILES_PER_KIND,
                mimeTypes = PaymentProofSelection.mimeTypes) { attachCheckFiles(it) }
            if (uploadField == "check" && uploadError.isNotBlank()) Text(uploadError, color = Danger)
            checkErrors["image"]?.let { Text(it, color = Danger) }
            if (checkFiles.isNotEmpty()) GhadirButton(
                "${faNumber(checkFiles.size)} فایل چک انتخاب شد · پاک کردن انتخاب",
                { patch { PaymentProofSelection.set(it, "check", listOfNotNull(registrationReceipt?.data)) } },
                enabled = !busy, secondary = true,
            )
            ImageUploadButton(if (registrationReceipt == null) "بارگذاری رسید ثبت چک (اختیاری)" else "تعویض رسید ثبت چک",
                enabled = !busy && (registrationReceipt != null || checkDocuments.size < BuildConfig.PROOF_MAX_FILES_PER_KIND),
                mimeTypes = PaymentProofSelection.mimeTypes, singleSelection = true) { attachCheckFiles(it, registration = true) }
            if (uploadField == "registration" && uploadError.isNotBlank()) Text(uploadError, color = Danger)
            if (registrationReceipt != null) {
                TextButton(enabled = !busy, onClick = {
                    patch { PaymentProofSelection.set(it, "check", checkFiles.map { file -> file.data }) }
                }) { Text("حذف رسید ثبت چک") }
            }
        }
        if (editor == SettlementEditor.Deposit || editor == SettlementEditor.Check) {
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
        if (editor == SettlementEditor.Credit && creditActive) {
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
            if (validateEditor && !value.b("terms_accepted")) Text("شرایط درخواست اعتبار را بپذیرید.", color = Danger)
        }
        if (currentEditor == SettlementEditor.Wallet) {
            DetailLine("موجودی قابل استفاده کیف پول", formatMoney(walletAvailable) + " تومان")
            when {
                !creditLoaded -> Text(if (creditLoading) "در حال دریافت موجودی…" else "موجودی دریافت نشد.", color = Muted)
                checkActive && !walletSelected -> Text("کیف پول با تسویه چکی ترکیب نمی‌شود.", color = Muted)
                walletSelected -> SettlementAmountField("کیف پول", amountText("wallet"), enabled = !busy && !amountsLocked) { setAmount("wallet", it) }
            }
            if (walletOverLimit) Text("موجودی کیف پول برای این مبلغ کافی نیست.", color = Danger)
        }
        if (currentEditor == SettlementEditor.Credit && awaitingCredit && creditLoading)
            Text("در حال دریافت اعتبار…", color = Muted)
        if (!awaitingCredit) PaymentDialogButton(
            currentEditor, enabled = !busy && (currentEditor != SettlementEditor.Credit || creditLoaded),
        ) {
            validateEditor = true
            val issue = when (currentEditor) {
                SettlementEditor.Deposit -> CashDepositRows.depositError(value)
                SettlementEditor.Online -> if (!online) "مبلغ پرداخت آنلاین را وارد کنید." else ""
                SettlementEditor.Check -> SettlementProofRules.checkError(value)
                SettlementEditor.Credit -> if (creditAmount <= 0L || creditOverLimit || !value.b("terms_accepted")) "مبلغ و تعهد اعتباری را تکمیل کنید." else ""
                SettlementEditor.Wallet -> if (walletOverLimit) "مبلغ کیف پول را کاهش دهید." else ""
            }
            if (issue.isBlank()) editor = null
            else if (currentEditor == SettlementEditor.Deposit && issue.contains("جمع مبلغ")) amountWarning = issue
        }
        if (amountWarning.isNotBlank()) AlertDialog(
            onDismissRequest = { amountWarning = "" },
            title = { Text("بررسی مبلغ تسویه") },
            text = { Text(amountWarning) },
            confirmButton = { TextButton(onClick = { amountWarning = "" }) { Text("متوجه شدم") } },
        )
        if ((creditActive || awaitingCredit || walletNeedsBalance) && !creditLoaded && error.isNotBlank()) {
            GhadirButton("دریافت دوباره اعتبار و موجودی", { retry++ }, secondary = true, enabled = !creditLoading)
        }
        if (!creditLoaded && (creditActive || awaitingCredit || walletNeedsBalance) && error.isNotBlank()) Text(error, color = Danger)
    }
}

@Composable
private fun SettlementAmountField(
    label: String,
    amount: String,
    enabled: Boolean = true,
    compact: Boolean = false,
    fieldError: String = "",
    onAmount: (String) -> Unit,
) {
    DesignField(
        "$label — مبلغ (تومان)",
        amount,
        onAmount,
        "مبلغ به تومان",
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        transformation = SettlementAmountTransformation,
        textDirection = TextDirection.Ltr,
        compact = compact,
        fieldError = fieldError,
        textAlign = TextAlign.Right,
    )
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

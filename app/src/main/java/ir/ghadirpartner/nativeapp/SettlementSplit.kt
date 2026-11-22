package ir.ghadirpartner.nativeapp

import org.json.JSONObject

private val settlementParts = listOf("cash", "check", "credit", "wallet")

internal fun settlementInvoiceTotal(
    d: JSONObject,
    fallback: Long,
): Long = d.optLong("invoice_total").takeIf { it > 0L } ?: fallback

internal fun settlementAllocated(d: JSONObject): Long =
    settlementParts.sumOf { part ->
        if (d.b("${part}_enabled") && d.optLong("${part}_amount") > 0L) d.optLong("${part}_amount") else 0L
    }

internal fun settlementRemaining(
    d: JSONObject,
    invoiceTotal: Long,
): Long = (invoiceTotal - settlementAllocated(d)).coerceAtLeast(0L)

internal fun settlementPrimaryMethod(d: JSONObject): String {
    val parts =
        settlementParts.filter { part ->
            if (part == "wallet") return@filter false
            d.b("${part}_enabled") && d.optLong("${part}_amount") > 0L
        }
    return when {
        parts.size > 1 -> "mixed"
        parts.size == 1 -> parts.single()
        d.l("wallet_amount") > 0L -> "cash"
        d.s("payment_method").isNotBlank() -> d.s("payment_method")
        else -> "cash"
    }
}

internal fun defaultSplitSettlement(invoiceTotal: Long): JSONObject =
    JSONObject()
        .put("split", true)
        .put("invoice_total", invoiceTotal)
        .put("cash_enabled", true)
        .put("cash_amount", invoiceTotal)
        .put("check_enabled", false)
        .put("check_amount", 0L)
        .put("credit_enabled", false)
        .put("credit_amount", 0L)
        .put("invoice_remaining", 0L)
        .put("company_invoice_remaining", 0L)

/** Allocate the outstanding balance when selecting a payment method. */
internal fun toggleSettlementPart(
    d: JSONObject,
    part: String,
    enabled: Boolean,
    invoiceTotal: Long,
) {
    d.put("${part}_enabled", enabled)
    d.put("${part}_amount", 0L)
    val unallocatedPart = if (enabled) part else settlementParts.singleOrNull { d.b("${it}_enabled") }
    if (unallocatedPart != null && d.optLong("${unallocatedPart}_amount") == 0L) {
        d.put("${unallocatedPart}_amount", settlementRemaining(d, invoiceTotal))
    }
}

internal fun refreshSettlementTotals(
    d: JSONObject,
    invoiceTotal: Long,
): JSONObject {
    val next = JSONObject(d.toString())
    val previousTotal = next.optLong("invoice_total")
    val solePart = settlementParts.singleOrNull { next.b("${it}_enabled") }
    if (previousTotal != invoiceTotal && solePart != null && next.optLong("${solePart}_amount") == previousTotal) {
        next.put("${solePart}_amount", invoiceTotal)
    }
    next.put("invoice_total", invoiceTotal)
    val remaining = settlementRemaining(next, invoiceTotal)
    next.put("invoice_remaining", remaining)
    next.put("company_invoice_remaining", remaining)
    return next
}

internal fun settlementError(
    method: String,
    d: JSONObject,
    invoiceTotal: Long = settlementInvoiceTotal(d, 0L),
): String {
    if (!d.b("split")) return legacySettlementError(method, d)
    val allocated = settlementAllocated(d)
    if (allocated <= 0L) return "حداقل یک روش تسویه با مبلغ بیشتر از صفر انتخاب کنید"
    if (d.l("wallet_amount") > 0L && d.l("check_amount") > 0L) return "پرداخت با کیف پول فقط با نقد و اعتبار ترکیب می‌شود"
    if ((d.l("wallet_amount") > 0L || d.l("credit_amount") > 0L) && d.l("check_amount") == 0L && allocated != invoiceTotal) {
        return "جمع مبلغ‌های تسویه باید برابر مبلغ فاکتور باشد"
    }
    if (allocated > invoiceTotal) return "جمع مبالغ تسویه از مبلغ فاکتور بیشتر است"
    if (d.b("check_enabled")) SettlementProofRules.checkError(d).takeIf(String::isNotBlank)?.let { return it }
    if (d.b("credit_enabled") && d.optLong("credit_amount") > 0L && !d.b("terms_accepted")) {
        return "شرایط درخواست اعتبار را بپذیرید"
    }
    return ""
}

private fun legacySettlementError(
    method: String,
    d: JSONObject,
): String =
    when {
        method == "check" -> SettlementProofRules.checkError(d, requireAmount = false)
        method == "credit" && !d.b("terms_accepted") -> "شرایط درخواست اعتبار را بپذیرید"
        else -> ""
    }

/** Validate cheque fields and distinguish the optional registration receipt from the cheque image. */
internal object SettlementProofRules {
    /** Return errors keyed by their corresponding form field. */
    fun checkFields(details: JSONObject, requireAmount: Boolean = true): Map<String, String> = buildMap {
        if (requireAmount && details.l("check_amount") <= 0L) put("amount", "مبلغ چک را وارد کنید.")
        if (!details.s("check_number").matches(Regex("[0-9]{16}"))) put("number", "شناسه صیادی باید ۱۶ رقم باشد.")
        if (details.s("bank_name").isBlank()) put("bank", "بانک چک را انتخاب کنید.")
        if (details.s("due_date").isBlank()) put("date", "سررسید چک را انتخاب کنید.")
        if (!details.b("upload_proof_later") &&
            PaymentProofSelection.documents(details, "check").none { it.note != PaymentProofSelection.checkRegistration }
        ) put("image", "تصویر چک را بارگذاری کنید یا گزینهٔ ارسال بعداً را بزنید.")
    }

    /** Return the first cheque field error in entry order. */
    fun checkError(details: JSONObject, requireAmount: Boolean = true): String =
        checkFields(details, requireAmount).values.firstOrNull().orEmpty()
}

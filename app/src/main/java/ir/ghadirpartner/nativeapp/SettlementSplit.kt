package ir.ghadirpartner.nativeapp

import org.json.JSONObject

/** Resolve the two cash methods without changing the total used by checkout and accounting. */
internal class CashSettlement(private val details: JSONObject) {
    val online: Boolean get() = details.b("cash_online")
    val transfer: Boolean get() = details.optBoolean("cash_transfer", !online)
    val combined: Boolean get() = online && transfer
    val paysOnline: Boolean get() = online && details.b("cash_enabled") && onlineAmount > 0L
    val onlineAmount: Long
        get() = when {
            !online -> 0L
            combined -> details.l("cash_online_amount")
            else -> details.l("cash_amount")
        }
    val transferAmount: Long get() = (details.l("cash_amount") - onlineAmount).coerceAtLeast(0L)

    /** Keep the other payment method selected and preserve the bank evidence in the draft. */
    fun select(onlineMethod: Boolean, enabled: Boolean) {
        val useOnline = if (onlineMethod) enabled else online || !enabled
        val useTransfer = if (onlineMethod) transfer || !enabled else enabled
        details.put("cash_online", useOnline).put("cash_transfer", useTransfer)
        if (!combined) details.remove("cash_online_amount")
    }
}

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
    val cash = CashSettlement(d)
    if (d.b("cash_enabled") && cash.combined && (cash.onlineAmount <= 0L || cash.transferAmount <= 0L)) {
        return "برای پرداخت ترکیبی، سهم آنلاین و سهم واریز به حساب باید هر دو بیشتر از صفر باشند"
    }
    if (d.b("check_enabled") && d.optLong("check_amount") > 0L) {
        if (!d.s("check_number").matches(Regex("[0-9]{16}"))) return "شناسه صیادی باید ۱۶ رقم باشد"
        if (d.s("bank_name").isBlank() || d.s("due_date").isBlank()) return "بانک و سررسید چک را وارد کنید"
    }
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
        method == "check" && !d.s("check_number").matches(Regex("[0-9]{16}")) -> "شناسه صیادی باید ۱۶ رقم باشد"
        method == "check" && (d.s("bank_name").isBlank() || d.s("due_date").isBlank()) -> "بانک و سررسید چک را وارد کنید"
        method == "credit" && !d.b("terms_accepted") -> "شرایط درخواست اعتبار را بپذیرید"
        else -> ""
    }

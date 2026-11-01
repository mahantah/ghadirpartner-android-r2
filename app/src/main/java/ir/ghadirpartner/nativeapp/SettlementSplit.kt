package ir.ghadirpartner.nativeapp

import org.json.JSONObject

internal fun settlementInvoiceTotal(d: JSONObject, fallback: Long): Long =
    d.optLong("invoice_total").takeIf { it > 0L } ?: fallback

internal fun settlementAllocated(d: JSONObject): Long =
    listOf("cash", "check", "credit").sumOf { part ->
        if (d.b("${part}_enabled") && d.optLong("${part}_amount") > 0L) d.optLong("${part}_amount") else 0L
    }

internal fun settlementRemaining(d: JSONObject, invoiceTotal: Long): Long =
    (invoiceTotal - settlementAllocated(d)).coerceAtLeast(0L)

internal fun settlementPrimaryMethod(d: JSONObject): String {
    val parts = listOf("cash", "check", "credit").filter { part ->
        d.b("${part}_enabled") && d.optLong("${part}_amount") > 0L
    }
    return when {
        parts.size > 1 -> "mixed"
        parts.size == 1 -> parts.single()
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

internal fun refreshSettlementTotals(d: JSONObject, invoiceTotal: Long): JSONObject {
    val next = JSONObject(d.toString())
    next.put("invoice_total", invoiceTotal)
    val remaining = settlementRemaining(next, invoiceTotal)
    next.put("invoice_remaining", remaining)
    next.put("company_invoice_remaining", remaining)
    return next
}

internal fun settlementError(method: String, d: JSONObject, invoiceTotal: Long = settlementInvoiceTotal(d, 0L)): String {
    if (!d.b("split")) return legacySettlementError(method, d)
    val allocated = settlementAllocated(d)
    if (allocated <= 0L) return "حداقل یک روش تسویه با مبلغ بیشتر از صفر انتخاب کنید"
    if (allocated > invoiceTotal) return "جمع مبالغ تسویه از مبلغ فاکتور بیشتر است"
    if (d.b("check_enabled") && d.optLong("check_amount") > 0L) {
        if (!d.s("check_number").matches(Regex("[0-9]{16}"))) return "شناسه صیادی باید ۱۶ رقم باشد"
        if (d.s("bank_name").isBlank() || d.s("due_date").isBlank()) return "بانک و سررسید چک را وارد کنید"
    }
    if (d.b("credit_enabled") && d.optLong("credit_amount") > 0L && !d.b("terms_accepted")) {
        return "شرایط درخواست اعتبار را بپذیرید"
    }
    return ""
}

private fun legacySettlementError(method: String, d: JSONObject): String = when {
    method == "check" && !d.s("check_number").matches(Regex("[0-9]{16}")) -> "شناسه صیادی باید ۱۶ رقم باشد"
    method == "check" && (d.s("bank_name").isBlank() || d.s("due_date").isBlank()) -> "بانک و سررسید چک را وارد کنید"
    method == "credit" && !d.b("terms_accepted") -> "شرایط درخواست اعتبار را بپذیرید"
    else -> ""
}

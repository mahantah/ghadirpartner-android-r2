package ir.ghadirpartner.nativeapp

import org.json.JSONObject

/** Maps app (customer) prices to proforma line columns for official vs unofficial invoices. */
internal data class InvoiceLineAmounts(
    val unitPrice: Long,
    val lineTotal: Long,
    val netAfterAdjustments: Long,
    val taxAmount: Long?,
    val invoiceNet: Long,
)

internal object InvoiceAmounts {
    /** Gross line total is what the customer sees in the app (unit × qty, after line discounts if any). */
    fun fromAppLine(item: JSONObject, official: Boolean): InvoiceLineAmounts {
        val qty = item.optLong("qty").coerceAtLeast(1L)
        val unitApp = item.optLong("unit_price")
        val gross = if (item.has("line_total")) item.getLong("line_total") else Math.multiplyExact(unitApp, qty)
        val discount = item.optLong("discount_amount")
        val additional = item.optLong("additional_amount")
        val netAfter = gross - discount + additional
        if (!official) {
            return InvoiceLineAmounts(
                unitPrice = unitApp,
                lineTotal = gross,
                netAfterAdjustments = netAfter,
                taxAmount = null,
                invoiceNet = netAfter,
            )
        }
        val base = (netAfter * 10L) / 11L
        val tax = netAfter - base
        val unitBase = if (qty > 0L) base / qty else 0L
        return InvoiceLineAmounts(
            unitPrice = unitBase,
            lineTotal = base,
            netAfterAdjustments = base,
            taxAmount = tax,
            invoiceNet = netAfter,
        )
    }
}

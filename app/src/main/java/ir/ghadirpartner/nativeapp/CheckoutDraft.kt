package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject

internal fun checkoutDraftFromState(
    cart: List<CartLine>,
    step: Int,
    selectedAddressId: String,
    invoiceType: String,
    settlement: JSONObject,
    notes: String,
    promoCode: String,
    appliedOffer: JSONObject?,
    keepEvidence: Boolean = false,
): JSONObject {
    val items = JSONArray()
    cart.forEach { line ->
        items.put(
            JSONObject()
                .put("product", line.product)
                .put("qty", line.qty)
                .put("priceKey", line.priceKey)
                .put("priceLabel", line.priceLabel)
                .put("panelSwitch", line.panelSwitch)
                .put("unit", line.unit),
        )
    }
    val settlementCopy = JSONObject(settlement.toString())
    if (!keepEvidence) PaymentProofSelection.clear(settlementCopy)
    return JSONObject()
        .put("cart", items)
        .put("step", step)
        .put("selectedAddressId", selectedAddressId)
        .put("invoiceType", invoiceType)
        .put("settlement", settlementCopy)
        .put("notes", notes)
        .put("promoCode", promoCode)
        .put("appliedOffer", appliedOffer?.toString().orEmpty())
        .put("saved_at", System.currentTimeMillis())
}

internal fun cartFromDraft(draft: JSONObject): List<CartLine> =
    draft.optJSONArray("cart")?.objects()?.map { row ->
        CartLine(row.s("product"), row.i("qty"), row.s("priceKey"), row.s("priceLabel"), row.l("unit"), row.s("panelSwitch"))
    } ?: emptyList()

internal fun settlementSummaryLabel(settlement: JSONObject): String {
    val parts = listOf("cash" to "نقد", "check" to "چک", "credit" to "اعتباری", "wallet" to "کیف پول").mapNotNull { (key, label) ->
        if (settlement.b("${key}_enabled") && settlement.optLong("${key}_amount") > 0L) {
            "$label ${formatMoney(settlement.optLong("${key}_amount"))}"
        } else null
    }
    val remaining = settlement.optLong("invoice_remaining")
    val tail = if (remaining > 0L) " • مانده ${formatMoney(remaining)}" else ""
    return (if (parts.isEmpty()) "—" else parts.joinToString(" • ")) + tail
}


/** Seed the normal checkout with a returned order, omitting server-only proof tokens. */
internal fun draftFromReturnedOrder(order: JSONObject): JSONObject {
    val rows = order.arr("purchase_items").objects().ifEmpty { order.arr("items").objects() }
    val lines = rows.map { row ->
        CartLine(row.s("product"), row.i("qty"), row.s("price_key"), row.s("price_label"), row.l("unit_price"), row.s("panel_switch"))
    }
    val fallback = order.l("estimated_total")
    val previous = order.obj("native_settlement")
    val method = order.s("requested_payment_method")
    val settlement = defaultSplitSettlement(fallback)
    val walletAmount = order.obj("credit_checkout").l("wallet_reserved")
    if (walletAmount > 0L) settlement.put("wallet_enabled", true).put("wallet_amount", walletAmount)
    for (part in listOf("cash", "check", "credit")) {
        val amount = if (part == "cash" && order.has("credit_checkout")) {
            order.obj("credit_checkout").l("cash_required")
        } else {
            previous.l("${part}_amount").takeIf { it > 0L } ?: if (method == part) fallback else 0L
        }
        settlement.put("${part}_amount", amount).put("${part}_enabled", amount > 0L)
    }
    val terms = order.obj("customer_correction").optJSONObject("settlement_amounts")
    if (terms != null) {
        for (part in listOf("cash", "check", "credit", "wallet")) {
            val amount = terms.l("${part}_amount")
            settlement.put("${part}_amount", amount).put("${part}_enabled", amount > 0L)
        }
    }
    for (field in listOf("check_number", "tracking_number", "bank_name", "due_date")) {
        settlement.put(field, previous.s(field))
    }
    return checkoutDraftFromState(
        lines, if (terms == null) 0 else 1, order.s("address_id").ifBlank { order.obj("delivery_address").s("id") },
        order.s("invoice_type"), settlement, order.s("notes"), order.s("promo_code"), null,
    ).put("shippingMethod",order.s("shipping_type")).put("suggestedShippingDate", order.s("suggested_shipping_date"))
}

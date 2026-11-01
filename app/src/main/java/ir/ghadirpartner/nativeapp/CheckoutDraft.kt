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
): JSONObject {
    val items = JSONArray()
    cart.forEach { line ->
        items.put(
            JSONObject()
                .put("product", line.product)
                .put("qty", line.qty)
                .put("priceKey", line.priceKey)
                .put("priceLabel", line.priceLabel)
                .put("unit", line.unit),
        )
    }
    val settlementCopy = JSONObject(settlement.toString())
    listOf("image_base64", "cash_image_base64", "check_image_base64").forEach { settlementCopy.remove(it) }
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
        CartLine(row.s("product"), row.i("qty"), row.s("priceKey"), row.s("priceLabel"), row.l("unit"))
    } ?: emptyList()

internal fun settlementSummaryLabel(settlement: JSONObject): String {
    val parts = listOf("cash" to "نقد", "check" to "چک", "credit" to "اعتباری").mapNotNull { (key, label) ->
        if (settlement.b("${key}_enabled") && settlement.optLong("${key}_amount") > 0L) {
            "$label ${formatMoney(settlement.optLong("${key}_amount"))}"
        } else null
    }
    val remaining = settlement.optLong("invoice_remaining")
    val tail = if (remaining > 0L) " • مانده ${formatMoney(remaining)}" else ""
    return (if (parts.isEmpty()) "—" else parts.joinToString(" • ")) + tail
}

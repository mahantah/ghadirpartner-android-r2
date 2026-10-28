package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** An explicit customer decision, including the consent captured at checkout. */
internal data class OrderCheckout(
    val lines: List<CartLine>,
    val addressId: String,
    val invoiceType: String,
    val paymentMethod: String,
    val settlement: JSONObject,
    val notes: String,
    val promoCode: String,
)

/** Translates checkout data to the existing authenticated orders API. */
internal class OrderRepository(private val api: ApiClient) {
    /** Refresh one account-owned order without reusing the short-lived list cache. */
    suspend fun find(orderId: Int): JSONObject {
        val orders: JSONArray = api.request("GET", "/api/orders", null) as JSONArray
        return orders.objects().firstOrNull { it.i("id") == orderId }
            ?: throw IllegalArgumentException("سفارش در دسترس نیست")
    }

    /** Submit once; retries remain a deliberate customer action after an API failure. */
    suspend fun create(checkout: OrderCheckout): JSONObject {
        val items: JSONArray = JSONArray()
        checkout.lines.forEach { line ->
            items.put(JSONObject().put("product", line.product).put("qty", line.qty).put("price_key", line.priceKey))
        }
        val body: JSONObject = JSONObject()
            .put("items", items)
            .put("address_id", checkout.addressId)
            .put("invoice_type", checkout.invoiceType)
            .put("requested_payment_method", checkout.paymentMethod)
            .put("settlement_details", checkout.settlement)
            .put("notes", checkout.notes)
            .put("promo_code", checkout.promoCode)
        return api.post("/api/orders/add", body) as JSONObject
    }
}

/** The checkout facade; final submission repeats all required customer decisions. */
internal class OrderCheckoutService(private val repository: OrderRepository) {
    /** Read the latest company/fulfillment result through the same account-scoped repository. */
    suspend fun find(orderId: Int): JSONObject = repository.find(orderId)

    /** Return the first missing required decision without performing network I/O. */
    fun validation(checkout: OrderCheckout): String = when {
        checkout.lines.isEmpty() -> "حداقل یک کالا به سفارش اضافه کنید"
        checkout.invoiceType !in listOf("رسمی", "غیررسمی") -> "نوع فاکتور را انتخاب کنید"
        checkout.paymentMethod !in listOf("cash", "check", "credit") -> "روش تسویه را انتخاب کنید"
        else -> settlementError(checkout.paymentMethod, checkout.settlement)
    }

    /** Validate again before sending the customer order for company review. */
    suspend fun submit(checkout: OrderCheckout): JSONObject {
        val error: String = validation(checkout)
        require(error.isBlank()) { error }
        return repository.create(checkout)
    }
}

/** A review stage is independent of payment approval and warehouse fulfillment. */
internal data class CompanyReview(val label: String, val approved: Boolean, val stopped: Boolean) {
    companion object {
        /** Use the server's review result; do not turn registration into an approval. */
        fun from(approval: String, status: String): CompanyReview = when {
            approval in listOf("رد شده", "عدم تأیید", "عدم تایید") -> CompanyReview("رد شده توسط شرکت", false, true)
            status == "لغو شد" || approval == "لغو شد" -> CompanyReview("لغو شده", false, true)
            approval == "در انتظار تأیید" || status == "در انتظار تأیید سفارش" ->
                CompanyReview("در انتظار تأیید شرکت", false, false)
            approval == "تأیید شده" -> CompanyReview("تأیید شده توسط شرکت", true, false)
            status in listOf("ثبت شده", "در حال آماده سازی", "آماده ارسال", "ارسال شد", "تحویل شد") ->
                CompanyReview("تأیید شده توسط شرکت", true, false)
            else -> CompanyReview("وضعیت تأیید در دسترس نیست", false, false)
        }
    }
}

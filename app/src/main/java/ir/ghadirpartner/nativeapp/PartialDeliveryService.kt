package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** One physical model, with dispatched serials excluded from its ready quantity. */
internal data class PartialDeliveryLine(
    val product: String,
    val ordered: Int,
    val shipped: Int,
    val ready: Int,
) {
    val remaining: Int get() = (ordered - shipped).coerceAtLeast(0)
}

/** Explicit per-model quantities; never interpreted as a percentage of a changing order. */
internal data class PartialDeliveryQuantity(
    val product: String,
    val quantity: Int,
)

/** Preserve one idempotency identity across retries of a management delivery decision. */
internal data class PartialDeliveryRequest(
    val orderId: Int,
    val requestId: String,
    val reason: String,
    val quantities: List<PartialDeliveryQuantity>,
    val carrier: String,
    val tracking: String,
    val courierName: String,
    val courierPhone: String,
)

/** Pure quantity limits: stock-ready serials and a real order remainder both matter. */
internal class PartialDeliveryPolicy(
    private val maximumQuantity: Int,
) {
    /** Check one batch without changing financial approval or marking the whole order delivered. */
    fun validate(
        lines: List<PartialDeliveryLine>,
        quantities: List<PartialDeliveryQuantity>,
    ) {
        require(quantities.isNotEmpty()) { "تعداد تحویل هر مدل را مشخص کنید" }
        require(quantities.map { it.product }.distinct().size == quantities.size) { "هر مدل را فقط یک بار انتخاب کنید" }
        val models = lines.associateBy { it.product }
        quantities.forEach { selected ->
            val line = requireNotNull(models[selected.product]) { "مدل انتخاب‌شده در سفارش نیست" }
            require(selected.quantity in 1..maximumQuantity && selected.quantity <= minOf(line.ready, line.remaining)) {
                "تعداد انتخاب‌شده بیش از بار آماده و ارسال‌نشده است"
            }
        }
        require(quantities.sumOf { it.quantity.toLong() } < lines.sumOf { it.remaining.toLong() }) {
            "این مجوز فقط برای بخشی از بار است؛ تحویل کل مانده از مسیر تسویه انجام می‌شود"
        }
    }
}

/** Translate the existing JSON boundary without exposing transport objects to the form. */
internal class PartialDeliveryRepository(
    private val api: ApiClient,
) {
    /** Read actual shipment records and exclude already shipped serials from ready physical rows. */
    fun lines(order: JSONObject): List<PartialDeliveryLine> {
        val shipped = mutableMapOf<String, MutableSet<String>>()

        fun collect(items: JSONArray) {
            items.objects().forEach { row ->
                val serials = shipped.getOrPut(row.s("product")) { mutableSetOf() }
                row.arr("serials").strings().forEach { serials.add(it.trim().uppercase(java.util.Locale.ROOT)) }
            }
        }
        collect(order.arr("exit_items"))
        order.arr("shipment_batches").objects().forEach { collect(it.arr("items")) }
        return order.arr("items").objects().map { row ->
            val sent = shipped[row.s("product")].orEmpty()
            val ready =
                row
                    .arr(
                        "serials",
                    ).strings()
                    .map { it.trim().uppercase(java.util.Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .count {
                        it !in
                            sent
                    }
            PartialDeliveryLine(row.s("product"), row.i("qty"), sent.size, ready)
        }
    }

    /** Post only the explicit partial delivery operation, never the payment or whole-order status route. */
    suspend fun dispatch(request: PartialDeliveryRequest): JSONObject {
        val rows = JSONArray()
        request.quantities.forEach { rows.put(JSONObject().put("product", it.product).put("qty", it.quantity)) }
        val body =
            JSONObject()
                .put("id", request.orderId)
                .put("request_id", request.requestId)
                .put("reason", request.reason.trim())
                .put("items", rows)
                .put("shipping_type", request.carrier)
                .put("tracking_code", request.tracking.trim())
                .put("courier_name", request.courierName.trim())
                .put("courier_phone", request.courierPhone.trim())
        return api.post("/api/orders/partial-dispatch", body) as JSONObject
    }
}

/** The automation facade for a management-authorized, stock-limited partial delivery. */
internal class PartialDeliveryService(
    private val repository: PartialDeliveryRepository,
    private val policy: PartialDeliveryPolicy,
) {
    val carriers: List<String> = JSONArray(BuildConfig.PARTIAL_DELIVERY_CARRIERS).strings()
    private val trackingCarriers: List<String> = JSONArray(BuildConfig.PARTIAL_DELIVERY_TRACKING_CARRIERS).strings()
    private val readyStatuses: List<String> = JSONArray(BuildConfig.PARTIAL_DELIVERY_READY_STATUSES).strings()

    /** Expose a physical summary with no invented payment amount or warehouse movement. */
    fun lines(order: JSONObject): List<PartialDeliveryLine> = repository.lines(order)

    /** Management alone sees the operation; the server independently enforces authorization again. */
    fun canDispatch(
        order: JSONObject,
        roles: List<String>,
    ): Boolean =
        "admin" in roles && order.s("approval_status") == "تأیید شده" && order.s("status") in readyStatuses && !order.optBoolean("deleted")

    /** State whether the selected carrier requires a tracking code. */
    fun needsTracking(carrier: String): Boolean = carrier in trackingCarriers

    /** Validate all form fields, then let the server recheck current stock in its transaction. */
    suspend fun dispatch(
        lines: List<PartialDeliveryLine>,
        request: PartialDeliveryRequest,
    ): JSONObject {
        require(request.orderId > 0 && request.requestId.isNotBlank()) { "سفارش معتبر نیست" }
        require(request.reason.isNotBlank() && request.reason.length <= BuildConfig.PARTIAL_DELIVERY_MAX_REASON_LENGTH) {
            "علت مجوز تحویل بخشی را وارد کنید"
        }
        require(request.carrier in carriers) { "نوع ارسال را انتخاب کنید" }
        require(!needsTracking(request.carrier) || request.tracking.isNotBlank()) { "کد پیگیری الزامی است" }
        require(request.carrier != "پیک" || (request.courierName.isNotBlank() && request.courierPhone.isNotBlank())) {
            "نام و شماره تماس پیک الزامی است"
        }
        policy.validate(lines, request.quantities)
        return repository.dispatch(request)
    }
}

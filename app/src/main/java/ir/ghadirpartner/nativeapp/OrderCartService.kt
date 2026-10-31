package ir.ghadirpartner.nativeapp

/** A priced purchase option; several options may refer to the same physical model. */
internal data class CartLine(
    val product: String,
    val qty: Int,
    val priceKey: String,
    val priceLabel: String,
    val unit: Long,
)

/** The server-authorized prices and shared stock for one model. */
internal data class OrderCatalogEntry(
    val product: String,
    val stock: Int,
    val prices: Map<String, Long>,
)

/** Validated checkout limits, independent of Android and network I/O. */
internal data class OrderPolicy(
    val maximumQuantity: Int,
    val serialTierThreshold: Int,
) {
    init {
        require(maximumQuantity > 0)
        require(serialTierThreshold in 1..maximumQuantity)
    }
}

/** Owns cart identity, cumulative stock limits and quantity-dependent serial pricing. */
internal class OrderCartService(
    private val policy: OrderPolicy,
) {
    /** Add or merge a purchase option while checking stock across every option of the model. */
    fun add(
        lines: List<CartLine>,
        product: OrderCatalogEntry,
        quantity: Int,
        priceKey: String,
    ): List<CartLine> {
        require(quantity in 1..policy.maximumQuantity) { "تعداد معتبر وارد کنید" }
        val requested: Long = lines.filter { it.product == product.product }.sumOf { it.qty.toLong() } + quantity
        require(requested <= minOf(product.stock, policy.maximumQuantity)) {
            "مجموع تعداد این مدل در تمام نوع‌های خرید از موجودی قابل سفارش بیشتر است"
        }
        val unit: Long = product.prices[priceKey] ?: 0L
        require(unit > 0L) { "این نوع قیمت برای حساب شما فعال نیست" }
        val serial: Boolean = priceKey.startsWith("serial_")
        val existing: CartLine? =
            lines.firstOrNull {
                it.product == product.product && (it.priceKey == priceKey || (serial && it.priceKey.startsWith("serial_")))
            }
        val updated: List<CartLine> =
            if (existing == null) {
                lines + CartLine(product.product, quantity, priceKey, label(priceKey), unit)
            } else {
                lines.map { if (it == existing) it.copy(qty = it.qty + quantity) else it }
            }
        return reprice(updated, product)
    }

    /** Change one option while retaining the other options and their shared stock constraint. */
    fun changeQuantity(lines: List<CartLine>, line: CartLine, product: OrderCatalogEntry, quantity: Int): List<CartLine> {
        require(lines.any {it.product == line.product && it.priceKey == line.priceKey}) {"ردیف سفارش پیدا نشد"}
        return add(lines.filterNot {it.product == line.product && it.priceKey == line.priceKey}, product, quantity, line.priceKey)
    }

    /** Remove only the chosen option, keeping other purchases of the same model. */
    fun remove(
        lines: List<CartLine>,
        line: CartLine,
        product: OrderCatalogEntry,
    ): List<CartLine> = reprice(lines.filterNot { it.product == line.product && it.priceKey == line.priceKey }, product)

    /** Resolve a server-defined price key without inventing payment-specific serial prices. */
    fun priceKey(
        group: String,
        panelTier: String,
        quantity: Int,
    ): String =
        when (group) {
            "panel" -> panelTier
            "agent" -> "sales_agent"
            else -> if (quantity <= policy.serialTierThreshold) "serial_1_50" else "serial_51_200"
        }

    private fun reprice(
        lines: List<CartLine>,
        product: OrderCatalogEntry,
    ): List<CartLine> =
        lines.map { line ->
            if (line.product != product.product || !line.priceKey.startsWith("serial_")) {
                line
            } else {
                val key: String = priceKey("serial", "", line.qty)
                val unit: Long = product.prices[key] ?: 0L
                require(unit > 0L) { "قیمت سریال آزاد برای این تعداد فعال نیست" }
                line.copy(priceKey = key, priceLabel = label(key), unit = unit)
            }
        }

    private fun label(priceKey: String): String =
        when (priceKey) {
            "panel_cash" -> "ثبت در پنل — نقد"
            "panel_7d" -> "ثبت در پنل — هفت‌روزه"
            "panel_1m" -> "ثبت در پنل — یک‌ماهه"
            "serial_1_50" -> "سریال آزاد — ۱ تا ۵۰"
            "serial_51_200" -> "سریال آزاد — ۵۱ تا ۲۰۰"
            "sales_agent" -> "عامل فروش"
            else -> error("نوع قیمت نامعتبر است")
        }
}

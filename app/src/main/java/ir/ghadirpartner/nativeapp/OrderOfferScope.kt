package ir.ghadirpartner.nativeapp

/** A plan discounts all financial rows of its selected models; an empty scope means all models. */
internal class OrderOfferScope(private val products: Set<String>) {
    /** Sum eligible integer amounts without silently overflowing financial values. */
    fun eligibleAmount(lines: List<CartLine>): Long = lines
        .filter { products.isEmpty() || it.product in products }
        .fold(0L) { total, line -> Math.addExact(total, Math.multiplyExact(line.unit, line.qty.toLong())) }
}

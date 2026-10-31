package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests real cart transitions instead of live orders or financial writes. */
class OrderCartServiceTest {
    private val service: OrderCartService = OrderCartService(OrderPolicy(Int.MAX_VALUE, 50))
    private val product: OrderCatalogEntry =
        OrderCatalogEntry(
            "I90",
            100,
            mapOf("panel_cash" to 100L, "panel_7d" to 110L, "serial_1_50" to 120L, "serial_51_200" to 115L),
        )

    /** Panel and free-serial quantities share stock but keep independent financial rows. */
    @Test fun combinesTwoOptionsOfOneModel() {
        val panel: List<CartLine> = service.add(emptyList(), product, 2, "panel_cash")
        val mixed: List<CartLine> = service.add(panel, product, 3, "serial_1_50")
        assertEquals(2, mixed.size)
        assertEquals(560L, mixed.sumOf { it.qty * it.unit })
        assertEquals(listOf("panel_cash", "serial_1_50"), mixed.map { it.priceKey })
        assertEquals(listOf(mixed[1]), service.remove(mixed, mixed[0], product))
    }

    /** Adding an existing option merges it and reprices the entire serial quantity. */
    @Test fun mergesAndRepricesAtTheSerialBoundary() {
        val first: List<CartLine> = service.add(emptyList(), product, 50, "serial_1_50")
        val merged: List<CartLine> = service.add(first, product, 1, "serial_1_50")
        assertEquals(1, merged.size)
        assertEquals(51, merged.single().qty)
        assertEquals("serial_51_200", merged.single().priceKey)
        assertEquals(115L, merged.single().unit)
    }

    /** Neither splitting rows nor adding panel quantities can bypass shared stock. */
    @Test fun rejectsCumulativeOverstockAndUnavailablePrices() {
        val panel: List<CartLine> = service.add(emptyList(), product, 60, "panel_cash")
        assertThrows(IllegalArgumentException::class.java) { service.add(panel, product, 41, "serial_1_50") }
        assertThrows(IllegalArgumentException::class.java) { service.add(emptyList(), product, 1, "panel_1m") }
        assertThrows(IllegalArgumentException::class.java) { service.add(emptyList(), product, 0, "panel_cash") }
    }

    /** Restoring the R5.8 stock-driven limit must allow quantities above 999. */
    @Test fun largeQuantitiesAndOptionEditsKeepSharedStock() {
        val stocked = product.copy(stock = 1505)
        val panel = service.add(emptyList(), stocked, 400, "panel_cash")
        val mixed = service.add(panel, stocked, 1000, "serial_51_200")
        val edited = service.changeQuantity(mixed, mixed[1], stocked, 1105)
        assertEquals(1505L, edited.sumOf {it.qty.toLong()})
        assertEquals(400, edited.first().qty)
        assertThrows(IllegalArgumentException::class.java) { service.changeQuantity(edited, edited[1], stocked, 1106) }
        assertEquals(listOf(edited.first()), service.remove(edited, edited[1], stocked))
        val maximum = stocked.copy(stock = Int.MAX_VALUE)
        val huge = service.add(emptyList(), maximum, Int.MAX_VALUE, "serial_51_200")
        assertThrows(IllegalArgumentException::class.java) { service.add(huge, maximum, 1, "serial_1_50") }
    }

    /** Invalid policy values cannot silently alter quantity rules. */
    @Test fun rejectsInvalidPolicy() {
        assertThrows(IllegalArgumentException::class.java) { OrderPolicy(0, 50) }
        assertThrows(IllegalArgumentException::class.java) { OrderPolicy(20, 50) }
    }

    /** A pending or rejected company decision never advances fulfillment. */
    @Test fun distinguishesCompanyReviewStates() {
        assertEquals(false, CompanyReview.from("در انتظار تأیید", "در انتظار تأیید سفارش").approved)
        assertEquals(true, CompanyReview.from("تأیید شده", "ثبت شده").approved)
        assertEquals(true, CompanyReview.from("رد شده", "لغو شد").stopped)
        assertEquals("رد شده توسط شرکت", CompanyReview.from("رد شده", "لغو شد").label)
        assertEquals("لغو شده", CompanyReview.from("لغو شد", "لغو شد").label)
        assertEquals(false, CompanyReview.from("", "").approved)
    }
}

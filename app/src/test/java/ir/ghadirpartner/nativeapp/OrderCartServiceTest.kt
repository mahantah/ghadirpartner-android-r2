package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests real cart transitions instead of live orders or financial writes. */
class OrderCartServiceTest {
    private val service: OrderCartService = OrderCartService(OrderPolicy(999, 50))
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

package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent page truncation, malformed counters, and arithmetic overflow in the warehouse pager. */
class WarehouseInventoryPageTest {
    /** A search can have fewer matches than the global ownership totals. */
    @Test
    fun searchKeepsGlobalCounters() {
        val page = WarehouseInventoryPage(emptyList(), 201, 1000, 2000, 1, 200)
        assertTrue(page.hasMore())
        assertFalse(page.copy(page = 2).hasMore())
    }

    /** Empty searches finish without a spurious next-page button. */
    @Test
    fun emptyPageFinishes() {
        assertFalse(WarehouseInventoryPage(emptyList(), 0, 0, 0, 1, 200).hasMore())
    }

    /** Large inputs must not wrap the page multiplication into a negative value. */
    @Test
    fun pageArithmeticDoesNotOverflow() {
        assertFalse(WarehouseInventoryPage(emptyList(), Int.MAX_VALUE, Int.MAX_VALUE, 0, Int.MAX_VALUE, 200).hasMore())
    }

    /** Reject impossible server counters before displaying misleading totals. */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInconsistentCounts() {
        WarehouseInventoryPage(emptyList(), 5, 1, 1, 1, 200)
    }
}

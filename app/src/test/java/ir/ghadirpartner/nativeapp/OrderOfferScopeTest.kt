package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Match server scope semantics without Android storage or live order writes. */
class OrderOfferScopeTest {
    private val lines = listOf(CartLine("I90",2,"panel_cash","",101), CartLine("I90",1,"serial_1_50","",117), CartLine("M3P",1,"panel_cash","",200), CartLine("V77",1,"panel_cash","",900))
    /** Several selected models include both panel and free-serial rows of each model. */
    @Test fun multipleProductsExcludeOtherRows() { assertEquals(519L, OrderOfferScope(setOf("I90","M3P")).eligibleAmount(lines)) }
    /** Legacy single-product offers retain their original scope. */
    @Test fun singleProductRemainsRestricted() { assertEquals(319L, OrderOfferScope(setOf("I90")).eligibleAmount(lines)) }
    /** Explicit all-product scopes and scopes absent from the cart have opposite meanings. */
    @Test fun allAndNoEligibleRows() { assertEquals(1419L,OrderOfferScope(emptySet()).eligibleAmount(lines));assertEquals(0L,OrderOfferScope(setOf("UNKNOWN")).eligibleAmount(lines)) }
}

package ir.ghadirpartner.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verify the default allocations without touching the payment gateway or device. */
class SettlementSelectionTest {
    private val total = 1_000L
    private val selection = SettlementSelection(total)

    /** Full available credit prefills the invoice and replaces the default cash allocation. */
    @Test
    fun creditCoversInvoiceWhenAvailable() {
        val settlement = defaultSplitSettlement(total)
        selection.selectNonCash(settlement, "credit", 1_300L)
        assertEquals(total, settlement.l("credit_amount"))
        assertFalse(settlement.b("cash_enabled"))
        assertFalse(settlement.b("terms_accepted"))
    }

    /** Insufficient credit leaves an explicit balance for the customer to add as cash. */
    @Test
    fun limitedCreditLeavesUnpaidCashBalance() {
        val settlement = defaultSplitSettlement(total)
        selection.selectNonCash(settlement, "credit", 400L)
        assertEquals(600L, settlementRemaining(settlement, total))
        toggleSettlementPart(settlement, "cash", true, total)
        assertEquals(600L, settlement.l("cash_amount"))
        selection.selectNonCash(settlement, "check")
        assertEquals(total, settlement.l("check_amount"))
        assertFalse(settlement.b("credit_enabled"))
        assertFalse(settlement.has("bank_name"))
        assertFalse(settlement.has("due_date"))
    }

    /** Wallet use never exceeds its spendable balance and adjusts the remaining cash portion. */
    @Test
    fun walletUsesOnlySpendableFunds() {
        val settlement = defaultSplitSettlement(total)
        selection.setWallet(settlement, true, 700L, 400L)
        assertEquals(400L, settlement.l("wallet_amount"))
        assertEquals(600L, settlement.l("cash_amount"))
        assertTrue(settlement.b("cash_enabled"))
        selection.setWallet(settlement, false, 0L, 400L)
        assertEquals(total, settlement.l("cash_amount"))
    }
}

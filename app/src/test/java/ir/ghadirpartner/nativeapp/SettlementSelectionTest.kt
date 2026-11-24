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
    /** The first online selection can use the whole invoice before a cash option is picked. */
    @Test
    fun onlineUsesImplicitCashBalance() {
        val settlement = defaultSplitSettlement(total)
        assertFalse(CashDepositRows.hasExplicitMethod(settlement))
        assertEquals(total, selection.onlineDefault(settlement, keepDeposit = false))
    }

    /** A second cash method and later wallet selection preserve the first allocation. */
    @Test
    fun cashMethodsPrefillJustTheOutstandingAmount() {
        val settlement = defaultSplitSettlement(total)
        CashDepositRows.setMethods(settlement, deposit = false, online = true, amount = 300L, locked = false)
        selection.selectDeposit(settlement, keepOnline = true, locked = false)
        assertEquals(700L, CashDepositRows.read(settlement).single().amount)
        assertEquals(300L, CashDepositRows.onlineAmount(settlement))
        assertEquals(300L, selection.onlineDefault(settlement, keepDeposit = true))
        selection.setWallet(settlement, true, 900L, 900L)
        assertEquals(0L, settlement.l("wallet_amount"))
        assertEquals(total, settlement.l("cash_amount"))
    }

    /** Adding credit to an entered deposit uses only the balance left after that deposit. */
    @Test
    fun creditPreservesExplicitDeposit() {
        val settlement = defaultSplitSettlement(total)
        selection.selectDeposit(settlement, keepOnline = false, locked = false)
        CashDepositRows.set(settlement, listOf(CashDeposit(300L)), amountsLocked = false)
        selection.selectNonCash(settlement, "credit", 900L)
        assertEquals(300L, settlement.l("cash_amount"))
        assertEquals(700L, settlement.l("credit_amount"))
    }
    /** The advance warning counts the unpaid placeholder but excludes actual selected funds. */
    @Test
    fun outstandingBalanceUsesActualChoices() {
        val settlement = defaultSplitSettlement(total)
        assertEquals(total, outstandingSettlementBalance(settlement, total, fixedTerms = false))
        assertEquals(0L, outstandingSettlementBalance(settlement, total, fixedTerms = true))
        CashDepositRows.setMethods(settlement, deposit = false, online = true, amount = 300L, locked = false)
        assertEquals(700L, outstandingSettlementBalance(settlement, total, fixedTerms = false))
    }
}

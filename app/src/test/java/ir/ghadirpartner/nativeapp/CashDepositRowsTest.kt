package ir.ghadirpartner.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashDepositRowsTest {
    /** Keep bank-bound funds separate from the attached deposit even though both count as cash. */
    @Test
    fun separatesOnlineAmountFromManualProof() {
        val settlement = defaultSplitSettlement(100_000)
        CashDepositRows.set(settlement, listOf(CashDeposit(30_000, "first")), amountsLocked = false)
        CashDepositRows.setMethods(settlement, deposit = true, online = true, amount = 70_000, locked = false)
        assertEquals(100_000L, settlement.l("cash_amount"))
        assertEquals(70_000L, CashDepositRows.onlineAmount(settlement))
        assertEquals("", CashDepositRows.error(settlement))
        assertEquals(30_000L, PaymentProofSelection.active(settlement, "cash").single().second.single().depositAmount!!)
    }

    /** Keep the image and amount together from settlement review to the existing proof API. */
    @Test
    fun serializesEachDepositWithItsOwnAmount() {
        val settlement = defaultSplitSettlement(100_000)
        val rows = listOf(CashDeposit(30_000, "first"), CashDeposit(70_000, "second"))
        CashDepositRows.set(settlement, rows, amountsLocked = false)
        assertEquals(rows, CashDepositRows.read(settlement))
        assertEquals(100_000L, settlement.l("cash_amount"))
        assertEquals("", CashDepositRows.error(settlement))
        val outgoing = JSONObject(settlement.toString())
        PaymentProofSelection.prepare(outgoing, "cash")
        val files = outgoing.obj(PaymentProofSelection.field).getJSONArray("cash")
        assertEquals("first", files.getJSONObject(0).s("data"))
        assertTrue(files.getJSONObject(0).s("note").contains(formatMoney(30_000)))
        assertTrue(files.getJSONObject(1).s("note").contains(formatMoney(70_000)))
        assertFalse(outgoing.has(CashDepositRows.field))
    }

    /** Warn about incomplete rows, and apply the existing upload-later setting. */
    @Test
    fun requiresMatchingProofsAndKeepsApprovedTotal() {
        val settlement = defaultSplitSettlement(100_000)
        CashDepositRows.set(settlement, listOf(CashDeposit(30_000, "first"), CashDeposit(70_000)), amountsLocked = true)
        assertTrue(CashDepositRows.error(settlement).contains("تصویر"))
        settlement.put("upload_proof_later", true)
        assertEquals("", CashDepositRows.error(settlement))
        CashDepositRows.set(settlement, listOf(CashDeposit(25_000), CashDeposit(70_000)), amountsLocked = true)
        assertEquals(100_000L, settlement.l("cash_amount"))
        assertTrue(CashDepositRows.error(settlement).contains("جمع مبلغ"))
    }
}

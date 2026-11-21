package ir.ghadirpartner.nativeapp

import org.junit.Assert.*
import org.junit.Test

/** Exercise agent discount allocation and review triggers without network or financial writes. */
class AgentSettlementPolicyTest {
    private val policy = AgentSettlementPolicy(40)
    private val agent = CartLine("I90",1,"sales_agent","عامل فروش",100L)

    /** Default terms preserve the invoice exactly, including rounding. */
    @Test fun defaultsKeepExactCashAndChequeTotal() {
        val value=policy.defaults(listOf(agent),99L)
        assertEquals(40L,value.l("cash_amount"))
        assertEquals(59L,value.l("check_amount"))
        assertEquals(99L,settlementAllocated(value))
        assertFalse(policy.changed(listOf(agent),99L,value))
    }
    /** Non-agent lines stay cash while discounts apply proportionally to the agent portion. */
    @Test fun mixedPriceCartOnlyFinancesAgentLines() {
        val lines=listOf(agent,agent.copy(product="H9",priceKey="serial_1_50"))
        val value=policy.defaults(lines,180L)
        assertEquals(126L,value.l("cash_amount"))
        assertEquals(54L,value.l("check_amount"))
        assertTrue(policy.changed(lines,180L,value.put("cash_amount",180L).put("check_amount",0L)))
    }
    /** Existing free-serial and panel purchases retain their cash defaults. */
    @Test fun otherPriceGroupsDoNotRequireManagerReview() {
        val lines=listOf(agent.copy(priceKey="panel_cash"))
        val value=policy.defaults(lines,100L)
        assertEquals(100L,value.l("cash_amount"))
        assertFalse(policy.changed(lines,100L,value))
    }
}

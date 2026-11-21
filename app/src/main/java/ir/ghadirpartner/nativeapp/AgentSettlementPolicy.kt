package ir.ghadirpartner.nativeapp

import org.json.JSONObject

/** Derive the agent cheque portion without changing any server-authorized item prices. */
internal class AgentSettlementPolicy(private val cashPercent: Int = BuildConfig.AGENT_CASH_PERCENT) {
    init { require(cashPercent in 1..99) }

    /** Distribute discount proportionally and put rounding remainder into the cash portion. */
    fun defaults(lines: List<CartLine>, total: Long): JSONObject {
        val base = lines.sumOf { it.unit * it.qty }
        val agent = lines.filter { it.priceKey == "sales_agent" }.sumOf { it.unit * it.qty }
        val agentNet = if (base > 0L) (total.toDouble() * (agent.toDouble() / base)).toLong() else 0L
        val cheque = (agentNet / 100L) * (100 - cashPercent) + (agentNet % 100L) * (100 - cashPercent) / 100L
        return defaultSplitSettlement(total).put("cash_amount",total-cheque).put("check_enabled",cheque>0L).put("check_amount",cheque)
    }

    /** Compare amounts only; uploaded evidence and bank details do not change the agreed split. */
    fun changed(lines: List<CartLine>, total: Long, settlement: JSONObject): Boolean {
        if (lines.none { it.priceKey == "sales_agent" }) return false
        val expected = defaults(lines,total)
        return listOf("cash","check","credit","wallet").any { settlement.l("${it}_amount") != expected.l("${it}_amount") }
    }
}

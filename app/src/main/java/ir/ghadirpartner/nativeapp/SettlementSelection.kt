package ir.ghadirpartner.nativeapp

import org.json.JSONObject

/** Prefill a customer-selected method without changing the invoice or stored payment details. */
internal class SettlementSelection(private val invoiceTotal: Long) {
    /** Move the unpaid invoice balance to the selected non-cash method. */
    fun selectNonCash(settlement: JSONObject, method: String, availableCredit: Long = 0L) {
        if (method == "check") settlement.setPart("wallet", false, 0L)
        val wallet = settlement.activeAmount("wallet")
        val remainder = (invoiceTotal - wallet).coerceAtLeast(0L)
        val amount = if (method == "credit") remainder.coerceAtMost(availableCredit.coerceAtLeast(0L)) else remainder
        settlement.setPart("check", method == "check", if (method == "check") amount else 0L)
        settlement.setPart("credit", method == "credit", if (method == "credit") amount else 0L)
        settlement.setPart("cash", false, 0L)
    }

    /** Limit the wallet to its spendable balance and keep the non-cash allocation intact. */
    fun setWallet(settlement: JSONObject, enabled: Boolean, amount: Long, availableWallet: Long) {
        val nonCash = settlement.activeAmount("check") + settlement.activeAmount("credit")
        val walletAmount = if (enabled) amount.coerceIn(0L, maximumWallet(availableWallet, nonCash)) else 0L
        val previousWallet = settlement.activeAmount("wallet")
        val previousCash = settlement.activeAmount("cash")
        val cashCapacity = (invoiceTotal - nonCash - walletAmount).coerceAtLeast(0L)
        val cash = if (settlementRemaining(settlement, invoiceTotal) == 0L && previousWallet != walletAmount) {
            cashCapacity
        } else {
            previousCash.coerceAtMost(cashCapacity)
        }
        settlement.setPart("wallet", enabled, walletAmount)
        if (settlement.b("cash_enabled")) settlement.setPart("cash", true, cash)
    }

    private fun maximumWallet(availableWallet: Long, nonCash: Long): Long =
        availableWallet.coerceIn(0L, (invoiceTotal - nonCash).coerceAtLeast(0L))

    private fun JSONObject.activeAmount(part: String): Long =
        if (b("${part}_enabled")) l("${part}_amount").coerceAtLeast(0L) else 0L

    private fun JSONObject.setPart(part: String, enabled: Boolean, amount: Long) {
        put("${part}_enabled", enabled).put("${part}_amount", amount)
    }
}

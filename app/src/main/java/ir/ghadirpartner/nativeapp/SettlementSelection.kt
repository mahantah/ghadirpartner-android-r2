package ir.ghadirpartner.nativeapp

import org.json.JSONObject

/** Prefill a customer-selected method without changing the invoice or stored payment details. */
internal class SettlementSelection(private val invoiceTotal: Long) {
    /** Move the unpaid invoice balance to the selected non-cash method. */
    fun selectNonCash(settlement: JSONObject, method: String, availableCredit: Long = 0L) {
        if (method == "check") settlement.setPart("wallet", false, 0L)
        val wallet = settlement.activeAmount("wallet")
        val keepCash = settlement.b("cash_enabled") && CashDepositRows.hasExplicitMethod(settlement)
        val cash = if (keepCash) settlement.activeAmount("cash") else 0L
        val remainder = (invoiceTotal - wallet - cash).coerceAtLeast(0L)
        val amount = if (method == "credit") remainder.coerceAtMost(availableCredit.coerceAtLeast(0L)) else remainder
        settlement.setPart("check", method == "check", if (method == "check") amount else 0L)
        settlement.setPart("credit", method == "credit", if (method == "credit") amount else 0L)
        if (!keepCash) settlement.setPart("cash", false, 0L)
    }

    /** Allocate the available balance to online payment, accounting for an explicitly selected deposit. */
    fun onlineDefault(settlement: JSONObject, keepDeposit: Boolean): Long {
        val deposit = if (keepDeposit) CashDepositRows.read(settlement).sumOf { it.amount } else 0L
        return (cashCapacity(settlement) - deposit).coerceAtLeast(0L)
    }

    /** Prefill an empty deposit without changing other cash methods or existing receipt pairs. */
    fun selectDeposit(settlement: JSONObject, keepOnline: Boolean, locked: Boolean) {
        val online = if (keepOnline) CashDepositRows.onlineAmount(settlement) else 0L
        val rows = CashDepositRows.read(settlement)
        val capacity = if (locked) settlement.l("cash_amount") else cashCapacity(settlement)
        val available = (capacity - online).coerceAtLeast(0L)
        val first = rows.singleOrNull()
        if (first != null && first.image.isBlank()) {
            val saved = first.amount.takeIf { CashDepositRows.hasExplicitMethod(settlement) && it in 1..available }
            CashDepositRows.set(
                settlement,
                listOf(CashDeposit(saved ?: available)),
                amountsLocked = true,
            )
        }
        settlement.put("cash_enabled", true)
        CashDepositRows.setMethods(settlement, true, keepOnline, online, locked)
    }

    private fun cashCapacity(settlement: JSONObject): Long =
        (invoiceTotal - listOf("check", "credit", "wallet").sumOf { settlement.activeAmount(it) }).coerceAtLeast(0L)

    /** Limit the wallet to its spendable balance and keep the non-cash allocation intact. */
    fun setWallet(settlement: JSONObject, enabled: Boolean, amount: Long, availableWallet: Long) {
        val nonCash = settlement.activeAmount("check") + settlement.activeAmount("credit")
        val explicitCash = settlement.b("cash_enabled") && CashDepositRows.hasExplicitMethod(settlement)
        val reservedCash = if (explicitCash) settlement.activeAmount("cash") else 0L
        val walletAmount = if (enabled) amount.coerceIn(0L, maximumWallet(availableWallet, nonCash + reservedCash)) else 0L
        val previousWallet = settlement.activeAmount("wallet")
        val previousCash = settlement.activeAmount("cash")
        val cashCapacity = (invoiceTotal - nonCash - walletAmount).coerceAtLeast(0L)
        val cash = if (!explicitCash && settlementRemaining(settlement, invoiceTotal) == 0L && previousWallet != walletAmount) {
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

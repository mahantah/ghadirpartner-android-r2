package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** Keep each manual bank deposit paired with its own receipt. */
internal data class CashDeposit(val amount: Long = 0L, val image: String = "")

internal object CashDepositRows {
    const val field = "cash_deposits"

    /** Distinguish older online-only drafts from drafts with an explicit deposit choice. */
    fun depositEnabled(settlement: JSONObject): Boolean =
        settlement.optBoolean("cash_deposit_enabled", !settlement.b("cash_online"))

    /** Read only the portion that will go to the gateway. */
    fun onlineAmount(settlement: JSONObject): Long =
        if (!settlement.b("cash_online")) 0L else settlement.optLong("cash_online_amount", settlement.l("cash_amount"))

    /** Store the two cash options and recalculate the editable cash total. */
    fun setMethods(settlement: JSONObject, deposit: Boolean, online: Boolean, amount: Long, locked: Boolean) {
        val rows = read(settlement).let {
            if (deposit && !online && !depositEnabled(settlement) && it.all { row -> row.amount == 0L }) {
                listOf(CashDeposit(settlement.l("cash_amount")))
            } else it
        }
        set(settlement, rows, amountsLocked = true)
        settlement.put("cash_deposit_enabled", deposit).put("cash_online", online).put("cash_online_amount", amount)
        if (!locked) settlement.put("cash_amount", (if (deposit) rows.sumOf { it.amount } else 0L) + onlineAmount(settlement))
    }

    /** Seed the first row from the established cash amount, including older drafts. */
    fun read(settlement: JSONObject): List<CashDeposit> {
        val files = PaymentProofSelection.read(settlement, "cash")
        settlement.optJSONArray(field)?.let { array ->
            return List(array.length()) { index ->
                val row = array.getJSONObject(index)
                CashDeposit(row.l("amount"), files.getOrNull(row.optInt("image_index", -1)).orEmpty())
            }
        }
        val amount = (settlement.l("cash_amount") - onlineAmount(settlement)).coerceAtLeast(0L)
        return files.mapIndexed { index, data -> CashDeposit(if (index == 0) amount else 0L, data) }
            .ifEmpty { listOf(CashDeposit(amount)) }
    }

    /** Update the receipt rows while preserving any fixed cash total approved by staff. */
    fun set(settlement: JSONObject, rows: List<CashDeposit>, amountsLocked: Boolean) {
        val files = rows.map { it.image }.filter(String::isNotBlank)
        require(files.size == files.distinct().size) { "این تصویر فیش تکراری است." }
        PaymentProofSelection.requireBatchSize(files + PaymentProofSelection.read(settlement, "check"))
        PaymentProofSelection.set(settlement, "cash", files)
        var imageIndex = 0
        settlement.put(field, JSONArray(rows.map {
            JSONObject().put("amount", it.amount).put("image_index", if (it.image.isBlank()) -1 else imageIndex++)
        }))
        if (!amountsLocked) settlement.put("cash_amount", (if (depositEnabled(settlement)) rows.sumOf { it.amount } else 0L) + onlineAmount(settlement))
    }

    /** Check complete pairs before order submission and prevent changes to a locked total. */
    fun error(settlement: JSONObject): String {
        if (!settlement.b("cash_enabled")) return ""
        val online = onlineAmount(settlement)
        if (settlement.b("cash_online") && online <= 0L) return "مبلغ پرداخت آنلاین را وارد کنید."
        val rows = if (depositEnabled(settlement)) read(settlement) else emptyList()
        if (!settlement.has(field) && !settlement.has("cash_online_amount")) return ""
        if (rows.size > BuildConfig.PROOF_MAX_FILES_PER_KIND || rows.any { it.amount <= 0L }) {
            return "مبلغ هر ردیف واریز را وارد کنید یا ردیف خالی را حذف کنید."
        }
        if (rows.sumOf { it.amount } + online != settlement.l("cash_amount")) return "جمع مبلغ واریز و پرداخت آنلاین باید برابر سهم نقدی سفارش باشد."
        val hasMultipleOrAttached = rows.size > 1 || rows.any { it.image.isNotBlank() }
        if (hasMultipleOrAttached && !settlement.b("upload_proof_later") && rows.any { it.image.isBlank() }) {
            return "تصویر هر فیش را کنار مبلغ همان واریز بارگذاری کنید."
        }
        return ""
    }
}

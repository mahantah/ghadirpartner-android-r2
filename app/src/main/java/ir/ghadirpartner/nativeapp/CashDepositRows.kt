package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** Keep each manual bank deposit paired with its own receipt. */
internal data class CashDeposit(val amount: Long = 0L, val image: String = "")

internal object CashDepositRows {
    const val field = "cash_deposits"

    /** Seed the first row from the established cash amount, including older drafts. */
    fun read(settlement: JSONObject): List<CashDeposit> {
        val files = PaymentProofSelection.read(settlement, "cash")
        settlement.optJSONArray(field)?.let { array ->
            return List(array.length()) { index ->
                val row = array.getJSONObject(index)
                CashDeposit(row.l("amount"), files.getOrNull(row.optInt("image_index", -1)).orEmpty())
            }
        }
        val amount = settlement.l("cash_amount")
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
        if (!amountsLocked) settlement.put("cash_amount", rows.sumOf { it.amount })
    }

    /** Check complete pairs before order submission and prevent changes to a locked total. */
    fun error(settlement: JSONObject): String {
        if (!settlement.b("cash_enabled") || settlement.b("cash_online") || !settlement.has(field)) return ""
        val rows = read(settlement)
        if (rows.size > BuildConfig.PROOF_MAX_FILES_PER_KIND || rows.any { it.amount <= 0L }) {
            return "مبلغ هر ردیف واریز را وارد کنید یا ردیف خالی را حذف کنید."
        }
        if (rows.sumOf { it.amount } != settlement.l("cash_amount")) return "جمع مبلغ فیش‌ها باید برابر سهم نقدی سفارش باشد."
        val hasMultipleOrAttached = rows.size > 1 || rows.any { it.image.isNotBlank() }
        if (hasMultipleOrAttached && !settlement.b("upload_proof_later") && rows.any { it.image.isBlank() }) {
            return "تصویر هر فیش را کنار مبلغ همان واریز بارگذاری کنید."
        }
        return ""
    }
}

package ir.ghadirpartner.nativeapp

import org.json.JSONObject

/** Carries an explicit company decision, separate from financial approval. */
internal data class CompanyOrderDecision(val orderId: Int, val approved: Boolean, val reason: String)

/** Uses the server's sales/admin authorization for both company decisions. */
internal class CompanyOrderReviewRepository(private val api: ApiClient) {
    /** Send a single review decision; an HTTP failure is never presented as a successful review. */
    suspend fun decide(decision: CompanyOrderDecision): JSONObject {
        val path: String = if (decision.approved) "/api/customer-order/approve" else "/api/customer-order/reject"
        val body: JSONObject = JSONObject().put("id", decision.orderId)
        if (!decision.approved) body.put("reason", decision.reason.trim())
        return api.post(path, body) as JSONObject
    }
}

/** The staff-facing review facade; payment and fulfillment remain separate actions. */
internal class CompanyOrderReviewService(private val repository: CompanyOrderReviewRepository) {
    /** Validate a company decision before passing it to the authenticated repository. */
    suspend fun decide(decision: CompanyOrderDecision): JSONObject {
        require(decision.orderId > 0) { "سفارش معتبر نیست" }
        require(decision.approved || decision.reason.isNotBlank()) { "علت عدم تأیید سفارش را وارد کنید" }
        return repository.decide(decision)
    }
}

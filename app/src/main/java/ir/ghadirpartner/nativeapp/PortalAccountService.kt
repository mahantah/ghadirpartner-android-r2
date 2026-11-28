package ir.ghadirpartner.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** The saved bank destination associated with a signed-in customer. */
internal data class AccountBank(val iban: String = "", val name: String = "") {
    val complete: Boolean get() = iban.isNotBlank() && name.isNotBlank()

    /** Validate both the Iranian IBAN length and its international checksum. */
    fun validIban(): Boolean {
        if (!iban.matches(Regex("IR[0-9]{24}"))) return false
        return (iban.substring(4) + "1827" + iban.substring(2, 4))
            .fold(0) { remainder, digit -> (remainder * 10 + digit.digitToInt()) % 97 } == 1
    }
}

/** Keep account management and bank settings behind one authenticated API facade. */
internal class PortalAccountService(private val api: ApiClient) {
    /** Read the saved destination without changing it. */
    suspend fun bank(): AccountBank = bankFrom(api.request("GET", "/api/native/account-bank", null) as JSONObject)

    /** Save and return the server-validated destination. */
    suspend fun saveBank(bank: AccountBank): AccountBank =
        bankFrom(api.post("/api/native/account-bank", JSONObject().put("iban", bank.iban).put("bank_name", bank.name)) as JSONObject)

    /** Read the access level of the signed-in identity. */
    suspend fun accessLevel(): String =
        (api.request("GET", "/api/native/account-access", null) as JSONObject).s("access_level")

    /** Read active users of this account. */
    suspend fun users(): List<JSONObject> = (api.request("GET", "/api/native/account-users", null) as JSONArray).objects()

    /** Verify the new user's number before granting access. */
    suspend fun sendCode(mobile: String) {
        api.post("/api/native/account-users/code", JSONObject().put("mobile", mobile))
    }

    /** Enroll a separate login into this customer account. */
    suspend fun addUser(name: String, mobile: String, code: String, password: String, access: String): JSONObject =
        api.post("/api/native/account-users", JSONObject().put("name", name).put("mobile", mobile).put("otp", code).put("password", password).put("access_level", access)) as JSONObject

    /** Disable the selected user's access. */
    suspend fun revoke(id: Int) {
        api.post("/api/native/account-users/revoke", JSONObject().put("id", id))
    }

    private fun bankFrom(value: JSONObject): AccountBank = AccountBank(value.s("iban"), value.s("bank_name"))
}

/** Labels for the server-enforced levels granted by the representative manager. */
internal object DelegateAccess {
    val levels = linkedMapOf(
        "viewer" to "مشاهده",
        "sales" to "ثبت و پیگیری سفارش",
        "finance" to "امور مالی",
    )

    /** Convert the stored access level into a user-facing label. */
    fun label(level: String): String = levels[level] ?: "دسترسی قبلی"
}

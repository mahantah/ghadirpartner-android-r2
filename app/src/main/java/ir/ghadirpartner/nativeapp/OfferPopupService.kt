package ir.ghadirpartner.nativeapp

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject
import java.security.MessageDigest

/** Server eligibility and local display history are independent of purchase eligibility. */
internal data class OfferPopupEntry(val id: Int, val active: Boolean, val used: Boolean)

/** Select one unseen eligible offer without network or storage effects. */
internal class OfferPopupPolicy {
    /** Preserve the server's ordering, audience and validity filtering. */
    fun next(offers: List<OfferPopupEntry>, seen: Set<String>): Int? =
        offers.firstOrNull { it.id > 0 && it.active && !it.used && it.id.toString() !in seen }?.id
}

/** Persist only offer identifiers, scoped to a hashed account key on this installation. */
internal class OfferPopupRepository(private val preferences: SharedPreferences, account: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    /** Return a copy so callers cannot mutate SharedPreferences' backing set. */
    fun seen(): Set<String> = preferences.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    /** A dismissal acknowledges display without marking the server offer as purchased or read. */
    fun acknowledge(id: Int) { preferences.edit { putStringSet(key, seen() + id.toString()) } }
}

/** Home-screen facade keeps display selection and acknowledgement in one place. */
internal class OfferPopupService(private val repository: OfferPopupRepository, private val policy: OfferPopupPolicy) {
    /** The offers endpoint already restricts offers to this account and current dates. */
    fun next(offers: List<JSONObject>): JSONObject? {
        val id = policy.next(offers.map { OfferPopupEntry(it.i("id"), it.optBoolean("active", true), it.b("used_by_customer")) }, repository.seen())
        return offers.firstOrNull { it.i("id") == id }
    }
    /** Remember explicit dismissal or opening while leaving server state untouched. */
    fun acknowledge(offer: JSONObject) { repository.acknowledge(offer.i("id")) }
}

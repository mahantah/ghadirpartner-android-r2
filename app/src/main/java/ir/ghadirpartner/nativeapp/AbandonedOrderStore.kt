package ir.ghadirpartner.nativeapp

import android.content.Context
import org.json.JSONObject

/** Persists only unfinished checkout data for one signed-in customer. */
internal class AbandonedOrderRepository(
    context: Context,
    owner: String,
) {
    private val preferences = context.getSharedPreferences("ghadir_abandoned_order", Context.MODE_PRIVATE)
    private val key = "draft:${BuildConfig.APP_MODE}:$owner"

    init {
        // The old global slot has no owner, so it cannot safely be assigned to a customer.
        preferences.edit().remove("draft").apply()
    }

    /** Reads the account-specific serialized checkout. */
    fun read(): String? = preferences.getString(key, null)

    /** Replaces the account-specific serialized checkout. */
    fun write(raw: String) {
        preferences.edit().putString(key, raw).apply()
    }

    /** Removes only this customer's unfinished checkout. */
    fun delete() {
        preferences.edit().remove(key).apply()
    }
}

/** Facade enforcing expiration on every persisted draft read and write. */
internal class AbandonedOrderService(
    private val repository: AbandonedOrderRepository,
    private val policy: DailyDraftPolicy,
) {
    /** Loads a nonempty draft and removes invalid or expired persisted data. */
    fun load(): JSONObject? {
        val draft = repository.read()?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (draft != null &&
            policy.isValid(draft.optLong("saved_at")) &&
            (draft.optJSONArray("cart")?.length() ?: 0) > 0
        ) {
            return draft
        }
        repository.delete()
        return null
    }

    /** Saves an eligible draft without renewing its original creation day. */
    fun save(draft: JSONObject) {
        if (policy.isValid(draft.optLong("saved_at"))) repository.write(draft.toString()) else clear()
    }

    /** Clears the unfinished checkout, leaving submitted orders untouched. */
    fun clear() {
        repository.delete()
    }

    /** Returns the next expiry boundary for foreground refresh. */
    fun millisUntilMidnight(): Long = policy.millisUntilMidnight()
}

package ir.ghadirpartner.nativeapp

import android.content.Context
import org.json.JSONObject

/** One in-progress checkout saved when the customer leaves before submitting. */
internal object AbandonedOrderStore {
    private const val PREFS = "ghadir_abandoned_order"
    private const val KEY = "draft"

    fun load(context: Context): JSONObject? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optJSONArray("cart")?.length() ?: 0 > 0 }
    }

    fun save(context: Context, draft: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, draft.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

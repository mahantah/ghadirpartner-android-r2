package ir.ghadirpartner.nativeapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Reports foreground activity using only product names and quantities. */
internal class PortalActivityReporter(private val api: ApiClient) {
    private val lock = Mutex()
    private var pending: List<CartLine>? = null
    private var lastSent: List<Pair<String, Int>>? = null

    /** Send changed cart contents and retry a pending write on the next foreground signal. */
    suspend fun report(cart: List<CartLine>? = null): Long = lock.withLock {
        if (cart != null) pending = cart
        val snapshot = pending
        val products = snapshot?.map { it.product to it.qty }
        val body = JSONObject().put("channel", "android")
        if (products != null && products != lastSent) {
            val items = JSONArray()
            for ((name, qty) in products) items.put(JSONObject().put("product", name).put("qty", qty))
            body.put("cart", items)
        }
        try {
            val response = api.request("POST", "/api/portal-activity", body) as? JSONObject
            if (response?.optBoolean("ok") == true) {
                if (products != null) lastSent = products
                pending = null
            }
            (response?.optLong("heartbeat_seconds") ?: 0L).takeIf { it > 0L }
                ?.times(1000L) ?: BuildConfig.PORTAL_HEARTBEAT_RETRY_MS
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            BuildConfig.PORTAL_HEARTBEAT_RETRY_MS
        }
    }
}

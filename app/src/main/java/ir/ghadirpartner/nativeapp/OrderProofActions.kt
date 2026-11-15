package ir.ghadirpartner.nativeapp

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Submit payment documents to the customer-owned order. */
internal class OrderProofRepository(api: ApiClient, resolver: ContentResolver) {
    private val transport = api
    private val encoder = PaymentProofEncoder(resolver)

    /** Upload a selection and return the server count for this payment kind. */
    suspend fun upload(orderId: Int, kind: String, uris: List<Uri>, remaining: Int): Int {
        val result = transport.post("/api/native/order-proof", JSONObject()
            .put("order_id", orderId)
            .put("kind", kind)
            .put("files", JSONArray(encoder.encode(uris, remaining)))) as JSONObject
        check(result.b("ok") && result.i("count") > 0) { "ثبت فایل‌ها انجام نشد." }
        return result.i("count")
    }
}

/** Show recorded payment proofs and allow customers to add another selection. */
@Composable
internal fun OrderProofActions(api: ApiClient, order: JSONObject) {
    val id = order.i("id")
    val method = order.s("requested_payment_method")
    val settlement = order.obj("native_settlement")
    val kinds = listOf("cash" to "رسید پرداخت", "check" to "چک").filter { (kind, _) ->
        method == kind || (method == "mixed" && settlement.l("${kind}_amount") > 0)
    }
    if (id < 1 || kinds.isEmpty() ||
        order.s("status") in listOf("لغو شد", "رد شد", "رد شده") ||
        order.s("approval_status") in listOf("رد شد", "رد شده")
    ) return
    val recorded = settlement.arr("proofs").objects().filter { it.s("proof_token").isNotBlank() }
    val resolver = LocalContext.current.contentResolver
    val repository = remember(api, resolver) { OrderProofRepository(api, resolver) }
    val scope = rememberCoroutineScope()
    var target by rememberSaveable(id) { mutableStateOf("") }
    var cashUploaded by rememberSaveable(id) { mutableStateOf(0) }
    var checkUploaded by rememberSaveable(id) { mutableStateOf(0) }

    fun count(kind: String): Int {
        val legacyProof = recorded.isEmpty() && settlement.s("proof_token").isNotBlank() &&
            kind == settlement.s("kind").ifBlank { if (method == "check") "check" else "cash" }
        val stored = if (legacyProof) 1 else recorded.count { it.s("kind") == kind }
        return maxOf(stored, if (kind == "cash") cashUploaded else checkUploaded)
    }
    var busy by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val kind = target
        target = ""
        if (uris.isNotEmpty() && kind.isNotBlank()) {
            busy = true
            error = ""
            scope.launch {
                try {
                    val total = repository.upload(id, kind, uris, BuildConfig.PROOF_MAX_FILES_PER_KIND - count(kind))
                    if (kind == "cash") cashUploaded = total else checkUploaded = total
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    error = failure.message ?: "ثبت فایل‌ها انجام نشد. دوباره تلاش کنید."
                } finally {
                    busy = false
                }
            }
        }
    }
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مدارک پرداخت")
            for ((kind, label) in kinds) {
                val stored = count(kind)
                if (stored > 0) Text("$label · ${faNumber(stored)} فایل ثبت شد")
                if (stored < BuildConfig.PROOF_MAX_FILES_PER_KIND) {
                    GhadirButton(
                        if (busy) "در حال ثبت فایل‌ها…" else "افزودن عکس یا PDF $label",
                        { target = kind; picker.launch(PaymentProofSelection.mimeTypes) },
                        enabled = !busy && target.isEmpty(),
                        secondary = true,
                    )
                }
            }
            if (error.isNotBlank()) Text(error, color = Danger)
        }
    }
}

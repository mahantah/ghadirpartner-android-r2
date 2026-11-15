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
import org.json.JSONObject

/** Submit one missing payment image to the customer-owned order. */
internal class OrderProofRepository(api: ApiClient, resolver: ContentResolver) {
    private val transport = api
    private val encoder = PaymentProofEncoder(resolver)

    /** Upload an image and require a successful persistence response. */
    suspend fun upload(orderId: Int, kind: String, uri: Uri) {
        val result = transport.post("/api/native/order-proof", JSONObject()
            .put("order_id", orderId)
            .put("kind", kind)
            .put("image_base64", encoder.encode(uri))) as JSONObject
        check(result.b("ok")) { "ثبت تصویر انجام نشد." }
    }
}

/** Display missing payment proofs and upload them directly from order details. */
@Composable
internal fun OrderProofActions(api: ApiClient, order: JSONObject) {
    val id = order.i("id")
    val method = order.s("requested_payment_method")
    val settlement = order.obj("native_settlement")
    val kinds = listOf("cash" to "رسید پرداخت", "check" to "تصویر چک").filter { (kind, _) ->
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
    var uploaded by rememberSaveable(id) { mutableStateOf("") }
    var busy by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val kind = target
        target = ""
        if (uri != null && kind.isNotBlank()) {
            busy = true
            error = ""
            scope.launch {
                try {
                    repository.upload(id, kind, uri)
                    uploaded = (uploaded.split(",") + kind).joinToString(",")
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    error = failure.message ?: "ثبت تصویر انجام نشد. دوباره تلاش کنید."
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
                val legacyProof = recorded.isEmpty() && settlement.s("proof_token").isNotBlank() &&
                    kind == settlement.s("kind").ifBlank { if (method == "check") "check" else "cash" }
                if (kind in uploaded.split(",") || legacyProof || recorded.any { it.s("kind") == kind }) {
                    Text("$label ثبت شد و در پروندهٔ سفارش موجود است.")
                } else {
                    GhadirButton(
                        if (busy) "در حال ثبت تصویر…" else "بارگذاری $label",
                        { target = kind; picker.launch("image/*") },
                        enabled = !busy && target.isEmpty(),
                        secondary = true,
                    )
                }
            }
            if (error.isNotBlank()) Text(error, color = Danger)
        }
    }
}

package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Let customers review and describe evidence before adding it to an existing order. */
@Composable
internal fun OrderProofActions(api: ApiClient, order: JSONObject, onChanged: () -> Unit = {}) {
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
    val context = LocalContext.current
    val encoder = remember(context) { PaymentProofEncoder(context.contentResolver) }
    val repository = remember(api) { OrderFollowupRepository(api) }
    val scope = rememberCoroutineScope()
    var target by rememberSaveable(id) { mutableStateOf("") }
    var files by remember(id) { mutableStateOf(emptyList<SelectedProof>()) }
    var busy by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf("") }
    var cashCount by remember(id) { mutableStateOf(0) }
    var checkCount by remember(id) { mutableStateOf(0) }
    fun count(kind: String): Int {
        val legacy = recorded.isEmpty() && settlement.s("proof_token").isNotBlank() && kind == if (method == "check") "check" else "cash"
        val stored = if (legacy) 1 else recorded.count { it.s("kind") == kind }
        return maxOf(stored, if (kind == "cash") cashCount else checkCount)
    }
    fun receiveImages(uris: List<android.net.Uri>) {
        val kind = target
        if (uris.isEmpty() || kind.isBlank()) {
            target = ""
            return
        }
        scope.launch {
            busy = true
            error = ""
            try {
                files = encoder.encode(uris, BuildConfig.PROOF_MAX_FILES_PER_KIND - count(kind)).map { SelectedProof(it) }.distinctBy { it.data }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                target = ""
                error = failure.message ?: "آماده‌سازی فایل‌ها انجام نشد."
            } finally {
                busy = false
            }
        }
    }
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مدارک پرداخت")
            for ((kind, label) in kinds) {
                val current = recorded.filter { it.s("kind") == kind }
                current.forEachIndexed { index, item -> StoredOrderProof(repository, id, item, index + 1) }
                if (count(kind) < BuildConfig.PROOF_MAX_FILES_PER_KIND && files.isEmpty()) {
                    ImageUploadButton("بارگذاری $label", enabled=!busy&&target.isEmpty(),mimeTypes=PaymentProofSelection.mimeTypes) { uris -> target=kind;receiveImages(uris) }
                }
                if (kind == target && files.isNotEmpty()) {
                    SelectedProofReview(label, files, { index, note ->
                        files = files.mapIndexed { i, file -> if (i == index) file.copy(note = note) else file }
                    }, enabled = !busy)
                    GhadirButton("ثبت مدارک", {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                val result = api.post("/api/native/order-proof", JSONObject().put("order_id", id)
                                    .put("kind", kind).put("files", JSONArray(files.map(SelectedProof::payload)))) as JSONObject
                                if (kind == "cash") cashCount = result.i("count") else checkCount = result.i("count")
                                files = emptyList()
                                target = ""
                                onChanged()
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (failure: Exception) {
                                error = failure.message ?: "ثبت مدارک انجام نشد."
                            } finally {
                                busy = false
                            }
                        }
                    }, enabled = !busy)
                    GhadirButton("انصراف از انتخاب", { target = ""; files = emptyList() }, enabled = !busy, secondary = true)
                }
            }
            if (error.isNotBlank()) Text(error, color = Danger)
        }
    }
}

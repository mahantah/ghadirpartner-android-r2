package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun CheckoutAddressSection(
    api: ApiClient,
    customer: JSONObject,
    addresses: List<JSONObject>,
    selectedAddressId: String,
    onSelected: (String) -> Unit,
    onAddressesUpdated: (List<JSONObject>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showNew by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var postal by remember { mutableStateOf("") }
    val addressRow = addresses.firstOrNull { it.s("id") == selectedAddressId } ?: customer
    val addressText = listOf(addressRow.s("province"), addressRow.s("city"), addressRow.s("address")).filter { it.isNotBlank() }.joinToString("، ")
    val choices = listOf(JSONObject().put("id", "").put("title", "آدرس اصلی پروفایل").put("address", customer.s("address"))) + addresses
    fun saveNew() {
        scope.launch {
            busy = true
            error = ""
            try {
                val body = JSONObject()
                    .put("title", title.trim())
                    .put("province", province.trim())
                    .put("city", city.trim())
                    .put("address", address.trim())
                    .put("postal_code", postal)
                val rows = (api.post("/api/addresses", body) as JSONArray).objects()
                onAddressesUpdated(rows)
                rows.maxByOrNull { it.s("id") }?.let { onSelected(it.s("id")) }
                showNew = false
                title = ""
                province = ""
                city = ""
                address = ""
                postal = ""
            } catch (e: Exception) {
                error = e.message ?: "ذخیره آدرس ناموفق بود"
            } finally {
                busy = false
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownField("نشانی تحویل", addressText.ifBlank { "نشانی در پروفایل ثبت نشده است" }, choices, { it.s("title") + " • " + it.s("address") }) {
            onSelected(it.s("id"))
        }
        GhadirButton(if (showNew) "بستن فرم آدرس جدید" else "ثبت آدرس جدید", { showNew = !showNew }, secondary = true, enabled = !busy)
        if (showNew) {
            GlassSurface {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("آدرس جدید", color = Ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    AddressFormFields(title, { title = it }, province, { province = it }, city, { city = it }, address, { address = it }, postal, { postal = it })
                    if (error.isNotBlank()) Text(error, color = Danger)
                    GhadirButton(
                        if (busy) "در حال ذخیره…" else "ذخیره و انتخاب این آدرس",
                        { saveNew() },
                        enabled = !busy && title.isNotBlank() && province.isNotBlank() && city.isNotBlank() && address.isNotBlank(),
                    )
                }
            }
        }
    }
}

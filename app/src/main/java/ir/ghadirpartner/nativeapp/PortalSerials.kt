package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Read order-issued serials while preserving the selected model and original identifiers. */
internal fun orderSerialRows(order: JSONObject, model: String = ""): List<OrderSerialRow> = buildList {
    for (line in order.arr("items").objects()) {
        val product = line.s("product")
        if (model.isNotBlank() && product != model) continue
        val numbers = line.arr("serials")
        for (index in 0 until numbers.length()) {
            val value = numbers.opt(index)
            val serial = when (value) {
                is JSONObject -> value.s("serial")
                is String -> value
                else -> ""
            }.trim()
            if (serial.isNotEmpty()) add(OrderSerialRow(product, serial, (value as? JSONObject)?.s("imei").orEmpty()))
        }
    }
}

/** Identify orders with issued serial numbers. */
internal fun orderHasSerials(order: JSONObject): Boolean = orderSerialRows(order).isNotEmpty()

/** Present the issued serials for an order without payment or shipping details. */
@Composable
internal fun SerialOrderCard(order: JSONObject, onOpen: () -> Unit) {
    val serials = remember(order) { orderSerialRows(order) }
    ApprovedSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("سفارش " + faDigits(order.s("number")), color = Ink, fontWeight = FontWeight.Bold)
            Text(formatDateFa(order.s("created_at"), includeTime = false), color = Muted)
            Text(faNumber(serials.size) + " سریال", color = Ink)
            ApprovedButton("سریال‌های سفارش", onOpen, secondary = true)
        }
    }
}

/** Offer export immediately and load the visible serial rows only when requested. */
@Composable
internal fun PortalSerials(api:ApiClient,order:JSONObject,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val models = remember(order) { orderSerialRows(order).map(OrderSerialRow::model).filter(String::isNotBlank).distinct() }
    var model by rememberSaveable(order.i("id")) { mutableStateOf("") }
    var showRows by rememberSaveable(order.i("id")) { mutableStateOf(false) }
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    if(order.s("status")!="تحویل شد") {
        Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DesignNotice("دریافت سریال‌ها فقط برای سفارش تحویل‌شده امکان‌پذیر است.")
            GhadirButton("بازگشت به جزئیات سفارش",onBack,secondary=true)
        }
        return
    }
    fun download(excel: Boolean) {
        if(busy)return
        scope.launch {
            busy=true;error=""
            try {
                val path = if (excel) api.saveSerialsExcel(order, model) else api.saveSerialsPdf(order, model)
                Toast.makeText(context, "فایل سریال‌ها ذخیره شد: $path", Toast.LENGTH_LONG).show()
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message?:"فایل سریال‌ها دریافت نشد"}finally{busy=false}
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if (error.isNotBlank()) item { Text(error, color = Danger) }
            item {DesignNotice("سریال‌های سفارش تحویل‌شده\n\n"+faDigits(order.s("number")))}
            item {
                DropdownField("مدل دستگاه", model.ifBlank { "همه مدل‌ها" }, listOf("") + models,
                    { it.ifBlank { "همه مدل‌ها" } }) {
                    model = it
                    showRows = false
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhadirButton("دانلود PDF", { download(false) }, secondary = true, enabled = !busy, modifier = Modifier.weight(1f))
                    GhadirButton("دانلود Excel", { download(true) }, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
            item { Text(if (model.isBlank()) "خروجی شامل همهٔ سریال‌های سفارش است." else "خروجی فقط شامل سریال‌های مدل انتخاب‌شده است.", color = Muted, fontSize = 12.sp) }
            item { GhadirButton(if (showRows) "بستن فهرست سریال‌ها" else "نمایش سریال‌ها", { showRows = !showRows }, secondary = true) }
            if (showRows) {
                items(orderSerialRows(order, model)) { row ->
                    GlassSurface {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(shortProductName(row.model), color = Ink, fontWeight = FontWeight.Bold)
                            Text("SN: " + row.serial, color = Ink, fontSize = 14.sp)
                            Text("IMEI: " + row.imei.ifBlank { "ثبت نشده" }, color = Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
    }
}

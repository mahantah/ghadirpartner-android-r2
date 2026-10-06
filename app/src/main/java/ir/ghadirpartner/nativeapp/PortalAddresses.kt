package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal val priceTypes = listOf("serial_1_50" to "سریال آزاد ۱ تا ۵۰","serial_51_200" to "سریال آزاد ۵۱ تا ۲۰۰","sales_agent" to "عامل فروش","panel_cash" to "پنل — نقد","panel_7d" to "پنل — هفت‌روزه","panel_1m" to "پنل — یک‌ماهه")

@Composable
internal fun AddressSelector(api: ApiClient,selected: String,onSelected: (String)->Unit) {
    var addresses by remember {mutableStateOf(emptyList<JSONObject>())}
    var error by remember {mutableStateOf("")}
    LaunchedEffect(Unit) {try {addresses=(api.get("/api/addresses") as JSONArray).objects()}catch(e:Exception){error=e.message ?: "دریافت آدرس ناموفق بود"}}
    Column {
        DropdownField("آدرس تحویل",addresses.firstOrNull {it.s("id")==selected}?.s("title") ?: "آدرس اصلی پروفایل",listOf(JSONObject().put("id","").put("title","آدرس اصلی پروفایل"))+addresses,{it.s("title")+" · "+it.s("address")}) {onSelected(it.s("id"))}
        if(error.isNotBlank()) Text(error,color=Danger)
        Text("آدرس جدید را در پروفایل ← آدرس‌های من ذخیره کنید.",color=Muted)
    }
}

@Composable
internal fun PortalAddresses(api: ApiClient) {
    val scope=rememberCoroutineScope()
    var rows by remember {mutableStateOf(emptyList<JSONObject>())}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var id by remember {mutableStateOf("")}
    var title by remember {mutableStateOf("")}
    var province by remember {mutableStateOf("")}
    var city by remember {mutableStateOf("")}
    var address by remember {mutableStateOf("")}
    var postal by remember {mutableStateOf("")}
    LaunchedEffect(Unit) {try {rows=(api.get("/api/addresses") as JSONArray).objects()}catch(e:Exception){error=e.message ?: "خطا"}}
    fun save(remove: String?=null) {scope.launch {
        busy=true;error=""
        try {
            val body=if(remove!=null) JSONObject().put("delete_id",remove) else JSONObject().put("id",id).put("title",title.trim()).put("province",province.trim()).put("city",city.trim()).put("address",address.trim()).put("postal_code",postal)
            rows=(api.post("/api/addresses",body) as JSONArray).objects()
            id="";title="";province="";city="";address="";postal=""
        }catch(e:Exception){error=e.message ?: "ذخیره آدرس ناموفق بود"}finally {busy=false}
    }}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {ErrorBanner(error){error=""}}
        items(rows,key={it.s("id")}) {row->GlassSurface {Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(row.s("title"),color=Ink);Text(row.s("province")+"، "+row.s("city")+"، "+row.s("address"),color=Muted)
            Row {GlassTextButton(onClick={id=row.s("id");title=row.s("title");province=row.s("province");city=row.s("city");address=row.s("address");postal=row.s("postal_code")},enabled=!busy){Text("ویرایش")}
                GlassTextButton(onClick={save(row.s("id"))},enabled=!busy){Text("حذف",color=Danger)}}
        }}}
        item {Text(if(id.isBlank()) "آدرس جدید" else "ویرایش آدرس",color=Ink)}
        item {OutlinedTextField(title,{title=it.take(60)},label={Text("عنوان، مثلاً دفتر")},modifier=Modifier.fillMaxWidth())}
        item {OutlinedTextField(province,{province=it.take(80)},label={Text("استان")},modifier=Modifier.fillMaxWidth())}
        item {OutlinedTextField(city,{city=it.take(80)},label={Text("شهر")},modifier=Modifier.fillMaxWidth())}
        item {OutlinedTextField(address,{address=it.take(1000)},label={Text("نشانی کامل و پلاک")},modifier=Modifier.fillMaxWidth(),minLines=2)}
        item {OutlinedTextField(postal,{postal=it.filter(Char::isDigit).take(10)},label={Text("کدپستی اختیاری")},modifier=Modifier.fillMaxWidth())}
        item {GhadirButton(if(busy) "در حال ذخیره…" else "ذخیره آدرس",{save()},enabled=!busy&&title.isNotBlank()&&province.isNotBlank()&&city.isNotBlank()&&address.isNotBlank())}
    }
}

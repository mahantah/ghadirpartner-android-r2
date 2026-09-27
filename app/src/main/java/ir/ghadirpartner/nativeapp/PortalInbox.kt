package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Stable
class PortalInboxState(private val api: ApiClient) {
    var items by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    private val lock = Mutex()
    val unread: Int get() = items.count { !it.b("read") }
    suspend fun refresh(): Unit = lock.withLock {
        loading=true
        try {
            val response=api.get("/api/notifications") as? JSONObject
                ?: throw IllegalStateException("مرکز پیام هنوز روی سرور فعال نشده است.")
            if (!response.has("items")) throw IllegalStateException("پاسخ مرکز پیام معتبر نیست.")
            items=response.arr("items").objects(); error=""
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error=e.message ?: "دریافت اعلان‌ها ناموفق بود" } finally { loading=false }
    }
    suspend fun markRead(ids: List<String>): Unit = lock.withLock {
        if(ids.isNotEmpty()) try {
            api.post("/api/notifications/read",JSONObject().put("ids",JSONArray(ids)))
            items=items.map { JSONObject(it.toString()).apply { if(s("id") in ids) put("read",true) } }
            error=""
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error=e.message ?: "ثبت خوانده‌شدن ناموفق بود" }
    }
}

@Composable
fun NotificationBell(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick=onClick,modifier=modifier.size(48.dp).glass()) {
        Box(contentAlignment=Alignment.TopEnd) {
            Image(painterResource(R.drawable.figma_bell),"پیام‌ها و آفرها؛ ${faNumber(unread)} خوانده‌نشده",Modifier.size(24.dp))
            if(unread>0) Badge(containerColor=Orange,contentColor=Navy) { Text(if(unread>99) "۹۹+" else faNumber(unread),fontSize=9.sp) }
        }
    }
}

@Composable
fun PortalInbox(state: PortalInboxState, onOrder: (Int) -> Unit, onOffer: (Int) -> Unit) {
    val scope=rememberCoroutineScope()
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var filter by remember { mutableStateOf("all") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=16.dp,bottom=112.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { GhadirButton(if(state.loading) "در حال بروزرسانی…" else "بروزرسانی پیام‌ها",{scope.launch {state.refresh()}},enabled=!state.loading,secondary=true) }
        if(state.error.isNotBlank()) item { Text(state.error,color=Danger) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("all" to "همه", "message" to "پیام‌ها", "offer" to "آفرها", "order" to "سفارش‌ها").forEach { (key,label) ->
                GlassFilterChip(selected=filter==key,onClick={filter=key},label={Text(label,fontSize=11.sp)})
            }
        } }
        if(state.unread>0) item { GhadirButton("خواندن همه",{scope.launch {state.markRead(state.items.filterNot {it.b("read")}.map {it.s("id")})}},secondary=true) }
        val visible=state.items.filter {filter=="all"||it.s("kind")==filter}
        if(visible.isEmpty()&&!state.loading&&state.error.isBlank()) item { EmptyState("اعلانی وجود ندارد","پیام‌ها، آفرها و تغییر وضعیت سفارش‌ها اینجا نمایش داده می‌شوند.") }
        items(visible,key={it.s("id")}) { n ->
            GlassSurface(Modifier.fillMaxWidth().clickable {selected=n;scope.launch {state.markRead(listOf(n.s("id")))}}) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text((if(n.b("read")) "" else "●  ")+n.s("title"),fontWeight=FontWeight.Bold,color=Ink)
                    Text(n.s("body"),color=Muted,fontSize=13.sp,maxLines=3)
                    if(n.s("created_at").isNotBlank()) Text(formatDateFa(n.s("created_at")),color=Muted,fontSize=11.sp)
                }
            }
        }
    }
    selected?.let { n -> AlertDialog(onDismissRequest={selected=null},title={Text(n.s("title"))},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(n.s("body")) }
    },confirmButton={GlassTextButton(onClick={selected=null;when(n.s("kind")){"order"->onOrder(n.i("order_id"));"offer"->onOffer(n.i("offer_id"))}}){Text(if(n.s("kind")=="message") "بستن" else "مشاهده جزئیات")}},dismissButton={if(n.s("kind")!="message")GlassTextButton(onClick={selected=null}){Text("بستن")}}) }
}

@Composable
fun PortalOffers(api: ApiClient, selectedId: Int?, onUse: (String) -> Unit) {
    var offers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading=true
        try {offers=(api.get("/api/offers") as JSONArray).objects();error=""} catch(e: CancellationException){throw e} catch(e: Exception){error=e.message ?: "خطا در دریافت آفرها"} finally{loading=false}
    }
    if(loading){LoadingPane();return}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=16.dp,bottom=112.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {Text(error,color=Danger);GhadirButton("تلاش دوباره",{retry++})}
        val rows=offers.filter {selectedId==null||it.i("id")==selectedId}
        if(rows.isEmpty()&&error.isBlank())item {EmptyState("آفر فعالی موجود نیست","ممکن است زمان طرح تمام شده باشد یا برای حساب شما فعال نباشد.")}
        items(rows,key={it.i("id")}) {o->GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(o.s("title"),fontWeight=FontWeight.Bold,color=Ink,fontSize=18.sp)
            Text(offerDiscountTextNative(o),color=Orange)
            Text(o.s("description"),color=Ink)
            if(o.s("end_date").isNotBlank()) Text("اعتبار تا: ${formatDateFa(o.s("end_date"))}",color=Muted)
            if(o.s("promo_code").isNotBlank())Text("کد طرح: ${o.s("promo_code")}",color=Ink)
            GhadirButton(if(o.b("used_by_customer")) "قبلاً استفاده شده" else "ثبت سفارش با این طرح",{onUse(o.s("promo_code"))},enabled=!o.b("used_by_customer"))
        }} }
    }
}

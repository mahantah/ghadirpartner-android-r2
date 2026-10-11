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
class PortalInboxState(private val api:ApiClient,context:android.content.Context?=null,account:String="") {
    var items by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    private val prefs=context?.getSharedPreferences("ghadir_inbox",android.content.Context.MODE_PRIVATE)
    private val readKey="read:"+BuildConfig.APP_MODE+":"+account
    private var locallyRead=prefs?.getStringSet(readKey,emptySet())?.toSet()?:emptySet()
    private var localMode=false
    private val lock=Mutex()
    val unread:Int get()=items.count {!it.b("read")}

    suspend fun refresh():Unit=lock.withLock {
        loading=true
        try {
            val response=try{api.get("/api/notifications")}catch(e:ApiException){
                if(e.status !in setOf(404,405,501))throw e
                JSONArray()
            }
            val next=when(response) {
                is JSONObject->{
                    require(response.has("items")){"پاسخ مرکز پیام معتبر نیست."}
                    localMode=false
                    response.arr("items").objects()
                }
                is JSONArray->{
                    localMode=true
                    if(response.length()>0)response.objects() else accountActivity()
                }
                else->throw IllegalStateException("پاسخ مرکز پیام معتبر نیست.")
            }
            items=next.filter{it.s("id").isNotBlank()}.distinctBy{it.s("id")}.map {
                JSONObject(it.toString()).apply {if(s("id") in locallyRead)put("read",true)}
            }.sortedByDescending{it.s("created_at")}
            error=""
        }catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message?:"دریافت پیام‌ها ناموفق بود"}
        finally{loading=false}
    }

    private suspend fun accountActivity():List<JSONObject> {
        // These endpoints return only the authenticated customer's authorized data.
        val orders=(api.get("/api/orders") as JSONArray).objects()
        val offers=(api.get("/api/offers") as JSONArray).objects().filterNot{it.b("used_by_customer")}
        val orderMessages=orders.map {o->
            val latest=o.arr("history").objects().lastOrNull()
            val at=o.s("updated_at").ifBlank {latest?.s("at").orEmpty()}.ifBlank{o.s("created_at")}
            JSONObject().put("id","order:"+o.i("id")+":"+o.s("status")+":"+at)
                .put("kind","order").put("order_id",o.i("id"))
                .put("title","سفارش "+faDigits(o.s("number"))+" • "+o.s("status"))
                .put("body","وضعیت سفارش شما: "+o.s("status"))
                .put("created_at",at).put("read",false)
        }
        val offerMessages=offers.map {o->
            JSONObject().put("id","offer:"+o.i("id")+":"+o.s("updated_at").ifBlank{o.s("start_date")})
                .put("kind","offer").put("offer_id",o.i("id")).put("title",o.s("title"))
                .put("body",o.s("description")).put("created_at",o.s("created_at").ifBlank{o.s("start_date")})
                .put("read",false)
        }
        return orderMessages+offerMessages
    }

    suspend fun markRead(ids:List<String>):Unit=lock.withLock {
        val owned=ids.filter{id->items.any{it.s("id")==id}}.distinct()
        if(owned.isEmpty())return@withLock
        try {
            if(!localMode)try {
                api.post("/api/notifications/read",JSONObject().put("ids",JSONArray(owned)))
            }catch(e:ApiException){if(e.status !in setOf(404,405,501))throw e}
            locallyRead=(locallyRead+owned).takeLastSet(1000)
            prefs?.edit()?.putStringSet(readKey,locallyRead)?.apply()
            items=items.map {JSONObject(it.toString()).apply {if(s("id") in owned)put("read",true)}}
            error=""
        }catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message?:"ثبت خوانده‌شدن ناموفق بود"}
    }
    private fun Set<String>.takeLastSet(limit:Int):Set<String> = toList().takeLast(limit).toSet()
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
fun PortalInbox(state:PortalInboxState,onOrder:(Int)->Unit,onOffer:(Int)->Unit) {
    val scope=rememberCoroutineScope()
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var filter by remember {mutableStateOf("all")}
    fun open(n:JSONObject) {
        scope.launch {
            state.markRead(listOf(n.s("id")))
            when(n.s("kind")){"order"->onOrder(n.i("order_id"));"offer"->onOffer(n.i("offer_id"));else->selected=n}
        }
    }
    val visible=state.items.filter {filter=="all"||it.s("kind")==filter}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {DesignChoices(listOf("all" to "همه","order" to "سفارش‌ها","offer" to "آفرها"),filter,{filter=it})}
        item {Text("اعلان‌های سفارش و پیشنهادهای همکاران",color=Muted,fontSize=12.sp)}
        if(state.error.isNotBlank())item {ErrorBanner(state.error){};GhadirButton("تلاش دوباره",{scope.launch{state.refresh()}},enabled=!state.loading)}
        if(state.loading&&visible.isEmpty())item {LoadingPane()}
        if(visible.isEmpty()&&!state.loading&&state.error.isBlank())item {EmptyState("اعلانی وجود ندارد","پیام‌ها، آفرها و تغییر وضعیت سفارش‌ها اینجا نمایش داده می‌شوند.")}
        items(visible,key={it.s("id")}) {n->
            GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                val body=n.s("body")
                val asset=when {
                    n.s("kind")=="offer"->R.drawable.design_gift
                    body.contains("تحویل")||n.s("title").contains("تحویل")->R.drawable.design_notification_check
                    body.contains("ارسال")||n.s("title").contains("ارسال")->R.drawable.design_truck
                    else->R.drawable.design_package
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    DesignGlyph(asset,null,Modifier.size(30.dp))
                    Text((if(n.b("read"))"" else "● ")+n.s("title"),color=Ink,fontSize=16.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                }
                Text(body,color=Muted,fontSize=12.sp,lineHeight=20.sp)
                if(n.s("created_at").isNotBlank())Text(formatDateFa(n.s("created_at")),color=Muted,fontSize=11.sp)
                GhadirButton(when(n.s("kind")){"order"->if(n.s("title").contains("تحویل"))"دریافت سریال‌ها" else "مشاهده سفارش";"offer"->"شرایط طرح";else->"مشاهده پیام"},{open(n)},secondary=true)
            }}
        }
        if(state.unread>0)item {GhadirButton("خواندن همه",{scope.launch{state.markRead(state.items.filterNot{it.b("read")}.map{it.s("id")})}},secondary=true)}
        item {Text("قیمت و شرایط نهایی هنگام ثبت سفارش نمایش داده می‌شود.",color=Muted,fontSize=12.sp)}
    }
    selected?.let {n->AlertDialog(onDismissRequest={selected=null},title={Text(n.s("title"))},
        text={Text(n.s("body"),modifier=Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={selected=null}){Text("بستن")}})}
}

@Composable
fun PortalOffers(api:ApiClient,selectedId:Int?,onOpen:(Int)->Unit={},onUse:(String)->Unit) {
    var offers by remember {mutableStateOf(emptyList<JSONObject>())}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf("")}
    var retry by remember {mutableIntStateOf(0)}
    var detail by remember(selectedId){mutableStateOf(selectedId)}
    LaunchedEffect(retry) {
        loading=true;error=""
        try{offers=(api.get("/api/offers") as JSONArray).objects()}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message?:"دریافت آفرها ناموفق بود"}finally{loading=false}
    }
    if(loading){LoadingPane();return}
    val rows=offers.filter {detail==null||it.i("id")==detail}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""};GhadirButton("تلاش دوباره",{retry++})}
        if(rows.isEmpty()&&error.isBlank())item {EmptyState("آفر فعالی موجود نیست","ممکن است زمان طرح تمام شده باشد یا برای حساب شما فعال نباشد.")}
        if(detail==null&&rows.isNotEmpty())item {Text("پیشنهادهای ویژه همکاران",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)}
        items(rows,key={it.i("id")}) {o->
            val mix=o.s("title").contains("میکس")||o.s("description").contains("ترکیبی")
            if(mix) {
                DesignOfferHero(o.s("title"),o.s("description").ifBlank {"شرایط و مدل‌های مشمول این پیشنهاد را بررسی کنید."},showIcon=true)
                Spacer(Modifier.height(16.dp))
            }
            if(detail==null) {
                if(mix)GhadirButton("شرایط "+o.s("title"),{detail=o.i("id");onOpen(o.i("id"))})
                else GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){DesignGlyph(if(o.s("title").contains("اعتبار"))R.drawable.design_wallet else R.drawable.design_gift,null,Modifier.size(28.dp))}
                    Text(o.s("title"),color=Ink,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
                    Text(o.s("description"),color=Ink,fontSize=13.sp,lineHeight=22.sp)
                    GhadirButton(if(o.b("used_by_customer"))"طرح استفاده‌شده؛ مشاهده جزئیات" else "جزئیات طرح",{detail=o.i("id");onOpen(o.i("id"))},secondary=true)
                }}
            } else {
                if(!mix)Text(o.s("title"),color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
                GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(if(mix)"بسته ترکیبی برای خرید همکاران" else "شرایط طرح",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    if(!mix)Text(o.s("description"),color=Ink,fontSize=14.sp)
                    val products=if(o.s("product").isNotBlank())listOf(o.s("product")) else o.arr("products").strings()
                    if(products.isNotEmpty()) {
                        Text("مدل‌های مشمول",color=Muted,fontSize=12.sp)
                        products.forEach {Text(shortProductName(it),color=Ink,fontWeight=FontWeight.Bold)}
                    }
                    if(o.i("min_qty")>0)DetailLine("حداقل خرید",faNumber(o.i("min_qty"))+" دستگاه")
                    if(o.s("end_date").isNotBlank())DetailLine("مهلت",formatDateFa(o.s("end_date")))
                    if(o.l("discount_value")>0)Text(offerDiscountTextNative(o),color=Orange,fontWeight=FontWeight.Bold)
                    if(o.s("promo_code").isNotBlank())DetailLine("کد طرح",o.s("promo_code"))
                }}
                Spacer(Modifier.height(16.dp))
                DesignNotice("قیمت و شرایط نهایی هنگام ثبت سفارش نمایش داده می‌شود.")
                Spacer(Modifier.height(16.dp))
                GhadirButton(if(o.b("used_by_customer"))"قبلاً استفاده شده" else if(o.s("promo_code").isBlank())"ثبت سفارش جدید" else "ثبت سفارش این طرح",
                    {onUse(o.s("promo_code"))},enabled=!o.b("used_by_customer"))
            }
        }
        if(detail==null)item {DesignNotice("اعتبار و مبلغ نهایی در زمان ثبت سفارش بررسی می‌شود.")}
    }
}

package ir.ghadirpartner.nativeapp

import android.widget.Toast
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject

private val portalNav = listOf(
    NavItem("home", "پیشخوان", Icons.Default.Home),
    NavItem("catalog", "قیمت‌ها", Icons.Default.LocalOffer),
    NavItem("new", "ثبت سفارش", Icons.Default.AddCircle),
    NavItem("orders", "سفارش‌ها", Icons.Default.ReceiptLong),
    NavItem("profile", "پروفایل", Icons.Default.AccountCircle)
)

@Composable
fun PortalApp(api: ApiClient, me: JSONObject, onLogout: () -> Unit) {
    val context = LocalContext.current
    val inbox = remember(api, me.s("username")) { PortalInboxState(api) }
    val appScope = rememberCoroutineScope()
    var selectedOffer by remember { mutableStateOf<Int?>(null) }
    var offerCode by remember { mutableStateOf("") }
    LaunchedEffect(inbox) {
        (context as FragmentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { inbox.refresh(); delay(60000) }
        }
    }
    val photoKey = "photo_uri:" + me.s("username")
    val profilePrefs = remember { context.getSharedPreferences("ghadir_profile", android.content.Context.MODE_PRIVATE) }
    var profilePhotoUri by remember { mutableStateOf(profilePrefs.getString(photoKey, "") ?: "") }
    var screen by remember { mutableStateOf("home") }
    var selectedOrder by remember { mutableStateOf<JSONObject?>(null) }
    var proformaOrigin by remember {mutableStateOf("documents")}
    var currentMe by remember { mutableStateOf(JSONObject(me.toString())) }
    val customerName = currentMe.obj("customer").s("name").ifBlank { currentMe.s("username") }

    BackHandler(enabled = screen != "home") {
        when (screen) {
            "order-detail" -> { selectedOrder = null; screen = "orders" }
            "serial-detail" -> screen="order-detail"
            "proforma" -> screen=proformaOrigin
            "documents","addresses","summary" -> screen="profile"
            "offers" -> if(selectedOffer!=null){selectedOffer=null} else screen="home"
            else -> { selectedOrder = null; screen = "home" }
        }
    }

    var checkoutTitle by remember {mutableStateOf("ثبت سفارش")}
    var checkoutBackRequest by remember {mutableIntStateOf(0)}
    var profileTitle by remember {mutableStateOf("پروفایل")}
    var profileBackRequest by remember {mutableIntStateOf(0)}
    var cancelView by remember {mutableStateOf(false)}
    var orderBackRequest by remember {mutableIntStateOf(0)}
    val titles = mapOf("documents" to "پیش‌فاکتورهای من", "proforma" to "پیش‌فاکتور", "success" to "سفارش ثبت شد", "serials" to "سریال‌های من", "addresses" to "آدرس‌های من", "home" to "پیشخوان", "catalog" to "قیمت و موجودی", "new" to "ثبت سفارش", "orders" to "سفارش‌های من", "profile" to "پروفایل", "order-detail" to "جزئیات سفارش", "inbox" to "اعلان‌ها", "offers" to "طرح‌ها و آفرها")
    Box(Modifier.fillMaxSize().portalBackdrop()) { Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val backScreen=screen in setOf("order-detail","documents","proforma","success","addresses","serial-detail") || (screen=="offers"&&selectedOffer!=null) || (screen=="new"&&checkoutTitle!="ثبت سفارش") || (screen=="profile"&&profileTitle in setOf("ویرایش اطلاعات","تصویر پروفایل"))
            val title=if(screen=="serial-detail")"سریال دستگاه‌ها" else if(screen=="order-detail"&&cancelView)"لغو سفارش" else if(screen=="profile")profileTitle else if(screen=="new")checkoutTitle else if(screen=="offers"&&selectedOffer!=null)"جزئیات آفر" else titles[screen]?:"قدیر پارتنر"
            if(backScreen) PortalBackHeader(title) {
                screen=when(screen){"profile"->{profileBackRequest++;"profile"};"new"->{checkoutBackRequest++;"new"};"order-detail"->if(cancelView){orderBackRequest++;"order-detail"}else "orders";"success"->"orders";"serial-detail"->"order-detail";"proforma"->proformaOrigin;"offers"->{selectedOffer=null;"offers"};else->"profile"}
            } else PortalGlassHeader(title,screen=="home",inbox.unread,onOffers={selectedOffer=null;screen="offers"}) {
                screen="inbox";appScope.launch {inbox.refresh()}
            }
        },
        bottomBar = {
            val navScreen=when(screen){"serials","serial-detail","order-detail","success"->"orders";"documents","proforma","addresses","summary"->"profile";"inbox","offers"->"home";else->screen}
            DesignNavigationBar(portalNav,navScreen) {selectedOrder=null;selectedOffer=null;screen=it}
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.fillMaxSize()) {
            Crossfade(targetState = screen, label = "portal-nav") { target ->
                when (target) {
                    "home" -> PortalHome(
                        api,
                        navigate = { selectedOffer=null; screen = it },
                        customerName = customerName,
                        openOrder = { selectedOrder = it; screen = "order-detail" },
                        openOffer = {selectedOffer=it;screen="offers"}
                    )
                    "orders" -> PortalOrders(api,onNavigate={screen=it}) { selectedOrder = it; screen = "order-detail" }
                    "new" -> PortalNewOrder(api, offerCode, onStageChanged={checkoutTitle=it},backRequest=checkoutBackRequest) { createdOrder ->
                        selectedOrder = createdOrder
                        screen = "success"
                    }
                    "serials" -> PortalOrders(api, deliveredOnly=true,onNavigate={screen=it}) {selectedOrder=it;screen="order-detail"}
                    "documents" -> PortalDocuments(api,currentMe,onOpen={selectedOrder=it;proformaOrigin="documents";screen="proforma"})
                    "proforma" -> PortalDocuments(api,currentMe,selectedOrder,onOpen={})
                    "success" -> selectedOrder?.let {order->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            DesignState("سفارش با موفقیت ثبت شد","از بخش سفارش‌ها مراحل آماده‌سازی و ارسال را پیگیری کنید.",R.drawable.design_success,success=true)
                            GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                DetailLine("کد پیگیری",faDigits(order.s("number")))
                                DetailLine("اقلام",orderItemsDescription(order))
                            }}
                            GhadirButton("مشاهده سفارش",{screen="order-detail"})
                            GhadirButton("دریافت پیش‌فاکتور",{proformaOrigin="success";screen="proforma"},secondary=true)
                        }
                    }
                    "addresses" -> PortalAddresses(api)
                    "summary" -> PortalHome(api,customerName,{screen=it},{selectedOrder=it;screen="order-detail"},showReport=true)
                    "catalog" -> PortalCatalog(api,onHome={screen="home"})
                    "offers" -> PortalOffers(api, selectedOffer,onOpen={selectedOffer=it}) { code -> offerCode=code; screen="new" }
                    "inbox" -> PortalInbox(inbox, onOrder={ id ->
                        appScope.launch {
                            try {
                                val orders=(api.get("/api/orders") as JSONArray).objects()
                                selectedOrder=orders.firstOrNull {it.i("id")==id}
                                if(selectedOrder!=null)screen="order-detail" else Toast.makeText(context,"سفارش در دسترس نیست",Toast.LENGTH_LONG).show()
                            } catch(e: Exception){Toast.makeText(context,"دریافت سفارش ناموفق بود؛ دوباره تلاش کنید",Toast.LENGTH_LONG).show()}
                        }
                    }, onOffer={ id -> selectedOffer=id;screen="offers" })
                    "order-detail" -> selectedOrder?.let { order ->
                        PortalOrderDetails(api, order, onBack = { screen = "orders" }, onProforma={proformaOrigin="order-detail";screen="proforma"},me=currentMe,
                            onSerials={screen="serial-detail"},onSectionChanged={cancelView=it},backRequest=orderBackRequest)
                    } ?: PortalOrders(api,onNavigate={screen=it}) { selectedOrder = it; screen = "order-detail" }
                    "serial-detail" -> selectedOrder?.let {order->PortalSerials(api,order,currentMe){screen="order-detail"}}
                    else -> PortalProfile(
                        api = api,
                        me = currentMe,
                        onUpdated = { result ->
                            val next = JSONObject(currentMe.toString())
                            next.put("customer", result.obj("customer"))
                            next.put("alternate_mobile", result.s("alternate_mobile"))
                            currentMe = next
                        },
                        onPhotoUpdated = { profilePhotoUri = it },
                        onLogout = onLogout,
                        onProforma = { screen="documents" }, onNavigate = {screen=it},
                        onSectionChanged={profileTitle=it},backRequest=profileBackRequest
                    )
                }
            }
            }
        }
    } }
}


private fun promoNorm(v: String): String = v.replace(Regex("\\s+"), "").trim().uppercase()

internal fun offerDiscountTextNative(o: JSONObject): String {
    val type = o.s("discount_type")
    val value = o.l("discount_value")
    return when {
        type == "percent" && value > 0 -> "${faNumber(value)}٪ تخفیف"
        type == "amount" && value > 0 -> "${formatMoney(value)} تومان تخفیف"
        else -> "پیشنهاد ویژه"
    }
}

@Composable
private fun PortalHome(api: ApiClient, customerName: String, navigate: (String) -> Unit, openOrder: (JSONObject) -> Unit, openOffer:(Int)->Unit={}, showReport:Boolean=false) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf(emptyList<JSONObject>()) }
    var offers by remember { mutableStateOf(emptyList<JSONObject>()) }
    var stats by remember { mutableStateOf(JSONObject()) }
    var loadedAccount by remember {mutableStateOf(false)}

    fun refresh() {
        scope.launch {
            loading = true; error = ""
            try {
                val loaded = coroutineScope {
                    val ordersReq = async { (api.get("/api/orders") as JSONArray).objects().sortedByDescending { it.i("id") } }
                    val offersReq = async { (api.get("/api/offers") as JSONArray).objects().filterNot { it.b("used_by_customer") } }
                    val statsReq = async {
                        try { api.get("/api/reports/customer-summary") as? JSONObject ?: JSONObject() }
                        catch(e: kotlinx.coroutines.CancellationException) { throw e }
                        catch(_: Exception) { JSONObject() }
                    }
                    Triple(ordersReq.await(), offersReq.await(), statsReq.await())
                }
                orders = loaded.first
                offers = loaded.second
                stats = loaded.third
                loadedAccount=true
                if(stats.length()==0) stats = customerSummaryFromOrders(orders)
                try {
                    stats.put("purchased_device_qty",(api.get("/api/purchased-devices") as JSONObject).i("qty"))
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(_: Exception) {
                    val devices=(api.get("/api/catalog") as JSONArray).objects().filter {it.b("serial_required")}.map {it.s("name")}.toSet()
                    stats.put("purchased_device_qty",orders.filter {it.s("status")=="تحویل شد"}.sumOf {o->o.arr("items").objects().filter {it.s("product") in devices}.sumOf {it.i("qty")}})
                }
            } catch (e: kotlinx.coroutines.CancellationException) {throw e}
            catch (e: Exception) { error = e.message ?: "خطا در دریافت اطلاعات" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    if (loading) { LoadingPane(); return }
    if(error.isNotBlank()&&!loadedAccount) {
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DesignState("اطلاعات حساب دریافت نشد","ارتباط با سرویس برقرار نشد. اتصال را بررسی کنید و دوباره تلاش کنید.",R.drawable.design_offline)
            GhadirButton("تلاش دوباره",{refresh()})
        }
        return
    }

    if(showReport) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(error.isNotBlank())item {ErrorBanner(error){error=""};GhadirButton("تلاش دوباره",{refresh()})}
            item {StatTile("کل دستگاه‌های خریداری‌شده",faNumber(stats.i("purchased_device_qty")),"سفارش‌های تحویل‌شده",Modifier.fillMaxWidth())}
            item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                StatTile("۷ روز",faNumber(stats.obj("week").i("orders")),"سفارش",Modifier.weight(1f))
                StatTile("۳۰ روز",faNumber(stats.obj("month").i("orders")),"سفارش",Modifier.weight(1f))
                StatTile("۹۰ روز",faNumber(stats.obj("three_months").i("orders")),"سفارش",Modifier.weight(1f))
            }}
        }
        return
    }
    val featured=offers.firstOrNull {it.s("title").contains("میکس")} ?: offers.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""};GhadirButton("تلاش دوباره",{refresh()})}
        item {Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("سلام، $customerName",color=Ink,fontSize=22.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
            Text("خلاصه حساب شما",color=Muted,fontSize=14.sp,lineHeight=23.sp)
        }}
        item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DetailLine("سفارش در جریان",faNumber(orders.count {it.s("status") !in setOf("تحویل شد","لغو شد")})+" سفارش")
            HorizontalDivider(color=Border)
            DetailLine("خرید این ماه",formatMoney(stats.obj("month").l("amount"))+" تومان")
        }}}
        item {GhadirButton("ثبت سفارش جدید",{navigate("new")})}
        item {DesignOfferHero(featured?.s("title")?:"طرح‌ها و آفرهای قدیر پارتنر",
            featured?.s("description")?.ifBlank {"شرایط و مدل‌های مشمول را ببینید."}
                ?:"پیشنهادهای مجاز حساب شما در بخش آفرها نمایش داده می‌شوند.",
            "مشاهده آفر") {if(featured!=null)openOffer(featured.i("id")) else navigate("offers")}}
        if(orders.isEmpty())item {
            DesignState("هنوز سفارشی ندارید","پس از ثبت اولین سفارش، وضعیت و جزئیات آن را اینجا می‌بینید.",R.drawable.design_empty_orders)
        } else item {DesignOrderCard(orders.first(),latest=true){openOrder(orders.first())}}
    }
}
@Composable
private fun PortalSummaryHero(amount: Long, orders: Int, qty: Int) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("خلاصه حساب شما",color=Muted,fontSize=14.sp)
        GlassSurface { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DetailLine("سفارش‌های این ماه", "${faNumber(orders)} سفارش")
            DetailLine("خرید این ماه", "${formatMoney(amount)} تومان")
        } }
    }
}

@Composable
private fun MiniAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(elevation = CardDefaults.cardElevation(defaultElevation = 5.dp), modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFE7D5)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Orange, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(title, color = NavyDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    GlassCard(elevation = CardDefaults.cardElevation(defaultElevation = 5.dp), modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = NavyDeep, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text(caption, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PortalOrders(api:ApiClient,deliveredOnly:Boolean=false,onNavigate:(String)->Unit={},onOpen:(JSONObject)->Unit) {
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf("")}
    var group by remember {mutableStateOf(if(deliveredOnly)"delivered" else "all")}
    var query by remember {mutableStateOf("")}
    var orders by remember {mutableStateOf(emptyList<JSONObject>())}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(retry) {
        loading=true;error=""
        try {orders=(api.get("/api/orders") as JSONArray).objects().sortedByDescending{it.i("id")}}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"دریافت سفارش‌ها ناموفق بود"}finally{loading=false}
    }
    val term=asciiDigits(query).trim()
    val filtered=orders.filter {o->
        val delivered=o.s("status")=="تحویل شد"
        (!deliveredOnly||delivered)&&when(group){"delivered"->delivered;"active"->!delivered&&!o.s("status").contains("لغو");else->true}&&
            (term.isBlank()||listOf(o.s("number"),o.s("status"),o.s("payment_status"),orderItemsDescription(o)).any {asciiDigits(it).contains(term,true)})
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {DesignField("جستجوی سفارش",query,{query=it},"شماره سفارش",R.drawable.design_search)}
        if(!deliveredOnly)item {DesignChoices(listOf("all" to "همه","active" to "در جریان","delivered" to "تحویل‌شده"),group,{group=it})}
        when {
            loading->item{LoadingPane()}
            error.isNotBlank()->item {
                DesignState("دریافت سفارش‌ها ناموفق بود",error,R.drawable.design_offline)
                GhadirButton("تلاش دوباره",{retry++})
            }
            filtered.isEmpty()->item {
                DesignState(if(orders.isEmpty())"هنوز سفارشی ندارید" else "سفارشی پیدا نشد",
                    if(orders.isEmpty())"پس از ثبت اولین سفارش، وضعیت و جزئیات آن را اینجا می‌بینید." else "فیلتر یا عبارت جستجو را تغییر دهید.",
                    R.drawable.design_empty_orders)
                GhadirButton(if(orders.isEmpty())"ثبت اولین سفارش" else "پاک کردن جستجو و فیلترها",
                    {if(orders.isEmpty())onNavigate("new") else {query="";group=if(deliveredOnly)"delivered" else "all"}})
                Spacer(Modifier.height(16.dp));GhadirButton("مشاهده قیمت‌ها",{onNavigate("catalog")},secondary=true)
            }
            else->items(filtered,key={it.i("id")}) {o->DesignOrderCard(o){onOpen(o)}}
        }
    }
}
@Composable
private fun PortalOrderDetails(api:ApiClient,order:JSONObject,onBack:()->Unit,onProforma:()->Unit,me:JSONObject=JSONObject(),onSerials:()->Unit={},onSectionChanged:(Boolean)->Unit={},backRequest:Int=0) {
    val scope=rememberCoroutineScope()
    var cancelView by remember {mutableStateOf(false)}
    var cancelling by remember {mutableStateOf(false)}
    var reason by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var cancelSent by remember {mutableStateOf(false)}
    var more by remember {mutableStateOf(false)}
    val total=order.l("approved_total").takeIf {it>0}?:order.l("estimated_total")
    val delivered=order.s("status")=="تحویل شد"
    val canCancel=order.s("status") !in listOf("ارسال شد","تحویل شد","لغو شد")
    LaunchedEffect(cancelView){onSectionChanged(cancelView)}
    LaunchedEffect(backRequest){if(backRequest>0)cancelView=false}
    BackHandler(cancelView){if(!cancelling)cancelView=false}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""}}
        if(cancelSent)item {DesignNotice("درخواست لغو ثبت شد و در انتظار بررسی است.")}
        if(cancelView) {
            item {DesignNotice("لغو سفارش\n\nآیا از لغو سفارش "+faDigits(order.s("number"))+" مطمئن هستید؟")}
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(faDigits(order.s("number")),color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Text(orderItemsDescription(order)+" • "+order.s("status"),color=Muted,fontSize=14.sp)
            }}}
            item {DesignField("علت لغو — الزامی",reason,{reason=it.take(250)},"دلیل درخواست لغو را بنویسید",singleLine=false)}
            item {Text("برای ثبت درخواست، علت لغو را وارد کنید.",color=Muted,fontSize=12.sp)}
            item {GhadirButton(if(cancelling)"در حال ثبت…" else "ثبت درخواست لغو",{
                scope.launch {
                    cancelling=true;error=""
                    try {
                        api.post("/api/customer-order/cancel-request",JSONObject().put("id",order.i("id")).put("reason",reason.trim()).put("note",""))
                        cancelSent=true;cancelView=false
                    }catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(e:Exception){error=e.message?:"ثبت درخواست لغو ناموفق بود"}finally{cancelling=false}
                }
            },enabled=reason.trim().isNotBlank()&&!cancelling)}
            item {GhadirButton("انصراف و نگهداری سفارش",{cancelView=false},secondary=true,enabled=!cancelling)}
        } else {
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(faDigits(order.s("number")),color=Ink,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
                Text(order.s("status"),color=Ink,fontWeight=FontWeight.Bold)
                DetailLine("تسویه",listOf(order.s("requested_payment_method_label"),order.s("payment_status")).filter{it.isNotBlank()}.joinToString(" • "))
                DetailLine("اقلام",orderItemsDescription(order))
                DetailLine("مبلغ کل",formatMoney(total)+" تومان")
            }}}
            item {OrderDeliveryTimeline(order.s("status"))}
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("اطلاعات تحویل",color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
                DetailLine("گیرنده",order.s("customer_name").ifBlank {me.obj("customer").s("name")})
                DetailLine("شرکت",order.s("customer_company").ifBlank {me.obj("customer").s("company")})
                val snapshot=order.obj("delivery_address")
                val shipping=order.s("shipping_address").ifBlank {listOf(snapshot.s("province"),snapshot.s("city"),snapshot.s("address")).filter{it.isNotBlank()}.joinToString("، ")}
                if(shipping.isNotBlank())Text("نشانی: "+shipping,color=Ink,fontSize=14.sp)
                if(order.s("shipping_type").isNotBlank())DetailLine("روش ارسال",order.s("shipping_type"))
                if(order.s("tracking_code").isNotBlank())DetailLine("کد مرسوله",faDigits(order.s("tracking_code")))
            }}}
            if(delivered)item {GhadirButton("دریافت PDF سریال‌ها",onSerials)}
            item {GhadirButton("مشاهده پیش‌فاکتور",onProforma,secondary=true)}
            if(canCancel&&!cancelSent)item {GhadirButton("درخواست لغو سفارش",{cancelView=true;error=""},secondary=true)}
            item {TextButton(onClick={more=!more}){Text(if(more)"بستن اطلاعات بیشتر" else "اقلام، توضیحات و آخرین تغییرات",color=Muted)}}
            if(more) {
                items(order.arr("items").objects()) {line->GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(shortProductName(line.s("product")),color=Ink,fontWeight=FontWeight.Bold)
                    DetailLine("تعداد",faNumber(line.i("qty")))
                    if(line.s("price_label").isNotBlank())DetailLine("نوع قیمت",line.s("price_label"))
                    if(line.l("unit_price")>0)DetailLine("قیمت واحد",formatMoney(line.l("unit_price"))+" تومان")
                    if(line.l("line_total")>0)DetailLine("جمع",formatMoney(line.l("line_total"))+" تومان")
                }}}
                if(order.s("notes").isNotBlank())item {DesignNotice(order.s("notes"))}
                items(order.arr("history").objects().takeLast(8).reversed()) {h->GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(h.s("action"),color=Ink,fontSize=12.sp)
                    Text(formatDateFa(h.s("at"))+" • "+h.s("by"),color=Muted,fontSize=12.sp)
                }}}
            }
        }
    }
}
@Composable
internal fun DetailLine(title: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color=Muted, fontSize=12.sp, lineHeight=20.sp, modifier=Modifier.width(110.dp), textAlign=TextAlign.Start)
        Text(value, color = Ink, fontWeight=FontWeight.Bold, fontSize=14.sp, lineHeight=23.sp,
            modifier=Modifier.weight(1f), textAlign=TextAlign.Start)
    }
}

private data class CartLine(val product: String, val qty: Int, val priceKey: String, val priceLabel: String, val unit: Long)

private fun discountForOfferNative(offer: JSONObject?, cart: List<CartLine>): Long {
    if (offer == null) return 0L
    val product = offer.s("product")
    val eligible = cart.filter { product.isBlank() || it.product == product }.sumOf { it.unit * it.qty }
    if (eligible <= 0L) return 0L
    val value = offer.l("discount_value").coerceAtLeast(0L)
    return when (offer.s("discount_type")) {
        "percent" -> minOf(eligible, eligible * minOf(100L, value) / 100L)
        "amount" -> minOf(eligible, value)
        else -> 0L
    }
}


@Composable
private fun PortalNewOrder(api:ApiClient,initialOfferCode:String="",onStageChanged:(String)->Unit={},backRequest:Int=0,onSuccess:(JSONObject)->Unit) {
    val scope=rememberCoroutineScope()
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    var loading by remember {mutableStateOf(true)}
    var step by remember {mutableIntStateOf(0)}
    var submitting by remember {mutableStateOf(false)}
    var evidenceBusy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var retry by remember {mutableIntStateOf(0)}
    var products by remember {mutableStateOf(emptyList<JSONObject>())}
    var offers by remember {mutableStateOf(emptyList<JSONObject>())}
    var addresses by remember {mutableStateOf(emptyList<JSONObject>())}
    var customer by remember {mutableStateOf(JSONObject())}
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var group by remember {mutableStateOf("serial")}
    var panelTier by remember {mutableStateOf("panel_cash")}
    var qtyText by remember {mutableStateOf("1")}
    var selectedAddressId by remember {mutableStateOf("")}
    var payment by remember {mutableStateOf("cash")}
    var settlement by remember {mutableStateOf(JSONObject())}
    var invoiceType by remember {mutableStateOf("غیررسمی")}
    var notes by remember {mutableStateOf("")}
    var advanced by remember {mutableStateOf(false)}
    var promoCode by remember(initialOfferCode) {mutableStateOf(initialOfferCode)}
    var appliedOffer by remember {mutableStateOf<JSONObject?>(null)}
    var promoMessage by remember {mutableStateOf("")}
    var cart by remember {mutableStateOf(emptyList<CartLine>())}
    LaunchedEffect(error) {if(error.isNotBlank())listState.scrollToItem(0)}
    LaunchedEffect(step,payment) {listState.scrollToItem(0);onStageChanged(when(step){1->when(payment){"check"->"تسویه با چک";"credit"->"تسویه اعتباری";else->"تسویه نقدی"};2->"تأیید نهایی سفارش";else->"ثبت سفارش"})}
    LaunchedEffect(backRequest) {if(backRequest>0&&step>0&&!submitting)step--}
    BackHandler(step>0) {if(!submitting)step--}
    LaunchedEffect(retry) {
        loading=true;error=""
        try {
            coroutineScope {
                val catalog=async{(api.get("/api/catalog") as JSONArray).objects().filter{catalogInStock(it)}}
                val plans=async{(api.get("/api/offers") as JSONArray).objects().filterNot{it.b("used_by_customer")}}
                val account=async{api.me().obj("customer")}
                val saved=async{try{(api.get("/api/addresses") as JSONArray).objects()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:ApiException){if(e.status==404)emptyList() else throw e}}
                products=catalog.await();offers=plans.await();customer=account.await();addresses=saved.await()
            }
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"دریافت اطلاعات سفارش ناموفق بود"}finally{loading=false}
    }
    val qty=qtyText.toIntOrNull()?:0
    val priceKey=when(group){"panel"->panelTier;"agent"->"sales_agent";else->if(qty<=50)"serial_1_50" else "serial_51_200"}
    val unit=selected?.obj("prices")?.l(priceKey)?:0L
    val subtotal=cart.sumOf {it.unit*it.qty}
    val discount=discountForOfferNative(appliedOffer,cart)
    val total=(subtotal-discount).coerceAtLeast(0L)
    val address=addresses.firstOrNull {it.s("id")==selectedAddressId}?:customer
    val addressText=listOf(address.s("province"),address.s("city"),address.s("address")).filter {it.isNotBlank()}.joinToString("، ")
    val receiver=listOf(customer.s("name"),customer.s("company")).filter {it.isNotBlank()}.joinToString(" • ")
    fun applyPromo() {
        val match=offers.firstOrNull {promoNorm(it.s("promo_code"))==promoNorm(promoCode)&&it.s("discount_type") in listOf("percent","amount")&&it.l("discount_value")>0}
        val amount=discountForOfferNative(match,cart)
        appliedOffer=if(amount>0)match else null
        promoMessage=if(amount>0)offerDiscountTextNative(match!!)+" اعمال شد." else "این کد برای کالاهای فعلی سفارش معتبر یا فعال نیست."
    }
    LaunchedEffect(initialOfferCode,offers,cart) {
        if(initialOfferCode.isNotBlank()&&promoNorm(promoCode)==promoNorm(initialOfferCode))applyPromo()
    }
    fun submit() {
        if(submitting)return
        val validation=settlementError(payment,settlement)
        if(validation.isNotBlank()){error=validation;step=1;return}
        if(promoCode.isNotBlank()&&(appliedOffer==null||discount<=0)){error="کد تخفیف را ابتدا اعمال و بررسی کنید";step=0;return}
        scope.launch {
            submitting=true;error=""
            try {
                val items=JSONArray()
                cart.forEach {items.put(JSONObject().put("product",it.product).put("qty",it.qty).put("price_key",it.priceKey))}
                val body=JSONObject().put("items",items).put("address_id",selectedAddressId)
                    .put("settlement_details",settlement).put("invoice_type",invoiceType)
                    .put("requested_payment_method",payment).put("notes",notes)
                    .put("promo_code",appliedOffer?.s("promo_code")?:"")
                val order=api.post("/api/orders/add",body) as JSONObject
                onSuccess(order)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"ثبت سفارش ناموفق بود"}
            finally{submitting=false}
        }
    }
    if(loading){LoadingPane();return}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),state=listState,contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""};if(products.isEmpty())GhadirButton("تلاش دوباره",{retry++})}
        if(step==0) {
            item {Text("۱. انتخاب کالا",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)}
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                DropdownField("مدل دستگاه",selected?.let {shortProductName(it.s("name"))+" • "+it.s("manufacturer")}?:"انتخاب دستگاه",products,{shortProductName(it.s("name"))+" • "+it.s("manufacturer")}) {selected=it}
                Text("نوع قیمت",color=Muted,fontSize=12.sp)
                DesignChoices(listOf("agent" to "عامل فروش","panel" to "ثبت در پنل","serial" to "سریال آزاد"),group,{group=it})
                if(group=="panel")DropdownField("نوع خرید پنل",catalogPriceRows("panel").first {it.first==panelTier}.second,catalogPriceRows("panel"),{it.second}){panelTier=it.first}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("تعداد",color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick={qtyText=(qty+1).coerceAtMost(999).toString()}) {Text("+",color=Ink)}
                    androidx.compose.foundation.text.BasicTextField(qtyText,{qtyText=asciiDigits(it).filter(Char::isDigit).take(3)},
                        modifier=Modifier.width(48.dp).heightIn(min=44.dp),singleLine=true,
                        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),
                        textStyle=MaterialTheme.typography.bodyMedium.copy(color=Ink,textAlign=TextAlign.Center),
                        decorationBox={inner->Box(Modifier.fillMaxWidth().heightIn(min=44.dp),contentAlignment=Alignment.Center){inner()}})
                    TextButton(onClick={qtyText=(qty-1).coerceAtLeast(1).toString()}){Text("−",color=Ink)}
                }
                DetailLine("قیمت واحد",if(unit>0)formatMoney(unit)+" تومان" else "—")
                GhadirButton("افزودن کالا",{
                    val product=selected?:return@GhadirButton
                    if(qty<1||qty>product.i("stock")){error="تعداد انتخاب‌شده در حال حاضر قابل سفارش نیست";return@GhadirButton}
                    if(cart.any {it.product==product.s("name")}){error="این مدل قبلاً به سفارش اضافه شده است";return@GhadirButton}
                    val label=priceTypes.firstOrNull {it.first==priceKey}?.second?:priceKey
                    cart=cart+CartLine(product.s("name"),qty,priceKey,label,unit);error=""
                },enabled=selected!=null&&unit>0&&qty>0,secondary=true)
            }}}
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("سبد سفارش",color=Ink,fontWeight=FontWeight.Bold)
                if(cart.isEmpty())Text("هنوز کالایی به سبد اضافه نشده است.",color=Muted,fontSize=12.sp)
                cart.forEach {line->
                    DetailLine("کالا",faNumber(line.qty)+" × "+shortProductName(line.product))
                    DetailLine("نوع قیمت",line.priceLabel)
                    DetailLine("قیمت واحد",formatMoney(line.unit)+" تومان")
                    TextButton(onClick={cart=cart.filterNot {it.product==line.product}}){Text("حذف "+shortProductName(line.product),color=Danger)}
                }
                DetailLine("تسویه",when(payment){"cash"->"نقد";"check"->"چک";else->"اعتباری"})
                HorizontalDivider(color=Border)
                if(discount>0)DetailLine("تخفیف",formatMoney(discount)+" تومان")
                DetailLine("جمع سفارش",formatMoney(total)+" تومان")
            }}}
            item {Text("۲. تحویل و تسویه",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)}
            item {
                val choices=listOf(JSONObject().put("id","").put("title","آدرس اصلی پروفایل").put("address",customer.s("address")))+addresses
                DropdownField("نشانی تحویل",addressText.ifBlank {"نشانی در پروفایل ثبت نشده است"},choices,{it.s("title")+" • "+it.s("address")}){selectedAddressId=it.s("id")}
            }
            item {DesignField("گیرنده",receiver,{},"",enabled=false)}
            item {Text("روش تسویه • انتخاب نقد / چک / اعتباری",color=Muted,fontSize=12.sp)}
            item {DesignChoices(listOf("cash" to "نقد","check" to "چک","credit" to "اعتباری"),payment,{payment=it;settlement=JSONObject()})}
            item {GhadirButton("ادامه و بررسی سفارش",{error="";step=1},enabled=cart.isNotEmpty()&&addressText.isNotBlank())}
            item {TextButton(onClick={advanced=!advanced}) {Text(if(advanced)"بستن جزئیات بیشتر" else "کد طرح، نوع فاکتور و توضیحات",color=Muted)}}
            if(advanced||initialOfferCode.isNotBlank()) {
                item {DesignField("کد طرح",promoCode,{promoCode=promoNorm(it).take(24);appliedOffer=null;promoMessage=""})}
                item {GhadirButton("اعمال کد",{applyPromo()},secondary=true);if(promoMessage.isNotBlank())Text(promoMessage,color=if(appliedOffer!=null)Success else Danger)}
                item {DropdownField("نوع فاکتور",invoiceType,listOf("غیررسمی","رسمی"),{it}){invoiceType=it}}
                item {DesignField("توضیحات سفارش — اختیاری",notes,{notes=it.take(500)},singleLine=false)}
            }
        } else if(step==1) {
            item {DesignChoices(listOf("cash" to "نقد","check" to "چک","credit" to "اعتباری"),payment,{payment=it;settlement=JSONObject()})}
            item {SettlementForm(api,payment,total,settlement,onBusyChanged={evidenceBusy=it}){settlement=it}}
            item {GhadirButton(if(payment=="credit")"پذیرش و ادامه" else "ادامه به بررسی نهایی",{
                error=settlementError(payment,settlement);if(error.isBlank())step=2
            },enabled=!evidenceBusy)}
            item {GhadirButton("بازگشت و ویرایش",{step=0},secondary=true)}
        } else {
            item {DesignNotice("سفارش را نهایی می‌کنید؟\n\nمدل، تعداد، نشانی و روش تسویه را بررسی کنید. پس از ثبت، تغییر سفارش نیازمند بررسی پشتیبانی است.")}
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("بررسی سفارش",color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
                cart.forEach {line->
                    DetailLine("کالا",faNumber(line.qty)+" × "+shortProductName(line.product))
                    DetailLine("نوع قیمت",line.priceLabel)
                    DetailLine("قیمت واحد",formatMoney(line.unit)+" تومان")
                }
                DetailLine("تسویه",when(payment){"cash"->"نقد";"check"->"چک";else->"اعتباری"})
                HorizontalDivider(color=Border)
                if(discount>0)DetailLine("تخفیف",formatMoney(discount)+" تومان")
                DetailLine("جمع سفارش",formatMoney(total)+" تومان")
                Text("گیرنده: "+receiver,color=Ink,fontSize=14.sp)
                Text(addressText,color=Ink,fontSize=14.sp)
            }}}
            item {GhadirButton(if(submitting)"در حال ثبت…" else "تأیید و ثبت نهایی",{submit()},enabled=!submitting&&cart.isNotEmpty())}
            item {GhadirButton("بازگشت و ویرایش",{step=0},secondary=true,enabled=!submitting)}
        }
    }
}
@Composable
private fun PortalCatalog(api: ApiClient,onHome:()->Unit={}) {
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf("")}
    var query by remember {mutableStateOf("")}
    var products by remember {mutableStateOf(emptyList<JSONObject>())}
    var showFilters by remember {mutableStateOf(false)}
    var modelFilter by remember {mutableStateOf("همه مدل‌ها")}
    var manufacturerFilter by remember {mutableStateOf("همه سازنده‌ها")}
    var stockFilter by remember {mutableStateOf("همه")}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(retry) {
        loading=true;error=""
        try {products=(api.get("/api/catalog") as JSONArray).objects()}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"قیمت‌های به‌روز دریافت نشد"}
        finally {loading=false}
    }
    val filtered=products.filter {p->
        val maker=p.s("manufacturer").ifBlank {"بدون سازنده"}
        val matches=query.isBlank()||listOf(p.s("name"),maker).any {it.contains(query,true)}
        matches&&(modelFilter=="همه مدل‌ها"||p.s("name")==modelFilter)&&
            (manufacturerFilter=="همه سازنده‌ها"||maker==manufacturerFilter)&&
            when(stockFilter){"موجود"->catalogInStock(p);"ناموجود"->!catalogInStock(p);else->true}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {DesignField("جستجوی مدل یا شرکت سازنده",query,{query=it},"مدل یا سازنده را وارد کنید",R.drawable.design_search)}
        item {DesignChoices(listOf("all" to "همه مدل‌ها","available" to "موجود","filters" to "فیلترها"),
            if(showFilters)"filters" else if(stockFilter=="موجود")"available" else "all") {choice->
            when(choice){"filters"->showFilters=!showFilters;"available"->{stockFilter="موجود";showFilters=false};else->{stockFilter="همه";showFilters=false}}
        }}
        if(showFilters)item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DropdownField("مدل دستگاه",modelFilter,listOf("همه مدل‌ها")+products.map {it.s("name")}.distinct(),{shortProductName(it)}){modelFilter=it}
            DropdownField("شرکت سازنده",manufacturerFilter,listOf("همه سازنده‌ها")+products.map {it.s("manufacturer").ifBlank {"بدون سازنده"}}.distinct(),{it}){manufacturerFilter=it}
            DropdownField("وضعیت موجودی",stockFilter,listOf("همه","موجود","ناموجود"),{it}){stockFilter=it}
        }}
        when {
            loading-> {
                item {Text("در حال دریافت قیمت‌ها…",color=Muted,fontSize=12.sp)}
                items(3){GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Box(Modifier.fillMaxWidth(.6f).height(24.dp).clip(RoundedCornerShape(8.dp)).background(AppSecondary))
                    repeat(3){Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp)).background(AppSecondary))}
                }}}
            }
            error.isNotBlank()->item {
                DesignState("اتصال برقرار نشد","قیمت‌های به‌روز دریافت نشد. اتصال اینترنت را بررسی و دوباره تلاش کنید.",R.drawable.design_offline)
                Text(error,color=Danger,fontSize=12.sp)
                GhadirButton("تلاش دوباره",{retry++})
                Spacer(Modifier.height(16.dp));GhadirButton("بازگشت به پیشخوان",onHome,secondary=true)
            }
            filtered.isEmpty()->item {
                DesignState("نتیجه‌ای پیدا نشد","نام مدل را بررسی کنید یا فیلترها را بردارید.",R.drawable.design_search_empty)
                GhadirButton("پاک کردن جستجو و فیلترها",{query="";stockFilter="همه";manufacturerFilter="همه سازنده‌ها";modelFilter="همه مدل‌ها";showFilters=false},secondary=true)
            }
            else->{
                val date=products.map {it.s("price_updated_at")}.filter {it.isNotBlank()}.maxOrNull()
                if(date!=null)item {Text("آخرین به‌روزرسانی: "+formatDateFa(date),color=Muted,fontSize=12.sp)}
                items(filtered,key={it.s("name")}) {p->CatalogListRow(p)}
            }
        }
    }
}

internal fun catalogInStock(p:JSONObject):Boolean=p.b("available")&&p.i("stock")>0

internal fun catalogPriceRows(group:String):List<Pair<String,String>> = when(group) {
    "panel"->listOf("panel_cash" to "نقد","panel_7d" to "هفت‌روزه","panel_1m" to "یک‌ماهه")
    "agent"->listOf("sales_agent" to "عامل فروش")
    else->listOf("serial_1_50" to "۱ تا ۵۰","serial_51_200" to "۵۱ تا ۲۰۰")
}

@Composable
private fun CatalogListRow(p:JSONObject) {
    var group by remember(p.s("name")){mutableStateOf("serial")}
    val inStock=catalogInStock(p)
    GlassSurface(shape=RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(shortProductName(p.s("name")),color=Ink,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
                    Text(p.s("manufacturer").ifBlank {"بدون سازنده"},color=Muted,fontSize=12.sp,lineHeight=20.sp)
                }
                Surface(color=if(AppAppearance.dark)AppSecondary else if(inStock)Color(0xFFE9F6EF) else Color(0xFFFDEEF0),shape=RoundedCornerShape(12.dp)) {
                    Text(if(inStock)"موجود" else "ناموجود",color=if(inStock)Success else Danger,fontSize=12.sp,
                        fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp))
                }
            }
            DesignChoices(listOf("agent" to "عامل فروش","panel" to "ثبت در پنل","serial" to "سریال آزاد"),group,{group=it})
            Text("نوع خرید / تعداد • قیمت (تومان)",color=Muted,fontSize=10.sp,lineHeight=16.sp)
            catalogPriceRows(group).forEach {(key,label)->
                Surface(color=AppSecondary,shape=RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(label,color=Muted,fontSize=12.sp,modifier=Modifier.width(90.dp))
                        val price=p.obj("prices").l(key)
                        Text(if(inStock&&price>0)formatMoney(price)+" تومان" else "—",color=Ink,fontSize=14.sp,lineHeight=23.sp,
                            fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
@Composable
private fun PortalProfile(api:ApiClient,me:JSONObject,onUpdated:(JSONObject)->Unit,onPhotoUpdated:(String)->Unit,onLogout:()->Unit,onProforma:()->Unit,onNavigate:(String)->Unit,onSectionChanged:(String)->Unit={},backRequest:Int=0) {
    val initial=me.obj("customer")
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var view by remember {mutableStateOf("profile")}
    var extra by remember {mutableStateOf(false)}
    var name by remember(me.toString()){mutableStateOf(initial.s("name"))}
    var company by remember(me.toString()){mutableStateOf(initial.s("company"))}
    var province by remember(me.toString()){mutableStateOf(initial.s("province"))}
    var city by remember(me.toString()){mutableStateOf(initial.s("city"))}
    var address by remember(me.toString()){mutableStateOf(initial.s("address"))}
    var alternate by remember(me.toString()){mutableStateOf(me.s("alternate_mobile"))}
    var postalCode by remember(me.toString()){mutableStateOf(initial.s("postal_code"))}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var saved by remember {mutableStateOf("")}
    val photoKey="photo_uri:"+me.s("username")
    val prefs=remember(context){context.getSharedPreferences("ghadir_profile",android.content.Context.MODE_PRIVATE)}
    var photoUri by remember(me.s("username")){mutableStateOf(prefs.getString(photoKey,"")?:"")}
    fun savePhoto(uri:String){photoUri=uri;prefs.edit().putString(photoKey,uri).apply();onPhotoUpdated(uri);view="profile"}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri:Uri?->
        if(uri!=null) {
            try{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
            savePhoto(uri.toString())
        }
    }
    val cameraFile=remember(me.i("id")){java.io.File(context.filesDir,"profile/camera-"+me.i("id")+".jpg").apply{parentFile?.mkdirs()}}
    val cameraUri=remember(cameraFile){androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".files",cameraFile)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)savePhoto(cameraUri.buildUpon().appendQueryParameter("v",System.currentTimeMillis().toString()).build().toString())}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok->if(ok)camera.launch(cameraUri) else error="برای گرفتن عکس، اجازهٔ دوربین لازم است."}
    LaunchedEffect(view){onSectionChanged(when(view){"edit"->"ویرایش اطلاعات";"photo"->"تصویر پروفایل";"deleted"->"حساب کاربری";else->"پروفایل"})}
    LaunchedEffect(backRequest){if(backRequest>0)view="profile"}
    BackHandler(view!="profile"){if(!saving)view="profile"}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""}}
        if(saved.isNotBlank()&&view=="profile")item {DesignNotice(saved)}
        when(view) {
            "edit"->{
                item {DesignField("نام و نام خانوادگی",name,{name=it;saved=""})}
                item {DesignField("نام شرکت / فروشگاه",company,{company=it;saved=""})}
                item {DesignField("شماره همراه",faDigits(me.s("username")),{},enabled=false)}
                item {DesignField("نشانی تحویل",address,{address=it;saved=""},singleLine=false)}
                item {DesignNotice("نام، شرکت و نشانی را پیش از ثبت تغییرات بررسی کنید.")}
                item {GhadirButton(if(saving)"در حال ذخیره…" else "ثبت تغییرات",{
                    scope.launch {
                        saving=true;error="";saved=""
                        try {
                            val body=JSONObject().put("name",name.trim()).put("company",company.trim()).put("province",province.trim())
                                .put("city",city.trim()).put("address",address.trim()).put("postal_code",postalCode).put("alternate_mobile",alternate)
                            val result=api.post("/api/profile/update",body) as JSONObject
                            onUpdated(result);view="profile";saved="اطلاعات پروفایل با موفقیت به‌روزرسانی شد."
                        }catch(e:kotlinx.coroutines.CancellationException){throw e}
                        catch(e:Exception){error=e.message?:"ذخیره پروفایل ناموفق بود"}finally{saving=false}
                    }
                },enabled=!saving&&name.trim().length>=3)}
                item {TextButton(onClick={extra=!extra}){Text("استان، شهر و شماره جایگزین",color=Muted)}}
                if(extra) {
                    item {DesignField("استان",province,{province=it})}
                    item {DesignField("شهر",city,{city=it})}
                    item {DesignField("شماره جایگزین — اختیاری",alternate,{alternate=asciiDigits(it).filter(Char::isDigit).take(11)})}
                    item {DesignField("کدپستی — اختیاری",postalCode,{postalCode=asciiDigits(it).filter(Char::isDigit).take(10)})}
                }
            }
            "photo"->{
                item {Column(Modifier.fillMaxWidth().padding(top=28.dp,bottom=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                    ProfilePhoto(photoUri,112.dp);Text(name,color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
                }}
                item {DesignNotice("انتخاب تصویر\n\nاز دوربین یا گالری عکس انتخاب کنید. در صورت حذف، لوگوی اصلی شرکت نمایش داده می‌شود.")}
                item {GhadirButton("انتخاب از گالری",{gallery.launch(arrayOf("image/*"))})}
                item {GhadirButton("گرفتن عکس",{permission.launch(android.Manifest.permission.CAMERA)},secondary=true)}
                item {GhadirButton("حذف عکس و بازگشت به لوگو",{
                    photoUri="";prefs.edit().remove(photoKey).apply();onPhotoUpdated("");view="deleted"
                },secondary=true,enabled=photoUri.isNotBlank())}
                item {GhadirButton("انصراف",{view="profile"},secondary=true)}
            }
            "deleted"->{
                item {GlassSurface {Column(Modifier.fillMaxWidth().padding(40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){
                    ProfilePhoto("",112.dp);Text(name,color=Ink,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(company,color=Muted,fontSize=13.sp)
                }}}
                item {Surface(color=if(AppAppearance.dark)Color(0xFF16382D) else Color(0xFFE9F6EF),shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Border)){
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Text("عکس شخصی حذف شد",color=Ink,fontWeight=FontWeight.Bold)
                        Text("تصویر پیش‌فرض حساب، لوگوی شرکت خواهد بود.",color=Ink,fontSize=12.sp)
                    }
                }}
                item {GhadirButton("انتخاب عکس جدید",{view="photo"},secondary=true)}
                item {GhadirButton("بازگشت به پروفایل",{view="profile"},secondary=true)}
            }
            else->{
                item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                        ProfilePhoto(photoUri)
                        Column(Modifier.weight(1f)){
                            Text(name.ifBlank {me.s("username")},color=Ink,fontSize=24.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold)
                            Text(company+" • کد همکار "+faDigits(initial.s("code")),color=Muted,fontSize=12.sp)
                        }
                    }
                    HorizontalDivider(color=Border)
                    DetailLine("شماره همراه",faDigits(me.s("username")))
                    GhadirButton("انتخاب یا حذف عکس",{view="photo"},secondary=true)
                }}}
                item {GhadirButton("ویرایش اطلاعات",{view="edit"},secondary=true)}
                item {BiometricPreference(me.s("username"))}
                item {GhadirButton("پیش‌فاکتورهای من",onProforma,secondary=true)}
                item {GhadirButton("سریال‌های من",{onNavigate("serials")},secondary=true)}
                item {GhadirButton("آفرهای من",{onNavigate("offers")},secondary=true)}
                item {GhadirButton("خروج از حساب",onLogout,secondary=true)}
                item {TextButton(onClick={extra=!extra}){Text(if(extra)"بستن خدمات بیشتر" else "آدرس‌ها، گزارش خرید و پشتیبانی",color=Muted)}}
                if(extra) {
                    item {GhadirButton("آدرس‌های من",{onNavigate("addresses")},secondary=true)}
                    item {GhadirButton("گزارش خرید",{onNavigate("summary")},secondary=true)}
                    item {GhadirButton("تماس با پشتیبانی · ۰۲۱۷۷۲۴۷۰۷۰",{
                        try{context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL,Uri.parse("tel:02177247070")))}
                        catch(_:Exception){error="برنامه تماس روی این دستگاه در دسترس نیست"}
                    },secondary=true)}
                    item {Text("نسخه Native "+BuildConfig.VERSION_NAME,color=Muted,fontSize=12.sp)}
                }
            }
        }
    }
}
@Composable
private fun ProfilePhoto(uri: String, size: androidx.compose.ui.unit.Dp = 80.dp) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        if (uri.isBlank()) null else try {
            val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {BitmapFactory.decodeStream(it,null,opts)}
            opts.inJustDecodeBounds=false;opts.inSampleSize=1
            while(opts.outWidth/opts.inSampleSize>512||opts.outHeight/opts.inSampleSize>512)opts.inSampleSize*=2
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it,null,opts) }
        } catch (_: Exception) { null }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = AppSurface, modifier = Modifier.size(size)) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "عکس پروفایل", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painter = painterResource(R.drawable.design_logo),
                contentDescription = "لوگوی قدیر پرداخت",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun ProfileEditField(title: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(title) }, shape = RoundedCornerShape(17.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border, focusedContainerColor = AppSurface, unfocusedContainerColor = AppSurface)
    )
}

@Composable
internal fun <T> DropdownField(title:String,value:String,options:List<T>,display:(T)->String,onSelect:(T)->Unit) {
    var open by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,color=Muted,fontSize=12.sp,lineHeight=20.sp)
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clip(RoundedCornerShape(16.dp)).background(AppSurface)
                .border(1.dp,Border,RoundedCornerShape(16.dp)).clickable {open=true}
                .semantics {contentDescription=title}.padding(14.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Image(painterResource(if(title=="نشانی تحویل"||title=="آدرس تحویل")R.drawable.design_map_pin else R.drawable.design_chevron),
                    null,Modifier.size(22.dp),colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(Muted))
                Text(value,color=Ink,fontSize=14.sp,lineHeight=23.sp,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded=open,onDismissRequest={open=false},modifier=Modifier.fillMaxWidth(.86f)) {
                options.forEach {option->DropdownMenuItem(text={Text(display(option))},onClick={onSelect(option);open=false})}
            }
        }
    }
}

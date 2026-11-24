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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
    key(me.s("username")) { PortalAccountApp(api,me,onLogout) }
}

@Composable
private fun PortalAccountApp(api: ApiClient, me: JSONObject, onLogout: () -> Unit) {
    val context = LocalContext.current
    val inbox = remember(api, me.s("username")) { PortalInboxState(api,context,me.s("username")) }
    val appScope = rememberCoroutineScope()
    val screenStates = rememberSaveableStateHolder()
    var orderOrigin by rememberSaveable { mutableStateOf("orders") }
    var offersOrigin by rememberSaveable { mutableStateOf("home") }
    var serialsOrigin by rememberSaveable { mutableStateOf("orders") }
    var catalogProduct by rememberSaveable { mutableStateOf("") }
    var catalogGroup by rememberSaveable { mutableStateOf("serial") }
    var selectedOffer by rememberSaveable { mutableStateOf<Int?>(null) }
    var offerCode by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(inbox) {
        (context as FragmentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { inbox.refresh(); delay(60000) }
        }
    }
    val photoKey = "photo_uri:" + me.s("username")
    val profilePrefs = remember { context.getSharedPreferences("ghadir_profile", android.content.Context.MODE_PRIVATE) }
    var profilePhotoUri by remember { mutableStateOf(profilePrefs.getString(photoKey, "") ?: "") }
    var screen by rememberSaveable { mutableStateOf("home") }
    var selectedOrder by rememberSaveable(stateSaver = NullableJsonSaver) { mutableStateOf<JSONObject?>(null) }
    var proformaOrigin by rememberSaveable {mutableStateOf("documents")}
    var currentMe by rememberSaveable(stateSaver=JsonSaver) { mutableStateOf(JSONObject(me.toString())) }
    val customerName = currentMe.obj("customer").s("name").ifBlank { currentMe.s("username") }

    var checkoutBusy by remember {mutableStateOf(false)}
    var profileBusy by remember {mutableStateOf(false)}
    var checkoutDraftSnapshot by remember {mutableStateOf<JSONObject?>(null)}
    var checkoutSessionKey by rememberSaveable {mutableIntStateOf(0)}
    var restoreCheckoutDraft by remember {mutableStateOf<JSONObject?>(null)}
    val abandonedStore = remember(me.s("username")) {
        AbandonedOrderService(
            AbandonedOrderRepository(context, me.s("username")),
            DailyDraftPolicy(CheckoutClock { System.currentTimeMillis() }, java.util.TimeZone.getTimeZone(BuildConfig.CHECKOUT_TIME_ZONE)),
        )
    }
    var abandonedRevision by remember {mutableIntStateOf(0)}
    LaunchedEffect(abandonedStore) {
        (context as FragmentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                abandonedRevision++
                delay(abandonedStore.millisUntilMidnight())
            }
        }
    }
    val abandonedOrder=remember(abandonedRevision) {abandonedStore.load()}
    val activity = remember(api) { PortalActivityReporter(api) }
    var activityCart by remember { mutableStateOf(abandonedOrder?.let(::cartFromDraft)) }
    LaunchedEffect(activity) {
        (context as FragmentActivity).lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) delay(activity.report())
        }
    }
    LaunchedEffect(activity, activityCart) {
        val lines = activityCart ?: return@LaunchedEffect
        delay(500)
        activity.report(lines)
    }
    fun parkActiveCheckout() {
        val snapshot=checkoutDraftSnapshot ?: abandonedStore.load()
        if(snapshot!=null&&snapshot.optJSONArray("cart")?.length()?:0>0) {
            abandonedStore.save(snapshot)
            abandonedRevision++
        }
        checkoutDraftSnapshot=null
        checkoutSessionKey++
    }
    fun clearAbandonedDraft() {
        abandonedStore.clear()
        abandonedRevision++
        restoreCheckoutDraft=null
    }
    var checkoutTitle by remember {mutableStateOf("ثبت سفارش")}
    var checkoutBackRequest by rememberSaveable {mutableIntStateOf(0)}
    var profileTitle by remember {mutableStateOf("پروفایل")}
    var profileBackRequest by rememberSaveable {mutableIntStateOf(0)}
    var cancelView by remember {mutableStateOf(false)}
    var orderBackRequest by remember {mutableIntStateOf(0)}
    fun goBack() {
        if(checkoutBusy||profileBusy)return
        when (screen) {
            "revision" -> {
                val termsLocked = selectedOrder?.obj("customer_correction")?.has("settlement_amounts") == true
                if (checkoutTitle == "ثبت سفارش" || (termsLocked && checkoutTitle == "نحوه تسویه")) screen="order-detail"
                else checkoutBackRequest++
            }
            "new" -> if (checkoutTitle != "ثبت سفارش") checkoutBackRequest++ else {parkActiveCheckout();screen="home"}
            "profile" -> if (profileTitle != "پروفایل") profileBackRequest++ else screen="home"
            "order-detail" -> if(cancelView) orderBackRequest++ else screen=orderOrigin
            "serial-detail" -> screen="order-detail"
            "serials" -> screen=serialsOrigin
            "proforma" -> screen=proformaOrigin
            "documents", "addresses", "summary" -> screen="profile"
            "offers" -> if(selectedOffer!=null) selectedOffer=null else screen=offersOrigin
            "success" -> screen="orders"
            else -> screen="home"
        }
    }
    BackHandler(enabled = screen != "home") { goBack() }
    fun selectTab(destination:String) {
        if(checkoutBusy||profileBusy)return
        if(screen=="new"&&destination!="new")parkActiveCheckout()
        selectedOffer=null;screen=destination
    }
    val navScreen=when(screen){"serials","serial-detail","order-detail","success","revision"->"orders";"documents","proforma","addresses","summary"->"profile";"inbox","offers"->"home";else->screen}
    val wideNavigation=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=600
    val backdrop=rememberGraphicsLayer()
    val blurBackdrop=android.os.Build.VERSION.SDK_INT>=31&&!wideNavigation
    var backdropPosition by remember {mutableStateOf(Offset.Zero)}
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val direction=LocalLayoutDirection.current
    val titles = mapOf("documents" to "پیش‌فاکتورهای من", "proforma" to "پیش‌فاکتور", "success" to "سفارش ثبت شد", "serials" to "سریال‌های من", "addresses" to "آدرس‌های من", "home" to "پیشخوان", "catalog" to "قیمت و موجودی", "new" to "ثبت سفارش", "orders" to "سفارش‌های من", "profile" to "پروفایل", "order-detail" to "جزئیات سفارش", "inbox" to "مرکز پیام", "offers" to "طرح‌ها و آفرها")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        if (AppAppearance.dark) listOf(Color(0xFF121D33), Color(0xFF172B43))
        else listOf(Color(0xFFF4F6FF), Color(0xFFEDF3FC))
    ))) { Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val backScreen=screen in setOf("order-detail","documents","proforma","success","addresses","serial-detail","revision") || (screen=="offers"&&selectedOffer!=null) || (screen=="new"&&checkoutTitle!="ثبت سفارش") || (screen=="profile"&&profileTitle!="پروفایل")
            val title=if(screen=="serial-detail")"سریال دستگاه‌ها" else if(screen=="order-detail"&&cancelView)"لغو سفارش" else if(screen=="profile")profileTitle else if(screen=="revision"&&checkoutTitle=="ثبت سفارش")"اصلاح سفارش" else if(screen in setOf("new","revision"))checkoutTitle else if(screen=="offers"&&selectedOffer!=null)"جزئیات آفر" else titles[screen]?:"قدیر پارتنر"
            if(backScreen) PortalBackHeader(if(title=="نحوه تسویه") "تسویه حساب" else title) {
                goBack()
            } else PortalGlassHeader(title,screen=="home",inbox.unread,onOffers={if(!checkoutBusy&&!profileBusy){if(screen=="new")parkActiveCheckout();offersOrigin=screen;selectedOffer=null;screen="offers"}}) {
                if(!checkoutBusy&&!profileBusy){if(screen=="new")parkActiveCheckout();screen="inbox";appScope.launch {inbox.refresh()}}
            }
        },
        bottomBar = {
            if(!wideNavigation&&!keyboardVisible)FigmaBottomBar(portalNav,navScreen,::selectTab,modifier=Modifier.navigationBarsPadding(),backdrop=if(blurBackdrop)backdrop else null,contentPosition=backdropPosition)
        }
    ) { pad ->
        val bodyPadding=PaddingValues(start=pad.calculateStartPadding(direction),top=pad.calculateTopPadding(),end=pad.calculateEndPadding(direction),bottom=if(wideNavigation)pad.calculateBottomPadding() else 0.dp)
        CompositionLocalProvider(LocalPortalBottomPadding provides if(!wideNavigation&&!keyboardVisible)pad.calculateBottomPadding() else 0.dp) {
        Row(Modifier.fillMaxSize().padding(bodyPadding).consumeWindowInsets(bodyPadding).imePadding()
            .onGloballyPositioned {backdropPosition=it.positionInRoot()}
            .drawWithContent {if(blurBackdrop){backdrop.record {this@drawWithContent.drawContent()};drawLayer(backdrop)}else drawContent()}) {
            if(wideNavigation) AppNavigationRail(portalNav,navScreen,::selectTab)
            Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.TopCenter) {
            Box(Modifier.widthIn(max=840.dp).fillMaxSize()) {
            Crossfade(targetState = screen, label = "portal-nav") { target ->
                screenStates.SaveableStateProvider(me.s("username") + ":" + target + if(target=="new")":$checkoutSessionKey" else "") {
                when (target) {
                    "home" -> PortalHome(
                        api,
                        navigate = { selectedOffer=null; screen = it },
                        customerName = customerName, accountKey=me.s("username"),
                        openOrder = { orderOrigin=screen; selectedOrder = it; screen = "order-detail" },
                        openOffer = {offersOrigin=screen;selectedOffer=it;screen="offers"}
                    )
                    "orders" -> PortalOrders(api,abandonedOrder=abandonedOrder,onContinueAbandoned={
                        restoreCheckoutDraft=it;checkoutSessionKey++;screen="new"
                    },onDeleteAbandoned={clearAbandonedDraft();activityCart=emptyList()},onNavigate={screen=it}) { orderOrigin=screen; selectedOrder = it; screen = "order-detail" }
                    "new" -> PortalNewOrder(api, offerCode, catalogProduct, catalogGroup, restoreDraft=restoreCheckoutDraft,abandonedBanner=abandonedOrder,
                        onProductConsumed={catalogProduct=""},onBusyChanged={checkoutBusy=it},
                        onDraftSnapshot={snapshot->
                            if (snapshot != null || checkoutDraftSnapshot != null) {
                                activityCart = snapshot?.let(::cartFromDraft).orEmpty()
                            }
                            checkoutDraftSnapshot=snapshot
                            // Persist during edits so immediate navigation cannot discard the last cart.
                            if(snapshot!=null)abandonedStore.save(snapshot)
                        },onClearAbandoned={clearAbandonedDraft()},onRestoreConsumed={restoreCheckoutDraft=null},
                        onStageChanged={checkoutTitle=it},backRequest=checkoutBackRequest) { createdOrder ->
                        offerCode=""
                        clearAbandonedDraft()
                        activityCart=emptyList()
                        orderOrigin="orders"
                        selectedOrder = createdOrder
                        screen = "success"
                    }
                    "serials" -> PortalOrders(api, deliveredOnly=true,onNavigate={screen=it}) {orderOrigin=screen;selectedOrder=it;screen="order-detail"}
                    "documents" -> PortalDocuments(api,currentMe,onOpen={selectedOrder=it;proformaOrigin="documents";screen="proforma"})
                    "proforma" -> PortalDocuments(api,currentMe,selectedOrder,onOpen={})
                    "success" -> selectedOrder?.let {order->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom=LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            DesignState("سفارش با موفقیت ثبت شد","از بخش سفارش‌ها مراحل آماده‌سازی و ارسال را پیگیری کنید.",R.drawable.design_success,success=true)
                            GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                DetailLine("کد پیگیری",faDigits(order.s("number")))
                                DetailLine("اقلام",orderItemsDescription(order))
                            }}
                            DesignNotice("سفارش برای بررسی شرکت ثبت شد. نتیجهٔ تأیید یا عدم تأیید را در مراحل سفارش می‌بینید.")
                            GhadirButton("مشاهده سفارش",{screen="order-detail"})
                            GhadirButton("دریافت پیش‌فاکتور",{proformaOrigin="success";screen="proforma"},secondary=true)
                        }
                    }
                    "addresses" -> PortalAddresses(api)
                    "summary" -> PortalHome(api,customerName,{screen=it},{orderOrigin=screen;selectedOrder=it;screen="order-detail"},showReport=true)
                    "catalog" -> PortalCatalog(api,onHome={screen="home"},onOrder={ product, group -> catalogProduct=product;catalogGroup=group;screen="new" })
                    "offers" -> PortalOffers(api, selectedOffer,onOpen={selectedOffer=it}) { code -> offerCode=code; screen="new" }
                    "inbox" -> PortalInbox(inbox, onOrder={ id ->
                        appScope.launch {
                            try {
                                val orders=(api.get("/api/orders") as JSONArray).objects()
                                selectedOrder=orders.firstOrNull {it.i("id")==id}
                                if(selectedOrder!=null){orderOrigin="inbox";screen="order-detail"} else Toast.makeText(context,"سفارش در دسترس نیست",Toast.LENGTH_LONG).show()
                            } catch(e: Exception){Toast.makeText(context,"دریافت سفارش ناموفق بود؛ دوباره تلاش کنید",Toast.LENGTH_LONG).show()}
                        }
                    }, onOffer={ id -> offersOrigin="inbox";selectedOffer=id;screen="offers" })
                    "revision" -> selectedOrder?.let { order ->
                        val version = order.obj("customer_correction").i("version")
                        val revisionDraft = remember(order.i("id"), version) { draftFromReturnedOrder(order) }
                        key(order.i("id"), version) {
                            PortalNewOrder(
                                api, restoreDraft = revisionDraft, revisionOrder = order,
                                onBusyChanged = { checkoutBusy = it }, onStageChanged = { checkoutTitle = it },
                                backRequest = checkoutBackRequest,
                            ) { updated ->
                                selectedOrder = updated
                                screen = "order-detail"
                            }
                        }
                    }
                    "order-detail" -> selectedOrder?.let { order ->
                        PortalOrderDetails(api, order, onBack = { screen = orderOrigin }, onProforma={proformaOrigin="order-detail";screen="proforma"},me=currentMe,
                            onSerials={screen="serial-detail"},onRevise={selectedOrder=it;checkoutTitle="ثبت سفارش";screen="revision"},onOrderUpdated={selectedOrder=it},onSectionChanged={cancelView=it},backRequest=orderBackRequest)
                    } ?: PortalOrders(api,onNavigate={screen=it}) { orderOrigin=screen; selectedOrder = it; screen = "order-detail" }
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
                        onLogout = {
                            runCatching {java.io.File(context.cacheDir,"checkout-state").deleteRecursively()}
                            onLogout()
                        },
                        onProforma = { screen="documents" }, onNavigate = {if(it=="serials")serialsOrigin="profile";if(it=="offers")offersOrigin="profile";screen=it},
                        onSectionChanged={profileTitle=it},onBusyChanged={profileBusy=it},backRequest=profileBackRequest
                    )
                }
                }
            }
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
private fun PortalHome(api: ApiClient, customerName: String, navigate: (String) -> Unit, openOrder: (JSONObject) -> Unit, openOffer:(Int)->Unit={}, showReport:Boolean=false, accountKey:String="") {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf(emptyList<JSONObject>()) }
    var ordersLoaded by remember { mutableStateOf(false) }
    var offers by remember { mutableStateOf(emptyList<JSONObject>()) }
    var stats by remember { mutableStateOf(JSONObject()) }
    var loadedAccount by remember {mutableStateOf(false)}
    val popupContext=LocalContext.current
    val popupService=remember(accountKey) {OfferPopupService(OfferPopupRepository(popupContext.getSharedPreferences("offer-popup",0),accountKey),OfferPopupPolicy())}
    var popup by rememberSaveable(accountKey,stateSaver=NullableJsonSaver) {mutableStateOf<JSONObject?>(null)}
    LaunchedEffect(offers) {
        if(BuildConfig.OFFER_POPUP_ENABLED&&!showReport&&accountKey.isNotBlank())popup=popupService.next(offers)
    }


    fun refresh() {
        scope.launch {
            loading = true; error = ""
            try {
                coroutineScope {
                    val ordersReq = async {
                        (api.get("/api/orders") as JSONArray).objects().sortedByDescending { it.i("id") }.also {
                            orders = it
                            ordersLoaded = true
                        }
                    }
                    launch {
                        try {
                            offers = (api.get("/api/offers") as JSONArray).objects().filterNot { it.b("used_by_customer") }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Offers have their own page and may be retried independently.
                        }
                    }
                    val statsReq = async {
                        try { api.get("/api/reports/customer-summary") as? JSONObject ?: JSONObject() }
                        catch(e: kotlinx.coroutines.CancellationException) { throw e }
                        catch(_: Exception) { JSONObject() }
                    }
                    val purchasedReq = if (showReport) async {
                        try { (api.get("/api/purchased-devices") as JSONObject).i("qty") }
                        catch(e: kotlinx.coroutines.CancellationException) { throw e }
                        catch(_: Exception) { null }
                    } else null

                    val loadedOrders = ordersReq.await()
                    val loadedStats = statsReq.await().takeIf { it.length() > 0 }
                        ?: customerSummaryFromOrders(loadedOrders)
                    stats = loadedStats
                    loadedAccount = true
                    if (purchasedReq != null) {
                        val purchased = purchasedReq.await() ?: run {
                            val serialModels = (api.get("/api/catalog") as JSONArray).objects()
                                .filter { it.b("serial_required") }.map { it.s("name") }.toSet()
                            loadedOrders.filter { it.s("status") == "تحویل شد" }.sumOf { order ->
                                order.arr("items").objects()
                                    .filter { it.s("product") in serialModels }.sumOf { it.i("qty") }
                            }
                        }
                        stats = JSONObject(loadedStats.toString()).put("purchased_device_qty", purchased)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {throw e}
            catch (e: Exception) { error = e.message ?: "خطا در دریافت اطلاعات" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    if (showReport && loading) { LoadingPane(); return }
    if(showReport && error.isNotBlank()&&!loadedAccount) {
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DesignState("اطلاعات حساب دریافت نشد","ارتباط با سرویس برقرار نشد. اتصال را بررسی کنید و دوباره تلاش کنید.",R.drawable.design_offline)
            GhadirButton("تلاش دوباره",{refresh()})
        }
        return
    }

    popup?.let { offer ->
        fun dismissOffer() {popupService.acknowledge(offer);popup=null}
        AlertDialog(onDismissRequest={dismissOffer()},icon={Icon(Icons.Default.LocalOffer,null,tint=Orange)},
            title={Text(offer.s("title"),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())},
            text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(offer.s("description").ifBlank {"شرایط این طرح ویژه را ببینید."},color=Ink,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                Text(offerDiscountTextNative(offer),color=Orange,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
            }},confirmButton={TextButton(onClick={dismissOffer();openOffer(offer.i("id"))}){Text("مشاهده طرح")}},
            dismissButton={TextButton(onClick={dismissOffer()}){Text("فعلاً بستن")}},containerColor=AppSurface)
    }

    if(showReport) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp+LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(error.isNotBlank())item {ErrorBanner(error){error=""};GhadirButton("تلاش دوباره",{refresh()})}
            item {PurchaseTrendCard(orders)}
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp+LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {
            if (!ordersLoaded) DesignState("اطلاعات حساب دریافت نشد","ارتباط با سرویس برقرار نشد. اتصال را بررسی کنید و دوباره تلاش کنید.",R.drawable.design_offline)
            else ErrorBanner(error){error=""}
            GhadirButton("تلاش دوباره",{refresh()})
        }
        item {Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("سلام، $customerName",color=Ink,fontSize=22.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
            Text("خلاصه حساب شما",color=Muted,fontSize=14.sp,lineHeight=23.sp)
        }}
        if (error.isBlank() || ordersLoaded) item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DetailLine("سفارش در جریان",if(ordersLoaded) faNumber(orders.count {it.s("status") !in setOf("تحویل شد","لغو شد")})+" سفارش" else "—")
            HorizontalDivider(color=Border)
            DetailLine("خرید این ماه",if(loadedAccount) formatMoney(stats.obj("month").l("amount"))+" تومان" else "—")
        }}}
        item {GhadirButton("ثبت سفارش جدید",{navigate("new")})}
        item {DesignOfferHero(featured?.s("title")?:"طرح‌ها و آفرهای قدیر پارتنر",
            featured?.s("description")?.ifBlank {"شرایط و مدل‌های مشمول را ببینید."}
                ?:"پیشنهادهای مجاز حساب شما در بخش آفرها نمایش داده می‌شوند.",
            "مشاهده آفر") {if(featured!=null)openOffer(featured.i("id")) else navigate("offers")}}
        if (ordersLoaded) {
            if (orders.isEmpty()) item {
                DesignState("هنوز سفارشی ندارید","پس از ثبت اولین سفارش، وضعیت و جزئیات آن را اینجا می‌بینید.",R.drawable.design_empty_orders)
            } else item { DesignOrderCard(orders.first(), latest = true) { openOrder(orders.first()) } }
        }
    }
}
@Composable
private fun PortalSummaryHero(amount: Long, orders: Int, qty: Int) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Navy) {
        Row(Modifier.background(PortalBrandBrush).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("خلاصه حساب شما", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("خرید این ماه: ${formatMoney(amount)} تومان", color = Color(0xFFFFC574), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${faNumber(orders)} سفارش · ${faNumber(qty)} دستگاه", color = Color.White, fontSize = 12.sp)
            }
            PaymentArtwork(R.drawable.payment_ref_hero, Modifier.size(82.dp, 64.dp))
        }
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
private fun PortalOrders(
    api:ApiClient,
    deliveredOnly:Boolean=false,
    abandonedOrder:JSONObject?=null,
    onContinueAbandoned:(JSONObject)->Unit={},
    onDeleteAbandoned:()->Unit={},
    onNavigate:(String)->Unit={},
    onOpen:(JSONObject)->Unit,
) {
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
        (!deliveredOnly||delivered)&&when(group){"delivered"->delivered;"active"->!delivered&&!o.s("status").contains("لغو");"pending"->o.s("status").contains("انتظار");"preparing"->o.s("status").contains("آماده");"shipped"->o.s("status")=="ارسال شد";else->true}&&
            (term.isBlank()||listOf(o.s("number"),o.s("status"),o.s("payment_status"),orderItemsDescription(o)).any {asciiDigits(it).contains(term,true)})
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.Transparent).padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp+LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(!deliveredOnly&&abandonedOrder!=null)item {
            ApprovedSurface {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("سفارشات رها شده",color=ApprovedPalette.ink,fontWeight=FontWeight.Bold,fontSize=14.sp)
                Text("یک سفارش تکمیل‌نشده ذخیره شده است؛ معتبر تا ساعت ۲۴ امروز به وقت تهران.",color=ApprovedPalette.muted,fontSize=13.sp)
                val preview=cartFromDraft(abandonedOrder)
                if(preview.isNotEmpty())Text(preview.joinToString(" • "){"${shortProductName(it.product)} × ${faNumber(it.qty)}"},color=ApprovedPalette.ink,fontSize=13.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ApprovedButton("ادامه سفارش",{onContinueAbandoned(abandonedOrder)},modifier=Modifier.weight(1f))
                    ApprovedButton("حذف سفارش",onDeleteAbandoned,secondary=true,modifier=Modifier.weight(1f))
                }
            }}
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                ApprovedButton("سفارش جدید", {onNavigate("new")})
            }
        }
        item {ApprovedSearchBox(query,{query=it},"جست‌وجوی شماره سفارش یا کالا")}
        if(!deliveredOnly)item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("all" to "همه","pending" to "در انتظار تأیید","preparing" to "در حال آماده‌سازی","shipped" to "ارسال‌شده","active" to "در جریان","delivered" to "تحویل‌شده").forEach { (key,label) ->
                    FilterChip(selected=group==key,onClick={group=key},label={Text(label)},
                        colors=FilterChipDefaults.filterChipColors(containerColor=ApprovedPalette.surface,
                            selectedContainerColor=ApprovedPalette.accent,labelColor=ApprovedPalette.ink,selectedLabelColor=Color.White))
                }
            }
        }
        when {
            loading->item{LoadingPane()}
            error.isNotBlank()->item {
                DesignState("دریافت سفارش‌ها ناموفق بود",error,R.drawable.design_offline)
                ApprovedButton("تلاش دوباره",{retry++})
            }
            filtered.isEmpty()->item {
                DesignState(if(orders.isEmpty())"هنوز سفارشی ندارید" else "سفارشی پیدا نشد",
                    if(orders.isEmpty())"پس از ثبت اولین سفارش، وضعیت و جزئیات آن را اینجا می‌بینید." else "فیلتر یا عبارت جستجو را تغییر دهید.",
                    R.drawable.design_empty_orders)
                ApprovedButton(if(orders.isEmpty())"ثبت اولین سفارش" else "پاک کردن جستجو و فیلترها",
                    {if(orders.isEmpty())onNavigate("new") else {query="";group=if(deliveredOnly)"delivered" else "all"}})
                Spacer(Modifier.height(16.dp));ApprovedButton("مشاهده قیمت‌ها",{onNavigate("catalog")},secondary=true)
            }
            else->items(filtered,key={it.i("id")}) {o->ApprovedOrderCard(o){onOpen(o)}}
        }
    }
}
@Composable
private fun PortalOrderDetails(api:ApiClient,initialOrder:JSONObject,onBack:()->Unit,onProforma:()->Unit,me:JSONObject=JSONObject(),onSerials:()->Unit={},onSectionChanged:(Boolean)->Unit={},backRequest:Int=0,onOrderUpdated:(JSONObject)->Unit={},onRevise:(JSONObject)->Unit={}) {
    var order by remember(initialOrder.i("id")) {mutableStateOf(initialOrder)}
    var refreshing by remember {mutableStateOf(false)}
    val orderService = remember(api) {OrderCheckoutService(OrderRepository(api))}
    val scope=rememberCoroutineScope()
    var cancelView by remember {mutableStateOf(false)}
    var cancelling by remember {mutableStateOf(false)}
    var reason by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var cancelSent by remember {mutableStateOf(false)}
    val total=order.l("approved_total").takeIf {it>0}?:order.l("estimated_total")
    val delivered=order.s("status")=="تحویل شد"
    val canCancel=order.s("status") !in listOf("ارسال شد","تحویل شد","لغو شد")
    fun refreshOrder() {
        if(refreshing)return
        scope.launch {
            refreshing=true
            try {order=orderService.find(order.i("id"));onOrderUpdated(order);error=""}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"به‌روزرسانی وضعیت ناموفق بود"}
            finally {refreshing=false}
        }
    }
    LaunchedEffect(initialOrder.i("id")){refreshOrder()}
    LaunchedEffect(cancelView){onSectionChanged(cancelView)}
    LaunchedEffect(backRequest){if(backRequest>0)cancelView=false}
    BackHandler(cancelView){if(!cancelling)cancelView=false}
    LazyColumn(Modifier.fillMaxSize().background(Color.Transparent).padding(horizontal=16.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp+LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""}}
        if(cancelSent)item {DesignNotice("درخواست لغو ثبت شد و در انتظار بررسی است.")}
        if(cancelView) {
            item {DesignNotice("لغو سفارش\n\nآیا از لغو سفارش "+faDigits(order.s("number"))+" مطمئن هستید؟")}
            item {ApprovedSurface {Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
            item { ApprovedOrderSummary(order, me, total) }
            items(order.arr("purchase_items").objects().ifEmpty {order.arr("items").objects()}) { line ->
                ApprovedOrderProduct(line)
            }
            val correction = order.obj("customer_correction")
            if (correction.s("status") == "requested") {
                item { DesignNotice("درخواست اصلاح سفارش\n\n" + correction.s("reason")) }
                val label = if (correction.has("settlement_amounts")) "ادامه خرید با شرایط اعلام‌شده" else "اصلاح و ارسال دوباره"
                item { GhadirButton(label, { onRevise(order) }) }
                val proofs = order.obj("native_settlement").arr("proofs").objects()
                if (proofs.isNotEmpty()) item {
                    val repository = remember(api) { OrderFollowupRepository(api) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مدارک قبلی سفارش", color = Ink)
                        proofs.forEachIndexed { index, proof -> StoredOrderProof(repository, order.i("id"), proof, index + 1) }
                    }
                }
            } else if (correction.s("status") == "resubmitted") {
                item { DesignNotice("سفارش اصلاح‌شده برای بررسی شرکت فرستاده شد.") }
            }
            item { IranKishOrderPaymentActions(api, order, onConfirmed = { refreshOrder() }) }
            if (correction.s("status") != "requested") item { OrderProofActions(api, order) { refreshOrder() } }
            item { CustomerCreditRepayment(api, order) }
            item {OrderDeliveryTimeline(order.s("status"),order.s("approval_status"))}
            item(key = "shipping-receipts") { OrderShippingReceipts(api, order.i("id")) }
            item {GhadirButton(if(refreshing)"در حال به‌روزرسانی…" else "به‌روزرسانی وضعیت",{refreshOrder()},enabled=!refreshing,secondary=true)}
            if(order.s("rejection_reason").isNotBlank())item {DesignNotice("علت عدم تأیید شرکت\n\n"+order.s("rejection_reason"))}
            item {ApprovedSurface {Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
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
            if(order.s("notes").isNotBlank())item {DesignNotice(order.s("notes"))}
            items(order.arr("history").objects().takeLast(8).reversed()) {h->ApprovedSurface {Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(h.s("action"),color=Ink,fontSize=12.sp)
                Text(formatDateFa(h.s("at"))+" • "+h.s("by"),color=Muted,fontSize=12.sp)
            }}}
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

internal val NullableJsonSaver = Saver<JSONObject?, String>(
    save = { it?.toString().orEmpty() },
    restore = { if(it.isBlank()) null else JSONObject(it) }
)
internal fun cachedJsonSaver(context:android.content.Context):Saver<JSONObject,String> {
    val directory=java.io.File(context.cacheDir,"checkout-state").apply {mkdirs()}
    val file=java.io.File(directory,java.util.UUID.randomUUID().toString()+".json")
    return Saver(save={ value ->
        runCatching {file.writeText(value.toString());file.name}.getOrDefault("")
    },restore={ name ->
        if(!name.matches(Regex("[a-f0-9-]+\\.json"))) JSONObject().put("restore_failed",true)
        else runCatching {JSONObject(java.io.File(directory,name).readText())}.getOrElse {JSONObject().put("restore_failed",true)}
    })
}
internal val JsonSaver = Saver<JSONObject, String>(save={it.toString()}, restore={JSONObject(it)})

private val CartSaver = Saver<List<CartLine>, String>(
    save = { lines -> JSONArray().apply { lines.forEach { line ->
        put(JSONObject().put("product",line.product).put("qty",line.qty).put("priceKey",line.priceKey)
            .put("priceLabel",line.priceLabel).put("unit",line.unit).put("panelSwitch",line.panelSwitch))
    } }.toString() },
    restore = { JSONArray(it).objects().map { row -> CartLine(row.s("product"),row.i("qty"),row.s("priceKey"),row.s("priceLabel"),row.l("unit"),row.s("panelSwitch")) } }
)


private fun discountForOfferNative(offer: JSONObject?, cart: List<CartLine>): Long {
    if (offer == null) return 0L
    val products = offer.optJSONArray("products")?.strings()?.toSet()
        ?: setOf(offer.s("product")).filter { it.isNotBlank() }.toSet()
    val eligible = OrderOfferScope(products).eligibleAmount(cart)
    if (eligible <= 0L) return 0L
    val value = offer.l("discount_value").coerceAtLeast(0L)
    return when (offer.s("discount_type")) {
        "percent" -> minOf(eligible, (eligible / 100L) * minOf(100L, value) + (eligible % 100L) * minOf(100L, value) / 100L)
        "amount" -> minOf(eligible, value)
        else -> 0L
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalNewOrder(
    api:ApiClient,
    initialOfferCode:String="",
    initialProduct:String="",
    initialGroup:String="serial",
    restoreDraft:JSONObject?=null,
    abandonedBanner:JSONObject?=null,
    revisionOrder:JSONObject?=null,
    onProductConsumed:()->Unit={},
    onBusyChanged:(Boolean)->Unit={},
    onDraftSnapshot:(JSONObject?)->Unit={},
    onClearAbandoned:()->Unit={},
    onRestoreConsumed:()->Unit={},
    onStageChanged:(String)->Unit={},
    backRequest:Int=0,
    onSuccess:(JSONObject)->Unit,
) {
    val checkoutService = remember(api) { OrderCheckoutService(OrderRepository(api)) }
    val agentTermsLocked = revisionOrder?.obj("customer_correction")?.has("settlement_amounts") == true
    val lockedTotal = if (agentTermsLocked) revisionOrder?.l("estimated_total") else null
    val cartService = remember { OrderCartService(OrderPolicy(BuildConfig.ORDER_MAXIMUM_QUANTITY, BuildConfig.ORDER_SERIAL_TIER_THRESHOLD)) }
    val scope=rememberCoroutineScope()
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    var loading by remember {mutableStateOf(true)}
    var step by rememberSaveable {mutableIntStateOf(0)}
    var submitting by remember {mutableStateOf(false)}
    var evidenceBusy by remember {mutableStateOf(false)}
    var settlementAttempt by rememberSaveable { mutableIntStateOf(0) }
    var settlementDialog by rememberSaveable { mutableStateOf("") }
    var settlementChoiceError by rememberSaveable { mutableStateOf("") }
    var shippingAttempted by rememberSaveable { mutableStateOf(false) }
    var error by remember {mutableStateOf("")}
    var retry by remember {mutableIntStateOf(0)}
    var products by remember {mutableStateOf(emptyList<JSONObject>())}
    var offers by remember {mutableStateOf(emptyList<JSONObject>())}
    var addresses by remember {mutableStateOf(emptyList<JSONObject>())}
    var customer by remember {mutableStateOf(JSONObject())}
    var productQuery by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(stateSaver=NullableJsonSaver) {mutableStateOf<JSONObject?>(null)}
    var group by rememberSaveable {mutableStateOf("serial")}
    var panelTier by rememberSaveable {mutableStateOf("panel_cash")}
    var panelSwitch by rememberSaveable { mutableStateOf("") }
    var productSwitches by remember { mutableStateOf(JSONObject()) }
    val panelSwitches=productSwitches.arr(selected?.s("name").orEmpty()).strings()
    var switchLoadFailed by remember { mutableStateOf(false) }
    var switchOptionsLoaded by remember { mutableStateOf(false) }
    var switchRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, switchRetry) {
        switchLoadFailed = false
        switchOptionsLoaded = false
        try {
            val response = api.request("GET", "/api/native/panel-switches", null) as JSONObject
            productSwitches=response.obj("products")
            switchOptionsLoaded = true
            switchLoadFailed=false
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (_: Exception) {
            switchLoadFailed = true
        }
    }
    var qtyText by rememberSaveable {mutableStateOf("1")}
    var shippingMethod by rememberSaveable {mutableStateOf("")}
    val shippingMethods=remember {BuildConfig.ORDER_SHIPPING_METHODS.split("|")}
    var selectedAddressId by rememberSaveable {mutableStateOf("")}
    val context=LocalContext.current
    // Receipt images can exceed Android's saved-instance-state transaction limit.
    val settlementSaver=remember(context) { cachedJsonSaver(context) }
    var paymentId by rememberSaveable { mutableStateOf("") }
    var paymentConfirmed by rememberSaveable { mutableStateOf(false) }
    var confirmWalletReturn by rememberSaveable { mutableStateOf(false) }
    var settlement by rememberSaveable(stateSaver=settlementSaver) {mutableStateOf(JSONObject())}
    var invoiceType by rememberSaveable {mutableStateOf("")}
    var notes by rememberSaveable {mutableStateOf("")}
    var optionSheet by rememberSaveable {mutableStateOf("")}
    var promoCode by rememberSaveable {mutableStateOf(initialOfferCode)}
    var appliedOffer by rememberSaveable(stateSaver=NullableJsonSaver) {mutableStateOf<JSONObject?>(null)}
    var promoMessage by rememberSaveable {mutableStateOf("")}
    var cart by rememberSaveable(stateSaver=CartSaver) {mutableStateOf(emptyList<CartLine>())}
    LaunchedEffect(settlement) {
        if(settlement.b("restore_failed")){step=1;error="اطلاعات تسویه بازیابی نشد؛ لطفاً دوباره تکمیل کنید.";settlement=defaultSplitSettlement(0L)}
    }
    var restoreApplied by rememberSaveable {mutableStateOf(false)}
    var checkoutPaymentAmount by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(paymentConfirmed) {
        if (paymentConfirmed) listState.scrollToItem(0)
    }
    LaunchedEffect(submitting,evidenceBusy){onBusyChanged(submitting||evidenceBusy)}
    DisposableEffect(Unit){onDispose{onBusyChanged(false)}}
    LaunchedEffect(error) {if(error.isNotBlank())listState.scrollToItem(0)}
    LaunchedEffect(step) {listState.scrollToItem(0);onStageChanged(when(step){1->"نحوه تسویه";2->"نحوه ارسال";3->"مشاهده سبد و ثبت نهایی";else->"ثبت سفارش"})}
    BackHandler(submitting) { /* Keep the pending request alive until the server responds. */ }
    LaunchedEffect(initialProduct,products) {
        if(initialProduct.isNotBlank()&&products.isNotEmpty()) {
            selected=products.firstOrNull { it.s("name")==initialProduct }
            group=initialGroup;step=0;onProductConsumed()
        }
    }
    LaunchedEffect(initialOfferCode) { if(initialOfferCode.isNotBlank()) { promoCode=initialOfferCode;appliedOffer=null } }
    LaunchedEffect(retry) {
        loading=true;error=""
        try {
            coroutineScope {
                val catalog=async{(api.get("/api/catalog") as JSONArray).objects().filter{catalogInStock(it)}}
                val plans=async{(api.get("/api/offers") as JSONArray).objects().filter {
                    !it.b("used_by_customer") || it.s("promo_code") == revisionOrder?.s("promo_code")
                }}
                val account=async{api.me().obj("customer")}
                val saved=async{try{(api.get("/api/addresses") as JSONArray).objects()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:ApiException){if(e.status==404)emptyList() else throw e}}
                products=catalog.await();offers=plans.await();customer=account.await();addresses=saved.await()
            }
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"دریافت اطلاعات سفارش ناموفق بود"}finally{loading=false}
    }
    val qty=qtyText.toIntOrNull()?:0
    val priceKey=cartService.priceKey(group,panelTier,qty)
    val unit=selected?.obj("prices")?.l(priceKey)?:0L
    val subtotal=cart.sumOf {it.unit*it.qty}
    val discount=discountForOfferNative(appliedOffer,cart)
    val total=lockedTotal ?: (subtotal-discount).coerceAtLeast(0L)
    val agentReviewRequired = revisionOrder == null && cart.any { it.priceKey == "sales_agent" }
    LaunchedEffect(cart,step,selectedAddressId,invoiceType,settlement,notes,promoCode,appliedOffer,total,paymentId,shippingMethod) {
        if (revisionOrder == null) onDraftSnapshot(
            if (cart.isEmpty()) null else checkoutDraftFromState(cart,step,selectedAddressId,invoiceType,settlement,notes,promoCode,appliedOffer,keepEvidence=true).put("shippingMethod",shippingMethod)
                .put("online_checkout_id", paymentId),
        )
    }
    LaunchedEffect(total) {
        settlement=if(!settlement.b("split"))AgentSettlementPolicy().defaults(cart,total) else refreshSettlementTotals(settlement,total)
    }
    LaunchedEffect(restoreDraft,products,loading) {
        val draft=restoreDraft ?: return@LaunchedEffect
        if(restoreApplied||loading||(products.isEmpty()&&revisionOrder==null))return@LaunchedEffect
        restoreApplied=true
        cart = cartFromDraft(draft).map { line ->
            val currentPrice = products.firstOrNull { it.s("name") == line.product }?.obj("prices")?.l(line.priceKey)
            if (revisionOrder == null || agentTermsLocked || currentPrice == null || currentPrice <= 0) line else line.copy(unit = currentPrice)
        }
        step=draft.optInt("step").coerceIn(0,3)
        paymentId = draft.s("online_checkout_id")
        selectedAddressId=draft.s("selectedAddressId");shippingMethod=draft.s("shippingMethod")
        invoiceType=draft.s("invoiceType")
        notes=draft.s("notes")
        promoCode=draft.s("promoCode")
        val restoredTotal=lockedTotal ?: cart.sumOf{it.unit*it.qty}.coerceAtLeast(0L)
        settlement=refreshSettlementTotals(draft.obj("settlement"),restoredTotal)
        val offerRaw=draft.s("appliedOffer")
        appliedOffer=if(offerRaw.isNotBlank())runCatching{JSONObject(offerRaw)}.getOrNull() else null
        onRestoreConsumed()
        onClearAbandoned()
    }
    val address=addresses.firstOrNull {it.s("id")==selectedAddressId}?:customer
    val addressText=listOf(address.s("province"),address.s("city"),address.s("address")).filter {it.isNotBlank()}.joinToString("، ")
    val receiver=listOf(customer.s("name"),customer.s("company")).filter {it.isNotBlank()}.joinToString(" • ")
    fun applyPromo() {
        val match=offers.firstOrNull {promoNorm(it.s("promo_code"))==promoNorm(promoCode)&&it.s("discount_type") in listOf("percent","amount")&&it.l("discount_value")>0}
        val amount=discountForOfferNative(match,cart)
        if(appliedOffer?.toString()!=match?.toString()) {
            // The discount changes the invoice total during the next composition.
            settlement=defaultSplitSettlement((subtotal-amount).coerceAtLeast(0L))
        }
        appliedOffer=if(amount>0)match else null
        promoMessage=if(amount>0)offerDiscountTextNative(match!!)+" اعمال شد." else "این کد برای کالاهای فعلی سفارش معتبر یا فعال نیست."
    }
    LaunchedEffect(initialOfferCode,offers,cart) {
        val original = revisionOrder?.s("promo_code") ?: initialOfferCode
        if (!agentTermsLocked && original.isNotBlank() && promoNorm(promoCode) == promoNorm(original) && cart.isNotEmpty()) applyPromo()
    }
    fun catalogEntry(product: JSONObject): OrderCatalogEntry = OrderCatalogEntry(
        product.s("name"), product.i("stock"),
        product.obj("prices").keys().asSequence().associateWith { product.obj("prices").l(it) },
    )
    fun checkout(): OrderCheckout {
        val refreshed=refreshSettlementTotals(settlement,total)
        val checkoutPromoCode = if (agentTermsLocked) promoCode else appliedOffer?.s("promo_code").orEmpty()
        return OrderCheckout(cart,selectedAddressId,invoiceType,settlementPrimaryMethod(refreshed),refreshed,notes,checkoutPromoCode,
            revisionOrderId = revisionOrder?.i("id") ?: 0, revisionVersion = revisionOrder?.obj("customer_correction")?.i("version") ?: 0,
            agentTermsLocked = agentTermsLocked, shippingMethod=shippingMethod)
    }
    fun changeQuantity(line:CartLine, next:Int) {
        val product=products.firstOrNull {it.s("name")==line.product} ?: return
        try {
            cart=cartService.changeQuantity(cart,line,catalogEntry(product),next)
            settlement=AgentSettlementPolicy().defaults(cart,total);error=""
        } catch(e:IllegalArgumentException) {error=e.message?:"تعداد نامعتبر است"}
    }
    val orderPayment = remember(api) { IranKishOrderPayment(api) }

    fun updatePayment() {
        if (submitting || paymentId.isBlank() || paymentConfirmed) return
        scope.launch {
            submitting = true
            error = ""
            try {
                val result = orderPayment.recheckCheckout(paymentId)
                paymentConfirmed = result.b("confirmed")
                if (!paymentConfirmed) {
                    if (result.b("expired") || result.b("failed")) {
                        paymentId = ""
                        checkoutPaymentAmount = 0L
                    }
                    error = if (result.b("failed")) "پرداخت ناموفق بود. می‌توانید سفارش را ویرایش کنید." else "پرداخت هنوز تأیید نشده است."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "بررسی وضعیت پرداخت ناموفق بود"
            } finally {
                submitting = false
            }
        }
    }

    val gateway = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { updatePayment() }

    fun editOrder(nextStep: Int) {
        if (submitting || evidenceBusy || paymentConfirmed || (agentTermsLocked && nextStep < 1)) return
        if (paymentId.isBlank()) {
            step = nextStep
            return
        }
        scope.launch {
            submitting = true
            try {
                paymentConfirmed = orderPayment.releaseCheckout(paymentId)
                if (paymentConfirmed) {
                    error = "پرداخت تأیید شد. اکنون سفارش را ثبت کنید."
                } else {
                    paymentId = ""
                    checkoutPaymentAmount = 0L
                    error = ""
                    step = nextStep
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "بازگشت به ویرایش ممکن نشد."
            } finally {
                submitting = false
            }
        }
    }

    var handledBackRequest by rememberSaveable { mutableIntStateOf(backRequest) }
    LaunchedEffect(backRequest) {
        if (backRequest != handledBackRequest && step > 0) editOrder(step - 1)
        handledBackRequest=backRequest
    }
    val firstCheckoutStep = if (agentTermsLocked) 1 else 0
    BackHandler(step > firstCheckoutStep || evidenceBusy) { if (step > firstCheckoutStep) editOrder(step - 1) }

    if (settlementDialog.isNotBlank()) AlertDialog(
        onDismissRequest={settlementDialog=""},
        title={Text("بررسی مبلغ تسویه")},
        text={Text(settlementDialog)},
        confirmButton={TextButton(onClick={settlementDialog=""}) {Text("متوجه شدم")}},
    )

    fun submit() {
        if(submitting)return
        if(addressText.isBlank()||shippingMethod.isBlank()){shippingAttempted=true;step=2;return}
        if(!paymentConfirmed && cart.any {line -> val p=products.firstOrNull {it.s("name")==line.product};p==null||!catalogInStock(p)||cart.filter{it.product==line.product}.sumOf{it.qty.toLong()}>p.i("stock")}) {
            error="موجودی سبد را دوباره بررسی کنید";if(!agentTermsLocked)step=0;return
        }
        val validation=checkoutService.validation(checkout())
        if(settlementAllocated(checkout().settlement)!=total){
            step=1;settlementDialog="جمع مبلغ‌های تسویه باید برابر مبلغ فاکتور باشد.";return
        }
        if(validation.isNotBlank()){
            step=if(invoiceType.isBlank())0 else 1
            if(step==0) error=validation else {settlementChoiceError=validation;settlementAttempt++}
            return
        }
        if(!agentTermsLocked&&promoCode.isNotBlank()&&(appliedOffer==null||discount<=0)){error="کد تخفیف را ابتدا اعمال و بررسی کنید";step=0;return}
        scope.launch {
            submitting=true;error=""
            try {
                val reviewed = checkout()
                val online = CashDepositRows.requiresOnline(reviewed.settlement)
                if (online && !paymentConfirmed) {
                    if (paymentId.isNotBlank()) {
                        val existing = orderPayment.recheckCheckout(paymentId)
                        paymentConfirmed = existing.b("confirmed")
                        if (!paymentConfirmed && (existing.b("expired") || existing.b("failed"))) {
                            paymentId = ""
                            checkoutPaymentAmount = 0L
                        }
                    }
                    if (paymentId.isBlank() && !paymentConfirmed) {
                        val result = checkoutService.preparePayment(reviewed)
                        paymentId = result.s("checkout_id")
                        checkoutPaymentAmount = CashDepositRows.onlineAmount(reviewed.settlement)
                        paymentConfirmed = result.b("confirmed")
                    }
                    if (!paymentConfirmed) {
                        gateway.launch(IranKishPaymentActivity.intent(context, orderPayment.launchUri(paymentId)))
                    }
                    return@launch
                }
                val order=checkoutService.submit(reviewed, if (online) paymentId else "")
                paymentId = ""; checkoutPaymentAmount = 0L; paymentConfirmed = false
                cart=emptyList();step=0;settlement=defaultSplitSettlement(0);notes="";promoCode="";appliedOffer=null
                selected=null;selectedAddressId="";invoiceType="";optionSheet="";promoMessage=""
                onSuccess(order)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"ثبت سفارش ناموفق بود"}
            finally{submitting=false}
        }
    }
    if(loading){LoadingPane();return}
    Box(Modifier.fillMaxSize().background(Color.Transparent)) {
    LazyColumn(Modifier.fillMaxSize().background(Color.Transparent).padding(horizontal=20.dp),state=listState,contentPadding=PaddingValues(top=12.dp,bottom=(if(step==0)88.dp else 28.dp)+LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank())item {ErrorBanner(error){error=""};if(products.isEmpty())ApprovedButton("تلاش دوباره",{retry++})}
        if (revisionOrder != null) item { DesignNotice(revisionOrder.obj("customer_correction").s("reason")) }
        if(abandonedBanner!=null&&cart.isEmpty()&&restoreDraft==null)item {
            ApprovedSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                DesignNotice("سفارش تکمیل نشده دارید. می‌توانید همان سفارش را ادامه دهید.")
                ApprovedButton("ادامه همان سفارش",{
                    val restored=cartFromDraft(abandonedBanner)
                    val restoredTotal=restored.sumOf{it.unit*it.qty}.coerceAtLeast(0L)
                    cart=restored
                    step=abandonedBanner.optInt("step").coerceIn(0,3)
                    paymentId = abandonedBanner.s("online_checkout_id")
                    selectedAddressId=abandonedBanner.s("selectedAddressId");shippingMethod=abandonedBanner.s("shippingMethod")
                    invoiceType=abandonedBanner.s("invoiceType")
                    notes=abandonedBanner.s("notes")
                    promoCode=abandonedBanner.s("promoCode")
                    settlement=refreshSettlementTotals(abandonedBanner.obj("settlement"),restoredTotal)
                    val offerRaw=abandonedBanner.s("appliedOffer")
                    appliedOffer=if(offerRaw.isNotBlank())runCatching{JSONObject(offerRaw)}.getOrNull() else null
                    onClearAbandoned()
                })
            }}
        }
        item { ApprovedCheckoutProgress(step) }
        if(step==0) {
            item { ApprovedSearchBox(productQuery, {productQuery=it}, "جست‌وجوی کالا یا مدل") }
            item {Text("۱. انتخاب کالا",color=ApprovedPalette.ink,fontSize=18.sp,fontWeight=FontWeight.Bold)}
            item {ApprovedSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                DropdownField("مدل دستگاه",selected?.let {deviceModelLabel(it)}?:"انتخاب دستگاه",products.filter { productQuery.isBlank() || it.s("name").contains(productQuery,true) || it.s("manufacturer").contains(productQuery,true) },{deviceModelLabel(it)}) {selected=it;panelSwitch=""}
                selected?.let { product ->
                    Text(deviceModelLabel(product), color=ApprovedPalette.ink, fontWeight=FontWeight.Bold, fontSize=16.sp, lineHeight=26.sp)
                }
                Text("نوع قیمت",color=ApprovedPalette.muted,fontSize=12.sp)
                DesignChoices(listOf("agent" to "عامل فروش","panel" to "ثبت در پنل","serial" to "سریال آزاد"),group,{group=it})
                if (group == "panel") {
                    DropdownField("نوع خرید پنل",catalogPriceRows("panel").first {it.first==panelTier}.second,catalogPriceRows("panel"),{it.second}){panelTier=it.first}
                    if (switchLoadFailed) {
                        ApprovedButton("دریافت دوبارهٔ سوئیچ‌ها", { switchRetry++ }, secondary = true)
                    } else if (panelSwitches.isNotEmpty()) {
                        DropdownField("مدل سوئیچ", panelSwitch.takeIf { it in panelSwitches } ?: "انتخاب سوئیچ", panelSwitches, { it }) { panelSwitch = it }
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("تعداد",color=ApprovedPalette.muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick={qtyText=(qty.toLong()+1).coerceAtMost((selected?.i("stock")?:0).toLong()).toString()},enabled=selected!=null&&qty<(selected?.i("stock")?:0)) {Text("+",color=ApprovedPalette.ink)}
                    androidx.compose.foundation.text.BasicTextField(qtyText,{qtyText=asciiDigits(it).filter(Char::isDigit).take(10)},
                        modifier=Modifier.width(100.dp).heightIn(min=48.dp).semantics {contentDescription="تعداد کالا"},singleLine=true,
                        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),
                        textStyle=MaterialTheme.typography.bodyMedium.copy(color=ApprovedPalette.ink,textAlign=TextAlign.Center),
                        decorationBox={inner->Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.Center){inner()}})
                    TextButton(onClick={qtyText=(qty-1).coerceAtLeast(1).toString()}){Text("−",color=ApprovedPalette.ink)}
                }
                if(selected!=null&&qty>(selected?.i("stock")?:0))Text("تعداد از موجودی کالا بیشتر است",color=Danger,fontSize=12.sp)
                DetailLine("قیمت واحد",if(unit>0)formatMoney(unit)+" تومان" else "—")
                ApprovedButton("افزودن به سبد خرید",{
                    val product=selected?:return@ApprovedButton
                    try {
                        val selectedSwitch = panelSwitch.takeIf { group == "panel" && it in panelSwitches }.orEmpty()
                        cart=cartService.add(cart,catalogEntry(product),qty,priceKey,selectedSwitch)
                        selected=null;qtyText="1";panelSwitch="";productQuery=""
                        settlement=AgentSettlementPolicy().defaults(cart,total);error=""
                    } catch(e:IllegalArgumentException) { error=e.message?:"افزودن کالا ناموفق بود" }
                },enabled=selected!=null&&unit>0&&qty>0&&qty<=(selected?.i("stock")?:0)&&
                    (group!="panel"||(switchOptionsLoaded&&(panelSwitches.isEmpty()||panelSwitch in panelSwitches))),secondary=true)
            }}}
            item {ApprovedSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("سبد سفارش",color=ApprovedPalette.ink,fontWeight=FontWeight.Bold)
                if(cart.isEmpty())Text("هنوز کالایی به سبد اضافه نشده است.",color=ApprovedPalette.muted,fontSize=12.sp)
                cart.forEach { line ->
                    key(line.stableKey) {
                        OrderLineCard(
                            line = line,
                            onIncrease = { changeQuantity(line, (line.qty.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) },
                            onDecrease = { changeQuantity(line, line.qty - 1) },
                            onRemove = {
                                val product = products.firstOrNull { it.s("name") == line.product }
                                cart = if (product != null) cartService.remove(cart, line, catalogEntry(product))
                                    else cart.filterNot { it.stableKey == line.stableKey }
                                settlement = AgentSettlementPolicy().defaults(cart,total)
                            },
                        )
                    }
                }
                HorizontalDivider(color=Border)
                if(discount>0)DetailLine("تخفیف",formatMoney(discount)+" تومان")
                DetailLine("جمع سفارش",formatMoney(total)+" تومان")
            }}}
            item(key="order-options") {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("نوع فاکتور",color=ApprovedPalette.ink,fontWeight=FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        listOf("رسمی", "غیررسمی").forEach { choice ->
                            FilterChip(selected=invoiceType==choice,onClick={invoiceType=choice},
                                label={Text(choice)},modifier=Modifier.weight(1f),
                                leadingIcon={Icon(if(invoiceType==choice) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,null)},
                                colors=FilterChipDefaults.filterChipColors(containerColor=ApprovedPalette.surface,
                                    selectedContainerColor=ApprovedPalette.secondary,labelColor=ApprovedPalette.ink,selectedLabelColor=ApprovedPalette.accent))
                        }
                    }
                    CheckoutOptionCard("کد تخفیف",promoCode.ifBlank {"افزودن کد"},Icons.Default.LocalOffer,
                        Modifier.fillMaxWidth(),appliedOffer!=null){optionSheet="discount"}
                    CheckoutOptionCard("توضیحات",if(notes.isBlank())"افزودن یادداشت" else "یادداشت ثبت شد",Icons.Default.EditNote,
                        Modifier.fillMaxWidth(),notes.isNotBlank()){optionSheet="notes"}
                }
            }
        } else if(step==1) {
            item {Text("نحوه تسویه",color=ApprovedPalette.ink,fontWeight=FontWeight.Bold)}
            if (agentTermsLocked) item { DesignNotice("مبالغ این مرحله را شرکت تعیین کرده است. اطلاعات پرداخت و مدارک را تکمیل کنید.") }
            item {SettlementForm(api,total,invoiceType,settlement,amountsLocked=agentTermsLocked,agentReviewRequired=agentReviewRequired,onBusyChanged={evidenceBusy=it},validationAttempt=settlementAttempt){settlement=it}}
            if(!agentTermsLocked && cart.any {it.priceKey=="sales_agent"}) item {
                DesignNotice("شرایط عامل فروش: ${faNumber(BuildConfig.AGENT_CASH_PERCENT)}٪ نقد و ${faNumber(100-BuildConfig.AGENT_CASH_PERCENT)}٪ چک. تغییر شرایط فقط پس از بررسی مدیر قابل تأیید است.")
                if(AgentSettlementPolicy().changed(cart,total,settlement)) DesignField("علت درخواست تغییر شرایط",settlement.s("agent_change_reason"),{settlement=JSONObject(settlement.toString()).put("agent_change_reason",it.take(BuildConfig.REQUEST_NOTE_LIMIT))},"درخواست برای مدیر ارسال می‌شود",singleLine=false,
                    fieldError=if(settlementAttempt>0 && settlement.s("agent_change_reason").isBlank()) "علت تغییر روش تسویه را وارد کنید." else "")
            }
            if(settlementChoiceError.isNotBlank()) item { Text(settlementChoiceError,color=Danger) }
            item {ApprovedButton("ادامه و ثبت تسویه",{
                val draft = checkout()
                val issue = checkoutService.validation(draft)
                error = ""
                settlementChoiceError = ""
                when {
                    settlementAllocated(draft.settlement) != total -> settlementDialog = "جمع مبلغ‌های تسویه باید برابر مبلغ فاکتور باشد."
                    issue.isBlank() -> step = 2
                    issue == CashDepositRows.error(draft.settlement) || issue == SettlementProofRules.checkError(draft.settlement) ||
                        issue.startsWith("علت درخواست") || issue.startsWith("شرایط درخواست") -> settlementAttempt++
                    else -> settlementChoiceError = issue
                }
            },enabled=!evidenceBusy,modifier=Modifier.fillMaxWidth())}
            if (!agentTermsLocked) item {ApprovedButton("بازگشت و ویرایش",{step=0},secondary=true)}
        } else if(step==2) {
            item {
                CheckoutAddressSection(api,customer,addresses,selectedAddressId,{selectedAddressId=it},{addresses=it})
                if(shippingAttempted && addressText.isBlank()) Text("نشانی تحویل را انتخاب کنید.",color=Danger)
            }
            item {DesignField("گیرنده",receiver,{},"",enabled=false)}
            item {
                DropdownField("روش ارسال",shippingMethod.ifBlank {"انتخاب روش ارسال"},shippingMethods,{it}){shippingMethod=it}
                if(shippingAttempted && shippingMethod.isBlank()) Text("روش ارسال را انتخاب کنید.",color=Danger)
            }
            item {ApprovedButton("مشاهده سبد و ثبت نهایی",{
                shippingAttempted=true
                if(addressText.isNotBlank() && shippingMethod.isNotBlank())step=3
            })}
            item {ApprovedButton("بازگشت به تسویه",{step=1},secondary=true)}
        } else {
            val online = CashDepositRows.requiresOnline(settlement)
            val showPaymentSuccess = online && paymentConfirmed
            val submitLabel = when {
                submitting -> "در حال پردازش…"
                online && !paymentConfirmed -> "پرداخت آنلاین"
                revisionOrder != null -> "ارسال سفارش اصلاح‌شده"
                agentReviewRequired -> "ارسال برای بررسی تسویه"
                else -> "تأیید و ثبت نهایی"
            }
            if (showPaymentSuccess) {
                item(key = "checkout-payment-success") {
                    PaymentSuccessCard(checkoutPaymentAmount) {
                        ApprovedButton(submitLabel, { submit() }, enabled = !submitting && cart.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth())
                        if (paymentId.isNotBlank() && error.isNotBlank()) {
                            ApprovedButton("برگشت مبلغ به کیف پول", { confirmWalletReturn = true },
                                enabled = !submitting, secondary = true)
                        }
                    }
                }
            }
            item {DesignNotice("سفارش را نهایی می‌کنید؟\n\nمدل، تعداد، نشانی و روش تسویه را بررسی کنید. پس از ثبت، سفارش در انتظار تأیید شرکت قرار می‌گیرد و نتیجه در مراحل سفارش نمایش داده می‌شود.")}
            item {ApprovedSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("بررسی سفارش",color=ApprovedPalette.ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
                DetailLine("نوع فاکتور",invoiceType)
                cart.forEach { line ->
                    key(line.stableKey) { OrderLineCard(line) }
                }
                DetailLine("تسویه",settlementSummaryLabel(refreshSettlementTotals(settlement,total)))
                if(settlementRemaining(settlement,total)>0L)DetailLine("مانده فاکتور",formatMoney(settlementRemaining(settlement,total))+" تومان")
                HorizontalDivider(color=Border)
                if(discount>0)DetailLine("تخفیف",formatMoney(discount)+" تومان")
                DetailLine("جمع سفارش",formatMoney(total)+" تومان")
                if(appliedOffer!=null)DetailLine("کد تخفیف",appliedOffer!!.s("promo_code"))
                if(notes.isNotBlank())Text("توضیحات: "+notes,color=ApprovedPalette.ink,fontSize=14.sp)
                Text("گیرنده: "+receiver,color=ApprovedPalette.ink,fontSize=14.sp)
                Text(addressText,color=ApprovedPalette.ink,fontSize=14.sp)
                DetailLine("روش ارسال",shippingMethod)
            }}}
            PaymentProofSelection.active(settlement, settlementPrimaryMethod(settlement)).forEach { (kind, files) ->
                item(key = "review-$kind") {
                    SelectedProofReview(
                        if (kind == "cash") "رسید" else "چک", files,
                        onNote = { index, note -> settlement = PaymentProofSelection.note(settlement, kind, index, note) },
                        onRemove = if (kind == "cash" && settlement.has(CashDepositRows.field)) null else { index ->
                            settlement = JSONObject(settlement.toString()).also { next ->
                                PaymentProofSelection.set(next, kind, files.filterIndexed { i, _ -> i != index }.map { it.data })
                            }
                        },
                        enabled = !submitting && paymentId.isBlank() && !paymentConfirmed,
                    )
                }
            }
            if (online && paymentId.isNotBlank() && !paymentConfirmed) item {
                ApprovedButton("بررسی نتیجه پرداخت", { updatePayment() }, enabled = !submitting, secondary = true)
            }
            if (!showPaymentSuccess) item {
                ApprovedButton(submitLabel, { submit() }, enabled = !submitting && cart.isNotEmpty())
            }
            item { ApprovedButton("بازگشت و ویرایش", { editOrder(0) }, secondary = true, enabled = !submitting && !paymentConfirmed) }
        }
    }
    if (step == 0) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom=LocalPortalBottomPadding.current)
            .semantics {contentDescription="خلاصه ثابت سفارش"},
            color=ApprovedPalette.surface, shadowElevation=4.dp) {
            ApprovedCheckoutSummary(total, cart.isNotEmpty()) {
                if(invoiceType.isBlank()) error="نوع فاکتور را انتخاب کنید" else {error="";step=1}
            }
        }
    }
    }
    if (confirmWalletReturn) {
        AlertDialog(
            onDismissRequest = { confirmWalletReturn = false },
            title = { Text("برگشت مبلغ به کیف پول") },
            text = { Text("سفارش ثبت نمی‌شود و مبلغ پرداخت‌شده به موجودی کیف پول شما اضافه می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWalletReturn = false
                    scope.launch {
                        submitting = true
                        try {
                            check(orderPayment.returnCheckoutToWallet(paymentId))
                            paymentId = ""; checkoutPaymentAmount = 0L; paymentConfirmed = false
                            error = ""
                            step = 1
                            android.widget.Toast.makeText(context, "مبلغ به کیف پول اضافه شد.", android.widget.Toast.LENGTH_LONG).show()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "برگشت مبلغ ناموفق بود."
                        } finally {
                            submitting = false
                        }
                    }
                }) { Text("تأیید برگشت مبلغ") }
            },
            dismissButton = { TextButton(onClick = { confirmWalletReturn = false }) { Text("انصراف") } },
        )
    }
    if(optionSheet.isNotBlank()) {
        ModalBottomSheet(onDismissRequest={optionSheet=""},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=AppSurface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(when(optionSheet){"discount"->"کد تخفیف";"invoice"->"نوع فاکتور";else->"توضیحات سفارش"},fontSize=20.sp,fontWeight=FontWeight.Bold,color=ApprovedPalette.ink)
                when(optionSheet) {
                    "discount"->{
                        DesignField("کد تخفیف",promoCode,{promoCode=promoNorm(it).take(24);appliedOffer=null;settlement=AgentSettlementPolicy().defaults(cart,total);promoMessage=""},"کد تخفیف را وارد کنید")
                        ApprovedButton("اعمال کد تخفیف",{applyPromo()},enabled=promoCode.isNotBlank()&&cart.isNotEmpty())
                        if(promoMessage.isNotBlank())Text(promoMessage,color=if(appliedOffer!=null)Success else Danger)
                    }
                    "invoice"->DesignChoices(listOf("غیررسمی" to "غیررسمی","رسمی" to "رسمی"),invoiceType,{invoiceType=it})
                    else->{
                        DesignField("توضیحات سفارش — اختیاری",notes,{notes=it.take(500)},"توضیحات موردنیاز برای این سفارش",singleLine=false)
                        Text(faNumber(notes.length)+" از ۵۰۰ کاراکتر",color=ApprovedPalette.muted,fontSize=12.sp)
                    }
                }
                ApprovedButton("تأیید و بستن",{optionSheet=""},secondary=true)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalCatalog(api: ApiClient,onHome:()->Unit={},onOrder:(String,String)->Unit={_,_->}) {
    var group by rememberSaveable {mutableStateOf("serial")}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf("")}
    var query by rememberSaveable {mutableStateOf("")}
    var products by remember {mutableStateOf(emptyList<JSONObject>())}
    var showFilters by rememberSaveable {mutableStateOf(false)}
    var modelFilter by rememberSaveable {mutableStateOf("همه مدل‌ها")}
    var manufacturerFilter by rememberSaveable {mutableStateOf("همه سازنده‌ها")}
    var expandAll by rememberSaveable { mutableStateOf(false) }
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
        catalogInStock(p)&&matches&&(modelFilter=="همه مدل‌ها"||p.s("name")==modelFilter)&&
            (manufacturerFilter=="همه سازنده‌ها"||maker==manufacturerFilter)
    }
    Box(Modifier.fillMaxSize().background(Color.Transparent)) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=100.dp+LocalPortalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { ApprovedSearchBox(query, {query=it}, "جست‌وجوی نام یا مدل کالا") }
        item { ApprovedButton(if(expandAll) "بستن همه قیمت‌ها" else "نمایش همه قیمت‌ها", {expandAll=!expandAll}, modifier=Modifier.fillMaxWidth(), secondary=true) }
        when {
            loading-> {
                item {Text("در حال دریافت قیمت‌ها…",color=ApprovedPalette.muted,fontSize=12.sp)}
                items(3){GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Box(Modifier.fillMaxWidth(.6f).height(24.dp).clip(RoundedCornerShape(8.dp)).background(AppSecondary))
                    repeat(3){Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp)).background(AppSecondary))}
                }}}
            }
            error.isNotBlank()->item {
                DesignState("اتصال برقرار نشد","قیمت‌های به‌روز دریافت نشد. اتصال اینترنت را بررسی و دوباره تلاش کنید.",R.drawable.design_offline)
                Text(error,color=Danger,fontSize=12.sp)
                ApprovedButton("تلاش دوباره",{retry++})
                Spacer(Modifier.height(16.dp));ApprovedButton("بازگشت به پیشخوان",onHome,secondary=true)
            }
            filtered.isEmpty()->item {
                DesignState("نتیجه‌ای پیدا نشد","نام مدل را بررسی کنید یا فیلترها را بردارید.",R.drawable.design_search_empty)
                ApprovedButton("پاک کردن جستجو و فیلترها",{query="";manufacturerFilter="همه سازنده‌ها";modelFilter="همه مدل‌ها";showFilters=false},secondary=true)
            }
            else->{
                val date=products.map {it.s("price_updated_at")}.filter {it.isNotBlank()}.maxOrNull()
                if(date!=null)item {Text("آخرین به‌روزرسانی: "+formatDateFa(date),color=ApprovedPalette.muted,fontSize=12.sp)}
                items(filtered,key={it.s("name")}) {p->ApprovedPriceCard(p,group,expandAll){onOrder(p.s("name"),group)}}
            }
        }
    }
    FloatingActionButton(onClick={showFilters=true},modifier=Modifier.align(Alignment.BottomEnd)
        .padding(end=20.dp,bottom=20.dp+LocalPortalBottomPadding.current),containerColor=Navy,contentColor=Orange) {
        Icon(Icons.Default.FilterList,contentDescription="فیلتر لیست قیمت‌ها")
    }
    }
    if(showFilters)ModalBottomSheet(onDismissRequest={showFilters=false},containerColor=AppSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("جست‌وجو و فیلتر قیمت‌ها",color=ApprovedPalette.ink,fontWeight=FontWeight.Bold,fontSize=20.sp)
            DesignField("جستجوی مدل یا شرکت سازنده",query,{query=it},"مدل یا سازنده را وارد کنید",R.drawable.design_search)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("serial" to "سریال آزاد","panel" to "پنل","agent" to "عاملین فروش").forEach { (key,label) ->
                    FilterChip(selected=group==key,onClick={group=key},label={Text(label)},
                        modifier=Modifier.weight(1f).semantics {contentDescription="دسته قیمت "+label},
                        colors=FilterChipDefaults.filterChipColors(containerColor=ApprovedPalette.surface,
                            selectedContainerColor=ApprovedPalette.accent,labelColor=ApprovedPalette.ink,selectedLabelColor=Color.White))
                }
            }

            DropdownField("مدل دستگاه",modelFilter,listOf("همه مدل‌ها")+products.map {it.s("name")}.distinct(),{shortProductName(it)}){modelFilter=it}
            DropdownField("شرکت سازنده",manufacturerFilter,listOf("همه سازنده‌ها")+products.map {it.s("manufacturer").ifBlank {"بدون سازنده"}}.distinct(),{it}){manufacturerFilter=it}
            ApprovedButton("نمایش نتایج",{showFilters=false})
            ApprovedButton("پاک کردن فیلترها",{query="";manufacturerFilter="همه سازنده‌ها";modelFilter="همه مدل‌ها"},secondary=true)
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
private fun PortalProfile(api:ApiClient,me:JSONObject,onUpdated:(JSONObject)->Unit,onPhotoUpdated:(String)->Unit,onLogout:()->Unit,onProforma:()->Unit,onNavigate:(String)->Unit,onSectionChanged:(String)->Unit={},onBusyChanged:(Boolean)->Unit={},backRequest:Int=0) {
    val initial=me.obj("customer")
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var view by remember {mutableStateOf("profile")}
        var name by rememberSaveable(me.toString()){mutableStateOf(initial.s("name"))}
    var company by rememberSaveable(me.toString()){mutableStateOf(initial.s("company"))}

    val alternate = me.s("alternate_mobile")
    val postalCode = initial.s("postal_code")
    var editAttempted by rememberSaveable { mutableStateOf(false) }
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var saved by remember {mutableStateOf("")}
    LaunchedEffect(saving){onBusyChanged(saving)}
    DisposableEffect(Unit){onDispose{onBusyChanged(false)}}
    val photoKey="photo_uri:"+me.s("username")
    val prefs=remember(context){context.getSharedPreferences("ghadir_profile",android.content.Context.MODE_PRIVATE)}
    var photoUri by remember(me.s("username")){mutableStateOf(prefs.getString(photoKey,"")?:"")}
    fun savePhoto(uri:String){photoUri=uri;prefs.edit().putString(photoKey,uri).apply();onPhotoUpdated(uri);view="services"}
    fun parentView()=when(view){"edit","photo","deleted"->"services";"password","users"->"settings";else->"profile"}
    LaunchedEffect(view){onSectionChanged(when(view){
        "services"->"پروفایل من";"settings"->"تنظیمات";"password"->"تغییر رمز ورود";"users"->"دسترسی کاربران";"wallet"->"کیف پول و اعتبار"
        "edit"->"ویرایش اطلاعات مشتری";"photo"->"تصویر پروفایل";"deleted"->"تصویر پروفایل";else->"پروفایل"})}
    var handledBackRequest by rememberSaveable {mutableIntStateOf(backRequest)}
    LaunchedEffect(backRequest){if(backRequest!=handledBackRequest&&!saving)view=parentView();handledBackRequest=backRequest}
    BackHandler(view!="profile"){if(!saving)view=parentView()}
    val isOverview = view == "profile"
    val navigationPadding = LocalPortalBottomPadding.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = if (isOverview) 12.dp else 28.dp + navigationPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if(error.isNotBlank()&&view!="edit")item {ErrorBanner(error){error=""}}
            if(saved.isNotBlank()&&view in setOf("profile","services","settings"))item {DesignNotice(saved)}
            when(view) {
                "services"->{
                    item {Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Navy,Color(0xFF213E61)))).padding(18.dp)) {
                        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            ProfilePhoto(photoUri,56.dp)
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(initial.s("name").ifBlank {me.s("username")},textAlign=TextAlign.Center,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                                Text("حساب همکاری • "+faDigits(initial.s("code")),color=Color.White.copy(alpha=.75f),fontSize=12.sp)
                            }
                        }
                    }}
                    item {ProfileServiceTile("ویرایش اطلاعات مشتری","مشخصات فروشگاه و اطلاعات بانکی",Icons.Default.ManageAccounts,Modifier.fillMaxWidth(),wide=true){view="edit"}}
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ProfileServiceTile("آدرس‌های منتخب","نشانی‌های ذخیره‌شده",Icons.Default.LocationOn,Modifier.weight(1f)){onNavigate("addresses")}
                        ProfileServiceTile("فاکتورهای من","مشاهده و دریافت PDF",Icons.Default.ReceiptLong,Modifier.weight(1f)){onProforma()}
                    }}
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ProfileServiceTile("سریال‌های من","شناسهٔ دستگاه‌ها",Icons.Default.QrCode2,Modifier.weight(1f)){onNavigate("serials")}
                        ProfileServiceTile("آفرهای من","طرح‌ها و پیشنهادها",Icons.Default.LocalOffer,Modifier.weight(1f)){onNavigate("offers")}
                    }}
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ProfileServiceTile("تصویر پروفایل","انتخاب یا تغییر تصویر",Icons.Default.PhotoCamera,Modifier.weight(1f)){view="photo"}
                        ProfileServiceTile("گزارش خرید","مرور سفارش‌های شما",Icons.Default.BarChart,Modifier.weight(1f)){onNavigate("summary")}
                    }}
                }
                "settings"->{
                    item {GhadirButton("تغییر رمز ورود",{view="password"},secondary=true)}
                    item {GhadirButton("دسترسی کاربران",{view="users"},secondary=true)}
                    item {BiometricPreference(me.s("username"))}
                    item {Text("نسخه "+BuildConfig.VERSION_NAME,color=Muted,fontSize=12.sp)}
                }
                "password"->{item {AccountPasswordForm(api,me.s("username"),onChanged={saved="رمز ورود با موفقیت تغییر کرد.";view="settings"},onBusyChanged={saving=it})}}
                "users"-> item { AccountUsersForm(api, onBusyChanged={saving=it}) }
                "wallet" -> item { CustomerWallet(api, onEditBank={view="edit"}) }
                "edit"->{
                    item {DesignField("نام و نام خانوادگی",name,{name=it;saved="";error=""},
                        fieldError=if(editAttempted&&name.trim().length<3)"نام و نام خانوادگی را کامل وارد کنید." else "")}
                    item {DesignField("نام شرکت / فروشگاه",company,{company=it;saved=""})}
                    item {DesignField("شماره همراه",faDigits(me.s("username")),{},enabled=false)}
                    item { AccountBankForm(api, onBusyChanged={saving=it}) }
                    if(error.isNotBlank()) item { Text(error,color=Danger) }
                    item {GhadirButton(if(saving)"در حال ذخیره…" else "ثبت تغییرات",{
                        editAttempted=true
                        if(name.trim().length<3)return@GhadirButton
                        scope.launch {
                            saving=true;error="";saved=""
                            try {
                                val body=JSONObject().put("name",name.trim()).put("company",company.trim()).put("province",initial.s("province"))
                                    .put("city",initial.s("city")).put("address",initial.s("address")).put("postal_code",postalCode).put("alternate_mobile",alternate)
                                val result=api.post("/api/profile/update",body) as JSONObject
                                onUpdated(result);view="services";saved="اطلاعات پروفایل با موفقیت به‌روزرسانی شد."
                            }catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(e:Exception){error=e.message?:"ذخیره پروفایل ناموفق بود"}finally{saving=false}
                        }
                    },enabled=!saving)}

                }
                "photo"->{
                    item {Column(Modifier.fillMaxWidth().padding(top=28.dp,bottom=28.dp+LocalPortalBottomPadding.current),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                        ProfilePhoto(photoUri,112.dp);Text(name,color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    }}
                    item {DesignNotice("انتخاب تصویر\n\nاز دوربین یا گالری عکس انتخاب کنید. در صورت حذف، لوگوی اصلی شرکت نمایش داده می‌شود.")}
                    item {ImageUploadButton { uris -> uris.firstOrNull()?.let {savePhoto(it.toString())} }}
                    item {GhadirButton("حذف عکس و بازگشت به لوگو",{
                        photoUri="";prefs.edit().remove(photoKey).apply();onPhotoUpdated("");view="deleted"
                    },secondary=true,enabled=photoUri.isNotBlank())}
                    item {GhadirButton("انصراف",{view="services"},secondary=true)}
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
                    item {GhadirButton("بازگشت به پروفایل",{view="services"},secondary=true)}
                }
                else->{
                    item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
                            ProfilePhoto(photoUri)
                            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){
                                Text(initial.s("name").ifBlank {me.s("username")},textAlign=TextAlign.Center,color=Ink,fontSize=24.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold)
                                Text(initial.s("company")+" • کد همکار "+faDigits(initial.s("code")),textAlign=TextAlign.Center,color=Muted,fontSize=12.sp)
                            }
                        }
                        HorizontalDivider(color=Border)
                        Text("شماره همراه: "+faDigits(me.s("username")),color=Ink,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                    }}}
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ProfileServiceTile("پروفایل من","اطلاعات و خدمات مشتری",Icons.Default.PersonOutline,Modifier.weight(1f)){view="services"}
                        ProfileServiceTile("تنظیمات","امنیت و اطلاعات تماس",Icons.Default.Settings,Modifier.weight(1f)){view="settings"}
                    }}
                    item {ProfileServiceTile("کیف پول و اعتبار","موجودی، سقف اعتبار و گردش پرداخت",Icons.Default.ReceiptLong,Modifier.fillMaxWidth(),wide=true){view="wallet"}}
                    item {ProfileServiceTile("تماس با پشتیبانی ۰۲۱۷۷۲۴۷۰۷۰","همراه شما در خرید و خدمات",Icons.Default.HeadsetMic,Modifier.fillMaxWidth(),wide=true) {
                        try{context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL,Uri.parse("tel:02177247070")))}
                        catch(_:Exception){error="برنامه تماس روی این دستگاه در دسترس نیست"}
                    }}
                    item {ProfileServiceTile("خروج از حساب","پایان نشست فعلی",Icons.Default.Logout,Modifier.fillMaxWidth(),wide=true,onClick=onLogout)}
                }
            }
        }
        if (isOverview) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = navigationPadding),
                contentAlignment = Alignment.Center,
            ) {
                PortalPrivacyLink()
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
                painter = painterResource(R.drawable.brand_logo),
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
                DesignGlyph(if(title=="نشانی تحویل"||title=="آدرس تحویل")R.drawable.design_map_pin else R.drawable.design_chevron,
                    null,Modifier.size(28.dp),tint=androidx.compose.ui.graphics.ColorFilter.tint(Muted))
                Text(value,color=Ink,fontSize=14.sp,lineHeight=23.sp,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded=open,onDismissRequest={open=false},modifier=Modifier.fillMaxWidth(.86f)) {
                options.forEach {option->DropdownMenuItem(text={Text(display(option))},onClick={onSelect(option);open=false})}
            }
        }
    }
}

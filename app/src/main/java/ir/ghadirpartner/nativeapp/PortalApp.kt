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
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    NavItem("profile", "حساب", Icons.Default.AccountCircle)
)

@Composable
fun PortalApp(api: ApiClient, me: JSONObject, onLogout: () -> Unit) {
    val context = LocalContext.current
    val profilePrefs = remember { context.getSharedPreferences("ghadir_profile", android.content.Context.MODE_PRIVATE) }
    var profilePhotoUri by remember { mutableStateOf(profilePrefs.getString("photo_uri", "") ?: "") }
    var screen by remember { mutableStateOf("home") }
    var selectedOrder by remember { mutableStateOf<JSONObject?>(null) }
    var currentMe by remember { mutableStateOf(JSONObject(me.toString())) }
    val customerName = currentMe.obj("customer").s("name").ifBlank { currentMe.s("username") }

    BackHandler(enabled = screen != "home") {
        when (screen) {
            "order-detail" -> { selectedOrder = null; screen = "orders" }
            else -> { selectedOrder = null; screen = "home" }
        }
    }

    val backdrop = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var contentPosition by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val titles = mapOf("home" to "پیشخوان", "catalog" to "قیمت و موجودی", "new" to "ثبت سفارش", "orders" to "سفارش‌های من", "profile" to "حساب کاربری", "order-detail" to "جزئیات سفارش")
    Scaffold(
        containerColor = Canvas,
        topBar = { BrandHeader(titles[screen] ?: "قدیر پارتنر", "قدیر پارتنر", profilePhotoUri) }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.fillMaxSize()
                .onGloballyPositioned { contentPosition = it.positionInRoot() }
                .drawWithContent { backdrop.record { this@drawWithContent.drawContent() }; drawLayer(backdrop) }
            ) {
            Crossfade(targetState = screen, label = "portal-nav") { target ->
                when (target) {
                    "home" -> PortalHome(
                        api,
                        navigate = { screen = it },
                        openOrder = { selectedOrder = it; screen = "order-detail" }
                    )
                    "orders" -> PortalOrders(api) { selectedOrder = it; screen = "order-detail" }
                    "new" -> PortalNewOrder(api) { createdOrder ->
                        selectedOrder = createdOrder
                        screen = "order-detail"
                    }
                    "catalog" -> PortalCatalog(api)
                    "order-detail" -> selectedOrder?.let { order ->
                        PortalOrderDetails(api, order, onBack = { screen = "orders" })
                    } ?: PortalOrders(api) { selectedOrder = it; screen = "order-detail" }
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
                        onLogout = onLogout
                    )
                }
            }
            }
            if (screen != "order-detail") {
                FigmaBottomBar(portalNav, screen, { selectedOrder = null; screen = it },
                    Modifier.align(Alignment.BottomCenter), backdrop, contentPosition)
            }
        }
    }
}


private fun promoNorm(v: String): String = v.replace(Regex("\\s+"), "").trim().uppercase()

private fun offerDiscountTextNative(o: JSONObject): String {
    val type = o.s("discount_type")
    val value = o.l("discount_value")
    return when {
        type == "percent" && value > 0 -> "${faNumber(value)}٪ تخفیف"
        type == "amount" && value > 0 -> "${formatMoney(value)} تومان تخفیف"
        else -> "پیشنهاد ویژه"
    }
}

@Composable
private fun PortalHome(api: ApiClient, navigate: (String) -> Unit, openOrder: (JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf(emptyList<JSONObject>()) }
    var offers by remember { mutableStateOf(emptyList<JSONObject>()) }
    var stats by remember { mutableStateOf(JSONObject()) }

    fun refresh() {
        scope.launch {
            loading = true; error = ""
            try {
                val loaded = coroutineScope {
                    val ordersReq = async { (api.get("/api/orders") as JSONArray).objects().sortedByDescending { it.i("id") } }
                    val offersReq = async { (api.get("/api/offers") as JSONArray).objects().filterNot { it.b("used_by_customer") } }
                    val statsReq = async { api.get("/api/reports/customer-summary") as JSONObject }
                    Triple(ordersReq.await(), offersReq.await(), statsReq.await())
                }
                orders = loaded.first
                offers = loaded.second
                stats = loaded.third
            } catch (e: Exception) { error = e.message ?: "خطا در دریافت اطلاعات" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    if (loading) { LoadingPane(); return }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { ErrorBanner(error) { error = "" } }
        if (offers.isNotEmpty()) {
            item { SectionTitle("طرح‌ها و آفرهای فعال") }
            items(offers.take(4), key = { "offer-" + it.i("id") }) { o ->
                Surface(shape = RoundedCornerShape(20.dp), color = OrangeSoft, border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (o.s("promo_code").isNotBlank()) {
                                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFEEF4FB)) {
                                    Text(o.s("promo_code"), color = NavySoft, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(o.s("title"), color = NavyDeep, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                Text(offerDiscountTextNative(o), color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                        if (o.s("description").isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(o.s("description"), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Start) }
                        if (o.s("end_date").isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("اعتبار تا ${formatDateFa(o.s("end_date"))}", color = Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
        item {
            PortalSummaryHero(
                amount = stats.obj("month").l("amount"),
                orders = stats.obj("month").i("orders"),
                qty = stats.obj("month").i("qty")
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MiniAction("سفارش جدید", Icons.Default.AddShoppingCart, Modifier.weight(1f)) { navigate("new") }
                MiniAction("لیست قیمت و موجودی", Icons.Default.LocalOffer, Modifier.weight(1f)) { navigate("catalog") }
                MiniAction("سفارش‌ها", Icons.Default.ReceiptLong, Modifier.weight(1f)) { navigate("orders") }
            }
        }
        item { SectionTitle("آخرین سفارش‌ها", "مشاهده همه") { navigate("orders") } }
        if (orders.isEmpty()) item { EmptyState("هنوز سفارشی ندارید", "از بخش سفارش جدید، اولین سفارش را ثبت کنید.") }
        items(orders.take(4), key = { it.i("id") }) { o ->
            OrderCard(
                number = o.s("number"), customer = "", status = o.s("status"), payment = o.s("payment_status"),
                amount = if (o.l("approved_total") > 0) o.l("approved_total") else o.l("estimated_total"),
                date = o.s("created_at")
            ) { openOrder(o) }
        }
        item { SectionTitle("گزارش خرید") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile("۷ روز", faNumber(stats.obj("week").i("orders")), "سفارش", Modifier.weight(1f))
                StatTile("۳۰ روز", faNumber(stats.obj("month").i("orders")), "سفارش", Modifier.weight(1f))
                StatTile("۹۰ روز", faNumber(stats.obj("three_months").i("orders")), "سفارش", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PortalSummaryHero(amount: Long, orders: Int, qty: Int) {
    Surface(color=Color.White, shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("خرید ۳۰ روز اخیر", color=Muted, fontSize=14.sp)
            Text("${formatMoney(amount)} تومان", color=Navy, fontSize=24.sp, fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                StatusPill("${faNumber(orders)} سفارش")
                StatusPill("${faNumber(qty)} قلم")
            }
        }
    }
}

@Composable
private fun MiniAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
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
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = NavyDeep, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text(caption, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PortalOrders(api: ApiClient, onOpen: (JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf(emptyList<JSONObject>()) }

    fun refresh() {
        scope.launch {
            loading = true; error = ""
            try { orders = (api.get("/api/orders") as JSONArray).objects().sortedByDescending { it.i("id") } }
            catch (e: Exception) { error = e.message ?: "خطا" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    if (loading) { LoadingPane(); return }

    val filtered = orders.filter {
        query.isBlank() || listOf(it.s("number"), it.s("status"), it.s("payment_status")).any { v -> v.contains(query, true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SearchBox(query, { query = it }, "جستجو در شماره یا وضعیت سفارش")
        Spacer(Modifier.height(9.dp)); ErrorBanner(error) { error = "" }
        Text("${faNumber(filtered.size)} سفارش", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        Spacer(Modifier.height(5.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 112.dp)) {
            if (filtered.isEmpty()) item { EmptyState("سفارشی پیدا نشد", "فیلتر یا عبارت جستجو را تغییر دهید.") }
            items(filtered, key = { it.i("id") }) { o ->
                OrderCard(
                    o.s("number"), "", o.s("status"), o.s("payment_status"),
                    if (o.l("approved_total") > 0) o.l("approved_total") else o.l("estimated_total"), o.s("created_at")
                ) { onOpen(o) }
            }
        }
    }
}

@Composable
private fun PortalOrderDetails(api: ApiClient, order: JSONObject, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var pdfDownloading by remember { mutableStateOf(false) }
    var cancelDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val total = if (order.l("approved_total") > 0) order.l("approved_total") else order.l("estimated_total")

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "بازگشت", tint = NavyDeep) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("جزئیات سفارش", color = NavyDeep, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text(faDigits(order.s("number")), color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { ErrorBanner(error) { error = "" } }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailLine("وضعیت سفارش", order.s("status").ifBlank { "-" })
                    DetailLine("وضعیت تسویه", order.s("payment_status").ifBlank { "-" })
                    DetailLine("روش پرداخت درخواستی", order.s("requested_payment_method_label").ifBlank { "-" })
                    DetailLine("نوع فاکتور", order.s("invoice_type").ifBlank { "-" })
                    DetailLine("تاریخ ثبت", formatDateFa(order.s("created_at")))
                    if (order.s("updated_at").isNotBlank()) DetailLine("آخرین تغییر", formatDateFa(order.s("updated_at")))
                    if (total > 0) DetailLine("مبلغ سفارش", "${formatMoney(total)} تومان", highlight = true)
                }
            }
        }
        if (order.s("shipping_type").isNotBlank() || order.s("tracking_code").isNotBlank()) item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEAF2FF)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                    Text("اطلاعات ارسال", color = NavyDeep, fontWeight = FontWeight.Black)
                    if (order.s("shipping_type").isNotBlank()) Text("روش ارسال: ${order.s("shipping_type")}", color = NavySoft, fontSize = 12.sp)
                    if (order.s("tracking_code").isNotBlank()) Text("کد مرسوله: ${order.s("tracking_code")}", color = Color(0xFF2457A6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SectionTitle("اقلام سفارش") }
        items(order.arr("items").objects(), key = { it.s("product") }) { item ->
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
                    Text(shortProductName(item.s("product")), color = NavyDeep, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("تعداد: ${faNumber(item.i("qty"))} • ${item.s("price_label").ifBlank { "قیمت ثبت نشده" }}", color = Muted, fontSize = 12.sp)
                    if (item.l("unit_price") > 0) Text("قیمت واحد: ${formatMoney(item.l("unit_price"))} تومان", color = NavySoft, fontSize = 12.sp)
                    if (item.l("line_total") > 0) Text("جمع: ${formatMoney(item.l("line_total"))} تومان", color = Navy, fontWeight = FontWeight.ExtraBold)
                    if (order.s("status") == "تحویل شد") {
                        Text("سریال ثبت‌شده: ${faNumber(item.arr("serials").length())}", color = Success, fontSize = 12.sp)
                    }
                }
            }
        }
        if (order.s("notes").isNotBlank()) item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF6EE)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), horizontalAlignment = Alignment.Start) {
                    Text("توضیحات", color = NavyDeep, fontWeight = FontWeight.Bold)
                    Text(order.s("notes"), color = NavySoft, fontSize = 12.sp, textAlign = TextAlign.Start)
                }
            }
        }
        if (order.s("status") == "تحویل شد") item {
            GhadirButton(
                text = if (downloading) "در حال ساخت PDF..." else "دانلود سریال‌های مشتری PDF",
                enabled = !downloading,
                onClick = {
                    scope.launch {
                        downloading = true; error = ""
                        try {
                            val where = api.saveSerialsPdf(order)
                            Toast.makeText(context, "PDF سریال‌ها ذخیره شد: $where", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) { error = e.message ?: "ساخت PDF سریال‌ها ناموفق بود" }
                        finally { downloading = false }
                    }
                }
            )
        }
        item {
            GhadirButton(
                text = if (pdfDownloading) "در حال ساخت PDF..." else "دانلود پیش‌فاکتور رسمی PDF",
                enabled = !pdfDownloading,
                onClick = {
                    scope.launch {
                        pdfDownloading = true; error = ""
                        try {
                            val where = api.saveProformaPdf(order)
                            Toast.makeText(context, "پیش‌فاکتور ذخیره شد: $where", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) { error = e.message ?: "ساخت پیش‌فاکتور ناموفق بود" }
                        finally { pdfDownloading = false }
                    }
                }
            )
        }
        if (order.s("status") !in listOf("ارسال شد", "تحویل شد", "لغو شد")) item {
            GhadirButton("درخواست لغو سفارش", onClick = { cancelDialog = true }, secondary = true)
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF1F5FA)) {
                Text(
                    "پشتیبانی: 09981638272",
                    color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center
                )
            }
        }
        if (order.arr("history").length() > 0) {
            item { SectionTitle("آخرین تغییرات سفارش") }
            items(order.arr("history").objects().takeLast(8).reversed()) { h ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.Start) {
                        Text(h.s("action"), color = NavyDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start)
                        Text("${formatDateFa(h.s("at"))} • ${h.s("by")}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (cancelDialog) {
        var reason by remember { mutableStateOf("افزودن یا کاهش محصول") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { cancelDialog = false },
            title = { Text("درخواست لغو سفارش") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("علت درخواست را انتخاب کنید. درخواست برای بررسی پشتیبانی ارسال می‌شود.")
                    listOf("افزودن یا کاهش محصول", "تغییر مدل", "به علت قیمت بالا").forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable { reason = r }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = reason == r, onClick = { reason = r })
                            Text(r, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("توضیحات اختیاری") }, modifier = Modifier.fillMaxWidth())
                    Text("تماس با پشتیبانی: 09981638272", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            api.post("/api/customer-order/cancel-request", JSONObject().put("id", order.i("id")).put("reason", reason).put("note", note))
                            cancelDialog = false
                            Toast.makeText(context, "درخواست لغو ثبت شد.", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) { error = e.message ?: "ثبت درخواست لغو ناموفق بود" }
                    }
                }) { Text("ثبت درخواست") }
            },
            dismissButton = { TextButton(onClick = { cancelDialog = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun DetailLine(title: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color=Muted, fontSize=12.sp, modifier=Modifier.weight(.42f), textAlign=TextAlign.Start)
        Text(value, color=Navy, fontWeight=FontWeight.Bold, fontSize=14.sp,
            modifier=Modifier.weight(.58f), textAlign=TextAlign.Start)
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
private fun PortalNewOrder(api: ApiClient, onSuccess: (JSONObject) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var products by remember { mutableStateOf(emptyList<JSONObject>()) }
    var offers by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var qtyText by remember { mutableStateOf("1") }
    var priceKey by remember { mutableStateOf("panel_cash") }
    var invoiceType by remember { mutableStateOf("غیررسمی") }
    var payment by remember { mutableStateOf("cash") }
    var notes by remember { mutableStateOf("") }
    var promoCode by remember { mutableStateOf("") }
    var promoMessage by remember { mutableStateOf("") }
    var appliedOffer by remember { mutableStateOf<JSONObject?>(null) }
    var cart by remember { mutableStateOf(emptyList<CartLine>()) }

    LaunchedEffect(Unit) {
        try {
            val loaded = coroutineScope {
                val productsReq = async { (api.get("/api/catalog") as JSONArray).objects().filter { it.b("available") } }
                val offersReq = async { (api.get("/api/offers") as JSONArray).objects().filterNot { it.b("used_by_customer") } }
                productsReq.await() to offersReq.await()
            }
            products = loaded.first
            offers = loaded.second
        } catch (e: Exception) { error = e.message ?: "خطا در لیست کالا" }
        loading = false
    }
    if (loading) { LoadingPane(); return }

    val priceLabels = listOf(
        "serial_1_50" to "سریال آزاد ۱ تا ۵۰", "serial_51_200" to "سریال آزاد ۵۱ تا ۲۰۰",
        "sales_agent" to "عامل فروش",
        "panel_cash" to "ثبت در پنل — نقد", "panel_7d" to "ثبت در پنل — هفت‌روزه", "panel_1m" to "ثبت در پنل — یک‌ماهه"
    )
    val selectedPrices = selected?.obj("prices") ?: JSONObject()
    val activePriceLabels = priceLabels.filter { selectedPrices.optLong(it.first, 0) > 0 }
    LaunchedEffect(selected?.s("name")) {
        val p = selected
        if (p != null) {
            val prices = p.obj("prices")
            priceKey = priceLabels.firstOrNull { prices.optLong(it.first, 0) > 0 }?.first ?: "panel_cash"
        }
    }
    val selectedUnitPrice = selectedPrices.optLong(priceKey, 0)
    val selectedQty = qtyText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val selectedLineTotal = selectedUnitPrice * selectedQty
    val priceDisplay: (String) -> String = { k ->
        val label = activePriceLabels.firstOrNull { it.first == k }?.second ?: k
        val amount = selectedPrices.optLong(k, 0)
        if (amount > 0) "$label — ${formatMoney(amount)} تومان" else label
    }
    val cartSubtotal = cart.sumOf { it.unit * it.qty }
    val promoDiscount = discountForOfferNative(appliedOffer, cart)
    val cartFinal = (cartSubtotal - promoDiscount).coerceAtLeast(0L)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ثبت سفارش جدید", color = NavyDeep, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Text("کالاها را اضافه کنید و روش پرداخت را همان ابتدا مشخص کنید.", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
        item { ErrorBanner(error) { error = "" } }
        item {
            DropdownField(
                title = "انتخاب محصول",
                value = selected?.let { shortProductName(it.s("name")) } ?: "انتخاب دستگاه",
                options = products.filter { it.i("stock") > 0 }.map { it.s("name") },
                display = { name -> shortProductName(name) },
                onSelect = { name -> selected = products.firstOrNull { it.s("name") == name } }
            )
        }
        if (selected != null) item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFF5E9),
                border = BorderStroke(1.dp, Color(0xFFFFC58F))
            ) {
                Text(
                    "سفارش هنوز نهایی نشده است. بعد از انتخاب دستگاه حتماً به پایین صفحه بروید و روی «ثبت نهایی سفارش» بزنید.",
                    color = Color(0xFF7A3C00),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = qtyText, onValueChange = { qtyText = it.filter(Char::isDigit).take(3) }, modifier = Modifier.weight(0.35f), singleLine = true,
                    label = { Text("تعداد") }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                )
                Box(Modifier.weight(0.65f)) {
                    DropdownField(
                        "نوع قیمت",
                        if (activePriceLabels.isEmpty()) "قیمت فعالی ثبت نشده" else priceDisplay(priceKey),
                        activePriceLabels.map { it.first },
                        { k -> priceDisplay(k) }
                    ) { priceKey = it }
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selectedUnitPrice > 0) Color(0xFFFFF5E9) else Color(0xFFF5F7FA),
                border = BorderStroke(1.dp, if (selectedUnitPrice > 0) Color(0xFFFFD2A8) else Border)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedUnitPrice > 0) "${formatMoney(selectedUnitPrice)} تومان" else "—",
                            color = if (selectedUnitPrice > 0) Orange else Muted,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("قیمت واحد انتخابی", color = NavySoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("دریافت زنده از اتوماسیون", color = Muted, fontSize = 8.sp)
                        }
                    }
                    if (selectedUnitPrice > 0 && selectedQty > 0) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatMoney(selectedLineTotal)} تومان", color = NavyDeep, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text("جمع این قلم برای ${faNumber(selectedQty)} عدد", color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (activePriceLabels.isEmpty()) {
                        Text("برای این محصول هنوز قیمت فعالی در اتوماسیون ثبت نشده است.", color = Danger, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
            }
        }
        item {
            GhadirButton("افزودن به سفارش", secondary = true, enabled = activePriceLabels.isNotEmpty(), onClick = {
                val p = selected ?: return@GhadirButton
                val q = qtyText.toIntOrNull() ?: 0
                if (q < 1 || q > p.i("stock")) { error = "تعداد انتخاب‌شده در حال حاضر قابل سفارش نیست"; return@GhadirButton }
                if (cart.any { it.product == p.s("name") }) { error = "این مدل قبلاً به سفارش اضافه شده است"; return@GhadirButton }
                val label = activePriceLabels.firstOrNull { it.first == priceKey }?.second ?: priceKey
                val unit = p.obj("prices").optLong(priceKey, 0)
                if (unit <= 0) { error = "برای نوع قیمت انتخاب‌شده مبلغ معتبری ثبت نشده است"; return@GhadirButton }
                cart = cart + CartLine(p.s("name"), q, priceKey, label, unit)
            })
        }
        if (cart.isNotEmpty()) {
            item { SectionTitle("اقلام سفارش") }
            items(cart, key = { it.product }) { line ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { cart = cart.filterNot { it.product == line.product }; promoMessage = if (appliedOffer != null) "مبلغ تخفیف با اقلام جدید دوباره محاسبه شد." else promoMessage }) { Icon(Icons.Default.DeleteOutline, null, tint = Danger) }
                        Text("${formatMoney(line.unit * line.qty)} تومان", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(shortProductName(line.product), color = NavyDeep, fontWeight = FontWeight.Bold)
                            Text("${faNumber(line.qty)} عدد • ${line.priceLabel}", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { SectionTitle("کد تخفیف") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val code = promoNorm(promoCode)
                            val match = offers.firstOrNull {
                                promoNorm(it.s("promo_code")) == code &&
                                    it.s("discount_type") in listOf("percent", "amount") &&
                                    it.l("discount_value") > 0
                            }
                            if (code.isBlank()) {
                                appliedOffer = null
                                promoMessage = "کد تخفیف را وارد کنید."
                            } else if (match == null) {
                                appliedOffer = null
                                promoMessage = "این کد برای حساب شما معتبر یا فعال نیست."
                            } else {
                                val d = discountForOfferNative(match, cart)
                                if (d <= 0) {
                                    appliedOffer = null
                                    promoMessage = "این کد برای کالاهای فعلی سفارش قابل استفاده نیست."
                                } else {
                                    appliedOffer = match
                                    promoCode = promoNorm(match.s("promo_code"))
                                    promoMessage = "${offerDiscountTextNative(match)} با موفقیت اعمال شد."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.height(54.dp)
                    ) { Text("اعمال", fontWeight = FontWeight.Black) }
                    OutlinedTextField(
                        value = promoCode,
                        onValueChange = {
                            promoCode = promoNorm(it).take(24)
                            appliedOffer = null
                            promoMessage = ""
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("کد تخفیف") },
                        placeholder = { Text("GHADIR10") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                    )
                }
                if (promoMessage.isNotBlank()) {
                    Text(
                        promoMessage,
                        color = if (appliedOffer != null) Success else Danger,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
                val visibleCodes = offers.filter {
                    it.s("promo_code").isNotBlank() &&
                        it.s("discount_type") in listOf("percent", "amount") &&
                        it.l("discount_value") > 0
                }.take(4)
                if (visibleCodes.isNotEmpty()) {
                    Text("کدهای فعال شما", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    visibleCodes.forEach { o ->
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = Color(0xFFF6F9FC),
                            modifier = Modifier.fillMaxWidth().clickable {
                                promoCode = promoNorm(o.s("promo_code"))
                                val d = discountForOfferNative(o, cart)
                                if (d > 0) {
                                    appliedOffer = o
                                    promoMessage = "${offerDiscountTextNative(o)} با موفقیت اعمال شد."
                                } else {
                                    appliedOffer = null
                                    promoMessage = "ابتدا کالای مشمول این طرح را به سفارش اضافه کنید."
                                }
                            }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(o.s("promo_code"), color = NavySoft, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(o.s("title"), color = NavyDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(offerDiscountTextNative(o), color = Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { DropdownField("نوع فاکتور", invoiceType, listOf("غیررسمی", "رسمی"), { it }) { invoiceType = it } }
        item {
            DropdownField(
                "روش پرداخت",
                when(payment) { "cash" -> "نقد"; "check" -> "چک"; "credit" -> "اعتباری"; else -> "انتخاب" },
                listOf("cash", "check", "credit"),
                { k -> when(k) { "cash" -> "نقد"; "check" -> "چک"; else -> "اعتباری" } },
                { payment = it }
            )
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF1F3F6)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = Muted); Spacer(Modifier.width(8.dp))
                    Text("روش تسویه انتخابی همراه سفارش ثبت می‌شود و پس از بررسی واحد فروش تأیید خواهد شد.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                }
            }
        }
        item {
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(500) }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                label = { Text("توضیحات سفارش – اختیاری") }, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
            )
        }
        item {
            HeroCard(
                if (promoDiscount > 0) "جمع سفارش پس از تخفیف" else "جمع تقریبی سفارش",
                "${formatMoney(cartFinal)} تومان",
                if (promoDiscount > 0) "قبل تخفیف ${formatMoney(cartSubtotal)} تومان • تخفیف ${formatMoney(promoDiscount)} تومان" else "مبلغ نهایی پس از بررسی واحد فروش تأیید می‌شود",
                Icons.Default.AccountBalanceWallet
            )
        }
        item {
            GhadirButton(
                if (submitting) "در حال ثبت..." else "ثبت نهایی سفارش", enabled = cart.isNotEmpty() && !submitting,
                onClick = {
                    scope.launch {
                        submitting = true; error = ""
                        try {
                            val itemsJson = JSONArray()
                            cart.forEach { line -> itemsJson.put(JSONObject().put("product", line.product).put("qty", line.qty).put("price_key", line.priceKey)) }
                            if (promoCode.isNotBlank() && appliedOffer == null) {
                                error = "کد تخفیف را ابتدا اعمال و بررسی کنید"
                                submitting = false
                                return@launch
                            }
                            if (appliedOffer != null && promoDiscount <= 0) {
                                error = "کد تخفیف برای اقلام فعلی قابل استفاده نیست"
                                submitting = false
                                return@launch
                            }
                            val body = JSONObject()
                                .put("invoice_type", invoiceType)
                                .put("requested_payment_method", payment)
                                .put("notes", notes)
                                .put("promo_code", appliedOffer?.s("promo_code") ?: "")
                                .put("items", itemsJson)
                            val createdOrder = api.post("/api/orders/add", body) as JSONObject
                            cart = emptyList()
                            promoCode = ""
                            promoMessage = ""
                            appliedOffer = null
                            onSuccess(createdOrder)
                        } catch (e: Exception) { error = e.message ?: "ثبت سفارش ناموفق بود" }
                        finally { submitting = false }
                    }
                }
            )
        }
    }
}

@Composable
private fun PortalCatalog(api: ApiClient) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf(emptyList<JSONObject>()) }
    var showFilters by remember { mutableStateOf(false) }
    var modelFilter by remember { mutableStateOf("همه مدل‌ها") }
    var manufacturerFilter by remember { mutableStateOf("همه سازنده‌ها") }
    var stockFilter by remember { mutableStateOf("همه") }

    LaunchedEffect(Unit) {
        try { products = (api.get("/api/catalog") as JSONArray).objects() }
        catch (e: Exception) { error = e.message ?: "خطا در دریافت لیست قیمت" }
        loading = false
    }
    if (loading) { LoadingPane(); return }

    val modelOptions = listOf("همه مدل‌ها") + products.map { it.s("name") }.filter { it.isNotBlank() }.distinct().sorted()
    val manufacturerOptions = listOf("همه سازنده‌ها") + products.map { it.s("manufacturer").ifBlank { "بدون سازنده" } }.distinct().sorted()
    fun isInStock(p: JSONObject): Boolean = p.b("available") && p.i("stock") > 0

    val filtered = products.filter { p ->
        val manufacturer = p.s("manufacturer").ifBlank { "بدون سازنده" }
        val matchesQuery = query.isBlank() || listOf(p.s("name"), manufacturer).any { it.contains(query, true) }
        val matchesModel = modelFilter == "همه مدل‌ها" || p.s("name") == modelFilter
        val matchesManufacturer = manufacturerFilter == "همه سازنده‌ها" || manufacturer == manufacturerFilter
        val matchesStock = when (stockFilter) {
            "موجود" -> isInStock(p)
            "ناموجود" -> !isInStock(p)
            else -> true
        }
        matchesQuery && matchesModel && matchesManufacturer && matchesStock
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),
        contentPadding=PaddingValues(top=20.dp, bottom=112.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { SearchBox(query, {query=it}, "مدل یا سازنده را وارد کنید") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=stockFilter=="همه", onClick={stockFilter="همه"}, label={Text("همه مدل‌ها")})
                FilterChip(selected=stockFilter=="موجود", onClick={stockFilter=if(stockFilter=="موجود") "همه" else "موجود"}, label={Text("موجود")})
                FilterChip(selected=showFilters, onClick={showFilters=!showFilters}, label={Text("فیلترها")})
            }
        }
        if(showFilters) item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                DropdownField("مدل دستگاه", modelFilter, modelOptions, {shortProductName(it)}) {modelFilter=it}
                DropdownField("شرکت سازنده", manufacturerFilter, manufacturerOptions, {it}) {manufacturerFilter=it}
                DropdownField("وضعیت موجودی", stockFilter, listOf("همه", "موجود", "ناموجود"), {it}) {stockFilter=it}
            }
        }
        item { ErrorBanner(error) {error=""} }
        item { Text("${faNumber(filtered.size)} کالا • قیمت‌ها به تومان", color=Muted, fontSize=12.sp) }
        if(filtered.isEmpty()) item {EmptyState("کالایی پیدا نشد", "فیلترها را تغییر دهید.")}
        items(filtered, key={it.s("name")}) {p -> CatalogListRow(p)}
    }
}

@Composable
private fun CatalogListRow(p: JSONObject) {
    val prices = p.obj("prices")
    val inStock = p.b("available") && p.i("stock") > 0
    val manufacturer = p.s("manufacturer").ifBlank {"شرکت سازنده ثبت نشده"}
    val tiers = listOf(
        "serial_1_50" to "آزاد • ۱ تا ۵۰", "serial_51_200" to "آزاد • ۵۱ تا ۲۰۰",
        "sales_agent" to "آزاد • عامل فروش", "panel_cash" to "پنل • نقد",
        "panel_7d" to "پنل • هفت‌روزه", "panel_1m" to "پنل • یک‌ماهه")
    Surface(color=Color.White, shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.Top, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment=Alignment.Start) {
                    Text(shortProductName(p.s("name")), color=Navy, fontSize=20.sp, fontWeight=FontWeight.Bold)
                    Text(manufacturer, color=Muted, fontSize=12.sp)
                }
                Surface(color=if(inStock) Color(0xFFE9F6EF) else Color(0xFFFDEEF0), shape=RoundedCornerShape(12.dp)) {
                    Text(if(inStock) "موجود" else "ناموجود", color=if(inStock) Success else Danger,
                        fontSize=12.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(8.dp))
                }
            }
            tiers.forEach {(key,label) ->
                val amount = prices.optLong(key,0)
                DetailLine(label, if(amount>0) "${formatMoney(amount)} تومان" else "—")
            }
            // Keep baseline credit availability and update date; never expose internal stock counts.
            DetailLine("فروش اعتباری", if(p.b("credit_available")) "دارد" else "ندارد")
            Text("آخرین بروزرسانی: ${formatDateFa(p.s("price_updated_at"))}", color=Muted, fontSize=12.sp)
        }
    }
}

@Composable
private fun PortalProfile(api: ApiClient, me: JSONObject, onUpdated: (JSONObject) -> Unit, onPhotoUpdated: (String) -> Unit, onLogout: () -> Unit) {
    val initial = me.obj("customer")
    val scope = rememberCoroutineScope()
    var name by remember(me.toString()) { mutableStateOf(initial.s("name")) }
    var company by remember(me.toString()) { mutableStateOf(initial.s("company")) }
    var province by remember(me.toString()) { mutableStateOf(initial.s("province")) }
    var city by remember(me.toString()) { mutableStateOf(initial.s("city")) }
    var address by remember(me.toString()) { mutableStateOf(initial.s("address")) }
    var alternate by remember(me.toString()) { mutableStateOf(me.s("alternate_mobile")) }
    var postalCode by remember(me.toString()) { mutableStateOf(initial.s("postal_code")) }
    val context = LocalContext.current
    val profilePrefs = remember { context.getSharedPreferences("ghadir_profile", android.content.Context.MODE_PRIVATE) }
    var photoUri by remember { mutableStateOf(profilePrefs.getString("photo_uri", "") ?: "") }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            photoUri = uri.toString()
            profilePrefs.edit().putString("photo_uri", photoUri).apply()
            onPhotoUpdated(photoUri)
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), contentPadding = PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { HeroCard("حساب کاربری", name.ifBlank { me.s("username") }, company.ifBlank { "مشتری قدیر پارتنر" }, Icons.Default.VerifiedUser, emphasis = true) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.Start) {
                        TextButton(onClick = { photoLauncher.launch(arrayOf("image/*")) }) { Text("انتخاب / تغییر عکس") }
                        if (photoUri.isNotBlank()) {
                            TextButton(onClick = {
                                try { context.contentResolver.releasePersistableUriPermission(Uri.parse(photoUri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                                photoUri = ""
                                profilePrefs.edit().remove("photo_uri").apply()
                                onPhotoUpdated("")
                            }) { Text("حذف عکس و بازگشت به لوگو", color = Danger, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("عکس پروفایل", color = NavyDeep, fontWeight = FontWeight.Bold)
                        Text(if (photoUri.isBlank()) "هنوز عکسی انتخاب نشده" else "عکس انتخاب شده", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    ProfilePhoto(photoUri)
                }
            }
        }
        item {
            val bio = remember {
                when (BiometricManager.from(context).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )) {
                    BiometricManager.BIOMETRIC_SUCCESS -> "آماده و فعال روی این گوشی"
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "اثر انگشت یا چهره در تنظیمات گوشی ثبت نشده"
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "سخت‌افزار بیومتریک روی این گوشی در دسترس نیست"
                    else -> "بیومتریک فعلاً در دسترس نیست"
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF4F7FB)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, null, tint = Orange)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("قفل بیومتریک", color = NavyDeep, fontWeight = FontWeight.Bold)
                        Text(bio, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Start)
                    }
                }
            }
        }
        item { ErrorBanner(error) { error = "" } }
        if (saved.isNotBlank()) item { Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE6F7EE)) { Text(saved, color = Success, modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Start, fontWeight = FontWeight.Bold) } }
        item { ProfileEditField("نام و نام خانوادگی", name) { name = it } }
        item { ProfileEditField("نام شرکت", company) { company = it } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { ProfileEditField("استان", province) { province = it } }
                Box(Modifier.weight(1f)) { ProfileEditField("شهر", city) { city = it } }
            }
        }
        item {
            OutlinedTextField(
                value = address, onValueChange = { address = it; saved = "" }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("آدرس") }, shape = RoundedCornerShape(17.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
            )
        }
        item {
            OutlinedTextField(
                value = me.s("username"), onValueChange = {}, modifier = Modifier.fillMaxWidth(), readOnly = true, singleLine = true,
                label = { Text("شماره ورود") }, shape = RoundedCornerShape(17.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border, disabledBorderColor = Border)
            )
        }
        item { ProfileEditField("شماره جایگزین – اختیاری", alternate) { alternate = it.filter(Char::isDigit).take(11) } }
        item { ProfileEditField("کدپستی – اختیاری", postalCode) { postalCode = it.filter(Char::isDigit).take(10) } }
        item {
            GhadirButton(if (saving) "در حال ذخیره..." else "ذخیره تغییرات پروفایل", enabled = !saving && name.trim().length >= 3, onClick = {
                scope.launch {
                    saving = true; error = ""; saved = ""
                    try {
                        val body = JSONObject().put("name", name).put("company", company).put("province", province).put("city", city).put("address", address).put("postal_code", postalCode).put("alternate_mobile", alternate)
                        val result = api.post("/api/profile/update", body) as JSONObject
                        onUpdated(result); saved = "اطلاعات پروفایل با موفقیت بروزرسانی شد."
                    } catch (e: Exception) { error = e.message ?: "ذخیره پروفایل ناموفق بود" }
                    finally { saving = false }
                }
            })
        }
        item { GhadirButton("خروج از حساب", onLogout, secondary = true) }
        item { Text("نسخه Native ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ProfilePhoto(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        if (uri.isBlank()) null else try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
    Surface(shape = CircleShape, color = Color(0xFFE8EEF5), modifier = Modifier.size(64.dp)) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "عکس پروفایل", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(
                painter = painterResource(R.drawable.ghadir_logo),
                contentDescription = "لوگوی قدیر پرداخت",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
private fun ProfileEditField(title: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(title) }, shape = RoundedCornerShape(17.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
    )
}

@Composable
private fun <T> DropdownField(title: String, value: String, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, modifier = Modifier.fillMaxWidth().clickable { open = true }, readOnly = true, singleLine = true,
            label = { Text(title) }, trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.fillMaxWidth(0.86f)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

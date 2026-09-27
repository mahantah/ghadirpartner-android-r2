package ir.ghadirpartner.nativeapp

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val automationNav = listOf(
    NavItem("home", "داشبورد", Icons.Default.SpaceDashboard),
    NavItem("orders", "سفارش‌ها", Icons.Default.ReceiptLong),
    NavItem("customers", "مشتریان", Icons.Default.Groups),
    NavItem("warehouse", "انبار", Icons.Default.Warehouse),
    NavItem("reports", "گزارش‌ها", Icons.Default.QueryStats)
)

@Composable
fun AutomationApp(api: ApiClient, me: JSONObject, onLogout: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    Scaffold(
        containerColor = Canvas,
        topBar = { BrandHeader("قدیر پارتنر", "اتوماسیون • ${me.s("username")}") },
        bottomBar = { BottomBar(automationNav, screen) { screen = it } }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Crossfade(screen, label = "automation-nav") { target ->
                when (target) {
                    "home" -> AutomationDashboard(api, me, navigate = { screen = it }, onLogout = onLogout)
                    "orders" -> AutomationOrders(api)
                    "customers" -> AutomationCustomers(api)
                    "warehouse" -> AutomationWarehouse(api)
                    else -> AutomationReports(api)
                }
            }
        }
    }
}

@Composable
private fun AutomationDashboard(api: ApiClient, me: JSONObject, navigate: (String) -> Unit, onLogout: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var orders by remember { mutableStateOf(emptyList<JSONObject>()) }
    var customers by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(Unit) {
        try {
            orders = (api.get("/api/orders") as JSONArray).objects()
            customers = (api.get("/api/customers") as JSONArray).objects()
        } catch (e: Exception) { error = e.message ?: "خطا در دریافت داشبورد" }
        loading = false
    }
    if (loading) { LoadingPane(); return }
    val waiting = orders.count { it.s("status") in listOf("ثبت شده", "در انتظار تأیید سفارش") }
    val prep = orders.count { it.s("status") in listOf("در حال آماده سازی", "آماده ارسال") }
    val shipped = orders.count { it.s("status") == "ارسال شد" }
    val unpaid = orders.count { it.s("payment_status") == "پرداخت نشده" || it.s("payment_status") == "در انتظار تأیید" }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 15.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ErrorBanner(error) { error = "" } }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(NavyDeep, Navy, Color(0xFF174472)))).padding(19.dp)) {
                    Box(Modifier.size(125.dp).align(Alignment.BottomStart).offset(x = (-35).dp, y = 52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x33FF7A1A)))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text("مرکز عملیات قدیر پارتنر", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("نمای زنده سفارش، مالی و انبار", color = Color(0xFFC7D7EA), fontSize = 11.sp)
                        Spacer(Modifier.height(17.dp))
                        Text(faNumber(waiting + prep + shipped), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text("سفارش فعال در جریان عملیات", color = Color(0xFFD7E5F7), fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                KpiCard("در انتظار", faNumber(waiting), Icons.Default.Schedule, Modifier.weight(1f)) { navigate("orders") }
                KpiCard("آماده‌سازی", faNumber(prep), Icons.Default.Inventory2, Modifier.weight(1f)) { navigate("orders") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                KpiCard("ارسال‌شده", faNumber(shipped), Icons.Default.LocalShipping, Modifier.weight(1f)) { navigate("orders") }
                KpiCard("نیاز مالی", faNumber(unpaid), Icons.Default.Payments, Modifier.weight(1f)) { navigate("orders") }
            }
        }
        item { SectionTitle("دسترسی سریع") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MiniAdminAction("سفارش‌ها", Icons.Default.ReceiptLong, Modifier.weight(1f)) { navigate("orders") }
                MiniAdminAction("مشتریان", Icons.Default.Groups, Modifier.weight(1f)) { navigate("customers") }
                MiniAdminAction("انبار", Icons.Default.Warehouse, Modifier.weight(1f)) { navigate("warehouse") }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLogout) { Text("خروج", color = Danger, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("کاربر فعال: ${me.s("username")}", color = NavyDeep, fontWeight = FontWeight.Black)
                        Text("دسترسی: ${me.arr("roles").strings().map(::roleLabel).joinToString("، ")}", color = Muted, fontSize = 10.sp)
                        Text("مشتریان فعال: ${faNumber(customers.size)}", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "admin" -> "مدیر"
    "sales" -> "فروش"
    "finance" -> "مالی"
    "prep" -> "انبار"
    "viewer" -> "مشاهده‌گر"
    else -> role
}

@Composable
private fun KpiCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.End) {
            Icon(icon, null, tint = Orange)
            Spacer(Modifier.height(9.dp))
            Text(value, color = NavyDeep, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(title, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MiniAdminAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Navy, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp)); Text(title, color = NavyDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AutomationOrders(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("همه") }
    var paymentFilter by remember { mutableStateOf("همه") }
    var orders by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }

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

    val statuses = listOf("همه") + orders.map { it.s("status") }.filter { it.isNotBlank() }.distinct()
    val payments = listOf("همه") + orders.map { it.s("payment_status") }.filter { it.isNotBlank() }.distinct()
    val filtered = orders.filter { o ->
        val matchesSearch = query.isBlank() || listOf(o.s("number"), o.s("customer_name"), o.s("status"), o.s("payment_status")).any { it.contains(query, true) }
        val matchesStatus = statusFilter == "همه" || o.s("status") == statusFilter
        val matchesPay = paymentFilter == "همه" || o.s("payment_status") == paymentFilter
        matchesSearch && matchesStatus && matchesPay
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SearchBox(query, { query = it }, "شماره سفارش یا نام مشتری")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipMenu("تسویه", paymentFilter, payments, Modifier.weight(1f)) { paymentFilter = it }
            FilterChipMenu("وضعیت", statusFilter, statuses, Modifier.weight(1f)) { statusFilter = it }
        }
        Spacer(Modifier.height(8.dp)); ErrorBanner(error) { error = "" }
        Text("${faNumber(filtered.size)} نتیجه", color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        Spacer(Modifier.height(5.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (filtered.isEmpty()) item { EmptyState("نتیجه‌ای پیدا نشد", "فیلترها را تغییر دهید.") }
            items(filtered, key = { it.i("id") }) { o ->
                OrderCard(
                    o.s("number"), o.s("customer_name"), o.s("status"), o.s("payment_status"),
                    if (o.l("approved_total") > 0) o.l("approved_total") else o.l("estimated_total"), o.s("created_at")
                ) { selected = o }
            }
        }
    }
    selected?.let { order ->
        AutomationOrderDialog(api, order, onDismiss = { selected = null }, onChanged = { selected = null; refresh() })
    }
}

@Composable
private fun FilterChipMenu(title: String, value: String, options: List<String>, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy)) {
            Text("$title: $value", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item); open = false }) }
        }
    }
}

@Composable
private fun AutomationOrderDialog(api: ApiClient, order: JSONObject, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(order.s("status")) }
    var payment by remember { mutableStateOf(order.s("payment_status")) }
    var shipping by remember { mutableStateOf(order.s("shipping_type").ifBlank { "پیک" }) }
    var tracking by remember { mutableStateOf(order.s("tracking_code")) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val statusOptions = listOf("ثبت شده", "در حال آماده سازی", "آماده ارسال", "ارسال شد", "تحویل شد")
    val payOptions = listOf("پرداخت نشده", "در انتظار تأیید", "بیعانه", "تسویه کامل", "اعتباری", "چک", "اقساطی")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("${faDigits(order.s("number"))} • ${order.s("customer_name")}", color = NavyDeep, fontWeight = FontWeight.Black, fontSize = 17.sp) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ErrorBanner(error) { error = "" } }
                item { SimpleDropdown("وضعیت سفارش", status, statusOptions) { status = it } }
                item { SimpleDropdown("وضعیت تسویه", payment, payOptions) { payment = it } }
                if (status == "ارسال شد") {
                    item { SimpleDropdown("نوع ارسال", shipping, listOf("پیک", "تیپاکس", "چاپار", "تحویل حضوری")) { shipping = it } }
                    if (shipping in listOf("تیپاکس", "چاپار")) item {
                        OutlinedTextField(
                            value = tracking, onValueChange = { tracking = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("کد مرسوله") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedBorderColor = Border)
                        )
                    }
                }
                items(order.arr("items").objects()) { item ->
                    Surface(shape = RoundedCornerShape(15.dp), color = Canvas) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), horizontalAlignment = Alignment.End) {
                            Text(shortProductName(item.s("product")), color = NavyDeep, fontWeight = FontWeight.Bold)
                            Text("${faNumber(item.i("qty"))} عدد • سریال ثبت‌شده: ${faNumber(item.arr("serials").length())}", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                item {
                    GhadirButton(if (saving) "در حال ذخیره..." else "ذخیره تغییرات", enabled = !saving, onClick = {
                        scope.launch {
                            saving = true; error = ""
                            try {
                                if (payment != order.s("payment_status")) {
                                    api.post("/api/payment", JSONObject().put("id", order.i("id")).put("status", payment))
                                }
                                if (status != order.s("status") || (status == "ارسال شد" && tracking != order.s("tracking_code"))) {
                                    val body = JSONObject().put("id", order.i("id")).put("status", status)
                                    if (status == "ارسال شد") body.put("shipping_type", shipping).put("tracking_code", tracking)
                                    api.post("/api/status", body)
                                }
                                onChanged()
                            } catch (e: Exception) { error = e.message ?: "ذخیره ناموفق بود" }
                            finally { saving = false }
                        }
                    })
                }
                item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("بستن", color = Muted) } }
            }
        },
        shape = RoundedCornerShape(28.dp), containerColor = Color.White
    )
}

@Composable
private fun SimpleDropdown(title: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep)) {
            Row(Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowDown, null); Spacer(Modifier.weight(1f)); Text("$title: $value", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false }) }
        }
    }
}

@Composable
private fun AutomationCustomers(api: ApiClient) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(Unit) {
        try { customers = (api.get("/api/customers") as JSONArray).objects() }
        catch (e: Exception) { error = e.message ?: "خطا" }
        loading = false
    }
    if (loading) { LoadingPane(); return }
    val filtered = customers.filter { c -> query.isBlank() || listOf(c.s("name"), c.s("company"), c.s("mobile"), c.s("code")).any { it.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SearchBox(query, { query = it }, "نام، شرکت، موبایل یا کد مشتری")
        Spacer(Modifier.height(8.dp)); ErrorBanner(error) { error = "" }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.i("id") }) { c ->
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Orange) }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(4f)) {
                            Text(c.s("name"), color = NavyDeep, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.s("company"), color = Muted, fontSize = 10.sp, maxLines = 1)
                            Text("${c.s("mobile")}  •  ${c.s("code")}", color = NavySoft, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationWarehouse(api: ApiClient) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var inventory by remember { mutableStateOf(emptyList<JSONObject>()) }
    LaunchedEffect(Unit) {
        try { inventory = (api.get("/api/inventory") as JSONArray).objects() }
        catch (e: Exception) { error = e.message ?: "خطا در دریافت انبار" }
        loading = false
    }
    if (loading) { LoadingPane(); return }
    val free = inventory.count { it.i("order_id") == 0 }
    val assigned = inventory.size - free
    val filtered = inventory.filter { i -> query.isBlank() || listOf(i.s("serial"), i.s("product"), i.s("carton_code"), i.s("order_number")).any { it.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard("تخصیص‌یافته", faNumber(assigned), Icons.Default.AssignmentTurnedIn, Modifier.weight(1f)) {}
            KpiCard("سریال آزاد", faNumber(free), Icons.Default.Inventory, Modifier.weight(1f)) {}
        }
        Spacer(Modifier.height(10.dp)); SearchBox(query, { query = it }, "جستجوی سریال، مدل، کارتن یا سفارش")
        Spacer(Modifier.height(8.dp)); ErrorBanner(error) { error = "" }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered.take(500), key = { it.i("id") }) { iv ->
                Surface(shape = RoundedCornerShape(17.dp), color = Color.White) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.End) {
                        Text(iv.s("serial"), color = NavyDeep, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(shortProductName(iv.s("product")), color = Muted, fontSize = 10.sp, maxLines = 1)
                        Row(Modifier.fillMaxWidth()) {
                            StatusPill(if (iv.i("order_id") == 0) "آزاد" else "${iv.s("order_number")}")
                            Spacer(Modifier.weight(1f))
                            if (iv.s("carton_code").isNotBlank()) Text(iv.s("carton_code"), color = NavySoft, fontSize = 10.sp)
                        }
                    }
                }
            }
            if (filtered.size > 500) item { Text("برای سرعت اپ فقط ۵۰۰ نتیجه اول نمایش داده شد. جستجو را دقیق‌تر کنید.", color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun AutomationReports(api: ApiClient) {
    var period by remember { mutableStateOf("monthly") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(JSONObject()) }
    LaunchedEffect(period) {
        loading = true; error = ""
        try { data = api.get("/api/reports/sales?period=$period") as JSONObject }
        catch (e: Exception) { error = e.message ?: "دسترسی به گزارش امکان‌پذیر نیست" }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("yearly" to "سالانه", "monthly" to "ماهانه", "weekly" to "هفتگی").forEach { (key, label) ->
                FilterChip(selected = period == key, onClick = { period = key }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFE4CF), selectedLabelColor = Orange))
            }
        }
        Spacer(Modifier.height(12.dp)); ErrorBanner(error) { error = "" }
        if (loading) LoadingPane("در حال محاسبه گزارش...") else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { HeroCard("فروش قطعی", "${formatMoney(data.l("total_amount"))} تومان", "${faNumber(data.i("total_orders"))} سفارش • ${faNumber(data.i("total_qty"))} قلم", Icons.Default.QueryStats, emphasis = true) }
                items(data.arr("series").objects()) { row ->
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${formatMoney(row.l("amount"))} تومان", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(row.s("label").ifBlank { row.s("period") }, color = NavyDeep, fontWeight = FontWeight.Bold)
                                Text("${faNumber(row.i("orders"))} سفارش • ${faNumber(row.i("qty"))} قلم", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

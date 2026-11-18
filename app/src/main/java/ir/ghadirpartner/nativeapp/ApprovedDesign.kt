package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Read the approved palette independently of the profile and home appearance. */
internal object ApprovedPalette {
    private fun color(light: String, dark: String): Color =
        Color(android.graphics.Color.parseColor(if (AppAppearance.dark) dark else light))
    val background get() = color(BuildConfig.DESIGN_BACKGROUND_LIGHT, BuildConfig.DESIGN_BACKGROUND_DARK)
    val surface get() = color(BuildConfig.DESIGN_SURFACE_LIGHT, BuildConfig.DESIGN_SURFACE_DARK)
    val secondary get() = color(BuildConfig.DESIGN_SECONDARY_LIGHT, BuildConfig.DESIGN_SECONDARY_DARK)
    val ink get() = color(BuildConfig.DESIGN_INK_LIGHT, BuildConfig.DESIGN_INK_DARK)
    val muted get() = color(BuildConfig.DESIGN_MUTED_LIGHT, BuildConfig.DESIGN_MUTED_DARK)
    val border get() = color(BuildConfig.DESIGN_BORDER_LIGHT, BuildConfig.DESIGN_BORDER_DARK)
    val accent get() = color(BuildConfig.DESIGN_ACCENT, BuildConfig.DESIGN_ACCENT)
}

/** Provide the approved opaque card styling inside the redesigned screens. */
@Composable
internal fun ApprovedSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = ApprovedPalette.surface, contentColor = ApprovedPalette.ink,
        border = BorderStroke(1.dp, ApprovedPalette.border), content = content)
}

/** Render a bounded primary or outlined action using the app's bundled Persian font. */
@Composable
internal fun ApprovedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = if (secondary) BorderStroke(1.dp, ApprovedPalette.border) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) ApprovedPalette.surface else ApprovedPalette.accent,
            contentColor = if (secondary) ApprovedPalette.ink else Color.White,
        )) { Text(text, fontWeight = FontWeight.Bold) }
}

/** Show the real checkout stages in their RTL progression. */
@Composable
internal fun ApprovedCheckoutProgress(step: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("کالاها", "تحویل و تسویه", "تأیید نهایی").forEachIndexed { index, title ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (index < step) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonChecked,
                    null, tint = if (index <= step) ApprovedPalette.accent else ApprovedPalette.muted)
                Text(title, color = if (index == step) ApprovedPalette.ink else ApprovedPalette.muted,
                    fontSize = 12.sp, fontWeight = if (index == step) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Expand one product into every price supplied by the authenticated catalog response. */
@Composable
internal fun ApprovedPriceCard(product: JSONObject, group: String, expandAll: Boolean, onOrder: () -> Unit) {
    var expanded by rememberSaveable(product.s("name"), expandAll) { mutableStateOf(expandAll) }
    val available = catalogInStock(product)
    ApprovedSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosTerminalIcon(Modifier.size(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(shortProductName(product.s("name")), color = ApprovedPalette.ink,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (available) "موجودی " + faNumber(product.i("stock")) + " عدد" else "ناموجود", color = if (available) ApprovedPalette.muted else Danger)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "بستن قیمت‌ها" else "نمایش همه قیمت‌ها",
                    tint = ApprovedPalette.accent)
            }
            if (expanded) {
                for ((key, title) in listOf("serial" to "دستگاه", "panel" to "پنل", "agent" to "عاملین فروش")) {
                    val rows = catalogPriceRows(key).filter { product.obj("prices").l(it.first) > 0L }
                    if (rows.isNotEmpty()) {
                        Surface(color = ApprovedPalette.surface, shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ApprovedPalette.border)) {
                            Column {
                                Surface(color = ApprovedPalette.secondary) {
                                    Text(title, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ApprovedPalette.ink)
                                }
                                rows.forEach { (priceKey, label) ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(label, color = ApprovedPalette.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Text(if (available) formatMoney(product.obj("prices").l(priceKey)) + " تومان" else "—",
                                            color = ApprovedPalette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    HorizontalDivider(color = ApprovedPalette.border)
                                }
                            }
                        }
                    }
                }
            }
            ApprovedButton("افزودن به سفارش", onOrder, modifier = Modifier.fillMaxWidth(), enabled = available &&
                catalogPriceRows(group).any { product.obj("prices").l(it.first) > 0L })
        }
    }
}

/** Present fulfillment and payment as distinct order facts with a stable details action. */
@Composable
internal fun ApprovedOrderCard(order: JSONObject, onClick: () -> Unit) {
    val progress = order.obj("settlement_progress")
    val method = order.s("requested_payment_method_label")
    val payment = progress.s("label").ifBlank { order.s("payment_status") }
    ApprovedSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, tint = ApprovedPalette.ink)
                Text(faDigits(order.s("number")), color = ApprovedPalette.ink, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                StatusPill(order.s("status"))
            }
            Text(formatDateFa(order.s("created_at")), color = ApprovedPalette.muted, fontSize = 12.sp)
            HorizontalDivider(color = ApprovedPalette.border)
            Text(orderItemsDescription(order), color = ApprovedPalette.ink)
            Text(formatMoney(order.l("approved_total").takeIf { it > 0L } ?: order.l("estimated_total")) + " تومان",
                color = ApprovedPalette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (method.isNotBlank() && !payment.contains(method)) {
                Text(method, color = ApprovedPalette.muted)
            }
            if (payment.isNotBlank()) {
                Surface(color = ApprovedPalette.secondary, shape = RoundedCornerShape(8.dp)) {
                    Text(payment, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = ApprovedPalette.ink)
                }
            }
            if (order.s("shipping_type").isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.LocalShipping, null, tint = ApprovedPalette.muted)
                    Text(order.s("shipping_type"), color = ApprovedPalette.muted)
                }
            }
            ApprovedButton("جزئیات سفارش", onClick, secondary = true)
        }
    }
}

/** Display wallet funds independently of the customer's purchase credit. */
@Composable
internal fun ApprovedWalletBalance(wallet: JSONObject) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Navy) {
        Column(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Navy, Navy.copy(red = .12f, green = .24f, blue = .34f))))
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("قدیر پارتنر", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("موجودی کیف پول", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                }
                Icon(Icons.Outlined.CreditCard, null, tint = ApprovedPalette.accent, modifier = Modifier.size(36.dp))
            }
            Text(formatMoney(wallet.l("balance")) + " تومان", color = Color.White,
                fontSize = 26.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = ApprovedPalette.accent.copy(alpha = .7f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((key, label) in listOf("available" to "قابل استفاده", "reserved" to "رزروشده")) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                        Text(formatMoney(wallet.l(key)) + " تومان", color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Expose every credit figure while keeping the usable credit prominent. */
@Composable
internal fun ApprovedWalletCredit(account: JSONObject) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ApprovedSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ShoppingCart, null, tint = ApprovedPalette.accent)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("اعتبار خرید", color = ApprovedPalette.muted)
                    Text(formatMoney(account.l("available")) + " تومان", color = ApprovedPalette.ink, fontWeight = FontWeight.Bold)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "جزئیات اعتبار", tint = ApprovedPalette.accent)
            }
            if (expanded) {
                DetailLine("سقف اعتبار", formatMoney(account.l("limit")) + " تومان")
                DetailLine("اعتبار رزروشده", formatMoney(account.l("reserved")) + " تومان")
                DetailLine("بدهی اعتباری", formatMoney(account.l("outstanding")) + " تومان")
            }
        }
    }
}

/** Render the existing audited wallet movements with their date and reference. */
@Composable
internal fun ApprovedWalletTransactions(entries: List<JSONObject>) {
    Text("گردش کیف پول", color = ApprovedPalette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    if (entries.isEmpty()) Text("هنوز تراکنشی ثبت نشده است.", color = ApprovedPalette.muted)
    entries.forEach { entry ->
        val amount = entry.l("amount")
        val title = when (entry.s("source")) {
            "order_cancellation" -> "بازگشت وجه"
            "order_purchase" -> "پرداخت سفارش"
            "credit_repayment" -> "بازپرداخت اعتبار"
            "manual_credit" -> "شارژ کیف پول"
            "irankish_credit" -> "شارژ آنلاین ایران‌کیش"
            else -> "گردش کیف پول"
        }
        ApprovedSurface {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (amount >= 0L) Icons.Outlined.AddCircleOutline else Icons.Outlined.RemoveCircleOutline,
                    null, tint = if (amount >= 0L) Success else Danger)
                Column(Modifier.weight(1f)) {
                    Text(title, color = ApprovedPalette.ink, fontWeight = FontWeight.Bold)
                    val reference = entry.s("number").ifBlank { entry.s("reference") }
                    if (reference.isNotBlank()) Text(faDigits(reference), color = ApprovedPalette.muted, fontSize = 12.sp)
                    if (entry.s("at").isNotBlank()) Text(formatDateFa(entry.s("at")), color = ApprovedPalette.muted, fontSize = 12.sp)
                }
                Text((if (amount > 0L) "+" else "") + formatMoney(amount) + " تومان",
                    color = if (amount >= 0L) Success else Danger, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Keep catalog and order search visible with the approved outline treatment. */
@Composable
internal fun ApprovedSearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ApprovedPalette.surface, unfocusedContainerColor = ApprovedPalette.surface,
            focusedBorderColor = ApprovedPalette.accent, unfocusedBorderColor = ApprovedPalette.border,
            focusedTextColor = ApprovedPalette.ink, unfocusedTextColor = ApprovedPalette.ink,
            focusedPlaceholderColor = ApprovedPalette.muted, unfocusedPlaceholderColor = ApprovedPalette.muted))
}

package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Use a handheld terminal silhouette with receipt, display, keypad and card slot. */
@Composable
internal fun PosTerminalIcon(modifier: Modifier = Modifier.size(28.dp)) {
    Icon(painterResource(R.drawable.pos_terminal), null, modifier, tint = ApprovedPalette.ink)
}

/** Keep the brand visible above the scrollable, keyboard-aware authentication form. */
@Composable
internal fun LoginBrandHeader() {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.brand_logo), "نشان قدیر پارتنر",
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
        Text("قدیر پارتنر", color = ApprovedPalette.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("همراه مطمئن کسب‌وکار شما", color = ApprovedPalette.muted, fontSize = 12.sp)
    }
}

/** Match OTP headings to the password form without changing code entry or resend rules. */
@Composable
internal fun LoginStepHeader(title: String, subtitle: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = ApprovedPalette.secondary) {
            Icon(icon, null, Modifier.padding(16.dp).size(32.dp), tint = ApprovedPalette.accent)
        }
        Text(title, color = ApprovedPalette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = ApprovedPalette.muted, fontSize = 13.sp)
    }
}

/** Group existing order operations while preserving their own permission predicates. */
@Composable
internal fun ApprovedDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ApprovedSurface {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = ApprovedPalette.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Present immutable order identity and customer facts above financial and delivery sections. */
@Composable
internal fun ApprovedOrderSummary(order: JSONObject, me: JSONObject, total: Long) {
    ApprovedSurface {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ReceiptLong, null, tint = ApprovedPalette.accent)
                Text(faDigits(order.s("number")), color = ApprovedPalette.ink,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusPill(order.s("status"))
            }
            Text(formatDateFa(order.s("created_at")), color = ApprovedPalette.muted, fontSize = 12.sp)
            Text(order.s("customer_name").ifBlank { me.obj("customer").s("name") },
                color = ApprovedPalette.ink, fontWeight = FontWeight.Bold)
            val company = order.s("customer_company").ifBlank { me.obj("customer").s("company") }
            if (company.isNotBlank()) Text(company, color = ApprovedPalette.muted, fontSize = 12.sp)
            HorizontalDivider(color = ApprovedPalette.border)
            DetailLine("تسویه", listOf(order.s("requested_payment_method_label"), order.s("payment_status"))
                .filter { it.isNotBlank() }.joinToString(" • "))
            DetailLine("اقلام", orderItemsDescription(order))
            Row(Modifier.fillMaxWidth().background(ApprovedPalette.secondary, RoundedCornerShape(10.dp))
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مبلغ کل", color = ApprovedPalette.muted, modifier = Modifier.weight(1f))
                Text(formatMoney(total) + " تومان", color = ApprovedPalette.ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Show all existing purchase-line fields immediately, without requiring a disclosure tap. */
@Composable
internal fun ApprovedOrderProduct(line: JSONObject) {
    ApprovedSurface {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosTerminalIcon()
                Text(shortProductName(line.s("product")), color = ApprovedPalette.ink,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(faNumber(line.i("qty")) + " عدد", color = ApprovedPalette.ink)
            }
            if (line.s("price_label").isNotBlank()) DetailLine("نوع قیمت", line.s("price_label"))
            if (line.s("panel_switch").isNotBlank()) DetailLine("سوئیچ", line.s("panel_switch"))
            if (line.l("unit_price") > 0) DetailLine("قیمت واحد", formatMoney(line.l("unit_price")) + " تومان")
            if (line.l("line_total") > 0) DetailLine("جمع", formatMoney(line.l("line_total")) + " تومان")
        }
    }
}

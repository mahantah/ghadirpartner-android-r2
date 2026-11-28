package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Present the current cart as a receipt before the existing final confirmation. */
@Composable
internal fun CheckoutReceipt(
    cart: List<CartLine>,
    invoiceType: String,
    payment: String,
    remaining: Long,
    discount: Long,
    total: Long,
    promoCode: String,
    delivery: List<Pair<String, String>>,
    notes: String,
) {
    Surface(color = AppSurface, shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SharpPortalIcon(Icons.Default.Receipt, Color(0xFFB5631D))
                Text("رسید سفارش", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            DetailLine("نوع فاکتور", invoiceType)
            ReceiptDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("شرح کالا", color = Muted, fontSize = 12.sp)
                Text("مبلغ (تومان)", color = Muted, fontSize = 12.sp)
            }
            cart.forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(shortProductName(line.product), color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${faNumber(line.qty)} × ${formatMoney(line.unit)}", color = Muted, fontSize = 12.sp)
                        listOf(line.priceLabel, line.panelSwitch).filter(String::isNotBlank)
                            .takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" • "), color = Muted, fontSize = 11.sp) }
                    }
                    Text(formatMoney(line.unit * line.qty), color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
            ReceiptDivider()
            if (discount > 0) DetailLine("تخفیف", formatMoney(discount) + " تومان")
            if (promoCode.isNotBlank()) DetailLine("کد تخفیف", promoCode)
            DetailLine("جمع سفارش", formatMoney(total) + " تومان", highlight = true)
            DetailLine("تسویه", payment)
            if (remaining > 0) DetailLine("ماندهٔ فاکتور", formatMoney(remaining) + " تومان")
            ReceiptDivider()
            delivery.filter { it.second.isNotBlank() }.forEach { (label, value) -> DetailLine(label, value) }
            if (notes.isNotBlank()) DetailLine("توضیحات", notes)
        }
    }
}

@Composable
private fun ReceiptDivider() {
    val color = Border
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, center.y), Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    }
}

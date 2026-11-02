package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Present one complete model and its purchase details without mixing adjacent rows. */
@Composable
internal fun OrderLineCard(
    line: CartLine,
    onIncrease: (() -> Unit)? = null,
    onDecrease: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("مدل دستگاه", color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(
                line.product,
                color = Ink,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(line.priceLabel, color = Ink, fontSize = 13.sp, lineHeight = 22.sp)
            HorizontalDivider(color = Border)
            Text("تعداد", color = Muted, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onDecrease != null) {
                    TextButton(
                        onClick = onDecrease,
                        enabled = line.qty > 1,
                        modifier = Modifier.semantics { contentDescription = "کاهش تعداد " + line.product },
                    ) { Text("−") }
                }
                Text(
                    faNumber(line.qty) + " دستگاه",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (onIncrease != null) {
                    TextButton(
                        onClick = onIncrease,
                        modifier = Modifier.semantics { contentDescription = "افزایش تعداد " + line.product },
                    ) { Text("+") }
                }
            }
            // Stack amounts to keep long prices legible on narrow screens and large fonts.
            Text("قیمت واحد", color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(formatMoney(line.unit) + " تومان", color = Ink, lineHeight = 24.sp)
            Text("مبلغ ردیف", color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(
                formatMoney(line.unit * line.qty) + " تومان",
                color = Ink,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
            )
            if (onRemove != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.semantics { contentDescription = "حذف " + line.product + " • " + line.priceLabel },
                ) { Text("حذف کالا", color = Danger) }
            }
        }
    }
}

package ir.ghadirpartner.nativeapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
    ApprovedSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PosTerminalIcon()
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(line.product, color = ApprovedPalette.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(line.priceLabel, color = ApprovedPalette.muted, fontSize = 12.sp)
                    Text("قیمت واحد " + formatMoney(line.unit) + " تومان", color = ApprovedPalette.muted, fontSize = 12.sp)
                }
                if (onRemove != null) IconButton(
                    onClick = onRemove,
                    modifier = Modifier.semantics { contentDescription = "حذف " + line.product + " • " + line.priceLabel },
                ) { Icon(Icons.Outlined.DeleteOutline, "حذف کالا", tint = ApprovedPalette.muted) }
            }
            if (line.panelSwitch.isNotBlank()) DetailLine("سوئیچ", line.panelSwitch)
            HorizontalDivider(color = ApprovedPalette.border)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onDecrease != null) OutlinedIconButton(
                    onClick = onDecrease, enabled = line.qty > 1,
                    modifier = Modifier.semantics { contentDescription = "کاهش تعداد " + line.product },
                ) { Icon(Icons.Outlined.Remove, null, tint = ApprovedPalette.accent) }
                Surface(shape = RoundedCornerShape(8.dp), color = ApprovedPalette.surface,
                    border = BorderStroke(1.dp, ApprovedPalette.border)) {
                    Text(faNumber(line.qty), color = ApprovedPalette.ink, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            .semantics { contentDescription = "تعداد " + line.product })
                }
                if (onIncrease != null) OutlinedIconButton(
                    onClick = onIncrease,
                    modifier = Modifier.semantics { contentDescription = "افزایش تعداد " + line.product },
                ) { Icon(Icons.Outlined.Add, null, tint = ApprovedPalette.accent) }
            }
            Text("مبلغ ردیف", color = ApprovedPalette.muted, fontSize = 12.sp)
            Text("جمع " + formatMoney(line.unit * line.qty) + " تومان", color = ApprovedPalette.ink,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

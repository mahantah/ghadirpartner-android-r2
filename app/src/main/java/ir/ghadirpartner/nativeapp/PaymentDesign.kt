package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val PortalBrandBrush
    get() = Brush.linearGradient(listOf(Color(0xFF101D39), Color(0xFF173C72)))

@Immutable
private data class MethodDesign(val title: String, val example: String, val icon: Int, val accent: Color, val light: Color) {
    val surface get() = if (AppAppearance.dark) accent.copy(alpha = .22f).compositeOn(ApprovedPalette.surface) else light
}

private fun Color.compositeOn(background: Color): Color = androidx.compose.ui.graphics.lerp(background, copy(alpha = 1f), alpha)

private val SettlementEditor.design: MethodDesign
    get() = when (this) {
        SettlementEditor.Wallet -> MethodDesign("کیف پول", "استفاده از موجودی کیف پول", R.drawable.payment_ref_wallet, Color(0xFF008577), Color(0xFFDEF8EF))
        SettlementEditor.Online -> MethodDesign("پرداخت آنلاین", "پرداخت با کارت بانکی", R.drawable.payment_ref_card, Color(0xFF1769CC), Color(0xFFE8F4FF))
        SettlementEditor.Deposit -> MethodDesign("واریز به حساب", "انتقال بانکی و ثبت فیش", R.drawable.payment_ref_bank, Color(0xFF643CC2), Color(0xFFF1E9FF))
        SettlementEditor.Credit -> MethodDesign("اعتبار", "استفاده از اعتبار خرید", R.drawable.payment_ref_credit, Color(0xFFB56900), Color(0xFFFFF3DD))
        SettlementEditor.Check -> MethodDesign("چک", "ثبت مشخصات و تصویر چک", R.drawable.payment_ref_receipt, Color(0xFF374C93), Color(0xFFE9EDFF))
    }

/** Show the reference illustration without changing its original colors. */
@Composable
internal fun PaymentArtwork(icon: Int, modifier: Modifier = Modifier) {
    Image(painterResource(icon), contentDescription = null, modifier = modifier)
}

/** Keep method selection independent of opening its detail dialog. */
@Composable
internal fun PaymentMethodCard(
    method: SettlementEditor, checked: Boolean, canOpen: Boolean, canToggle: Boolean, modifier: Modifier,
    onOpen: () -> Unit, horizontal: Boolean = false, onToggle: (Boolean) -> Unit,
) {
    val design = method.design
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = design.surface,
        border = BorderStroke(1.dp, design.accent.copy(alpha = if (checked) .8f else .28f))) {
        Box {
            val content = Modifier.fillMaxWidth().clickable(enabled = canOpen, onClick = onOpen)
            if (horizontal) {
                Row(content.padding(start = 52.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = design.accent) {
                        PaymentArtwork(design.icon, Modifier.padding(8.dp).size(30.dp))
                    }
                    Column {
                        Text(design.title, color = Ink, fontWeight = FontWeight.Bold)
                        Text(design.example, color = Muted, fontSize = 11.sp)
                    }
                }
            } else {
                Column(content.heightIn(min = 134.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    PaymentArtwork(design.icon, Modifier.size(60.dp, 45.dp))
                    Text(design.title, color = if (canOpen) Ink else Muted, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(design.example, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
            Checkbox(checked, onToggle, Modifier.align(Alignment.TopStart)
                .semantics { contentDescription = "فعال‌سازی " + design.title },
                enabled = canToggle, colors = CheckboxDefaults.colors(checkedColor = design.accent))
        }
    }
}

/** Present one payment form in a scrollable dialog, including when the keyboard is visible. */
@Composable
internal fun PaymentMethodDialog(method: SettlementEditor, busy: Boolean, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val design = method.design
    val height = (LocalConfiguration.current.screenHeightDp * .83f).dp
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp).widthIn(max = 520.dp),
            shape = RoundedCornerShape(28.dp), color = ApprovedPalette.surface) {
            Column(Modifier.heightIn(max = height).imePadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = if (method == SettlementEditor.Check) design.accent else design.surface) {
                        PaymentArtwork(design.icon, Modifier.size(48.dp).padding(5.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(design.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(design.example, color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.Default.Close, "بستن", tint = Ink) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

/** Apply the method accent to the dialog confirmation. */
@Composable
internal fun PaymentDialogButton(method: SettlementEditor, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (method == SettlementEditor.Online) Navy else method.design.accent, contentColor = Color.White)) {
        Text("ثبت و بازگشت", fontWeight = FontWeight.Bold)
    }
}

/** Display the invoice and live allocations above the method cards. */
@Composable
internal fun CheckoutAmountHero(total: Long, allocated: Long, remaining: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            color = if (AppAppearance.dark) Color(0xFF282E52) else Color(0xFFE5E3FF)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("مبلغ قابل تسویه", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(formatMoney(total) + " تومان", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                PaymentArtwork(R.drawable.payment_ref_hero, Modifier.size(85.dp, 64.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckoutAmountChip("مانده قابل پرداخت", remaining, Color(0xFFFFEFD9), Color(0xFF492D1E), Modifier.weight(1f))
            CheckoutAmountChip("جمع پرداخت‌ها", allocated, Color(0xFFE8F8EF), Color(0xFF173C34), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CheckoutAmountChip(title: String, amount: Long, light: Color, dark: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = if (AppAppearance.dark) dark else light) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Ink, fontSize = 11.sp, textAlign = TextAlign.Center)
            Text(formatMoney(amount) + " تومان", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** Repeat the actionable balance below the method cards. */
@Composable
internal fun CheckoutAmountFooter(allocated: Long, remaining: Long) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Navy) {
        Row(Modifier.background(PortalBrandBrush).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("جمع پرداخت‌ها: " + formatMoney(allocated) + " تومان", color = Color.White, fontSize = 13.sp)
                HorizontalDivider(color = Color.White.copy(alpha = .27f))
                Text("مانده قابل پرداخت: " + formatMoney(remaining) + " تومان", color = Color(0xFFFFBA56), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF5348AB)) {
                PaymentArtwork(R.drawable.payment_ref_receipt, Modifier.size(46.dp).padding(8.dp))
            }
        }
    }
}

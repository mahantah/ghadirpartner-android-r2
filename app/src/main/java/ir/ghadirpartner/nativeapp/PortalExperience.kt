package ir.ghadirpartner.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

/** Use the display label without changing the carrier value sent to the order API. */
internal fun carrierLabel(value: String): String = if (value == "ترمینال") "ترمینال (باربری)" else value

/** Present available shipping methods as selectable cards. */
@Composable
internal fun CarrierChoiceGrid(methods: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckoutArtworkHeading("روش ارسال", R.drawable.payment_ref_shipping)
        methods.filter(String::isNotBlank).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { carrier ->
                    val active = carrier == selected
                    val (icon, color) = when (carrier) {
                        "پیک" -> Icons.Default.TwoWheeler to Color(0xFF087C63)
                        "حضوری", "تحویل حضوری" -> Icons.Default.Storefront to Color(0xFF2364B5)
                        "ترمینال" -> Icons.Default.LocalShipping to Color(0xFF714AA4)
                        "چاپار" -> Icons.Default.Bolt to Color(0xFF1972B4)
                        "تیپاکس" -> Icons.Default.Inventory2 to Color(0xFFAE5819)
                        "کالارسان" -> Icons.Default.Warehouse to Color(0xFF755628)
                        "کاراپست" -> Icons.Default.MarkunreadMailbox to Color(0xFF187471)
                        "ماهکس" -> Icons.Default.Public to Color(0xFF5B429F)
                        else -> Icons.Default.LocalPostOffice to Color(0xFF3E5D82)
                    }
                    Surface(onClick = { onSelect(carrier) }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp), color = if (active) color.copy(alpha = .17f) else AppSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) color else Border)) {
                        Row(Modifier.padding(10.dp).heightIn(min = 42.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                            Text(carrierLabel(carrier), color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (active) Icon(Icons.Default.Check, "انتخاب شده", tint = color, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Pair a checkout section title with the existing illustrated payment assets. */
@Composable
internal fun CheckoutArtworkHeading(title: String, artwork: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PaymentArtwork(artwork, Modifier.size(38.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Copy a bank identifier without the display-only direction markers. */
@Composable
internal fun CopyableAccountLine(label: String, value: String) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { DetailLine(label, "\u2066$value\u2069") }
        IconButton(onClick = { copyToClipboard(context, label, value) }) {
            Icon(Icons.Default.ContentCopy, "کپی $label", tint = Ink)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
}

/** Group all contact methods together, with the support number ready to paste. */
@Composable
internal fun SupportMessengerActions() {
    val context = LocalContext.current
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, ("tel:" + BuildConfig.SUPPORT_LANDLINE).toUri())) }
                    .onFailure { android.widget.Toast.makeText(context, "برنامهٔ تماس در دسترس نیست.", android.widget.Toast.LENGTH_SHORT).show() }
            }) { Text("تماس تلفنی " + faDigits(BuildConfig.SUPPORT_LANDLINE)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("بله", "ir.nasim", R.drawable.support_bale),
                    Triple("روبیکا", "ir.resaneh1.iptv", R.drawable.support_rubika)).forEach { (label, packageName, logo) ->
                    OutlinedButton(onClick = {
                        copyToClipboard(context, "شماره پشتیبانی", BuildConfig.SUPPORT_MOBILE)
                        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                        val opened = launch != null && runCatching { context.startActivity(launch) }.isSuccess
                        val message = if (opened) "شماره پشتیبانی کپی شد" else "$label در دسترس نیست. شماره پشتیبانی کپی شد."
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) {
                        Image(painterResource(logo), null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(label)
                    }
                }
            }
            CopyableAccountLine("شماره پشتیبانی", BuildConfig.SUPPORT_MOBILE)
        }
    }
}

/** Draw a crisp vector glyph on a shaded tile at the requested size. */
@Composable
internal fun SharpPortalIcon(icon: ImageVector, accent: Color, modifier: Modifier = Modifier.size(36.dp)) {
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(
        Brush.linearGradient(listOf(accent.copy(alpha = .14f), accent.copy(alpha = .32f)))),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (AppAppearance.dark) Ink else accent, modifier = Modifier.fillMaxSize(.64f))
    }
}

/** Choose a device from a modal with a visible model and availability on every row. */
@Composable
internal fun CheckoutDeviceChooser(
    products: List<org.json.JSONObject>,
    selected: String,
    label: (org.json.JSONObject) -> String,
    onSelect: (org.json.JSONObject) -> Unit,
) {
    DropdownField(
        "مدل دستگاه", selected.ifBlank { "انتخاب مدل دستگاه" }, products, label,
        icon = Icons.Default.ShoppingCart, emptyText = "دستگاهی با این جست‌وجو پیدا نشد.", showLabel = false,
        onSelect = onSelect,
    )
}

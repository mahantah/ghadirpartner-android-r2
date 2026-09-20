package ir.ghadirpartner.nativeapp

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrandHeader(title: String, subtitle: String = "", avatarUri: String = "") {
    val context = LocalContext.current
    val avatarBitmap = remember(avatarUri) {
        if (avatarUri.isBlank()) null else try {
            context.contentResolver.openInputStream(Uri.parse(avatarUri))?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
    }
    Surface(color = Navy, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(title, color=Color.White, fontSize=20.sp, fontWeight=FontWeight.Bold)
                Text(subtitle.ifBlank { "قدیر پارتنر" }, color=Color.White, fontSize=12.sp)
            }
            if (avatarBitmap != null) {
                Image(avatarBitmap.asImageBitmap(), "عکس پروفایل", contentScale=ContentScale.Crop,
                    modifier=Modifier.size(32.dp).clip(CircleShape))
            } else {
                Image(painterResource(R.drawable.figma_bell), null, modifier=Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun HeroCard(title: String, value: String, caption: String, icon: ImageVector, emphasis: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (emphasis) Navy else CardWhite),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (emphasis) Orange else Color(0xFFFFE6D2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (emphasis) Color.White else Orange)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(title, color = if (emphasis) Color(0xFFD7E5F7) else Muted, fontSize = 12.sp)
                Text(value, color = if (emphasis) Color.White else NavyDeep, fontSize = 23.sp, fontWeight = FontWeight.Black)
                Text(caption, color = if (emphasis) Color(0xFFBFD2E9) else Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = Navy, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = NavyDeep, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Inbox, null, tint = Border, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = NavyDeep, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

@Composable
fun StatusPill(text: String) {
    val bg = when {
        text.contains("تحویل") || text.contains("تسویه کامل") || text.contains("تأیید شده") -> Color(0xFFE6F7EE)
        text.contains("لغو") || text.contains("پرداخت نشده") -> Color(0xFFFFE9EC)
        text.contains("ارسال") -> Color(0xFFEAF2FF)
        else -> Color(0xFFFFF1E4)
    }
    val fg = when {
        text.contains("تحویل") || text.contains("تسویه کامل") || text.contains("تأیید شده") -> Success
        text.contains("لغو") || text.contains("پرداخت نشده") -> Danger
        text.contains("ارسال") -> Color(0xFF2457A6)
        else -> Warning
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(text.ifBlank { "-" }, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String = "جستجو") {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Orange,
            unfocusedBorderColor = Border,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun GhadirButton(text: String, onClick: () -> Unit, enabled: Boolean = true, secondary: Boolean = false, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) Orange else Navy,
            contentColor = if (secondary) Navy else Color.White,
            disabledContainerColor = Border
        )
    ) { Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
}

@Composable
fun OrderCard(number: String, customer: String, status: String, payment: String, amount: Long, date: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize()
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status)
                Spacer(Modifier.weight(1f))
                Text(faDigits(number), color = NavyDeep, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            if (customer.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(customer, color = NavySoft, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateFa(date), color = Muted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                StatusPill(payment)
            }
            if (amount > 0) {
                Spacer(Modifier.height(10.dp))
                Text("${formatMoney(amount)} تومان", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun BottomBar(items: List<NavItem>, selected: String, onSelected: (String) -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 18.dp,
        tonalElevation = 0.dp
    ) {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.height(76.dp)) {
            items.forEach { item ->
                val active = item.key == selected
                NavigationBarItem(
                    selected = active,
                    onClick = { onSelected(item.key) },
                    icon = {
                        Box(
                            Modifier
                                .offset(y = if (active) (-5).dp else 0.dp)
                                .size(if (active) 45.dp else 34.dp)
                                .clip(CircleShape)
                                .background(if (active) Color(0xFFFFE2CB) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, null, tint = if (active) Orange else Muted, modifier = Modifier.size(if (active) 24.dp else 21.dp))
                        }
                    },
                    label = { Text(item.label, fontSize = 9.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Orange,
                        selectedTextColor = NavyDeep,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted
                    )
                )
            }
        }
    }
}
data class NavItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun LoadingPane(text: String = "در حال دریافت اطلاعات...") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Orange)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    if (message.isNotBlank()) {
        Surface(color = Color(0xFFFFE9EC), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Danger) }
                Spacer(Modifier.weight(1f))
                Text(message, color = Danger, fontSize = 12.sp, textAlign = TextAlign.Start, modifier = Modifier.weight(6f))
            }
        }
    }
}

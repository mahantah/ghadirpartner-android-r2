package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pill-shaped controls for profile shortcuts, using the same account actions as the rest of the app. */
@Composable
internal fun ProfileShortcut(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (subtitle == null) 50 else 24)
    val tint = androidx.compose.ui.graphics.lerp(AppSurface, accent, if (AppAppearance.dark) .19f else .09f)
    Surface(onClick = onClick, modifier = modifier, shape = shape, color = tint,
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)), shadowElevation = 2.dp) {
        Row(Modifier.heightIn(min = if (subtitle == null) 54.dp else 85.dp).padding(9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SharpPortalIcon(icon, accent)
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (subtitle != null) Text(subtitle, color = Ink, fontSize = 11.sp)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (AppAppearance.dark) Ink else accent,
                modifier = Modifier.size(17.dp))
        }
    }
}

/** Show identity information with the existing photo editor. */
@Composable
internal fun ProfileIdentityCard(
    name: String,
    role: String,
    code: String,
    onPhotoClick: () -> Unit,
    photoActionIcon: ImageVector = Icons.Default.Edit,
    photo: @Composable () -> Unit,
) {
    val surface = if (AppAppearance.dark) AppSurface else Color(0xFFD9E8FF)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), shadowElevation = 3.dp) {
        Row(
            Modifier.background(Brush.linearGradient(listOf(surface, AppSurface, Orange.copy(alpha = .22f))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                photo()
                FilledIconButton(onClick = onPhotoClick, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 9.dp, y = 9.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange, contentColor = Navy)) {
                    Icon(photoActionIcon, "تغییر تصویر پروفایل", modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(role, color = Ink, fontSize = 13.sp)
                if (code.isNotBlank()) {
                    Surface(shape = CircleShape, color = AppSurface) {
                        Text("کد همکاری  " + faDigits(code), Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Ink, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


/** Use the brand's navy pill for saving the personal information form. */
@Composable
internal fun ProfileSaveButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

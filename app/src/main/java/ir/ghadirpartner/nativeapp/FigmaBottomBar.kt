package ir.ghadirpartner.nativeapp

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/** Render the floating navigation with a glass tint without rerecording the screen. */
@Composable
fun FigmaBottomBar(
    items: List<NavItem>, selected: String, onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=12.dp)
        .shadow(10.dp, shape).clip(shape)
        .border(1.dp, if(AppAppearance.dark)Color.White.copy(alpha=.18f) else Color.White.copy(alpha=.8f), shape)
        .semantics {contentDescription="ناوبری شناور"}) {
        Row(Modifier.fillMaxWidth()
            .background(AppSurface.copy(alpha = .94f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(AppAppearance.dark) .04f else .20f),Color.Transparent)))
            .padding(8.dp).selectableGroup(), horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val active = item.key == selected
                Column(Modifier.weight(1f).heightIn(min=60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(if(active) Modifier.background(Orange.copy(alpha=.13f),RoundedCornerShape(18.dp)) else Modifier)
                    .selectable(active, role=Role.Tab, onClick={onSelected(item.key)})
                    .padding(vertical=8.dp), horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    val accent = when (item.key) {
                        "new" -> Color(0xFFB5631D)
                        "orders" -> Color(0xFF5148A7)
                        else -> navIconColor(item.key)
                    }
                    SharpPortalIcon(item.icon, accent, Modifier.size(29.dp))
                    Text(item.label, color=if(active) Orange else Muted, fontSize=10.sp,
                        fontWeight=if(active) FontWeight.Bold else FontWeight.Normal, maxLines=1)
                }
            }
        }
    }
}


@Composable
private fun navIconColor(key: String): Color {
    val value = when (key) {
        "home" -> if (AppAppearance.dark) BuildConfig.NAV_HOME_DARK else BuildConfig.NAV_HOME_LIGHT
        "catalog" -> if (AppAppearance.dark) BuildConfig.NAV_CATALOG_DARK else BuildConfig.NAV_CATALOG_LIGHT
        else -> if (AppAppearance.dark) BuildConfig.NAV_PROFILE_DARK else BuildConfig.NAV_PROFILE_LIGHT
    }
    return Color(value.toColorInt())
}

package ir.ghadirpartner.nativeapp

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma 24:35: the recorded content is blurred separately, leaving labels sharp. */
@Composable
fun FigmaBottomBar(
    items: List<NavItem>, selected: String, onSelected: (String) -> Unit,
    modifier: Modifier = Modifier, backdrop: GraphicsLayer? = null,
    contentPosition: Offset = Offset.Zero
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=12.dp)
        .shadow(12.dp, shape).clip(shape)
        .onGloballyPositioned { position = it.positionInRoot() }
        .border(1.dp, Color.White.copy(alpha=.9f), shape)) {
        if (backdrop != null) {
            Box(Modifier.matchParentSize().graphicsLayer {
                if (Build.VERSION.SDK_INT >= 31) {
                    renderEffect = RenderEffect.createBlurEffect(24.dp.toPx(), 24.dp.toPx(), Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            }.drawBehind {
                val delta = contentPosition - position
                translate(delta.x, delta.y) { drawLayer(backdrop) }
            })
        }
        // Older Android retains an opaque-enough translucent glass fallback.
        Row(Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.9f), Color.White.copy(alpha=.82f))))
            .padding(8.dp).selectableGroup(), horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val active = item.key == selected
                val asset = when(item.key) {
                    "home" -> R.drawable.figma_home
                    "catalog" -> R.drawable.figma_prices
                    "new" -> R.drawable.figma_add
                    "orders" -> R.drawable.figma_orders
                    else -> R.drawable.figma_profile
                }
                Column(Modifier.weight(1f).heightIn(min=60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if(active) Navy else Color.Transparent)
                    .selectable(active, role=Role.Tab, onClick={onSelected(item.key)})
                    .padding(vertical=8.dp), horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    Image(painterResource(asset), null, Modifier.size(22.dp),
                        colorFilter=ColorFilter.tint(if(active) Orange else Navy))
                    Text(item.label, color=if(active) Color.White else Navy, fontSize=10.sp,
                        fontWeight=if(active) FontWeight.Bold else FontWeight.Normal, maxLines=1)
                }
            }
        }
    }
}

package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val PortalGlass get() = BuildConfig.APP_MODE == "portal"
val Ink get() = if (PortalGlass) Color.White else Navy
val GlassFill = Color.White.copy(alpha = .065f)

/** R4 material: translucent body, specular top edge and subtle inner reflection. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), accent: Boolean = false): Modifier =
    clip(shape).background(if (accent) Orange else GlassFill)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(accent) .28f else .10f), Color.White.copy(alpha=.015f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha=.50f), Color.White.copy(alpha=.16f))), shape)

fun Modifier.portalBackdrop(): Modifier = background(Brush.verticalGradient(listOf(Navy, Color(0xFF050A18)))).drawBehind {
    val scale = size.width / 390f
    fun glow(center: Offset, radius: Float, color: Color) {
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.28f),color.copy(alpha=.10f),Color.Transparent),center,radius),radius,center)
    }
    glow(Offset(100f*scale, 340f*scale), 300f*scale, Orange)
    glow(Offset(370f*scale,630f*scale),245f*scale,Color(0xFF1066E5))
    glow(Offset(80f*scale,size.height),235f*scale,Orange)
}

@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp),
    color: Color = GlassFill, contentColor: Color = Ink, tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp, border: BorderStroke? = null, content: @Composable () -> Unit) {
    Surface(modifier=if(PortalGlass) modifier.glass(shape) else modifier, shape=shape,
        color=if(PortalGlass) Color.Transparent else color, contentColor=contentColor,
        tonalElevation=if(PortalGlass) 0.dp else tonalElevation,
        shadowElevation=if(PortalGlass) 0.dp else shadowElevation,
        border=if(PortalGlass) null else border, content=content)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp),
    colors: CardColors = CardDefaults.cardColors(), elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=if(PortalGlass) modifier.glass(shape) else modifier,shape=shape,
        colors=if(PortalGlass) CardDefaults.cardColors(containerColor=Color.Transparent,contentColor=Color.White) else colors,
        elevation=if(PortalGlass) CardDefaults.cardElevation() else elevation,border=if(PortalGlass) null else border,content=content)
}

@Composable
fun GlassTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick=onClick,enabled=enabled,
        modifier=modifier.heightIn(min=44.dp).then(if(PortalGlass) Modifier.glass(RoundedCornerShape(16.dp)) else Modifier),
        colors=ButtonDefaults.textButtonColors(contentColor=Ink,disabledContentColor=Muted),content=content)
}

@Composable
fun GlassFilterChip(selected: Boolean,onClick: () -> Unit,label: @Composable () -> Unit) {
    FilterChip(selected=selected,onClick=onClick,label=label,
        modifier=Modifier.heightIn(min=44.dp).glass(RoundedCornerShape(14.dp),accent=selected),
        colors=FilterChipDefaults.filterChipColors(containerColor=Color.Transparent,selectedContainerColor=Color.Transparent,labelColor=Color.White,selectedLabelColor=Navy),border=null)
}

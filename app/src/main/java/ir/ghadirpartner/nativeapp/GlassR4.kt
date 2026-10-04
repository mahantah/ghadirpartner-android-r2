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

// Standard surfaces use the light palette; liquid glass is reserved for navigation.
val PortalGlass = false
val Ink get() = AppInk
val GlassFill get() = AppSurface

/** R4 material: translucent body, specular top edge and subtle inner reflection. */
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(20.dp), accent: Boolean = false): Modifier =
    clip(shape).background(if (accent) Orange else GlassFill)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha=if(accent) .28f else .10f), Color.White.copy(alpha=.015f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha=.50f), Color.White.copy(alpha=.16f))), shape)

fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp), accent: Boolean = false): Modifier =
    clip(shape).background(if(accent) Orange else AppSurface).border(1.dp,Border,shape)

fun Modifier.portalBackdrop(): Modifier = background(AppBackground)

@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp),
    color: Color = GlassFill, contentColor: Color = Ink, tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp, border: BorderStroke? = null, content: @Composable () -> Unit) {
    Surface(modifier=modifier, shape=shape, color=AppSurface, contentColor=AppInk,
        tonalElevation=0.dp, shadowElevation=0.dp,
        border=border ?: BorderStroke(1.dp,Border), content=content)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(20.dp),
    colors: CardColors = CardDefaults.cardColors(), elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=modifier,shape=shape,
        colors=CardDefaults.cardColors(containerColor=AppSurface,contentColor=AppInk),
        elevation=CardDefaults.cardElevation(0.dp),border=border ?: BorderStroke(1.dp,Border),content=content)
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
        colors=FilterChipDefaults.filterChipColors(containerColor=Color.Transparent,selectedContainerColor=Color.Transparent,labelColor=Ink,selectedLabelColor=Navy),border=null)
}

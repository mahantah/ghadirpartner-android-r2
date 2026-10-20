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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Quiet payment-network signature; cached paths, no animation or bitmap allocation. */
fun Modifier.portalBackdrop(): Modifier {
    val dark=AppAppearance.dark
    val base=AppBackground
    val navy=if(dark)Color(0xFF385B7D) else Color(0xFF142B4B)
    val orange=Color(0xFFF58220)
    return background(base).drawWithCache {
        val w=size.width;val h=size.height
        val signature=Path().apply {
            moveTo(-w*.25f,h*.30f)
            cubicTo(w*.30f,-h*.02f,w*.70f,h*.50f,w*1.25f,h*.12f)
            moveTo(-w*.20f,h*.84f)
            cubicTo(w*.22f,h*.58f,w*.68f,h*1.16f,w*1.20f,h*.76f)
        }
        val network=Path().apply {
            moveTo(w*.78f,0f);lineTo(w*.78f,h*.14f);lineTo(w*.93f,h*.20f);lineTo(w*.93f,h*.34f)
            moveTo(0f,h*.70f);lineTo(w*.17f,h*.70f);lineTo(w*.27f,h*.76f);lineTo(w*.27f,h*.90f)
        }
        onDrawBehind {
            drawRect(Brush.radialGradient(listOf(navy.copy(alpha=if(dark).20f else .07f),Color.Transparent),center=Offset(w*.95f,h*.08f),radius=w*1.25f))
            drawRect(Brush.radialGradient(listOf(orange.copy(alpha=if(dark).10f else .07f),Color.Transparent),center=Offset(w*.05f,h*.85f),radius=w))
            drawPath(signature,navy.copy(alpha=if(dark).20f else .055f),style=Stroke(1.dp.toPx()))
            drawPath(network,orange.copy(alpha=if(dark).22f else .13f),style=Stroke(1.dp.toPx()))
            listOf(Offset(w*.93f,h*.34f),Offset(w*.27f,h*.90f)).forEach {drawCircle(orange.copy(alpha=.22f),3.dp.toPx(),it)}
        }
    }
}

@Composable
fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp),
    color: Color = GlassFill, contentColor: Color = Ink, tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp, border: BorderStroke? = null, content: @Composable () -> Unit) {
    Surface(modifier=modifier, shape=shape, color=if(color==Color.White)AppSurface else color, contentColor=contentColor,
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

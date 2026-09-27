package ir.ghadirpartner.nativeapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Navy = Color(0xFF0B1F3A)
val NavyDeep get() = if (PortalGlass) Color.White else Color(0xFF142033)
val NavySoft get() = if (PortalGlass) Color(0xFFD5E2F2) else Color(0xFF203B5C)
val Orange = Color(0xFFF58220)
val OrangeSoft = Color(0xFFFFF0E1)
val Canvas = Color(0xFFF5F7FA)
val CardWhite = Color(0xFFFFFFFF)
val Muted get() = if (PortalGlass) Color(0xFFD5DFEB) else Color(0xFF53647B)
val Border get() = if (PortalGlass) Color.White.copy(alpha=.28f) else Color(0xFFD5DEE9)
val Success get() = if (PortalGlass) Color(0xFF8DE3B3) else Color(0xFF176347)
val Danger get() = if (PortalGlass) Color(0xFFFFA7B2) else Color(0xFFAC2638)
val Warning get() = if (PortalGlass) Color(0xFFFFCB9A) else Color(0xFFB7791F)

private val GhadirLight = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Orange,
    onSecondary = Navy,
    background = Canvas,
    onBackground = NavyDeep,
    surface = CardWhite,
    onSurface = NavyDeep,
    outline = Border,
    error = Danger
)

private val GhadirFont = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal),
    Font(R.font.vazirmatn, FontWeight.Medium),
    Font(R.font.vazirmatn, FontWeight.Bold)
)
private val BaseTypography = Typography()
private val GhadirTypography = with(BaseTypography) {
    copy(displayLarge=displayLarge.copy(fontFamily=GhadirFont), displayMedium=displayMedium.copy(fontFamily=GhadirFont), displaySmall=displaySmall.copy(fontFamily=GhadirFont),
        headlineLarge=headlineLarge.copy(fontFamily=GhadirFont), headlineMedium=headlineMedium.copy(fontFamily=GhadirFont), headlineSmall=headlineSmall.copy(fontFamily=GhadirFont),
        titleLarge=titleLarge.copy(fontFamily=GhadirFont), titleMedium=titleMedium.copy(fontFamily=GhadirFont), titleSmall=titleSmall.copy(fontFamily=GhadirFont),
        bodyLarge=bodyLarge.copy(fontFamily=GhadirFont), bodyMedium=bodyMedium.copy(fontFamily=GhadirFont), bodySmall=bodySmall.copy(fontFamily=GhadirFont),
        labelLarge=labelLarge.copy(fontFamily=GhadirFont), labelMedium=labelMedium.copy(fontFamily=GhadirFont), labelSmall=labelSmall.copy(fontFamily=GhadirFont))
}

@Composable
fun GhadirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (PortalGlass) darkColorScheme(primary=Orange,onPrimary=Navy,secondary=Orange,onSecondary=Navy,background=Navy,surface=Color(0xFF15243A),onSurface=Color.White,onBackground=Color.White,surfaceVariant=Color(0xFF1C2B40),onSurfaceVariant=Muted,outline=Border,error=Danger) else GhadirLight,
        typography = GhadirTypography,
        shapes = Shapes(small=RoundedCornerShape(12.dp), medium=RoundedCornerShape(20.dp), large=RoundedCornerShape(20.dp)),
        content = content
    )
}

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
val NavyDeep get() = AppInk
val NavySoft get() = AppInk
val Orange get() = if(AppAppearance.dark) Color(0xFFF58220) else Color(0xFFE86F16)
val OrangeSoft get() = AppSecondary
val Canvas get() = AppBackground
val CardWhite get() = AppSurface
val Muted get() = if(AppAppearance.dark) Color(0xFFA4B1C2) else Color(0xFF81746B)
val Border get() = if(AppAppearance.dark) Color(0xFF33404F) else Color(0xFFF0E2D6)
val Success get() = if(AppAppearance.dark) Color(0xFF8DE3B3) else Color(0xFF176347)
val Danger get() = if(AppAppearance.dark) Color(0xFFFFA7B2) else Color(0xFFAC2638)
val Warning get() = if(AppAppearance.dark) Color(0xFFFFCB9A) else Color(0xFF94501C)

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
        colorScheme = if (AppAppearance.dark) darkColorScheme(primary=Orange,onPrimary=Navy,secondary=Orange,onSecondary=Navy,background=AppBackground,surface=AppSurface,onSurface=AppInk,onBackground=AppInk,surfaceVariant=AppSecondary,onSurfaceVariant=Muted,outline=Border,error=Danger) else lightColorScheme(primary=Orange,onPrimary=Navy,secondary=Orange,onSecondary=Navy,background=AppBackground,surface=AppSurface,onSurface=AppInk,onBackground=AppInk,surfaceVariant=AppSecondary,onSurfaceVariant=Muted,outline=Border,error=Danger),
        typography = GhadirTypography,
        shapes = Shapes(small=RoundedCornerShape(12.dp), medium=RoundedCornerShape(20.dp), large=RoundedCornerShape(20.dp)),
        content = content
    )
}

package ir.ghadirpartner.nativeapp

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** Figma 100:573, day/night tokens. Stored independently of the logged-in account. */
object AppAppearance {
    var dark by mutableStateOf(false)
        private set
    fun restore(context: Context) { dark = context.getSharedPreferences("appearance", 0).getBoolean("dark", false) }
    fun toggle(context: Context) { dark = !dark; context.getSharedPreferences("appearance", 0).edit().putBoolean("dark", dark).apply() }
}
val AppBackground get() = if(AppAppearance.dark) Color(0xFF10161F) else Color(0xFFFFF8F2)
val AppSurface get() = if(AppAppearance.dark) Color(0xFF1B2430) else Color.White
val AppSecondary get() = if(AppAppearance.dark) Color(0xFF253140) else Color(0xFFFFF0E2)
val AppInk get() = if(AppAppearance.dark) Color(0xFFF4F6FA) else Color(0xFF302A27)


package ir.ghadirpartner.nativeapp

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.caverock.androidsvg.SVG
import kotlin.math.ceil

/** Renders the complete, unmodified Figma SVG onto a transparent bitmap. */
@Composable
internal fun FigmaSvg(name:String, description:String?, modifier:Modifier, tint:ColorFilter?=null) {
    val context=LocalContext.current
    val density=LocalDensity.current.density
    val image=remember(name,density) {
        val svg=SVG.getFromAsset(context.assets,"figma/$name.svg")
        val width=ceil(svg.documentWidth*density*2).toInt().coerceAtLeast(1)
        val height=ceil(svg.documentHeight*density*2).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawPicture(svg.renderToPicture(width,height))
        }.asImageBitmap()
    }
    Image(image,description,modifier,colorFilter=tint)
}

@Composable
internal fun DesignGlyph(resource:Int,description:String?,modifier:Modifier,tint:ColorFilter?=null) {
    val name=when(resource){
        R.drawable.design_calendar->"calendar"
        R.drawable.design_map_pin->"map_pin"
        R.drawable.design_chevron->"chevron"
        R.drawable.design_success->"success"
        R.drawable.design_empty_orders->"empty_orders"
        R.drawable.design_offline->"offline"
        R.drawable.design_fingerprint->"fingerprint"
        R.drawable.design_fingerprint_error->"fingerprint_error"
        R.drawable.design_download_error->"download_error"
        R.drawable.design_otp->"otp"
        R.drawable.design_search_empty->"search_empty"
        R.drawable.design_delivered->"delivered"
        R.drawable.design_package->"package"
        R.drawable.design_truck->"truck"
        R.drawable.design_notification_check->"notification_check"
        R.drawable.design_gift->"gift"
        R.drawable.design_wallet->"wallet"
        else->error("Unmapped Figma glyph")
    }
    FigmaSvg(name,description,modifier,tint)
}

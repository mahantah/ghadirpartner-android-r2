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
        val width=ceil(svg.documentWidth*density).toInt().coerceAtLeast(1)
        val height=ceil(svg.documentHeight*density).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawPicture(svg.renderToPicture(width,height))
        }.asImageBitmap()
    }
    Image(image,description,modifier,colorFilter=tint)
}

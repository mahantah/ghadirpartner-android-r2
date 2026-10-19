package ir.ghadirpartner.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos

internal data class PurchasePoint(val label:String,val amount:Long)

/** Gregorian API dates are grouped by actual month; missing/cancelled orders add no invented values. */
internal fun purchaseHistory(orders:List<JSONObject>,now:Calendar=Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))):List<PurchasePoint> {
    val first=(now.clone() as Calendar).apply {set(Calendar.DAY_OF_MONTH,1);add(Calendar.MONTH,-5)}
    return (0..5).map {index->
        val month=(first.clone() as Calendar).apply {add(Calendar.MONTH,index)}
        val prefix=String.format(Locale.US,"%04d-%02d-",month.get(Calendar.YEAR),month.get(Calendar.MONTH)+1)
        val amount=orders.filter {it.s("status")=="تحویل شد"&&it.s("created_at").startsWith(prefix)}
            .sumOf {it.l("approved_total").takeIf {v->v>0}?:it.l("estimated_total").coerceAtLeast(0L)}
        PurchasePoint(if(index==5)"این ماه" else faNumber(5-index)+" ماه قبل",amount)
    }
}

@Composable
internal fun PurchaseTrendCard(orders:List<JSONObject>) {
    val points=remember(orders){purchaseHistory(orders)}
    var showValues by remember {mutableStateOf(false)}
    val accent=Orange
    val grid=Border
    val maximum=points.maxOfOrNull {it.amount}?.coerceAtLeast(1L)?:1L
    val description=points.joinToString("، "){it.label+": "+formatMoney(it.amount)+" تومان"}
    GlassSurface {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("روند خرید",color=Ink,fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text("مبلغ سفارش‌های تحویل‌شده • ۶ ماه اخیر",color=Muted,fontSize=12.sp)
            Text(formatMoney(points.sumOf {it.amount})+" تومان",color=Ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(170.dp).semantics {contentDescription="نمودار روند خرید؛ "+description}) {
                val left=10.dp.toPx();val right=size.width-left
                val top=12.dp.toPx();val bottom=size.height-12.dp.toPx()
                repeat(4){i->val y=top+(bottom-top)*i/3f;drawLine(grid,Offset(left,y),Offset(right,y),1.dp.toPx())}
                val coordinates=points.mapIndexed {i,p->Offset(left+(right-left)*i/(points.size-1),bottom-(bottom-top)*(p.amount.toDouble()/maximum).toFloat())}
                val line=Path().apply {
                    moveTo(coordinates.first().x,coordinates.first().y)
                    coordinates.zipWithNext().forEach {(a,b)->
                        // Cosine easing stays between the true points, including zero,
                        // giving a sinusoidal silhouette without negative/overshooting values.
                        for(sample in 1..24){val t=sample/24f;val eased=((1.0-cos(PI*t))/2.0).toFloat();lineTo(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*eased)}
                    }
                }
                val area=Path().apply {addPath(line);lineTo(right,bottom);lineTo(left,bottom);close()}
                drawPath(area,Brush.verticalGradient(listOf(accent.copy(alpha=.24f),accent.copy(alpha=.015f)),top,bottom))
                drawPath(line,accent,style=Stroke(3.dp.toPx()))
                coordinates.forEach {drawCircle(accent,4.dp.toPx(),it);drawCircle(Color.White,1.8.dp.toPx(),it)}
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(points.first().label,color=Muted,fontSize=11.sp);Text(points.last().label,color=Muted,fontSize=11.sp)}
            }
            if(points.all {it.amount==0L})Text("در این بازه خرید تحویل‌شده‌ای ثبت نشده است.",color=Muted,fontSize=12.sp)
            GhadirButton(if(showValues)"بستن جزئیات نمودار" else "جزئیات نمودار",{showValues=!showValues},secondary=true)
            if(showValues)points.forEach {DetailLine(it.label,formatMoney(it.amount)+" تومان")}
        }
    }
}

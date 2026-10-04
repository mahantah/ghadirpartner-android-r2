package ir.ghadirpartner.nativeapp

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Older portal servers omit summary endpoints; derive only from the caller's own orders. */
internal fun customerSummaryFromOrders(orders: List<JSONObject>): JSONObject {
    val parser=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply { timeZone=TimeZone.getTimeZone("Asia/Tehran");isLenient=false }
    val now=System.currentTimeMillis()
    fun period(days: Int): JSONObject {
        val eligible=orders.filter {o ->
            val at=try {parser.parse(o.s("created_at").take(10))?.time ?: 0L} catch(_:Exception){0L}
            o.s("status")!="لغو شد" && at>0 && now-at in 0..days*86400000L
        }
        return JSONObject().put("orders",eligible.size)
            .put("qty",eligible.sumOf {it.arr("items").objects().sumOf {line->line.i("qty")}})
            .put("amount",eligible.sumOf {if(it.l("approved_total")>0)it.l("approved_total") else it.l("estimated_total")})
    }
    return JSONObject().put("week",period(7)).put("month",period(30)).put("three_months",period(90))
}

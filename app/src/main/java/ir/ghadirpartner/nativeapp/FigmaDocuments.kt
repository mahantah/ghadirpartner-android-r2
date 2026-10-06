package ir.ghadirpartner.nativeapp

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@Composable
fun PortalDocuments(api: ApiClient, me: JSONObject, selected: JSONObject? = null, onOpen: (JSONObject)->Unit) {
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var orders by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var query by remember {mutableStateOf("")};var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(retry,selected) {
        busy=true
        try {orders=if(selected!=null) listOf(selected) else (api.get("/api/orders") as JSONArray).objects().sortedByDescending{it.i("id")};error=""}
        catch(e:Exception){error=e.message?:"دریافت پیش‌فاکتورها ناموفق بود"}finally{busy=false}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(selected==null)item{SearchBox(query,{query=it},"شماره پیش‌فاکتور / سفارش")}
        if(error.isNotBlank())item{ErrorBanner(error){error=""};GhadirButton("تلاش دوباره",{retry++},secondary=true)}
        if(busy)item{Text("در حال آماده‌سازی…",color=Muted)}
        val visible=orders.filter{query.isBlank()||it.s("number").contains(query,true)}
        if(visible.isEmpty()&&!busy&&error.isBlank())item{EmptyState("پیش‌فاکتوری پیدا نشد","پیش‌فاکتور سفارش‌های شما اینجا نمایش داده می‌شود.")}
        items(visible,key={it.i("id")}) { order ->
            GlassSurface {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("پیش‌فاکتور قدیر پارتنر",fontWeight=FontWeight.Bold,color=Ink,fontSize=20.sp)
                    Text(faDigits(order.s("number")),color=Muted)
                    Text("مشتری: "+me.obj("customer").s("name"),color=Ink)
                    Text("شرکت: "+me.obj("customer").s("company"),color=Ink)
                    Text("شماره تماس: "+faDigits(me.s("username")),color=Muted)
                    order.arr("items").objects().forEach {item ->
                        Text("${faNumber(item.i("qty"))} × ${shortProductName(item.s("product"))}",color=Ink,fontWeight=FontWeight.Bold)
                        Text("قیمت واحد: ${formatMoney(item.l("unit_price"))} تومان",color=Muted)
                    }
                    Text("مجموع: ${formatMoney(order.l("approved_total").takeIf{it>0}?:order.l("estimated_total"))} تومان",color=Ink,fontWeight=FontWeight.Bold)
                    Text("پیش‌فاکتور؛ به معنی پرداخت یا رزرو کالا نیست.",color=Muted,fontSize=12.sp)
                    if(selected==null)GhadirButton("مشاهده پیش‌فاکتور",{onOpen(order)},secondary=true)
                    GhadirButton("دانلود PDF پیش‌فاکتور",{scope.launch {busy=true;try{val path=api.saveProformaPdf(order);android.widget.Toast.makeText(context,"ذخیره شد: $path",android.widget.Toast.LENGTH_LONG).show()}catch(e:Exception){error=e.message?:"ذخیره ناموفق بود"}finally{busy=false}}},enabled=!busy)
                    GhadirButton("اشتراک‌گذاری",{scope.launch {
                        busy=true
                        try {
                            val uri=withContext(Dispatchers.IO){
                                val dir=File(context.cacheDir,"documents").apply{mkdirs()}
                                val file=File(dir,"proforma-${order.i("id")}.pdf")
                                val doc=PdfDocument()
                                try {OfficialInvoice.render(doc,order,me.obj("customer"));file.outputStream().use{doc.writeTo(it)}} finally {doc.close()}
                                FileProvider.getUriForFile(context,context.packageName+".files",file)
                            }
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"اشتراک‌گذاری پیش‌فاکتور"))
                        }catch(e:Exception){error="اشتراک‌گذاری انجام نشد؛ دوباره تلاش کنید"}finally{busy=false}
                    }},enabled=!busy,secondary=true)
                }
            }
        }
    }
}

@Composable
fun OrderDeliveryTimeline(status: String) {
    val labels=listOf("ثبت","آماده‌سازی","ارسال","تحویل")
    val current=when {status=="تحویل شد"->3;status=="ارسال شد"->2;status.contains("آماده")->1;else->0}
    GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("مراحل تحویل",fontWeight=FontWeight.Bold,color=Ink)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){labels.forEachIndexed{i,label->Text((if(i<=current) "● " else "○ ")+label,color=if(i<=current) Orange else Muted,fontSize=12.sp)}}
    }}
}

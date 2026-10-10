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
fun PortalDocuments(api:ApiClient,me:JSONObject,selected:JSONObject?=null,onOpen:(JSONObject)->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var orders by remember {mutableStateOf(emptyList<JSONObject>())}
    var query by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(retry,selected) {
        busy=true;error=""
        try{orders=if(selected!=null)listOf(selected) else (api.get("/api/orders") as JSONArray).objects().sortedByDescending{it.i("id")}}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"دریافت پیش‌فاکتورها ناموفق بود"}finally{busy=false}
    }
    fun download(order:JSONObject) {
        if(busy)return
        scope.launch {
            busy=true;error=""
            try{val path=api.saveProformaPdf(order);android.widget.Toast.makeText(context,"ذخیره شد: $path",android.widget.Toast.LENGTH_LONG).show()}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"ذخیره PDF ناموفق بود"}finally{busy=false}
        }
    }
    fun share(order:JSONObject) {
        if(busy)return
        scope.launch {
            busy=true;error=""
            try {
                val uri=withContext(Dispatchers.IO) {
                    val dir=File(context.cacheDir,"documents").apply{mkdirs()}
                    val file=File(dir,"proforma-"+order.i("id")+".pdf")
                    val doc=PdfDocument()
                    try{OfficialInvoice.render(doc,order,me.obj("customer"));file.outputStream().use{doc.writeTo(it)}}finally{doc.close()}
                    FileProvider.getUriForFile(context,context.packageName+".files",file)
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"اشتراک‌گذاری پیش‌فاکتور"))
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error="اشتراک‌گذاری انجام نشد؛ دوباره تلاش کنید"}finally{busy=false}
        }
    }
    val term=asciiDigits(query).trim()
    val visible=orders.filter {term.isBlank()||asciiDigits(it.s("number")).contains(term,true)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(selected==null) {
            item {DesignField("جستجوی پیش‌فاکتور",query,{query=it},"شماره پیش‌فاکتور",R.drawable.design_search)}
            if(!busy)item {Text(faNumber(visible.size)+" پیش‌فاکتور",color=Muted,fontSize=12.sp)}
        }
        if(error.isNotBlank())item {ErrorBanner(error){error=""};GhadirButton("تلاش دوباره",{retry++},secondary=true)}
        if(busy&&orders.isEmpty())item {LoadingPane()}
        if(visible.isEmpty()&&!busy&&error.isBlank())item {EmptyState("پیش‌فاکتوری پیدا نشد","پیش‌فاکتور سفارش‌های شما اینجا نمایش داده می‌شود.")}
        items(visible,key={it.i("id")}) {order->
            GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(if(selected==null)faDigits(order.s("number")) else "پیش‌فاکتور قدیر پارتنر",color=Ink,fontSize=20.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
                Text((if(selected!=null)faDigits(order.s("number"))+" • " else "")+formatDateFa(order.s("created_at")),color=Muted,fontSize=12.sp)
                HorizontalDivider(color=Border)
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("مشتری: "+me.obj("customer").s("name"),color=Ink,fontSize=13.sp)
                    Text("شرکت: "+me.obj("customer").s("company"),color=Ink,fontSize=13.sp)
                    Text("شماره تماس: "+faDigits(me.s("username")),color=Ink,fontSize=13.sp)
                }
                HorizontalDivider(color=Border)
                order.arr("items").objects().forEach {line->
                    DetailLine(shortProductName(line.s("product")),faNumber(line.i("qty"))+" دستگاه")
                    DetailLine("قیمت واحد",formatMoney(line.l("unit_price")))
                }
                DetailLine("مجموع",formatMoney(order.l("approved_total").takeIf{it>0}?:order.l("estimated_total"))+" تومان")
                if(selected==null) {
                    GhadirButton("مشاهده پیش‌فاکتور",{onOpen(order)},secondary=true)
                    GhadirButton("دانلود PDF پیش‌فاکتور",{download(order)},enabled=!busy)
                    GhadirButton("اشتراک‌گذاری",{share(order)},enabled=!busy,secondary=true)
                    Text("پیش‌فاکتور؛ به معنی پرداخت یا رزرو کالا نیست.",color=Muted,fontSize=12.sp)
                }
            }}
            if(selected!=null) {
                Spacer(Modifier.height(16.dp));DesignNotice("پیش‌فاکتور؛ به معنی پرداخت یا رزرو کالا نیست.")
                Spacer(Modifier.height(16.dp));GhadirButton("دانلود PDF پیش‌فاکتور",{download(order)},enabled=!busy)
                Spacer(Modifier.height(16.dp));GhadirButton("اشتراک‌گذاری",{share(order)},enabled=!busy,secondary=true)
            }
        }
    }
}
@Composable
fun OrderDeliveryTimeline(status: String) {
    val labels=listOf("ثبت","آماده‌سازی","ارسال","تحویل")
    val current=when {status=="تحویل شد"->3;status=="ارسال شد"->2;status.contains("آماده")->1;else->0}
    GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("مراحل تحویل",fontWeight=FontWeight.Bold,color=Ink,fontSize=20.sp)
        labels.forEachIndexed {i,label->
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(i<=current&&!status.contains("لغو"))androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.design_delivered),null,Modifier.size(22.dp))
                else Text("○",color=Muted,modifier=Modifier.width(22.dp))
                Text(label,color=Ink,fontSize=14.sp,lineHeight=23.sp)
            }
        }
    }}
}

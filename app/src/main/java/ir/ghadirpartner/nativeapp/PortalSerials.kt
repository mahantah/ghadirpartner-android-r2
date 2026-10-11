package ir.ghadirpartner.nativeapp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun PortalSerials(api:ApiClient,order:JSONObject,me:JSONObject,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    if(order.s("status")!="تحویل شد") {
        Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DesignNotice("دریافت سریال‌ها فقط برای سفارش تحویل‌شده امکان‌پذیر است.")
            GhadirButton("بازگشت به جزئیات سفارش",onBack,secondary=true)
        }
        return
    }
    fun download() {
        if(busy)return
        scope.launch {
            busy=true;error=""
            try {
                val path=api.saveSerialsPdf(order)
                Toast.makeText(context,"PDF سریال‌ها ذخیره شد: $path",Toast.LENGTH_LONG).show()
            }catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message?:"فایل سریال‌ها دریافت نشد"}finally{busy=false}
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(error.isNotBlank()) {
            item {DesignState("دریافت PDF ناموفق بود","فایل سریال‌ها دریافت نشد. سفارش و اطلاعات شما محفوظ است.",R.drawable.design_download_error)}
            item {DesignNotice(faDigits(order.s("number"))+"\n\nPDF سریال مشتری • "+orderItemsDescription(order))}
            item {Text(error,color=Danger,fontSize=12.sp)}
            item {GhadirButton("تلاش دوباره برای دانلود",{download()},enabled=!busy)}
            item {GhadirButton("بازگشت به جزئیات سفارش",onBack,secondary=true)}
        } else {
            item {DesignNotice("سریال‌های سفارش تحویل‌شده\n\n"+faDigits(order.s("number")))}
            item {GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("مشتری: "+order.s("customer_name").ifBlank {me.obj("customer").s("name")},color=Ink,fontWeight=FontWeight.Bold)
                Text("شرکت: "+order.s("customer_company").ifBlank {me.obj("customer").s("company")},color=Ink,fontWeight=FontWeight.Bold)
                order.arr("items").objects().forEach {line ->
                    Text(shortProductName(line.s("product"))+" • "+faNumber(line.i("qty"))+" دستگاه",color=Muted,fontSize=14.sp)
                    HorizontalDivider(color=Border)
                    val serials=line.arr("serials")
                    for(i in 0 until serials.length()) {
                        val raw=serials.opt(i)
                        GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("SN: "+if(raw is JSONObject)raw.s("serial") else raw?.toString().orEmpty(),color=Ink,fontSize=14.sp)
                            Text("IMEI: "+if(raw is JSONObject)raw.s("imei").ifBlank {"ثبت نشده"} else "ثبت نشده",color=Muted,fontSize=14.sp)
                        }}
                    }
                }
            }}}
            item {GhadirButton(if(busy)"در حال ساخت PDF…" else "دانلود PDF سریال مشتری",{download()},enabled=!busy)}
            item {Text("فقط سریال‌های مربوط به سفارش شما در فایل درج می‌شوند.",color=Muted,fontSize=12.sp)}
        }
    }
}

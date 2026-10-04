package ir.ghadirpartner.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal fun asciiDigits(value:String)=value.map {if(it.isDigit()) Character.digit(it,10).toString() else it.toString()}.joinToString("")
internal fun settlementError(method:String,d:JSONObject):String = when {
    method=="check" && !d.s("check_number").matches(Regex("[0-9]{16}"))->"شناسه صیادی باید ۱۶ رقم باشد"
    method=="check" && (d.s("bank_name").isBlank()||d.s("due_date").isBlank())->"بانک و سررسید چک را وارد کنید"
    method=="credit" && !d.b("terms_accepted")->"شرایط درخواست اعتبار را بپذیرید"
    else->""
}

@Composable
internal fun SettlementForm(api:ApiClient,method:String,amount:Long,value:JSONObject,onChange:(JSONObject)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    fun set(key:String,v:Any){onChange(JSONObject(value.toString()).put(key,v))}
    val tracking=value.s("tracking_number");val sayad=value.s("check_number")
    val bank=value.s("bank_name");val due=value.s("due_date")
    val accepted=value.b("terms_accepted");val image=value.s("image_base64")
    var credit by remember {mutableStateOf(JSONObject())};var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    LaunchedEffect(method) {if(method=="credit")try{credit=api.get("/api/native/credit") as JSONObject}catch(e:Exception){error=e.message?:"دریافت اعتبار ناموفق بود"}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri:Uri?->if(uri!=null)scope.launch{
        busy=true;error=""
        try {val encoded=withContext(Dispatchers.IO){
            val resolver=context.contentResolver
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
            require(bounds.outWidth>0&&bounds.outHeight>0){"تصویر معتبر نیست"}
            val options=BitmapFactory.Options().apply{inSampleSize=1;while(bounds.outWidth/inSampleSize>1600||bounds.outHeight/inSampleSize>1600)inSampleSize*=2}
            val bitmap=resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:throw IllegalArgumentException("تصویر خوانده نشد")
            val stream=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,80,stream);bitmap.recycle()
            require(stream.size()<1_000_000){"حجم تصویر زیاد است؛ تصویر کوچک‌تری انتخاب کنید"}
            Base64.encodeToString(stream.toByteArray(),Base64.NO_WRAP)
        };set("image_base64",encoded)}catch(e:Exception){error=e.message?:"انتخاب تصویر ناموفق بود"}finally{busy=false}
    }}
    GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(when(method){"check"->"تسویه با چک";"credit"->"تسویه اعتباری";else->"تسویه نقدی"},color=Ink,fontWeight=FontWeight.Bold)
        Text("مبلغ سفارش: ${formatMoney(amount)} تومان",color=Ink)
        if(method=="cash")OutlinedTextField(tracking,{set("tracking_number",asciiDigits(it).take(100))},label={Text("شماره پیگیری انتقال — در صورت پرداخت")},modifier=Modifier.fillMaxWidth())
        if(method=="check") {
            OutlinedTextField(sayad,{set("check_number",asciiDigits(it).filter(Char::isDigit).take(16))},label={Text("شناسه صیادی ۱۶ رقمی")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(bank,{set("bank_name",it.take(80))},label={Text("بانک")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(due,{set("due_date",asciiDigits(it).take(30))},label={Text("سررسید — تاریخ شمسی")},placeholder={Text("۱۴۰۵/۰۷/۲۶")},modifier=Modifier.fillMaxWidth())
        }
        if(method!="credit") {
            GhadirButton(if(busy)"در حال آماده‌سازی تصویر…" else if(image.isNotBlank())"تصویر انتخاب شد؛ تغییر تصویر" else if(method=="check")"بارگذاری تصویر چک" else "بارگذاری رسید پرداخت",{picker.launch("image/*")},enabled=!busy,secondary=true)
            if(image.isNotBlank())TextButton(onClick={set("image_base64","")}){Text("حذف تصویر")}
        } else {
            Text(if(credit.b("configured")) "اعتبار قابل استفاده: ${formatMoney(credit.l("available"))} تومان" else "سقف اعتبار برای این حساب ثبت نشده است؛ درخواست شما توسط واحد مالی بررسی می‌شود.",color=Muted)
            if(credit.b("configured"))Text("مانده پس از سفارش: ${formatMoney(credit.l("available")-amount)} تومان",color=Ink)
            if(credit.s("due_date").isNotBlank())Text("سررسید توافق‌شده: "+credit.s("due_date"),color=Ink)
            Row {Checkbox(accepted,{set("terms_accepted",it)});Text("شرایط درخواست اعتبار و سررسید توافق‌شده را می‌پذیرم. تأیید نهایی با واحد مالی است.",color=Ink)}
        }
        Text("ثبت اطلاعات به معنی تأیید تسویه یا مجوز ارسال نیست.",color=Muted)
        if(error.isNotBlank())Text(error,color=Danger)
    }}
}

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
import androidx.compose.ui.unit.sp
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
internal fun SettlementForm(api:ApiClient,method:String,amount:Long,value:JSONObject,onBusyChanged:(Boolean)->Unit={},onChange:(JSONObject)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val currentMethod by rememberUpdatedState(method)
    fun set(key:String,v:Any){onChange(JSONObject(value.toString()).put(key,v))}
    var credit by remember {mutableStateOf(JSONObject())}
    var creditLoaded by remember {mutableStateOf(false)}
    var creditLoading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(busy,creditLoading){onBusyChanged(busy||creditLoading)}
    DisposableEffect(Unit){onDispose{onBusyChanged(false)}}
    LaunchedEffect(method,retry) {
        error=""
        if(method=="credit") {
            creditLoading=true;creditLoaded=false
            try{credit=api.get("/api/native/credit") as JSONObject;creditLoaded=true}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"دریافت اعتبار ناموفق بود"}finally{creditLoading=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {uri:Uri?->
        if(uri!=null)scope.launch {
            val selectionMethod=method
            busy=true;error=""
            try {
                val encoded=withContext(Dispatchers.IO) {
                    val resolver=context.contentResolver
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                    require(bounds.outWidth>0&&bounds.outHeight>0){"تصویر معتبر نیست"}
                    val options=BitmapFactory.Options().apply{inSampleSize=1;while(bounds.outWidth/inSampleSize>1600||bounds.outHeight/inSampleSize>1600)inSampleSize*=2}
                    val bitmap=resolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:throw IllegalArgumentException("تصویر خوانده نشد")
                    try {
                        val stream=ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG,80,stream)
                        require(stream.size()<1_000_000){"حجم تصویر زیاد است؛ تصویر کوچک‌تری انتخاب کنید"}
                        Base64.encodeToString(stream.toByteArray(),Base64.NO_WRAP)
                    }finally{bitmap.recycle()}
                }
                if(selectionMethod==currentMethod)set("image_base64",encoded)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){error=e.message?:"انتخاب تصویر ناموفق بود"}finally{busy=false}
        }
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(method=="credit") {
            GlassSurface {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("اعتبار حساب شما",color=Ink,fontWeight=FontWeight.Bold,fontSize=20.sp)
                when {
                    creditLoading->Text("در حال دریافت اعتبار…",color=Muted)
                    creditLoaded&&credit.b("configured")->{
                        DetailLine("اعتبار قابل استفاده",formatMoney(credit.l("available"))+" تومان")
                        DetailLine("مبلغ سفارش",formatMoney(amount)+" تومان")
                        DetailLine("مانده پس از ثبت",formatMoney(credit.l("available")-amount)+" تومان")
                    }
                    creditLoaded->Text("سقف اعتبار برای این حساب ثبت نشده است؛ درخواست شما توسط واحد مالی بررسی می‌شود.",color=Muted)
                    else->Text("اطلاعات اعتبار دریافت نشده است.",color=Muted)
                }
            }}
            if(creditLoaded&&credit.s("due_date").isNotBlank())DesignField("سررسید توافق‌شده",credit.s("due_date"),{},icon=R.drawable.design_calendar,enabled=false)
            DesignNotice("تعهد تسویه\n\nشرایط اعتبار و سررسید را پیش از ثبت بررسی کنید. تأیید نهایی درخواست با واحد مالی است.")
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(value.b("terms_accepted"),{set("terms_accepted",it)})
                Text("شرایط اعتبار و سررسید توافق‌شده را می‌پذیرم.",color=Ink,modifier=Modifier.weight(1f))
            }
            if(error.isNotBlank())GhadirButton("دریافت دوباره اعتبار",{retry++},secondary=true,enabled=!creditLoading)
        } else {
            DesignNotice(if(method=="check")"تسویه با چک\n\nاطلاعات چک برای بررسی واحد مالی ثبت می‌شود." else "تسویه نقدی\n\nثبت رسید پرداخت برای بررسی واحد مالی.")
            DesignField(if(method=="check")"مبلغ چک" else "مبلغ",formatMoney(amount)+" تومان",{},enabled=false)
            if(method=="check") {
                DesignField("شناسه صیادی",value.s("check_number"),{set("check_number",asciiDigits(it).filter(Char::isDigit).take(16))},"شناسه ۱۶ رقمی",keyboardType=androidx.compose.ui.text.input.KeyboardType.Number,enabled=!busy)
                DesignField("بانک",value.s("bank_name"),{set("bank_name",it.take(80))},"نام بانک",enabled=!busy)
                DesignField("سررسید",value.s("due_date"),{set("due_date",asciiDigits(it).take(30))},"تاریخ شمسی",R.drawable.design_calendar,enabled=!busy)
            } else DesignField("شماره پیگیری",value.s("tracking_number"),{set("tracking_number",asciiDigits(it).take(100))},"شماره پیگیری انتقال — در صورت پرداخت",enabled=!busy)
            if(method=="cash")Text("تصویر رسید پرداخت را برای بررسی بارگذاری کنید.",color=Muted)
            GhadirButton(if(busy)"در حال آماده‌سازی تصویر…" else if(value.s("image_base64").isNotBlank())"تصویر انتخاب شد؛ تغییر تصویر" else if(method=="check")"بارگذاری تصویر چک" else "بارگذاری رسید پرداخت",{picker.launch("image/*")},enabled=!busy,secondary=true)
            if(value.s("image_base64").isNotBlank())TextButton(onClick={set("image_base64","")}){Text("حذف تصویر",color=Danger)}
            DesignNotice(if(method=="check")"ثبت چک به معنی تأیید تسویه و مجوز ارسال نیست." else "ارسال سفارش پس از تأیید تسویه انجام می‌شود.")
        }
        if(error.isNotBlank())Text(error,color=Danger)
    }
}

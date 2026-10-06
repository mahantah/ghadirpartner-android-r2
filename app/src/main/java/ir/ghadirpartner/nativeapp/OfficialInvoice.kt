package ir.ghadirpartner.nativeapp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/** Landscape printable form; amounts retain server meaning, displayed in rial. */
internal object OfficialInvoice {
    private val widths = floatArrayOf(24f,68f,172f,36f,40f,74f,74f,50f,48f,70f,62f,68f)
    private val headings = listOf("ردیف","کد کالا","شرح کالا / خدمت","تعداد / مقدار","واحد اندازه‌گیری","مبلغ واحد","مبلغ کل","مبلغ تخفیف","مبلغ اضافات","مبلغ پس از تخفیف و اضافات","مالیات و عوارض","خالص فاکتور")
    private fun words(n:Long):String {
        if(n==0L)return "صفر"
        if(n<0)return "منفی "+words(-n)
        val small=listOf("","یک","دو","سه","چهار","پنج","شش","هفت","هشت","نه","ده","یازده","دوازده","سیزده","چهارده","پانزده","شانزده","هفده","هجده","نوزده")
        val tens=listOf("","","بیست","سی","چهل","پنجاه","شصت","هفتاد","هشتاد","نود")
        val hundreds=listOf("","یکصد","دویست","سیصد","چهارصد","پانصد","ششصد","هفتصد","هشتصد","نهصد")
        fun chunk(v:Int):String { val parts=mutableListOf<String>();if(v>=100)parts.add(hundreds[v/100]);val r=v%100;if(r in 1..19)parts.add(small[r])else if(r>=20){parts.add(tens[r/10]);if(r%10>0)parts.add(small[r%10])};return parts.joinToString(" و ") }
        val scales=listOf("","هزار","میلیون","میلیارد","تریلیون","هزار تریلیون","میلیون تریلیون")
        var rest=n;var level=0;val parts=mutableListOf<String>();while(rest>0){val c=(rest%1000).toInt();if(c>0)parts.add((chunk(c)+" "+scales[level]).trim());rest/=1000;level++};return parts.reversed().joinToString(" و ")
    }
    private fun value(o: JSONObject, key: String) = o.optString(key).takeUnless { it.isBlank() || it=="null" } ?: "—"
    private fun layout(text: String, width: Float, size: Float=8f, bold: Boolean=false): StaticLayout {
        val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=size;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL) }
        return StaticLayout.Builder.obtain(text,0,text.length,p,(width-8f).toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(false).setLineSpacing(1f,1f).build()
    }
    fun render(doc: PdfDocument, order: JSONObject, customer: JSONObject) {
        val multiplier=if(order.optString("currency").equals("IRR",true))1L else 10L
        fun money(n: Long)=faDigits(NumberFormat.getIntegerInstance(Locale.US).format(Math.multiplyExact(n,multiplier)))
        fun optionalMoney(o:JSONObject,k:String)=if(o.has(k)&&!o.isNull(k))money(o.optLong(k)) else "—"
        val items=order.optJSONArray("items")
        val rows=(0 until (items?.length()?:0)).map {i ->
            val it=items!!.getJSONObject(i);val qty=it.optLong("qty");val unit=it.optLong("unit_price")
            val total=if(it.has("line_total"))it.optLong("line_total") else Math.multiplyExact(qty,unit)
            val net=total-it.optLong("discount_amount")+it.optLong("additional_amount")
            listOf(faNumber(i+1),value(it,"product_code"),value(it,"product"),faNumber(qty),it.optString("unit").ifBlank{"عدد"},money(unit),money(total),optionalMoney(it,"discount_amount"),optionalMoney(it,"additional_amount"),money(net),optionalMoney(it,"tax_amount"),if(it.has("tax_amount"))money(net+it.optLong("tax_amount"))else "—")
        }
        val heights=rows.map {r -> maxOf(28f,r.indices.maxOf {layout(r[it],widths[it],7.4f).height.toFloat()}+10f) }
        require(heights.all {it<=140f}) {"شرح یکی از اقلام برای یک ردیف چاپی بیش از حد طولانی است"}
        val groups=mutableListOf<List<Int>>();var group=mutableListOf<Int>();var used=0f
        rows.indices.forEach {i -> if(used+heights[i]>140f&&group.isNotEmpty()){groups.add(group);group=mutableListOf();used=0f};group.add(i);used+=heights[i] }
        if(group.isNotEmpty()||groups.isEmpty())groups.add(group)
        groups.forEachIndexed {pageIndex,indices ->
            val page=doc.startPage(PdfDocument.PageInfo.Builder(842,595,pageIndex+1).create());val c=page.canvas
            val border=Paint().apply{color=Color.BLACK;style=Paint.Style.STROKE;strokeWidth=.65f}
            fun box(x:Float,y:Float,w:Float,h:Float,text:String="",size:Float=8f,bold:Boolean=false,gray:Boolean=false) {
                if(gray)c.drawRect(x,y,x+w,y+h,Paint().apply{color=Color.rgb(245,245,245)})
                c.drawRect(x,y,x+w,y+h,border)
                if(text.isNotBlank()){val l=layout(text,w,size,bold);c.save();c.translate(x+4,y+maxOf(3f,(h-l.height)/2));l.draw(c);c.restore()}
            }
            fun label(text:String,x:Float,y:Float,w:Float,size:Float=8f,bold:Boolean=false){val l=layout(text,w,size,bold);c.save();c.translate(x+4,y);l.draw(c);c.restore()}
            label("پیش‌فاکتور فروش کالا و خدمات",260f,29f,400f,17f,true)
            box(28f,24f,150f,16f,"شماره: ${value(order,"number")}")
            box(28f,40f,150f,16f,"تاریخ: ${formatDateFa(order.optString("created_at"))}")
            box(28f,56f,150f,16f,"اعتبار: ${order.optString("valid_until").takeIf {it.isNotBlank()}?.let {formatDateFa(it)}?:"—"}")
            box(28f,80f,786f,15f,"مشخصات فروشنده",9f,true,true)
            box(28f,95f,786f,56f)
            label("نام شخص حقیقی / حقوقی: هوشمند پرداز پویا تجارت قدیر     شماره اقتصادی: ۱۴۰۱۰۳۶۶۳۹۵     شماره ثبت: ۵۸۴۵۵۹     شناسه ملی: ۱۴۰۱۰۳۶۶۳۹۵",33f,102f,776f,8.2f)
            label("کد پستی: ۱۶۸۷۶۸۷۵۱۸      شماره تلفن: ۰۲۱–۷۷۲۴۸۷۱۶      شماره فکس: —",33f,117f,776f)
            label("نشانی: تهران، تهرانپارس غربی، خیابان سراج جنوبی، روبه‌روی گلستان دوم، پلاک ۲۱۸، واحد ۵",33f,134f,776f)
            box(28f,151f,786f,15f,"مشخصات خریدار",9f,true,true)
            box(28f,166f,786f,65f)
            val address=order.optJSONObject("delivery_address")?:customer
            val name=customer.optString("company").ifBlank{customer.optString("name").ifBlank{order.optString("customer_name")}}
            label("نام شخص حقیقی / حقوقی: $name       شماره ثبت / ملی: ${value(customer,"national_id")}       شماره اقتصادی: ${value(customer,"economic_code")}",33f,173f,776f,8.5f)
            label("استان: ${value(address,"province")}     شهر: ${value(address,"city")}     کد پستی: ${value(address,"postal_code")}     تلفن: ${value(customer,"mobile")}",33f,190f,776f)
            label("نشانی: ${value(address,"address")}",33f,207f,776f)
            box(28f,231f,786f,16f,"مشخصات کالا یا خدمات مورد معامله — تمام مبالغ به ریال",9f,true,true)
            var x=814f;headings.indices.forEach {i->x-=widths[i];box(x,247f,widths[i],36f,headings[i],7.4f,true)}
            var y=283f
            indices.forEach {idx->x=814f;rows[idx].indices.forEach{i->x-=widths[i];box(x,y,widths[i],heights[idx],rows[idx][i],7.4f)};y+=heights[idx]}
            if(pageIndex==groups.lastIndex){
                val subtotal=if(order.has("subtotal_before_discount"))order.optLong("subtotal_before_discount")else (0 until (items?.length()?:0)).sumOf {items!!.getJSONObject(it).optLong("line_total")}
                val total=if(order.optLong("approved_total")>0)order.optLong("approved_total")else order.optLong("estimated_total")
                box(28f,y,786f,21f,"جمع کل: ${money(subtotal)}     تخفیف: ${optionalMoney(order,"discount_amount")}     مالیات و عوارض: ${optionalMoney(order,"tax_amount")}     مبلغ نهایی سفارش: ${money(total)}",8.5f,true,true);y+=21f
                box(28f,y,786f,25f,"شرایط و نحوه فروش: ${order.optString("requested_payment_method_label").ifBlank{order.optString("payment_method_label").ifBlank{"—"}}}     مبلغ به حروف: ${words(Math.multiplyExact(total,multiplier))} ریال",8f);y+=25f
                val note=if(!order.has("tax_amount"))"مالیات و عوارض در اطلاعات سفارش تفکیک نشده است؛ مبلغ نهایی عیناً از سفارش درج شده است." else "مبالغ بر اساس اطلاعات ثبت‌شده سفارش است."
                box(28f,y,786f,47f,"توضیحات: $note\nتسویه به حساب حقوقی بانک اقتصاد نوین، به نام هوشمند پرداز پویا تجارت قدیر\nشماره شبا: IR950550014100207494885001",8f);y+=47f
                box(28f,y,393f,33f,"مهر و امضای خریدار");box(421f,y,393f,33f,"مهر و امضای فروشنده")
            }else box(28f,y,786f,20f,"ادامه اقلام و جمع نهایی در صفحه بعد",8f)
            label("صفحه ${faNumber(pageIndex+1)} از ${faNumber(groups.size)}",28f,574f,160f)
            doc.finishPage(page)
        }
    }
}

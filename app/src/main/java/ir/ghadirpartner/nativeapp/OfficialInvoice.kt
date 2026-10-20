package ir.ghadirpartner.nativeapp

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/** Vector A4 landscape form, measured in points from the customer's reference PDF.
 * Both invoice types use this form; only an explicitly official order receives the stamp.
 * Amounts come from the order, never from a sample or an assumed tax percentage.
 */
internal object OfficialInvoice {
    private const val LEFT = 28.43f
    private const val RIGHT = 813.83f
    private const val WIDTH = RIGHT - LEFT
    private const val ROW = 14.16f
    private val widths = floatArrayOf(27f,78f,106.2f,35.52f,63.72f,56.76f,70.8f,56.76f,56.64f,78f,63.84f,92.16f)
    private val headings = listOf("ردیف","کد کالا","شرح کالا/ خدمت","تعداد/\nمقدار","واحد اندازه\nگیری","مبلغ واحد","مبلغ کل","مبلغ تخفیف","مبلغ اضافات","مبلغ کل پس از\nتخفیف و اضافات","جمع مالیات و\nعوارض","خالص فاکتور")

    internal fun isOfficial(order: JSONObject): Boolean =
        order.optString("invoice_type").replace("\u200c", "").replace(" ", "").trim().lowercase(Locale.ROOT) in setOf("رسمی", "official")
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
    private fun value(o: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> o.optString(key).takeUnless { it.isBlank() || it == "null" } } ?: "—"

    private class Form(val canvas: Canvas, val face: Typeface) {
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;style=Paint.Style.STROKE;strokeWidth=.7f }
        fun layout(text:String, width:Float, size:Float=8.4f, bold:Boolean=false, align:Layout.Alignment=Layout.Alignment.ALIGN_CENTER):StaticLayout {
            val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=size;typeface=Typeface.create(face,if(bold)Typeface.BOLD else Typeface.NORMAL) }
            return StaticLayout.Builder.obtain(text,0,text.length,paint,(width-4f).toInt().coerceAtLeast(1))
                .setAlignment(align).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setIncludePad(false).setLineSpacing(0f,1f).build()
        }
        fun text(text:String,x:Float,y:Float,w:Float,h:Float=ROW,size:Float=8.4f,bold:Boolean=false,right:Boolean=false) {
            val l=layout(text,w,size,bold,if(right)Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            canvas.save();canvas.translate(x+2,y+maxOf(0f,(h-l.height)/2f));l.draw(canvas);canvas.restore()
        }
        fun box(x:Float,y:Float,w:Float,h:Float,text:String="",size:Float=8.4f,bold:Boolean=false,gray:Boolean=false) {
            if(gray)canvas.drawRect(x,y,x+w,y+h,Paint().apply{color=Color.rgb(245,245,245)})
            canvas.drawRect(x,y,x+w,y+h,border)
            if(text.isNotBlank())text(text,x,y,w,h,size,bold)
        }
        fun field(label:String,value:String,x:Float,y:Float,w:Float,labelWidth:Float,h:Float=ROW) {
            text(label,x+w-labelWidth,y,labelWidth,h,right=true)
            text(faDigits(value),x,y,w-labelWidth,h)
        }
        fun banner(y:Float,title:String) = box(LEFT,y,WIDTH,ROW,title,8.6f,true,true)
    }

    fun render(doc: PdfDocument, order: JSONObject, customer: JSONObject, context: Context) {
        val face=ResourcesCompat.getFont(context,R.font.vazirmatn) ?: Typeface.create("sans-serif",Typeface.NORMAL)
        // A measuring canvas uses exactly the same font and widths as the printed form.
        val measure=Form(Canvas(),face)
        val multiplier=if(order.optString("currency").equals("IRR",true))1L else 10L
        fun money(n:Long)=faDigits(NumberFormat.getIntegerInstance(Locale.US).format(Math.multiplyExact(n,multiplier)))
        fun optionalMoney(o:JSONObject,k:String)=if(o.has(k)&&!o.isNull(k))money(o.getLong(k))else "—"
        val items=order.optJSONArray("items")
        val lines=(0 until (items?.length()?:0)).map {items!!.getJSONObject(it)}
        fun lineTotal(o:JSONObject)=if(o.has("line_total"))o.getLong("line_total")else Math.multiplyExact(o.optLong("qty"),o.optLong("unit_price"))
        val rows=lines.mapIndexed {i,item ->
            val total=lineTotal(item)
            val net=total-item.optLong("discount_amount")+item.optLong("additional_amount")
            listOf(faNumber(i+1),faDigits(value(item,"product_code")),value(item,"product"),faNumber(item.optLong("qty")),item.optString("unit").ifBlank{"عدد"},money(item.optLong("unit_price")),money(total),optionalMoney(item,"discount_amount"),optionalMoney(item,"additional_amount"),money(net),optionalMoney(item,"tax_amount"),if(item.has("tax_amount")&&!item.isNull("tax_amount"))money(net+item.getLong("tax_amount"))else "—")
        }
        val heights=rows.map {r -> maxOf(ROW,r.indices.maxOf {measure.layout(r[it],widths[it],7.8f).height.toFloat()}+3f) }
        val address=order.optJSONObject("delivery_address")?:customer
        val name=value(customer,"company","name").takeUnless{it=="—"}?:value(order,"customer_name")
        val nameExtra=maxOf(0f,measure.layout(name,246f).height-ROW)
        val addressHeight=maxOf(ROW,measure.layout(value(address,"address"),730f).height.toFloat()+2f)
        val addressExtra=addressHeight-ROW
        val buyerExtra=nameExtra+addressExtra
        val tableY=248.04f+buyerExtra
        val bodyY=tableY+35.52f
        val subtotal=if(order.has("subtotal_before_discount"))order.getLong("subtotal_before_discount")else lines.sumOf{lineTotal(it)}
        val total=order.optLong("approved_total").takeIf{it>0}?:order.optLong("estimated_total")
        fun aggregate(key:String):String = when {
            order.has(key)&&!order.isNull(key)->money(order.getLong(key))
            lines.isNotEmpty()&&lines.all{it.has(key)&&!it.isNull(key)}->money(lines.sumOf{it.getLong(key)})
            else->"—"
        }
        val discount=aggregate("discount_amount")
        val additional=aggregate("additional_amount")
        val tax=aggregate("tax_amount")
        val net=if(discount!="—"&&additional!="—")money(subtotal-(if(order.has("discount_amount"))order.getLong("discount_amount")else lines.sumOf{it.getLong("discount_amount")})+(if(order.has("additional_amount"))order.getLong("additional_amount")else lines.sumOf{it.getLong("additional_amount")}))else "—"
        val totalWords=words(Math.multiplyExact(total,multiplier))+" ریال"
        val paymentHeight=maxOf(27.6f,measure.layout(totalWords,537.27f).height+8f)
        val bankNotes="همکار عزیز نسبت به تسویه مبلغ فاکتور به صورت نقدی به حساب زیر واریز گردد:\n*بانک مهرایران* حقوقی\n*شماره شبا:* \u2066IR200600000000100438838749\u2069\n*نام صاحب حساب:* هوشمند پرداز پویا تجارت قدیر"
        val notes=listOf(bankNotes,order.optString("notes").takeUnless{it.isBlank()||it=="null"}).filterNotNull().joinToString("\n")
        val noteHeight=maxOf(71.64f,measure.layout(notes,680f,8f,align=Layout.Alignment.ALIGN_NORMAL).height+20f)
        val footerHeight=ROW+paymentHeight+noteHeight+35.4f
        val available=550f-bodyY-footerHeight
        require(available>=ROW*3 && heights.all{it<=available}) {"اطلاعات فاکتور برای یک صفحه چاپی بیش از حد طولانی است"}
        // Reserve the complete totals/payment/notes/signature block on every page.
        // This keeps long invoices inside the paper and repeats the reference header.
        val groups=mutableListOf<List<Int>>()
        var group=mutableListOf<Int>();var used=0f
        rows.indices.forEach {i ->
            if(used+heights[i]>available&&group.isNotEmpty()){groups.add(group);group=mutableListOf();used=0f}
            group.add(i);used+=heights[i]
        }
        if(group.isNotEmpty()||groups.isEmpty())groups.add(group)
        val official=isOfficial(order)
        val stamp=if(official)BitmapFactory.decodeResource(context.resources,R.drawable.invoice_sales_stamp)else null
        try {
            groups.forEachIndexed {pageIndex,indices ->
                val page=doc.startPage(PdfDocument.PageInfo.Builder(842,595,pageIndex+1).create())
                val f=Form(page.canvas,face)
                f.text("پیش فاکتور فروش کالا و خدمات",260f,30f,400f,23f,16f,true)
                listOf("شماره سریال :" to value(order,"number"),"تاریخ :" to formatDateFa(order.optString("created_at")),"تاریخ اعتبار:" to (order.optString("valid_until").takeIf{it.isNotBlank()&&it!="null"}?.let{formatDateFa(it)}?:"—")).forEachIndexed {i,pair ->
                    val y=28.32f+i*ROW
                    f.box(LEFT,y,70.92f,ROW,faDigits(pair.second),8.2f)
                    f.text(pair.first,105f,y,64f,right=true)
                }
                f.banner(78f,"مشخصات فروشنده")
                f.box(LEFT,92.16f,WIDTH,56.76f)
                f.field("نام شخص حقیقی/حقوقی :","هوشمند پرداز پویا تجارت قدیر",558f,93.2f,252f,112f)
                f.field("شماره اقتصادی :","۱۴۰۱۰۳۶۶۳۹۵",396f,93.2f,160f,84f)
                f.field("شماره ثبت /شماره ملی:","۵۸۴۵۵۹",210f,93.2f,185f,120f)
                f.field("شناسه ملی:","۱۴۰۱۰۳۶۶۳۹۵",32f,93.2f,146f,61f)
                f.field("کد پستی:","۱۶۸۷۶۸۷۵۱۸",295f,107.36f,113f,42f)
                f.field("شماره تلفن :","۰۲۱-۷۷۲۴۸۷۱۶",163f,107.36f,129f,58f)
                f.field("شماره فکس:","",40f,107.36f,121f,58f)
                f.field("نشانی :","تهران، تهرانپارس غربی، خیابان سراج جنوبی، پایین تر از خیابان دلاوران، روبروی گلستان دوم، پلاک ۲۱۸، واحد ۵",32f,128.6f,778f,46f)
                f.banner(148.92f,"مشخصات خریدار")
                f.box(LEFT,163.08f,WIDTH,70.8f+buyerExtra)
                f.field("نام شخص حقیقی/حقوقی :",name,448f,164f,363f,117f,ROW+nameExtra)
                f.field("شماره ثبت /شماره ملی:",value(customer,"registration_number","national_id"),84f,171.1f+nameExtra,228f,123f)
                f.field("شماره اقتصادی :",value(customer,"economic_code"),330f,185.32f+nameExtra,220f,93f)
                f.field("شهر :",value(address,"city"),150f,185.32f+nameExtra,162f,44f)
                f.field("نشانی کامل استان :",value(address,"province"),600f,199.48f+nameExtra,211f,98f)
                f.field("کد پستی :",value(address,"postal_code"),330f,199.48f+nameExtra,220f,70f)
                f.field("شماره تلفن :",value(customer,"mobile","phone"),150f,199.48f+nameExtra,162f,65f)
                f.field("نشانی :",value(address,"address"),32f,213.64f+nameExtra,779f,49f,addressHeight)
                f.banner(233.88f+buyerExtra,"مشخصات کالا یا خدمات مورد معامله")
                var x=RIGHT
                headings.indices.forEach {i ->
                    x-=widths[i]
                    f.box(x,tableY,widths[i],35.52f,if(i==0)""else headings[i],8.4f)
                    if(i==0){page.canvas.save();page.canvas.translate(x+widths[i]/2,tableY+17.76f);page.canvas.rotate(-90f);f.text(headings[i],-17.76f,-widths[i]/2,35.52f,widths[i],8.4f);page.canvas.restore()}
                }
                var y=bodyY
                indices.forEach {idx ->
                    x=RIGHT
                    rows[idx].indices.forEach {i -> x-=widths[i];f.box(x,y,widths[i],heights[idx],rows[idx][i],7.8f) }
                    y+=heights[idx]
                }
                // The supplied three-line example establishes the minimum table height.
                val minimumBottom=bodyY+ROW*3
                if(y<minimumBottom){x=RIGHT; widths.forEach {w -> x-=w;f.box(x,y,w,minimumBottom-y)};y=minimumBottom}
                if(pageIndex==groups.lastIndex) {
                    f.box(446.63f,y,367.2f,ROW)
                    f.text("جمع کل",447.23f,y,55f,right=true)
                    x=446.63f
                    listOf(money(subtotal),discount,additional,net,tax,money(total)).forEachIndexed {i,v -> x-=widths[i+6];f.box(x,y,widths[i+6],ROW,v,7.8f,gray=true) }
                    y+=ROW
                    f.box(LEFT,y,WIDTH,paymentHeight)
                    f.box(565.7f,y,RIGHT-565.7f,paymentHeight)
                    f.text("شرایط و نحوه فروش",723f,y,89f,paymentHeight,right=true)
                    val method=value(order,"requested_payment_method","payment_method")
                    val paymentLabel=value(order,"requested_payment_method_label","payment_method_label")
                    fun checkBox(bx:Float,label:String,checked:Boolean) {
                        val by=y+(paymentHeight-ROW)/2
                        f.box(bx,by,ROW,ROW)
                        if(checked)f.text("✓",bx,by,ROW,ROW,11f,true)
                        f.text(label,bx+ROW+2,by,43f)
                    }
                    checkBox(666.35f,"نقدی",method=="cash"||paymentLabel in listOf("نقد","نقدی"))
                    checkBox(574.19f,"غیر نقدی",method in listOf("check","credit")||paymentLabel in listOf("چک","اعتباری"))
                    f.text(totalWords,LEFT,y,537.27f,paymentHeight,8.6f,right=true)
                    y+=paymentHeight
                    f.box(LEFT,y,WIDTH,noteHeight)
                    f.text("توضیحات:",752f,y,59f,noteHeight,right=true)
                    f.text(notes,65f,y+8f,685f,noteHeight-16f,8f,right=true)
                    y+=noteHeight
                    f.box(LEFT,y,389.88f,35.4f)
                    f.box(418.31f,y,RIGHT-418.31f,35.4f)
                    f.text("مهر و امضاء خریدار",325f,y,91f,35.4f,8f,right=true)
                    f.text("مهر و امضای فروشنده",716f,y,96f,35.4f,8f,right=true)
                    if(stamp!=null) {
                        // Crop only the transparent margins when drawing; retain the original PNG.
                        // The complete ink fits the seller signature cell without covering its label.
                        page.canvas.drawBitmap(stamp,Rect(18,420,1242,990),RectF(601f,y+1f,673f,y+34.4f),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    }
                } else f.box(LEFT,y,WIDTH,ROW,"ادامه اقلام و جمع نهایی در صفحه بعد",8f)
                f.text("صفحه : ${faNumber(pageIndex+1)}"+(if(groups.size>1)" از ${faNumber(groups.size)}" else ""),LEFT,551f,110f,ROW,9f)
                doc.finishPage(page)
            }
        } finally {stamp?.recycle()}
    }
}

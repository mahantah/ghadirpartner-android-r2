package ir.ghadirpartner.nativeapp

import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import java.io.File

class InvoiceRenderTest {
    @Test fun referenceAndPagination() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val customer=JSONObject("""{"company":"مشتری نمونه — داده آزمایشی","national_id":"—","economic_code":"—","province":"تهران","city":"تهران","address":"نشانی نمونه برای بررسی قالب چاپ","postal_code":"—","mobile":"—"}""")
        val row=JSONObject("""{"product_code":"11","product":"دستگاه کارتخوان I90","qty":1,"unit":"عدد","unit_price":120909091,"line_total":120909091,"discount_amount":0,"additional_amount":0,"tax_amount":12090909}""")
        val order=JSONObject("""{"number":"SAMPLE-308","currency":"IRR","created_at":"2026-09-30","valid_until":"2026-09-30","subtotal_before_discount":120909091,"estimated_total":133000000,"discount_amount":0,"tax_amount":12090909,"requested_payment_method_label":"نقد"}""").put("items",JSONArray().put(row))
        order.put("invoice_type","غیررسمی").put("requested_payment_method","cash")
        val sampleRows=JSONArray()
        repeat(3){i->sampleRows.put(JSONObject(row.toString()).put("product_code",11+i).put("product",listOf("دستگاه کارتخوان Z-990","دستگاه کارتخوان I80","دستگاه کارتخوان I90")[i]))}
        order.put("items",sampleRows).put("subtotal_before_discount",3L*120909091L).put("estimated_total",3L*133000000L).put("tax_amount",3L*12090909L)
        fun write(name:String):File {
            val file=File(context.filesDir,name)
            val pdf=PdfDocument()
            try {OfficialInvoice.render(pdf,order,customer,context);check(pdf.pages.size==1);file.outputStream().use{pdf.writeTo(it)}}finally{pdf.close()}
            return file
        }
        fun bluePixels(file:File):Int {
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd ->
                PdfRenderer(fd).use {renderer ->
                    renderer.openPage(0).use {page ->
                        val bitmap=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                        val count=pixels.count {Color.blue(it)>Color.red(it)+60&&Color.blue(it)>Color.green(it)+60}
                        bitmap.recycle();return count
                    }
                }
            }
        }
        val unofficial=write("invoice-reference.pdf")
        check(!OfficialInvoice.isOfficial(order));check(bluePixels(unofficial)==0)
        order.put("invoice_type","رسمی")
        val official=write("invoice-official.pdf")
        check(OfficialInvoice.isOfficial(order));check(bluePixels(official)>100) {"Official invoice must contain the blue sales stamp"}
        for(type in listOf("غیر رسمی","غیر‌رسمی","unofficial","", "null")){order.put("invoice_type",type);check(!OfficialInvoice.isOfficial(order))}
        // Wrapped customer details/notes must still leave room for totals and signatures.
        order.put("invoice_type","غیررسمی").put("notes","توضیحات ثبت شده مشتری برای بررسی چاپ و ارسال سفارش. ".repeat(9))
        customer.put("address","تهران، خیابان نمونه، ساختمان آزمایشی با نشانی طولانی برای بررسی سطرهای چاپی و جلوگیری از همپوشانی متن، واحد دوازده. ".repeat(2))
        write("invoice-long-fields.pdf")
        order.remove("notes")
        val rows=JSONArray();repeat(24){i->rows.put(JSONObject(row.toString()).put("product","دستگاه کارتخوان مدل بلند آزمایشی شماره ${i+1} با گارانتی و لوازم کامل"))};order.put("items",rows).put("number","PAGINATION-24").put("subtotal_before_discount",24L*120909091L).put("estimated_total",24L*133000000L).put("tax_amount",24L*12090909L)
        val many=PdfDocument();try {OfficialInvoice.render(many,order,customer,context);check(many.pages.size>1);File(context.filesDir,"invoice-pagination.pdf").outputStream().use{many.writeTo(it)}}finally{many.close()}
    }
}

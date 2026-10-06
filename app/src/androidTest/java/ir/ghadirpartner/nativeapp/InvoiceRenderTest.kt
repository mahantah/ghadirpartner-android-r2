package ir.ghadirpartner.nativeapp

import android.graphics.pdf.PdfDocument
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
        val first=PdfDocument();try {OfficialInvoice.render(first,order,customer);check(first.pages.size==1);File(context.filesDir,"invoice-reference.pdf").outputStream().use{first.writeTo(it)}}finally{first.close()}
        val rows=JSONArray();repeat(24){i->rows.put(JSONObject(row.toString()).put("product","دستگاه کارتخوان مدل بلند آزمایشی شماره ${i+1} با گارانتی و لوازم کامل"))};order.put("items",rows).put("number","PAGINATION-24").put("subtotal_before_discount",24L*120909091L).put("estimated_total",24L*133000000L).put("tax_amount",24L*12090909L)
        val many=PdfDocument();try {OfficialInvoice.render(many,order,customer);check(many.pages.size>1);File(context.filesDir,"invoice-pagination.pdf").outputStream().use{many.writeTo(it)}}finally{many.close()}
    }
}

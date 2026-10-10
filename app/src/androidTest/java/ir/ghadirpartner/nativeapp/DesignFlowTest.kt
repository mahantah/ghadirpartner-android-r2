package ir.ghadirpartner.nativeapp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/** End-to-end UI contracts use an injected transport. No live SMS/order/payment is sent. */
class DesignFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val me=JSONObject("""{"username":"09121234567","roles":["customer"],"id":1024,"customer":{"name":"علی رضایی","company":"فروشگاه رضایی","code":"1024","province":"تهران","city":"تهران","address":"نشانی نمونه"}}""")
    private val order=JSONObject("""{"id":1024,"number":"GHP-MOCK-1024","customer_id":1024,"customer_name":"علی رضایی","status":"تحویل شد","payment_status":"تسویه کامل","requested_payment_method_label":"نقد","estimated_total":22800000,"approved_total":22800000,"created_at":"2026-10-04","shipping_address":"تهران، نشانی ثبت‌شده","items":[{"product":"i90","qty":2,"unit_price":11400000,"line_total":22800000,"serials":[{"serial":"902410001","imei":"865432109876543"},{"serial":"902410002"}]}]}""")
    private val writes=CopyOnWriteArrayList<Pair<String,JSONObject>>()
    private fun client():OkHttpClient=OkHttpClient.Builder().addInterceptor {chain->
        val request=chain.request()
        check(request.url.host=="ghadirpartner.ir")
        val path=request.url.encodedPath.removePrefix("/partners")
        if(request.method=="POST") {
            val buffer=Buffer();request.body!!.writeTo(buffer)
            writes.add(path to JSONObject(buffer.readUtf8()))
        }
        val payload=when(path) {
            "/api/me"->me.toString()
            "/api/orders"->"[$order]"
            "/api/orders/add"->JSONObject(order.toString()).put("id",1001).put("number","GHP-MOCK-1001").put("status","ثبت شد").toString()
            "/api/offers"->"""[{"id":3,"title":"قدیر میکس؛ خرید ترکیبی","description":"پیشنهاد ویژه برای همکاران؛ شرایط و مدل‌های مشمول را ببینید.","discount_type":"percent","discount_value":5,"promo_code":"MOCK5"}]"""
            "/api/catalog"->"""[{"name":"i90","manufacturer":"SZZT","available":true,"stock":4,"serial_required":true,"price_updated_at":"2026-10-04","prices":{"serial_1_50":11400000,"serial_51_200":11600000,"panel_cash":11000000,"panel_7d":11300000,"panel_1m":11900000,"sales_agent":11200000}}]"""
            "/api/addresses"->"""[{"id":"saved-A","title":"دفتر","province":"تهران","city":"تهران","address":"خیابان دفتر"}]"""
            "/api/notifications"->"{\"items\":[],\"unread\":0}"
            "/api/purchased-devices"->"{\"qty\":2}"
            "/api/reports/customer-summary"->"{\"month\":{\"amount\":34200000,\"orders\":3}}"
            "/api/native/credit"->"{\"configured\":true,\"available\":30000000,\"due_date\":\"1405/07/26\"}"
            "/api/native/login-otp/request"->"{\"ok\":true,\"retry_after\":60}"
            "/api/native/login-otp/confirm"->"{\"ok\":true}"
            else->throw AssertionError("Unexpected test request: ${request.method} $path")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(payload.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private fun shot(name:String) {
        compose.waitForIdle()
        val lists=compose.onAllNodes(hasScrollToIndexAction())
        if(lists.fetchSemanticsNodes().size==1)lists.onFirst().performScrollToIndex(0)
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.filesDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    private fun hideKeyboard(scenario:ActivityScenario<MainActivity>) {
        scenario.onActivity {(it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.window.decorView.windowToken,0)}
        compose.waitForIdle()
    }
    private fun waitText(text:String) {
        compose.waitUntil(15000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun showText(text:String) {
        val lists=compose.onAllNodes(hasScrollToIndexAction())
        if(lists.fetchSemanticsNodes().size==1)lists.onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onLast().performScrollTo()
    }
    private fun showField(description:String) {
        val lists=compose.onAllNodes(hasScrollToIndexAction())
        if(lists.fetchSemanticsNodes().size==1)lists.onFirst().performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performScrollTo()
    }
    @Test fun catalogCheckoutAndSerials() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                if(AppAppearance.dark)AppAppearance.toggle(activity)
                activity.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(activity,client()),me,{})}}}
            }
            waitText("سلام، علی رضایی");shot("design-home-day.png")
            compose.onNodeWithContentDescription("حالت شب").performClick();shot("design-home-night.png")
            compose.onNodeWithContentDescription("حالت روز").performClick()
            compose.onAllNodesWithText("قیمت‌ها").onLast().performClick()
            waitText("۱۱٬۴۰۰٬۰۰۰ تومان");shot("design-catalog-serial.png")
            compose.onNodeWithText("ثبت در پنل").performClick()
            compose.onNodeWithText("۱۱٬۰۰۰٬۰۰۰ تومان").assertExists();shot("design-catalog-panel.png")
            compose.onNodeWithText("عامل فروش").performClick()
            compose.onAllNodesWithText("۱۱٬۲۰۰٬۰۰۰ تومان").onLast().assertExists();shot("design-catalog-agent.png")
            compose.onAllNodesWithText("ثبت سفارش").onLast().performClick()
            compose.waitUntil(15000){compose.onAllNodesWithContentDescription("مدل دستگاه").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("مدل دستگاه").performClick()
            compose.onNodeWithText("i90 • SZZT").performClick()
            compose.onNodeWithText("افزودن کالا").performScrollTo().performClick()
            showField("نشانی تحویل");compose.onNodeWithContentDescription("نشانی تحویل").performClick()
            compose.onNodeWithText("دفتر • خیابان دفتر").performClick()
            shot("design-checkout-cart.png")
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            shot("design-checkout-cash.png")
            compose.onNodeWithText("چک").performClick()
            showText("ادامه به بررسی نهایی");compose.onNodeWithText("ادامه به بررسی نهایی").performClick()
            waitText("شناسه صیادی باید ۱۶ رقم باشد")
            compose.onNodeWithText("شناسه صیادی باید ۱۶ رقم باشد").assertExists()
            assertTrue(writes.isEmpty())
            showField("شناسه صیادی");compose.onNodeWithContentDescription("شناسه صیادی").performTextInput("۱۲۳۴۵۶۷۸۹۰۱۲۳۴۵۶")
            showField("بانک");compose.onNodeWithContentDescription("بانک").performTextInput("بانک نمونه")
            showField("سررسید");compose.onNodeWithContentDescription("سررسید").performTextInput("۱۴۰۵/۰۷/۲۶")
            hideKeyboard(scenario);shot("design-checkout-check.png")
            showText("ادامه به بررسی نهایی");compose.onNodeWithText("ادامه به بررسی نهایی").performClick()
            waitText("تأیید نهایی سفارش")
            showText("بازگشت و ویرایش");compose.onNodeWithText("بازگشت و ویرایش").performClick()
            showText("اعتباری");compose.onNodeWithText("اعتباری").performClick()
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            waitText("اعتبار حساب شما")
            compose.onNode(isToggleable()).performScrollTo().performClick()
            shot("design-checkout-credit.png")
            showText("پذیرش و ادامه");compose.onNodeWithText("پذیرش و ادامه").performClick()
            shot("design-checkout-review.png")
            showText("تأیید و ثبت نهایی");compose.onNodeWithText("تأیید و ثبت نهایی").performClick()
            waitText("سفارش با موفقیت ثبت شد");shot("design-checkout-success.png")
            val body=writes.single {it.first=="/api/orders/add"}.second
            assertEquals("saved-A",body.s("address_id"))
            assertEquals("credit",body.s("requested_payment_method"))
            assertTrue(body.obj("settlement_details").b("terms_accepted"))
            assertEquals("serial_1_50",body.arr("items").getJSONObject(0).s("price_key"))
            assertEquals(1,body.arr("items").getJSONObject(0).i("qty"))
            compose.onAllNodesWithText("سفارش‌ها").onLast().performClick()
            waitText("جزئیات سفارش");shot("design-orders.png")
            compose.onNodeWithText("جزئیات سفارش").performScrollTo().performClick()
            waitText("اطلاعات تحویل");shot("design-order-detail.png")
            showText("دریافت PDF سریال‌ها");compose.onNodeWithText("دریافت PDF سریال‌ها").performClick()
            waitText("SN: 902410001");shot("design-serials.png")
            compose.onNodeWithText("IMEI: 865432109876543").assertExists()
        }
    }
    @Test fun otpRequestWithPersianDigits() {
        val logged=AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                if(AppAppearance.dark)AppAppearance.toggle(activity)
                activity.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){LoginScreen(ApiClient(activity,client())){logged.incrementAndGet()}}}}
            }
            waitText("خوش آمدید");shot("design-login.png")
            compose.onNodeWithContentDescription("شماره همراه").performTextInput("۰۹۱۲۱۲۳۴۵۶۷")
            hideKeyboard(scenario)
            compose.onNodeWithText("ورود با کد یک‌بارمصرف").performScrollTo().performClick()
            waitText("کد تأیید را وارد کنید");shot("design-otp.png")
            compose.onNodeWithContentDescription("کد شش‌رقمی").performTextInput("۱۲۳۴۵۶")
            hideKeyboard(scenario)
            compose.onNodeWithText("تأیید و ورود").performScrollTo().performClick()
            compose.waitUntil(15000){logged.get()==1}
            assertEquals("09121234567",writes.single {it.first=="/api/native/login-otp/request"}.second.s("mobile"))
            assertEquals("123456",writes.single {it.first=="/api/native/login-otp/confirm"}.second.s("otp"))
        }
    }
    @Test fun undeliveredSerialsRemainPrivate() {
        val pending=JSONObject(order.toString()).put("status","ارسال شد")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        try{runBlocking{ApiClient(context,client()).saveSerialsPdf(pending)};fail("PDF must reject an undelivered order")}
        catch(expected:IllegalArgumentException){assertTrue(expected.message!!.contains("تحویل"))}
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->activity.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalSerials(ApiClient(activity,client()),pending,me,{})}}}}
            compose.onNodeWithText("SN: 902410001").assertDoesNotExist()
            compose.onNodeWithText("IMEI: 865432109876543").assertDoesNotExist()
            assertTrue(writes.isEmpty())
        }
    }
}

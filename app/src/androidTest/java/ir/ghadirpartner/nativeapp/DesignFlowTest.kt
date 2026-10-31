package ir.ghadirpartner.nativeapp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
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
    private var includeOrderMessage=false
    private var fixtureStock=4
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
            "/api/catalog"->"""[{"name":"i90","manufacturer":"SZZT","available":true,"stock":$fixtureStock,"serial_required":true,"price_updated_at":"2026-10-04","prices":{"serial_1_50":11400000,"serial_51_200":11600000,"panel_cash":11000000,"panel_7d":11300000,"panel_1m":11900000,"sales_agent":11200000}}]"""
            "/api/addresses"->"""[{"id":"saved-A","title":"دفتر","province":"تهران","city":"تهران","address":"خیابان دفتر"}]"""
            "/api/notifications"->if(includeOrderMessage) """{"items":[{"id":"order:1024","kind":"order","order_id":1024,"title":"به‌روزرسانی سفارش","body":"سفارش آماده است","read":false}],"unread":1}""" else "{\"items\":[],\"unread\":0}"
            "/api/notifications/read"->"{\"ok\":true}"
            "/api/password"->"{\"ok\":true}"
            "/api/profile/update"->{
                val body=writes.last().second
                JSONObject().put("customer",me.obj("customer")).put("alternate_mobile",body.s("alternate_mobile")).toString()
            }
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
    private fun shot(name:String,scrollToTop:Boolean=true) {
        compose.waitForIdle()
        val lists=compose.onAllNodes(hasScrollToIndexAction())
        if(scrollToTop&&lists.fetchSemanticsNodes().size==1)lists.onFirst().performScrollToIndex(0)
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val bitmap=compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
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
        clearFloatingBarOverlap(compose.onAllNodesWithText(text).onLast())
    }
    private fun showField(description:String) {
        val lists=compose.onAllNodes(hasScrollToIndexAction())
        if(lists.fetchSemanticsNodes().size==1)lists.onFirst().performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performScrollTo()
        clearFloatingBarOverlap(compose.onNodeWithContentDescription(description))
    }
    private fun clearFloatingBarOverlap(node:SemanticsNodeInteraction) {
        // A clipped node's bounds can initially hide part of the button. Re-read
        // after each scroll, and finish the pending frame before the tap.
        repeat(4) {
            compose.waitForIdle()
            val bars=compose.onAllNodesWithContentDescription("ناوبری شناور").fetchSemanticsNodes()
            if(bars.isEmpty())return
            val overlap=node.fetchSemanticsNode().boundsInRoot.bottom-bars.single().boundsInRoot.top+12f
            if(overlap<=0)return
            compose.onAllNodes(hasScrollToIndexAction()).onFirst()
                .performSemanticsAction(SemanticsActions.ScrollBy){it(0f,overlap)}
            compose.mainClock.advanceTimeByFrame()
        }
        compose.waitForIdle()
        val top=compose.onNodeWithContentDescription("ناوبری شناور").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Control must be above the floating navigation before tapping",node.fetchSemanticsNode().boundsInRoot.bottom<=top)
    }
    private fun chooseInvoice() {
        showField("تنظیم نوع فاکتور");compose.onNodeWithContentDescription("تنظیم نوع فاکتور").performClick()
        compose.onNodeWithText("غیررسمی",substring=false).performClick()
        compose.onNodeWithText("تأیید و بستن").performClick()
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
            waitText("۱۱٬۴۰۰٬۰۰۰ تومان")
            compose.onNodeWithContentDescription("جستجوی مدل یا شرکت سازنده").assertDoesNotExist()
            compose.onNodeWithContentDescription("فیلتر لیست قیمت‌ها").performClick()
            waitText("جست‌وجو و فیلتر قیمت‌ها")
            compose.onNodeWithContentDescription("جستجوی مدل یا شرکت سازنده").performTextInput("i90")
            hideKeyboard(scenario)
            compose.onNodeWithText("نمایش نتایج").performScrollTo().performClick()
            compose.onNodeWithContentDescription("جستجوی مدل یا شرکت سازنده").assertDoesNotExist()
            shot("design-catalog-serial.png")
            compose.onNodeWithText("ثبت در پنل").performClick()
            compose.onNodeWithText("۱۱٬۰۰۰٬۰۰۰ تومان").assertExists();shot("design-catalog-panel.png")
            compose.onNodeWithText("عامل فروش").performClick()
            compose.onAllNodesWithText("۱۱٬۲۰۰٬۰۰۰ تومان").onLast().assertExists();shot("design-catalog-agent.png")
            compose.onAllNodesWithText("ثبت سفارش").onLast().performClick()
            compose.waitUntil(15000){compose.onAllNodesWithContentDescription("مدل دستگاه").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("مدل دستگاه").performClick()
            compose.onNodeWithText("i90 • SZZT").performClick()
            showText("افزودن کالا");compose.onNodeWithText("افزودن کالا").performClick()
            showText("ثبت در پنل");compose.onNodeWithText("ثبت در پنل").performClick()
            showText("افزودن کالا");compose.onNodeWithText("افزودن کالا").performClick()
            shot("design-checkout-cart.png")
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            waitText("نوع فاکتور را انتخاب کنید")
            assertTrue(writes.isEmpty())
            chooseInvoice()
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            showField("نشانی تحویل");compose.onNodeWithContentDescription("نشانی تحویل").performClick()
            compose.onNodeWithText("دفتر • خیابان دفتر").performClick()
            shot("design-checkout-cash.png")
            showText("چک");compose.onNodeWithText("چک").performClick()
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
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            showText("اعتباری");compose.onNodeWithText("اعتباری").performClick()
            showText("اعتبار حساب شما")
            compose.onNode(isToggleable()).performScrollTo();clearFloatingBarOverlap(compose.onNode(isToggleable()));compose.onNode(isToggleable()).performClick()
            shot("design-checkout-credit.png")
            showText("پذیرش و ادامه")
            compose.waitUntil(15000){compose.onAllNodes(hasText("پذیرش و ادامه") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("پذیرش و ادامه").performClick()
            shot("design-checkout-review.png")
            showText("تأیید و ثبت نهایی");compose.onNodeWithText("تأیید و ثبت نهایی").performClick()
            waitText("سفارش با موفقیت ثبت شد");shot("design-checkout-success.png")
            val body=writes.single {it.first=="/api/orders/add"}.second
            assertEquals("saved-A",body.s("address_id"))
            assertEquals("credit",body.s("requested_payment_method"))
            assertTrue(body.obj("settlement_details").b("terms_accepted"))
            assertEquals(BuildConfig.ORDER_CREDIT_COMMITMENT_SHA256,body.obj("settlement_details").s("commitment_sha256"))
            assertEquals("غیررسمی",body.s("invoice_type"))
            assertEquals("serial_1_50",body.arr("items").getJSONObject(0).s("price_key"))
            assertEquals(1,body.arr("items").getJSONObject(0).i("qty"))
            assertEquals(2,body.arr("items").length())
            assertEquals("panel_cash",body.arr("items").getJSONObject(1).s("price_key"))
            assertEquals(1,body.arr("items").getJSONObject(1).i("qty"))
            compose.onAllNodesWithText("سفارش‌ها").onLast().performClick()
            waitText("جزئیات سفارش");shot("design-orders.png")
            showText("جزئیات سفارش");compose.onNodeWithText("جزئیات سفارش").performClick()
            showText("اطلاعات تحویل");shot("design-order-detail.png")
            showText("دریافت PDF سریال‌ها");compose.onNodeWithText("دریافت PDF سریال‌ها").performClick()
            waitText("SN: 902410001");shot("design-serials.png")
            compose.onNodeWithText("IMEI: 865432109876543").assertExists()
        }
    }
    @Test fun checkoutDraftSurvivesTabsAndSavedState() {
        var registry=SaveableStateRegistry(null){true}
        val visible=mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {
                if(visible.value) GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,LocalSaveableStateRegistry provides registry){PortalApp(ApiClient(a,client()),me,{})}}
            }}
            waitText("سلام، علی رضایی")
            compose.onAllNodesWithText("قیمت‌ها").onLast().performClick()
            waitText("افزودن به سفارش")
            showText("افزودن به سفارش");compose.onNodeWithText("افزودن به سفارش").performClick()
            waitText("افزودن کالا")
            showText("افزودن کالا");compose.onNodeWithText("افزودن کالا").performClick()
            showField("افزایش تعداد i90");compose.onNodeWithContentDescription("افزایش تعداد i90").performClick()
            chooseInvoice()
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            showField("نشانی تحویل");compose.onNodeWithContentDescription("نشانی تحویل").performClick()
            compose.onNodeWithText("دفتر • خیابان دفتر").performClick()
            compose.onAllNodesWithText("پروفایل").onLast().performClick()
            compose.onAllNodesWithText("ثبت سفارش").onLast().performClick()
            waitText("نشانی تحویل")
            compose.onNodeWithText("پیش‌نویس محفوظ است؛ از بخش ثبت سفارش ادامه دهید.").assertDoesNotExist()
            // Final request assertions below verify the restored address and edited quantity.
            var saved:Map<String,List<Any?>> = emptyMap()
            compose.runOnIdle {saved=registry.performSave();visible.value=false}
            compose.waitForIdle()
            compose.runOnIdle {registry=SaveableStateRegistry(saved){true};visible.value=true}
            waitText("نشانی تحویل")
            showText("ادامه به بررسی نهایی")
            compose.onNodeWithText("ادامه به بررسی نهایی").assertIsEnabled().performClick()
            compose.waitForIdle()
            try {showText("تأیید و ثبت نهایی")} catch(e:Throwable) {
                shot("design-draft-restore-failure.png",scrollToTop=false)
                throw AssertionError(compose.onRoot().printToString(),e)
            }
            compose.onNodeWithText("تأیید و ثبت نهایی").performClick()
            waitText("سفارش با موفقیت ثبت شد")
            val body=writes.single {it.first=="/api/orders/add"}.second
            assertEquals("saved-A",body.s("address_id"))
            assertEquals(2,body.arr("items").getJSONObject(0).i("qty"))
            compose.onAllNodesWithText("ثبت سفارش").onLast().performClick()
            showText("هنوز کالایی به سبد اضافه نشده است.")
            compose.onNodeWithText("هنوز کالایی به سبد اضافه نشده است.").assertExists()
        }
    }
    @Test fun inboxOrderReturnsToInbox() {
        includeOrderMessage=true
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(a,client()),me,{})}}}}
            waitText("سلام، علی رضایی")
            compose.onNodeWithContentDescription("اعلان‌ها").performClick()
            waitText("مشاهده سفارش")
            showText("مشاهده سفارش");compose.onNodeWithText("مشاهده سفارش").performClick()
            waitText("جزئیات سفارش")
            compose.onNodeWithContentDescription("بازگشت").performClick()
            waitText("مرکز پیام")
            compose.onNodeWithText("اعلان‌های سفارش و پیشنهادهای همکاران").assertExists()
        }
    }
    @Test fun largeReceiptUsesSmallSavedStateReference() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val receipt="A".repeat(1200000)
        val value=JSONObject().put("image_base64",receipt).put("bank_name","بانک نمونه")
        val saver=cachedJsonSaver(context)
        val scope=object:androidx.compose.runtime.saveable.SaverScope {override fun canBeSaved(value:Any)=true}
        val reference=with(saver){scope.save(value)}!!
        assertTrue(reference.length<100)
        val restored=saver.restore(reference)!!
        assertEquals(receipt,restored.s("image_base64"))
        assertEquals("بانک نمونه",restored.s("bank_name"))
    }
    @Test fun offlineAutomationNeverDisplaysZeroStats() {
        val offline=OkHttpClient.Builder().addInterceptor {throw java.io.IOException("offline fixture")}.build()
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {GhadirTheme {AutomationApp(ApiClient(a,offline),me,{})}}}
            waitText("اطلاعات داشبورد دریافت نشد")
            compose.onNodeWithText("سفارش فعال در جریان عملیات").assertDoesNotExist()
            compose.onNodeWithText("تلاش دوباره").assertExists()
        }
    }
    @Test fun quantitiesAbove999RespectActualStock() {
        fixtureStock=1505
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(a,client()),me,{})}}}}
            waitText("سلام، علی رضایی")
            compose.onAllNodesWithText("قیمت‌ها").onLast().performClick()
            waitText("افزودن به سفارش")
            showText("افزودن به سفارش");compose.onNodeWithText("افزودن به سفارش").performClick()
            waitText("تعداد")
            showField("تعداد کالا");compose.onNodeWithContentDescription("تعداد کالا").performTextReplacement("۱۵۰۶")
            hideKeyboard(scenario)
            showText("افزودن کالا");compose.onNodeWithText("افزودن کالا").assertIsNotEnabled()
            showField("تعداد کالا");compose.onNodeWithContentDescription("تعداد کالا").performTextReplacement("۱۰۰۰")
            hideKeyboard(scenario)
            showText("افزودن کالا");compose.onNodeWithText("افزودن کالا").performClick()
            showField("افزایش تعداد i90");compose.onNodeWithContentDescription("افزایش تعداد i90").performClick()
            chooseInvoice()
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            showText("ادامه به بررسی نهایی");compose.onNodeWithText("ادامه به بررسی نهایی").performClick()
            showText("تأیید و ثبت نهایی");compose.onNodeWithText("تأیید و ثبت نهایی").performClick()
            waitText("سفارش با موفقیت ثبت شد")
            val item=writes.single {it.first=="/api/orders/add"}.second.arr("items").getJSONObject(0)
            assertEquals(1001,item.i("qty"))
            assertEquals("serial_51_200",item.s("price_key"))
        }
    }
    @Test fun profileServicesAndSettingsHaveNoDuplicateActions() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(a,client()),me,{})}}}}
            waitText("سلام، علی رضایی")
            compose.onAllNodesWithText("پروفایل").onLast().performClick()
            compose.onNodeWithText("پروفایل من").performClick()
            waitText("ویرایش اطلاعات مشتری");shot("design-profile-services.png")
            compose.onAllNodesWithText("ویرایش اطلاعات مشتری").assertCountEquals(1)
            compose.onNodeWithText("آدرس‌های منتخب").assertExists()
            compose.onNodeWithText("فاکتورهای من").assertExists()
            compose.onAllNodesWithText("قفل بیومتریک",substring=true).assertCountEquals(0)
            compose.onNodeWithContentDescription("بازگشت").performClick()
            compose.onNodeWithText("تنظیمات").performClick()
            waitText("تغییر رمز ورود");shot("design-profile-settings.png")
            compose.onAllNodesWithText("قفل بیومتریک",substring=true).assertCountEquals(1)
            compose.onNodeWithText("فاکتورهای من").assertDoesNotExist()
            compose.onNodeWithText("تغییر رمز ورود").performClick()
            showField("رمز فعلی");compose.onNodeWithContentDescription("رمز فعلی").performTextInput("OldPassword57")
            showField("رمز جدید");compose.onNodeWithContentDescription("رمز جدید").performTextInput("NewPassword57")
            showField("تکرار رمز جدید");compose.onNodeWithContentDescription("تکرار رمز جدید").performTextInput("NewPassword57")
            hideKeyboard(scenario)
            showText("ثبت رمز جدید");compose.onNodeWithText("ثبت رمز جدید").performClick()
            waitText("رمز ورود با موفقیت تغییر کرد.")
            val password=writes.single {it.first=="/api/password"}.second
            assertEquals("OldPassword57",password.s("current"));assertEquals("NewPassword57",password.s("new"))
            showText("تغییر شماره تماس");compose.onNodeWithText("تغییر شماره تماس").performClick()
            showField("شماره تماس جایگزین");compose.onNodeWithContentDescription("شماره تماس جایگزین").performTextInput("۰۹۱۲۱۱۱۱۱۱۱")
            hideKeyboard(scenario)
            showText("ذخیره شماره تماس");compose.onNodeWithText("ذخیره شماره تماس").performClick()
            waitText("شماره تماس جایگزین ذخیره شد.")
            val contact=writes.single {it.first=="/api/profile/update"}.second
            assertEquals("09121111111",contact.s("alternate_mobile"))
            assertEquals(me.obj("customer").s("address"),contact.s("address"))
            assertFalse(contact.has("username"));assertFalse(contact.has("mobile"))
        }
    }
    @Test fun biometricPreferenceChangesAreObserved() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("ghadir_security",Context.MODE_PRIVATE)
        val account="observable-fixture-57"
        prefs.edit().remove("biometric:$account").commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                scenario.onActivity {a->a.setContent {
                    androidx.compose.material3.Text(if(rememberBiometricEnabled(a,account))"enabled-fixture" else "disabled-fixture")
                }}
                waitText("disabled-fixture")
                prefs.edit().putBoolean("biometric:$account",true).commit()
                waitText("enabled-fixture")
                prefs.edit().putBoolean("biometric:$account",false).commit()
                waitText("disabled-fixture")
            }
        }finally{prefs.edit().remove("biometric:$account").commit()}
    }
    @Test fun rememberedBiometricStartsWithoutPressingButtonAndDoesNotLoop() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=SecureLoginStore(context)
        val previous=store.load()
        val prefs=context.getSharedPreferences("ghadir_security",Context.MODE_PRIVATE)
        val account=me.s("username")
        val previousEnabled=biometricEnabled(context,account)
        store.save(account,"AutomaticFixture57")
        prefs.edit().putBoolean("biometric:$account",true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                scenario.onActivity {a->a.setContent {GhadirTheme {LoginScreen(ApiClient(a,client())){}}}}
                waitText("ورود امن")
                // CI emulator has no enrolled biometric. Password fallback must remain reachable.
                compose.onNodeWithText("ورود با رمز عبور").performScrollTo().performClick()
                waitText("خوش آمدید")
                compose.waitForIdle()
                compose.onNodeWithText("ورود امن").assertDoesNotExist()
            }
        }finally{
            prefs.edit().putBoolean("biometric:$account",previousEnabled).commit()
            if(previous==null)store.clear() else store.save(previous.identity,previous.password)
        }
    }
    @Test fun checkoutOptionsStayAccessibleAndReachTheOrderRequest() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(a,client()),me,{})}}}}
            waitText("سلام، علی رضایی")
            compose.onAllNodesWithText("قیمت‌ها").onLast().performClick()
            waitText("افزودن به سفارش")
            showText("افزودن به سفارش");compose.onNodeWithText("افزودن به سفارش").performClick()
            waitText("افزودن کالا")
            showText("افزودن کالا");compose.onNodeWithText("افزودن کالا").performClick()
            showField("تنظیم کد تخفیف");compose.onNodeWithContentDescription("تنظیم کد تخفیف").performClick()
            compose.onNodeWithContentDescription("کد تخفیف").performTextInput("MOCK5")
            hideKeyboard(scenario)
            compose.onNodeWithText("اعمال کد تخفیف").performScrollTo().performClick()
            compose.onNodeWithText("تأیید و بستن").performScrollTo().performClick()
            showField("تنظیم نوع فاکتور");compose.onNodeWithContentDescription("تنظیم نوع فاکتور").performClick()
            compose.onNodeWithText("رسمی",substring=false).performClick()
            compose.onNodeWithText("تأیید و بستن").performClick()
            showField("تنظیم توضیحات");compose.onNodeWithContentDescription("تنظیم توضیحات").performClick()
            compose.onNodeWithContentDescription("توضیحات سفارش — اختیاری").performTextInput("تماس قبل از ارسال")
            hideKeyboard(scenario)
            compose.onNodeWithText("تأیید و بستن").performScrollTo().performClick()
            showField("تنظیم کد تخفیف")
            compose.onNodeWithText("کد طرح").assertDoesNotExist()
            shot("design-checkout-options.png",scrollToTop=false)
            showText("ادامه و بررسی سفارش");compose.onNodeWithText("ادامه و بررسی سفارش").performClick()
            showText("ادامه به بررسی نهایی");compose.onNodeWithText("ادامه به بررسی نهایی").performClick()
            showText("تأیید و ثبت نهایی");compose.onNodeWithText("تأیید و ثبت نهایی").performClick()
            waitText("سفارش با موفقیت ثبت شد")
            val body=writes.single {it.first=="/api/orders/add"}.second
            assertEquals("MOCK5",body.s("promo_code"));assertEquals("رسمی",body.s("invoice_type"));assertEquals("تماس قبل از ارسال",body.s("notes"))
        }
    }
    @Test fun floatingBarLeavesTheLastServiceReachable() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(a,client()),me,{})}}}}
            waitText("سلام، علی رضایی")
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            val bar=compose.onNodeWithContentDescription("ناوبری شناور").fetchSemanticsNode().boundsInRoot
            assertTrue(bar.left>root.left);assertTrue(bar.right<root.right);assertTrue(bar.bottom<root.bottom)
            compose.onAllNodesWithText("پروفایل").onLast().performClick()
            showText("پروفایل من");compose.onNodeWithText("پروفایل من").performClick()
            showText("گزارش خرید")
            val button=compose.onNodeWithText("گزارش خرید").fetchSemanticsNode().boundsInRoot
            val nav=compose.onNodeWithContentDescription("ناوبری شناور").fetchSemanticsNode().boundsInRoot
            assertTrue("Last service must scroll above the floating bar",button.bottom<=nav.top)
            shot("design-profile-floating.png",scrollToTop=false)
            compose.onNodeWithText("گزارش خرید").performClick()
            waitText("کل دستگاه‌های خریداری‌شده")
        }
    }
    @Test fun purchaseSeriesUsesDeliveredOrdersInTheirActualMonths() {
        val now=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran")).apply {set(2026,9,20)}
        val cancelled=JSONObject(order.toString()).put("status","لغو شد").put("approved_total",900000000)
        val september=JSONObject(order.toString()).put("created_at","2026-09-30").put("approved_total",2000000)
        val tooOld=JSONObject(order.toString()).put("created_at","2025-10-04")
        val points=purchaseHistory(listOf(order,cancelled,september,tooOld),now)
        assertEquals(6,points.size)
        assertEquals(2000000L,points[4].amount)
        assertEquals(22800000L,points[5].amount)
        assertTrue(points.take(4).all {it.amount==0L})
        assertTrue(purchaseHistory(emptyList(),now).all {it.amount==0L})
        assertFalse(orderItemsDescription(order).contains("×"))
        assertTrue(orderItemsDescription(order).contains("۲ دستگاه"))
    }
    @Test fun profileOverviewOpensTheRealPurchaseChart() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {a->
                if(AppAppearance.dark)AppAppearance.toggle(a)
                a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(a,client()),me,{})}}}
            }
            waitText("سلام، علی رضایی")
            compose.onAllNodesWithText("پروفایل").onLast().performClick()
            showText("پروفایل من");shot("design-profile-main-day.png")
            compose.onNodeWithContentDescription("حالت شب").performClick();shot("design-profile-main-night.png")
            compose.onNodeWithContentDescription("حالت روز").performClick()
            compose.onNodeWithText("پروفایل من").performClick()
            showText("گزارش خرید");compose.onNodeWithText("گزارش خرید").performClick()
            waitText("روند خرید");shot("design-purchase-trend.png")
            compose.onNodeWithContentDescription("نمودار روند خرید؛",substring=true).assertExists()
            showText("جزئیات نمودار");compose.onNodeWithText("جزئیات نمودار").performClick()
            showText("این ماه");compose.onAllNodesWithText("این ماه").onLast().assertExists()
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
    @Test fun unavailableAccountDoesNotDisplayZeroBalances() {
        val offline=OkHttpClient.Builder().addInterceptor {throw java.io.IOException("offline fixture")}.build()
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                activity.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(activity,offline),me,{})}}}
            }
            waitText("اطلاعات حساب دریافت نشد");shot("design-account-offline.png")
            compose.onNodeWithText("خرید این ماه").assertDoesNotExist()
            compose.onNodeWithText("هنوز سفارشی ندارید").assertDoesNotExist()
            compose.onNodeWithText("تلاش دوباره").assertExists()
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

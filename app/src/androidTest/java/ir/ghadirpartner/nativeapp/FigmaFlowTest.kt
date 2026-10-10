package ir.ghadirpartner.nativeapp

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FigmaFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val me=JSONObject("""{"username":"09121234567","roles":["customer"],"id":1024,"customer":{"name":"علی رضایی","company":"فروشگاه رضایی","code":"1024","address":"تهران، نشانی نمونه"}}""")
    private val order="""{"id":1024,"number":"GHP-001024","status":"تحویل شد","payment_status":"تسویه کامل","requested_payment_method_label":"نقد","estimated_total":22800000,"approved_total":22800000,"created_at":"2026-10-04","customer_id":1024,"items":[{"product":"i90","qty":2,"unit_price":11400000,"line_total":22800000,"serials":["902410001","902410002"]}]}"""
    private fun shot(name:String){
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bmp=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir,name).outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
        bmp.recycle()
    }
    @Test fun authenticatedNavigationAndThemes(){
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val client=OkHttpClient.Builder().addInterceptor {chain->
            val request=chain.request()
            // All app API requests are intercepted; no real account, SMS, order or payment is used.
            check(request.method=="GET") {"Unexpected mutation during read-only UI test"}
            val payload=when(request.url.encodedPath.removePrefix("/partners")){
                "/api/me"->me.toString()
                "/api/orders"->"[$order]"
                "/api/offers"->"[]"
                "/api/notifications"->"{\"items\":[],\"unread\":0}"
                "/api/purchased-devices"->"{\"qty\":2}"
                "/api/catalog"->"[]"
                "/api/addresses"->"[]"
                else->"{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->
                if(AppAppearance.dark)AppAppearance.toggle(activity)
                activity.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){PortalApp(ApiClient(activity,client),me,{})}}}
            }
            compose.waitUntil(10000){compose.onAllNodesWithText("پروفایل").fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText("پروفایل").onLast().performClick()
            compose.onNodeWithText("انتخاب یا حذف عکس").assertExists()
            shot("profile-day.png")
            compose.onNodeWithContentDescription("حالت شب").performClick()
            compose.onNodeWithContentDescription("حالت روز").assertExists()
            shot("profile-night.png")
            compose.onNodeWithContentDescription("حالت روز").performClick()
            compose.onNodeWithText("پیش‌فاکتورهای من").performScrollTo().performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("دانلود PDF پیش‌فاکتور").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("دانلود PDF پیش‌فاکتور").assertExists()
            shot("invoice-list.png")
            compose.onNodeWithContentDescription("اعلان‌ها").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("اعلانی وجود ندارد").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("طرح‌ها و آفرها").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithText("آفر فعالی موجود نیست").fetchSemanticsNodes().isNotEmpty()}
        }
    }
    @Test fun settlementValidationAndPersianDigits(){
        assertEquals("09123456789",asciiDigits("۰۹۱۲۳۴۵۶۷۸۹"))
        assertTrue(settlementError("check",JSONObject()).isNotEmpty())
        assertEquals("",settlementError("check",JSONObject().put("check_number","1234567890123456").put("bank_name","بانک").put("due_date","1405/07/26")))
        assertTrue(settlementError("credit",JSONObject()).isNotEmpty())
        assertEquals("",settlementError("credit",JSONObject().put("terms_accepted",true)))
    }
}

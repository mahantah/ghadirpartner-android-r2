package ir.ghadirpartner.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Management delivery UI uses a fake HTTP boundary and never touches a real warehouse. */
class PartialDeliveryFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val writes = CopyOnWriteArrayList<JSONObject>()
    private val changed = AtomicInteger()
    private val order =
        JSONObject(
            """{"id":91,"number":"FIXTURE-91","customer_name":"مشتری آزمایشی","approval_status":"تأیید شده","status":"آماده ارسال","payment_status":"بیعانه","items":[{"product":"I90","qty":6,"serials":["I90-1","I90-2","I90-3","I90-4","I90-5","I90-6"]},{"product":"I80","qty":3,"serials":["I80-1","I80-2","I80-3"]}]}""",
        )

    private fun client(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                check(request.url.encodedPath.removePrefix("/partners") == "/api/orders/partial-dispatch")
                check(request.method == "POST")
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                writes.add(JSONObject(buffer.readUtf8()))
                val status = if (writes.size == 1) 503 else 200
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("fixture")
                    .body(
                        (
                            if (status ==
                                503
                            ) {
                                """{"error":"خطای آزمایشی؛ دوباره تلاش کنید"}"""
                            } else {
                                "{}"
                            }
                        ).toResponseBody("application/json".toMediaType()),
                    ).build()
            }.build()

    private fun show(
        scenario: ActivityScenario<MainActivity>,
        role: String,
    ) {
        scenario.onActivity { activity ->
            activity.setContent {
                GhadirTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        AutomationOrderDialog(ApiClient(activity, client()), order, JSONObject().put("roles", JSONArray().put(role)), {
                        }, { changed.incrementAndGet() })
                    }
                }
            }
        }
    }

    /** Admin selects one third, confirms stock effects, and safely retries with the same identity. */
    @Test fun managementCanDeliverOneThirdWithoutChangingPayment() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            show(scenario, "finance")
            compose.waitForIdle()
            compose.onNodeWithText("تحویل بخشی با مجوز مدیر").assertDoesNotExist()
            assertTrue(writes.isEmpty())
            show(scenario, "admin")
            compose.onNodeWithText("تحویل بخشی با مجوز مدیر").performScrollTo().performClick()
            compose.onNodeWithText("انتخاب یک‌سوم ماندهٔ هر مدل").performScrollTo().performClick()
            compose.onNodeWithContentDescription("علت مجوز تحویل بخشی").performScrollTo().performTextInput("تحویل بخشی با تأیید مدیر")
            compose.onNodeWithText("نوع تحویل بخشی: انتخاب کنید").performScrollTo().performClick()
            compose.onNodeWithText("حضوری").performClick()
            compose.onNodeWithText("بررسی و ثبت خروج بخشی").performScrollTo().performClick()
            compose.onNodeWithText("تأیید و ثبت خروج").performClick()
            compose.waitUntil(15000) { writes.size == 1 }
            compose.waitUntil(15000) { compose.onAllNodesWithText("تأیید و ثبت خروج").fetchSemanticsNodes().isEmpty() }
            assertEquals(0, changed.get())
            compose.onNodeWithText("بررسی و ثبت خروج بخشی").performScrollTo().performClick()
            compose.onNodeWithText("تأیید و ثبت خروج").performClick()
            compose.waitUntil(15000) { changed.get() == 1 }
            assertEquals(2, writes.size)
            assertEquals(writes[0].s("request_id"), writes[1].s("request_id"))
            assertEquals(91, writes[1].i("id"))
            assertEquals("حضوری", writes[1].s("shipping_type"))
            val rows = writes[1].arr("items").objects().associate { it.s("product") to it.i("qty") }
            assertEquals(mapOf("I90" to 2, "I80" to 1), rows)
            assertEquals(
                listOf("I90-1", "I90-2"),
                writes[1]
                    .arr("items")
                    .objects()
                    .first { it.s("product") == "I90" }
                    .arr("serials")
                    .strings(),
            )
            assertEquals("بیعانه", order.s("payment_status"))
        }
    }
}

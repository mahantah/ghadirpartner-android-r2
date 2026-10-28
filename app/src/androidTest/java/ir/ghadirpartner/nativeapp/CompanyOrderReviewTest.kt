package ir.ghadirpartner.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Company review fixtures never contact live orders, payments, SMS or inventory. */
class CompanyOrderReviewTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val writes: CopyOnWriteArrayList<Pair<String, JSONObject>> = CopyOnWriteArrayList()
    private val changed: AtomicInteger = AtomicInteger()
    private val order: JSONObject = JSONObject(
        """{"id":91,"number":"FIXTURE-91","customer_name":"مشتری آزمایشی","status":"در انتظار تأیید سفارش","approval_status":"در انتظار تأیید","items":[]}""",
    )

    private fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath.removePrefix("/partners")
        check(request.method == "POST")
        check(path in listOf("/api/customer-order/approve", "/api/customer-order/reject"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        writes.add(path to JSONObject(buffer.readUtf8()))
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("{}".toResponseBody("application/json".toMediaType())).build()
    }.build()

    private fun showDialog(scenario: ActivityScenario<MainActivity>, role: String): Unit = scenario.onActivity { activity ->
        val me = JSONObject().put("roles", org.json.JSONArray().put(role))
        activity.setContent {
            GhadirTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AutomationOrderDialog(ApiClient(activity, client()), order, me, {}, { changed.incrementAndGet() })
                }
            }
        }
    }.let { Unit }

    /** Only sales/admin can decide; rejection requires a reason and neither action posts payment/status. */
    @Test fun companyDecisionsRespectRolesAndRequireRejectionReason() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            showDialog(scenario, "finance")
            compose.waitUntil(15000) { compose.onAllNodesWithText("در انتظار تأیید شرکت").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("تأیید سفارش").assertDoesNotExist()
            compose.onNodeWithText("عدم تأیید سفارش").assertDoesNotExist()
            assertTrue(writes.isEmpty())
            showDialog(scenario, "sales")
            compose.waitUntil(15000) { compose.onAllNodesWithText("عدم تأیید سفارش").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("عدم تأیید سفارش").assertIsNotEnabled()
            compose.onNodeWithContentDescription("علت عدم تأیید").performTextInput("عدم امکان تأمین")
            compose.onNodeWithText("عدم تأیید سفارش").performScrollTo().performClick()
            compose.waitUntil(15000) { changed.get() == 1 }
            assertEquals("/api/customer-order/reject", writes.single().first)
            assertEquals("عدم امکان تأمین", writes.single().second.s("reason"))
            assertEquals(91, writes.single().second.i("id"))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            showDialog(scenario, "admin")
            compose.waitUntil(15000) { compose.onAllNodesWithText("تأیید سفارش").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("تأیید سفارش").performScrollTo().performClick()
            compose.waitUntil(15000) { changed.get() == 2 }
            assertEquals("/api/customer-order/approve", writes.last().first)
            assertEquals(2, writes.size)
        }
    }
}

package ir.ghadirpartner.nativeapp

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SecurityAndInboxTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun transport(handler:(Request)->String)=OkHttpClient.Builder().addInterceptor {
        val request=it.request()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(handler(request).toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Test fun rememberedLoginEncryptedAndModeIsolated() {
        val store=SecureLoginStore(context,"security-fixture")
        val other=SecureLoginStore(context,"security-other")
        store.clear();other.clear()
        try {
            store.save("09121234567","SecretFixture-56")
            assertEquals(RememberedLogin("09121234567","SecretFixture-56"),SecureLoginStore(context,"security-fixture").load())
            assertNull(other.load())
            val prefs=context.getSharedPreferences("ghadir_remembered_security-fixture",Context.MODE_PRIVATE)
            assertFalse(prefs.all.toString().contains("SecretFixture-56"))
            assertFalse(prefs.all.toString().contains("09121234567"))
            store.clear();assertNull(store.load())
        }finally{store.clear();other.clear()}
    }

    @Test fun corruptedRememberedLoginDoesNotCrash() {
        val store=SecureLoginStore(context,"corrupt-fixture")
        val prefs=context.getSharedPreferences("ghadir_remembered_corrupt-fixture",Context.MODE_PRIVATE)
        prefs.edit().putString("ciphertext","broken").commit()
        assertNull(store.load());assertTrue(prefs.all.isEmpty())
    }

    @Test fun biometricUsesWrappedActivityAndDeclaredPermission() {
        assertEquals(PackageManager.PERMISSION_GRANTED,context.checkSelfPermission("android.permission.USE_BIOMETRIC"))
        assertFalse(biometricEnabled(context,"new-fixture-account"))
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->assertSame(activity,ContextWrapper(ContextWrapper(activity)).fragmentActivity())}
        }
    }

    @Test fun legacyMessageCenterReadStateIsAccountScoped() = runBlocking {
        var status="آماده‌سازی"
        val http=transport {r->
            check(r.method=="GET") // Legacy read flags are local; never POST an unsupported route.
            when(r.url.encodedPath.removePrefix("/partners")){
                "/api/notifications"->"[]"
                "/api/orders"->JSONObject().put("id",56001).put("number","GHP-56").put("status",status).put("created_at","2026-10-11").let{"["+it+"]"}
                "/api/offers"->"[{\"id\":56002,\"title\":\"آفر حساب\",\"description\":\"شرایط حساب\",\"start_date\":\"2026-10-11\"}]"
                else->error("Unexpected fixture request")
            }
        }
        val api=ApiClient(context,http)
        val prefs=context.getSharedPreferences("ghadir_inbox",Context.MODE_PRIVATE)
        val a="r56-account-A";val b="r56-account-B"
        prefs.edit().remove("read:portal:"+a).remove("read:portal:"+b).commit()
        try {
            val first=PortalInboxState(api,context,a);first.refresh()
            assertEquals(2,first.items.size);assertEquals(2,first.unread)
            first.markRead(first.items.map{it.s("id")});assertEquals(0,first.unread)
            val restored=PortalInboxState(api,context,a);restored.refresh();assertEquals(0,restored.unread)
            val separate=PortalInboxState(api,context,b);separate.refresh();assertEquals(2,separate.unread)
            status="ارسال شد"
            val changed=PortalInboxState(ApiClient(context,http),context,a)
            changed.refresh();assertEquals(1,changed.unread)
            assertTrue(changed.items.any{it.s("body").contains("ارسال شد")})
        }finally{prefs.edit().remove("read:portal:"+a).remove("read:portal:"+b).commit()}
    }

    @Test fun modernMessageCenterMarksOnlyOwnedMessages() = runBlocking {
        var marked=0
        val api=ApiClient(context,transport {r->
            when(r.url.encodedPath.removePrefix("/partners")){
                "/api/notifications"->"{\"items\":[{\"id\":\"owned-56\",\"title\":\"پیام\",\"read\":false}]}"
                "/api/notifications/read"->{marked++; "{}"}
                else->error("Unexpected fixture request")
            }
        })
        val inbox=PortalInboxState(api);inbox.refresh()
        inbox.markRead(listOf("unowned"));assertEquals(0,marked)
        inbox.markRead(listOf("owned-56"));assertEquals(1,marked);assertEquals(0,inbox.unread)
    }

    @Test fun passwordLoginRestoresOptInFields() {
        val store=SecureLoginStore(context);store.clear()
        val logged=AtomicInteger()
        val apiClient=transport {r->
            when(r.url.encodedPath.removePrefix("/partners")){
                "/api/login"->{check(r.method=="POST");"{}"}
                "/api/me"->"{\"username\":\"09121234567\",\"roles\":[\"customer\"]}"
                else->error("Unexpected fixture request")
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                fun show(){scenario.onActivity {a->a.setContent {GhadirTheme {CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){LoginScreen(ApiClient(a,apiClient)){logged.incrementAndGet()}}}}}}
                show()
                compose.onNodeWithContentDescription("شماره همراه").performTextInput("09121234567")
                compose.onNodeWithContentDescription("رمز عبور").performTextInput("PasswordFixture56")
                compose.onNode(isToggleable()).performScrollTo().performClick()
                compose.onNodeWithText("ورود").performScrollTo().performClick()
                compose.waitUntil(10000){logged.get()==1}
                assertEquals("PasswordFixture56",store.load()!!.password)
                scenario.onActivity {a->a.setContent {}}
                compose.waitForIdle();show()
                compose.onNodeWithContentDescription("شماره همراه").assertTextContains("09121234567")
                compose.onNode(isToggleable()).assertIsOn()
                compose.onNode(isToggleable()).performScrollTo().performClick()
                assertNull(store.load())
            }
        }finally{store.clear()}
    }
}

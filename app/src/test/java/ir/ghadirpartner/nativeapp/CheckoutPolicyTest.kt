package ir.ghadirpartner.nativeapp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Covers business-day boundaries and response freshness without Android storage. */
class CheckoutPolicyTest {
    /** Midnight uses Tehran even when the phone is set to a different time zone. */
    @Test
    fun expiresExactlyAtBusinessMidnight() {
        val zone = TimeZone.getTimeZone("Asia/Tehran")
        val savedAt =
            Calendar
                .getInstance(zone)
                .apply {
                    set(2026, Calendar.NOVEMBER, 4, 23, 59, 59)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
        var now = savedAt
        val policy = DailyDraftPolicy(CheckoutClock { now }, zone)
        assertEquals(1000L, policy.millisUntilMidnight())
        assertTrue(policy.isValid(savedAt))
        now += 1000
        assertFalse(policy.isValid(savedAt))
        assertFalse(policy.isValid(0))
        assertFalse(policy.isValid(now + 1))
        assertTrue(policy.isValid(now))
    }

    /** Invoice selection changes only the configured legal beneficiary. */
    @Test
    fun choosesCompanyForFormalAndPersonForInformal() {
        val policy = ChequePayeePolicy("شرکت", "سلمان بور بور")
        assertEquals("شرکت", policy.nameFor("رسمی"))
        assertEquals("سلمان بور بور", policy.nameFor("غیر رسمی"))
    }

    /** Concurrent readers share transport but refresh when the TTL ends. */
    @Test
    fun coalescesReadsAndExpiresEntries() =
        runBlocking {
            var now = 10L
            var calls = 0
            val cache = ResponseCache(mapOf("/api/catalog" to 100L), CheckoutClock { now })
            val first =
                async {
                    cache.read("/api/catalog") {
                        calls++
                        delay(10)
                        "one"
                    }
                }
            val second =
                async {
                    cache.read("/api/catalog") {
                        calls++
                        "two"
                    }
                }
            assertEquals("one", first.await())
            assertEquals("one", second.await())
            assertEquals(1, calls)
            now = 110
            assertEquals("fresh", cache.read("/api/catalog") { "fresh" })
            cache.clear()
            assertEquals("after mutation", cache.read("/api/catalog") { "after mutation" })
        }

    /** Invalidation during transport prevents an old result repopulating the cache. */
    @Test
    fun rejectsInFlightEntriesAfterMutation() =
        runBlocking {
            val cache = ResponseCache(mapOf("/api/orders" to 100L), CheckoutClock { 1 })
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val old =
                async {
                    cache.read("/api/orders") {
                        started.complete(Unit)
                        finish.await()
                        "old"
                    }
                }
            started.await()
            cache.clear()
            finish.complete(Unit)
            old.await()
            assertEquals("new", cache.read("/api/orders") { "new" })
        }

    /** Financial and unlisted routes never reuse an earlier response. */
    @Test
    fun keepsFinancialCreditAndOtherRoutesFresh() =
        runBlocking {
            val cache = ResponseCache(mapOf("/api/catalog" to 100L), CheckoutClock { 1 })
            assertEquals("first", cache.read("/api/native/credit") { "first" })
            assertEquals("second", cache.read("/api/native/credit") { "second" })
            assertEquals("first", cache.read("/api/catalog-private") { "first" })
            assertEquals("second", cache.read("/api/catalog-private") { "second" })
        }
}

package ir.ghadirpartner.nativeapp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Verify a hard attempt limit and exclusion of authentication and validation errors. */
class PaymentPreparationRecoveryTest {
    @Test
    fun first502CanRecoverOnce() = runBlocking {
        var calls = 0
        val result =
            PaymentPreparationRecovery(0L).prepare {
                calls++
                if (calls == 1) throw ApiException(502, "fixture")
                "same-checkout"
            }
        assertEquals("same-checkout", result)
        assertEquals(2, calls)
    }

    @Test
    fun second502IsNotRetried() = runBlocking {
        var calls = 0
        try {
            PaymentPreparationRecovery(0L).prepare<String> {
                calls++
                throw ApiException(502, "fixture")
            }
            fail("Must propagate second failure")
        } catch (failure: ApiException) {
            assertEquals(502, failure.status)
        }
        assertEquals(2, calls)
    }

    @Test
    fun otherStatusesRemainSingleAttempt() = runBlocking {
        for (status in listOf(401, 403, 409, 422, 500, 503)) {
            var calls = 0
            try {
                PaymentPreparationRecovery(0L).prepare<String> {
                    calls++
                    throw ApiException(status, "fixture")
                }
                fail("Must propagate")
            } catch (failure: ApiException) {
                assertEquals(status, failure.status)
            }
            assertEquals(1, calls)
        }
    }
}

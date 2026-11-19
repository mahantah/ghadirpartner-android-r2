package ir.ghadirpartner.nativeapp

import kotlinx.coroutines.delay

/** Recover one transient preparation failure; never wrap final submission or wallet mutations. */
internal class PaymentPreparationRecovery(private val delayMs: Long) {
    /** Repeat only server-deduplicated checkout preparation after an explicit HTTP 502 response. */
    suspend fun <T> prepare(operation: suspend () -> T): T {
        return try {
            operation()
        } catch (failure: ApiException) {
            if (failure.status != 502) throw failure
            delay(delayMs)
            operation()
        }
    }
}

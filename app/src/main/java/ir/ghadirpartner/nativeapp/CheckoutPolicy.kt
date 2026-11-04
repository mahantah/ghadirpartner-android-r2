package ir.ghadirpartner.nativeapp

import java.util.Calendar
import java.util.TimeZone

/** Resolves the legal cheque beneficiary from the selected invoice type. */
internal class ChequePayeePolicy(
    private val formal: String = BuildConfig.CHEQUE_FORMAL_PAYEE,
    private val informal: String = BuildConfig.CHEQUE_INFORMAL_PAYEE,
) {
    /** Returns the configured beneficiary without changing settlement amounts. */
    fun nameFor(invoiceType: String): String = if (invoiceType == "رسمی") formal else informal
}

/** Supplies wall time for business-day boundaries independently of the device zone. */
internal fun interface CheckoutClock {
    /** Returns milliseconds since the Unix epoch. */
    fun now(): Long
}

/** Defines draft validity until the next midnight in the business time zone. */
internal class DailyDraftPolicy(
    private val clock: CheckoutClock,
    private val zone: TimeZone,
) {
    /** Returns the first instant of the day following creation. */
    fun expiresAt(savedAt: Long): Long =
        Calendar.getInstance(zone).run {
            timeInMillis = savedAt
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

    /** Rejects missing, future-dated and expired drafts. */
    fun isValid(savedAt: Long): Boolean {
        val now = clock.now()
        return savedAt > 0 && savedAt <= now && now < expiresAt(savedAt)
    }

    /** Returns the delay until the next business midnight. */
    fun millisUntilMidnight(): Long {
        val now = clock.now()
        return (expiresAt(now) - now).coerceAtLeast(1)
    }
}

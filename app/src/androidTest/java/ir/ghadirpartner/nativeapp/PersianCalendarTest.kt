package ir.ghadirpartner.nativeapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Validates Android ICU Persian leap years on supported API levels. */
@RunWith(AndroidJUnit4::class)
class PersianCalendarTest {
    private val service = PersianCalendarService(CheckoutClock { 0 }, "Asia/Tehran")

    /** Esfand length follows the Persian calendar rather than Gregorian leap rules. */
    @Test
    fun validatesLeapDayAndMonthTransition() {
        assertEquals(30, service.daysInMonth(PersianDay(1399, 12, 1)))
        assertEquals(29, service.daysInMonth(PersianDay(1400, 12, 1)))
        assertEquals(PersianDay(1400, 1, 1), service.moveMonth(PersianDay(1399, 12, 1), 1))
        assertEquals(PersianDay(1399, 12, 30), service.parse("۱۳۹۹/۱۲/۳۰"))
        assertEquals(service.today(), service.parse("1400/12/30"))
        assertEquals(1, service.firstWeekday(PersianDay(1400, 1, 1)))
    }
}

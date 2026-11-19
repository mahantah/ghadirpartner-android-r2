package ir.ghadirpartner.nativeapp

import org.junit.Assert.*
import org.junit.Test

/** Boundary checks cover external handoff, expiration, repeat events and clock rollback. */
class BackgroundLockTest {
    @Test
    fun shortHandoffDoesNotLock() {
        var time = 0L
        val lock = BackgroundLock(300000L) { time }
        assertFalse(lock.resumed())
        lock.stopped()
        time = 10000L
        assertFalse(lock.resumed())
        assertFalse(lock.resumed())
    }

    @Test
    fun repeatedStopDoesNotExtendGrace() {
        var time = 0L
        val lock = BackgroundLock(300000L) { time }
        lock.stopped()
        time = 299999L
        lock.stopped()
        time = 300000L
        assertTrue(lock.resumed())
    }

    @Test
    fun clockRollbackLocks() {
        var time = 100L
        val lock = BackgroundLock(300000L) { time }
        lock.stopped()
        time = 0L
        assertTrue(lock.resumed())
    }
}

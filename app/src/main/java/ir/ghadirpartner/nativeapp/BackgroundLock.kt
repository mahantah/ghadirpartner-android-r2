package ir.ghadirpartner.nativeapp

/** Preserve an unlocked session during brief external tasks using a monotonic clock. */
internal class BackgroundLock(private val graceMs: Long, private val now: () -> Long) {
    private var stoppedAt: Long? = null

    /** Mark the start of an actual background interval without extending an existing interval. */
    fun stopped() {
        if (stoppedAt == null) stoppedAt = now()
    }

    /** Consume the interval once; expiration requires the existing local unlock flow. */
    fun resumed(): Boolean {
        val start = stoppedAt ?: return false
        stoppedAt = null
        val elapsed = now() - start
        return elapsed < 0L || elapsed >= graceMs
    }
}

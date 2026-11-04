package ir.ghadirpartner.nativeapp

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Coalesces concurrent reads while retaining immutable response text per exact route. */
internal class ResponseCache(
    private val lifetimes: Map<String, Long>,
    private val clock: CheckoutClock,
) {
    private data class Entry(
        val text: String,
        val savedAt: Long,
        val generation: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val generation = AtomicLong()

    /** Loads an eligible response once per TTL; financial routes remain uncached. */
    suspend fun read(
        path: String,
        loader: suspend () -> String,
    ): String {
        val lifetime = lifetimes[path.substringBefore('?')] ?: return loader()
        return locks.getOrPut(path) { Mutex() }.withLock {
            val epoch = generation.get()
            val now = clock.now()
            val cached = entries[path]
            if (cached != null && cached.generation == epoch && now - cached.savedAt in 0 until lifetime) {
                cached.text
            } else {
                val text = loader()
                if (generation.get() == epoch) entries[path] = Entry(text, clock.now(), epoch)
                text
            }
        }
    }

    /** Invalidates all entries, including responses still loading before a mutation. */
    fun clear() {
        generation.incrementAndGet()
        entries.clear()
    }
}

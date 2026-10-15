package com.tpl.fast_mover.watch

/**
 * Decides when a newly seen file has finished being written.
 *
 * A file is "stable" once its size and modification time have stayed the same for at least
 * [minStableMs]. This keeps the mover away from photos the camera is still encoding and
 * from downloads that are still in flight. Pure Kotlin so it can be unit tested.
 */
class StabilityGate(private val minStableMs: Long = DEFAULT_MIN_STABLE_MS) {

    private data class Seen(val size: Long, val modified: Long, val unchangedSince: Long)

    private val seen = HashMap<String, Seen>()

    /**
     * Records an observation and returns true when the entry has been unchanged for long enough.
     * The first observation never returns true.
     */
    fun observe(key: String, size: Long, modified: Long, nowMs: Long): Boolean {
        val prev = seen[key]
        if (prev == null || prev.size != size || prev.modified != modified) {
            seen[key] = Seen(size, modified, nowMs)
            return false
        }
        return nowMs - prev.unchangedSince >= minStableMs
    }

    fun forget(key: String) { seen.remove(key) }

    /** Drops entries that disappeared from the folder so the map cannot grow forever. */
    fun retainOnly(keys: Set<String>) { seen.keys.retainAll(keys) }

    val size: Int get() = seen.size

    companion object {
        const val DEFAULT_MIN_STABLE_MS = 1500L
    }
}

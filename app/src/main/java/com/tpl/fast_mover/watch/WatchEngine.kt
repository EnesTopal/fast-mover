package com.tpl.fast_mover.watch

import android.util.Log
import com.tpl.fast_mover.core.AppPrefs
import com.tpl.fast_mover.core.Location
import com.tpl.fast_mover.core.TransferRecord
import com.tpl.fast_mover.transfer.FileMover
import com.tpl.fast_mover.transfer.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The watch loop: snapshot → detect new files → wait until stable → move → repeat.
 *
 * Runs until the calling coroutine is cancelled. Any exception from the watcher (folder gone,
 * permission revoked) propagates to the caller, which reports it as an error state.
 */
class WatchEngine(
    private val watcher: SourceWatcher,
    private val mover: FileMover,
    private val dest: Location,
    private val prefs: AppPrefs,
    private val sourceKey: String,
    /** True after a system restart: files newer than the stored high-water mark are still moved. */
    private val resumeFromHighWater: Boolean,
    private val listener: Listener
) {
    interface Listener {
        fun onWatching()
        fun onMoving(name: String, percent: Int)
        fun onResult(record: TransferRecord)
    }

    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val gate = StabilityGate()
    private val known = HashSet<String>()
    private val failures = HashMap<String, Int>()

    suspend fun run() = withContext(Dispatchers.IO) {
        val highWater = if (resumeFromHighWater) prefs.highWaterMs(sourceKey) else 0L
        val startedAt = System.currentTimeMillis()

        val initial = watcher.snapshot()
        var carriedOver = 0
        for (e in initial) {
            if (highWater <= 0L || e.lastModified <= highWater) known += e.name else carriedOver++
        }
        Log.d(TAG, "initial snapshot: ${initial.size} files, ${known.size} ignored, $carriedOver to process")
        if (highWater <= 0L) prefs.setHighWaterMs(sourceKey, startedAt)

        watcher.startNotifications { changes.trySend(Unit) }
        try {
            listener.onWatching()
            while (isActive) {
                val pending = scanAndMove()
                val wait = if (pending) PENDING_RECHECK_MS else watcher.reconcileIntervalMs
                withTimeoutOrNull(wait) { changes.receive() }
            }
        } finally {
            watcher.stopNotifications()
        }
    }

    /** @return true while some entries are still waiting to become stable or to be retried. */
    private suspend fun scanAndMove(): Boolean {
        val scanStart = System.currentTimeMillis()
        val entries = watcher.snapshot()
        val names = entries.mapTo(HashSet()) { it.name }
        gate.retainOnly(names)
        known.retainAll(names)
        failures.keys.retainAll(names)

        var pending = false
        for (entry in entries) {
            if (entry.name in known) continue
            if (FileNames.isTemporary(entry.name)) continue
            if (!gate.observe(entry.name, entry.size, entry.lastModified, System.currentTimeMillis())) {
                pending = true
                continue
            }

            Log.d(TAG, "moving ${entry.name} (${entry.size} bytes)")
            listener.onMoving(entry.name, 0)
            val record = mover.move(entry, dest) { copied, total ->
                val pct = if (total > 0) ((copied * 100) / total).toInt().coerceIn(0, 100) else 0
                listener.onMoving(entry.name, pct)
            }
            listener.onWatching()

            if (record.ok) {
                known += entry.name
                gate.forget(entry.name)
                failures.remove(entry.name)
                listener.onResult(record)
            } else {
                val attempts = (failures[entry.name] ?: 0) + 1
                failures[entry.name] = attempts
                Log.w(TAG, "move failed (${attempts}/$MAX_ATTEMPTS) ${entry.name}: ${record.error}")
                if (attempts >= MAX_ATTEMPTS) {
                    known += entry.name          // give up on this file, report once
                    listener.onResult(record)
                } else {
                    gate.forget(entry.name)      // must be stable again before the retry
                    pending = true
                }
            }
        }
        if (!pending) prefs.setHighWaterMs(sourceKey, scanStart)
        return pending
    }

    private companion object {
        const val TAG = "FastMover"
        const val PENDING_RECHECK_MS = 1_000L
        const val MAX_ATTEMPTS = 3
    }
}

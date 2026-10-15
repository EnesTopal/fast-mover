package com.tpl.fast_mover.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One finished (or failed) transfer. */
data class TransferRecord(
    val name: String,
    val bytes: Long,
    val timeMs: Long,
    val ok: Boolean,
    val error: String? = null
) {
    companion object
}

/**
 * Process-wide observable state shared by the service, the bubble overlay and the main screen.
 * The service is the only writer; everything else collects.
 */
object TransferState {

    sealed class Status {
        /** Service not running or not configured. */
        data object Idle : Status()
        data object Watching : Status()
        data object Paused : Status()
        data class Moving(val name: String, val percent: Int) : Status()
        data class Error(val message: String) : Status()
    }

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    /** Files moved since the service was last started. */
    private val _movedCount = MutableStateFlow(0)
    val movedCount: StateFlow<Int> = _movedCount

    private val _lastMoved = MutableStateFlow<TransferRecord?>(null)
    val lastMoved: StateFlow<TransferRecord?> = _lastMoved

    private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history: StateFlow<List<TransferRecord>> = _history

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName

    private val _destName = MutableStateFlow("")
    val destName: StateFlow<String> = _destName

    fun onServiceStarted(history: List<TransferRecord>) {
        _serviceRunning.value = true
        _movedCount.value = 0
        _history.value = history
        _lastMoved.value = history.firstOrNull { it.ok }
        _status.value = Status.Idle
    }

    fun onServiceStopped() {
        _serviceRunning.value = false
        _status.value = Status.Idle
    }

    fun setStatus(status: Status) { _status.value = status }

    fun setFolders(source: String, dest: String) {
        _sourceName.value = source
        _destName.value = dest
    }

    fun record(record: TransferRecord) {
        if (record.ok) {
            _movedCount.update { it + 1 }
            _lastMoved.value = record
        }
        _history.update { (listOf(record) + it).take(AppPrefs.HISTORY_LIMIT) }
    }
}

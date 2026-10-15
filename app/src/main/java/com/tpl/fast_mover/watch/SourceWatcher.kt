package com.tpl.fast_mover.watch

import android.net.Uri
import java.io.File

/** A regular file currently present in the source folder. */
sealed class Entry {
    abstract val name: String
    /** Size in bytes, or -1 when unknown. */
    abstract val size: Long
    /** Epoch millis, or 0 when unknown. */
    abstract val lastModified: Long

    data class FileEntry(
        val file: File,
        override val name: String,
        override val size: Long,
        override val lastModified: Long
    ) : Entry()

    data class DocEntry(
        val uri: Uri,
        val mime: String?,
        override val name: String,
        override val size: Long,
        override val lastModified: Long
    ) : Entry()
}

/**
 * Lists a source folder and signals when it may have changed.
 * Implementations throw (IOException / SecurityException) when the folder is no longer accessible;
 * the [WatchEngine] turns that into an error state.
 */
interface SourceWatcher {
    /** Current regular files (directories, hidden and temporary files filtered by the engine). */
    fun snapshot(): List<Entry>

    /** Start change notifications. [onChange] may be called from any thread, repeatedly. */
    fun startNotifications(onChange: () -> Unit)

    fun stopNotifications()

    /** Fallback full-scan interval used when no change notification arrives. */
    val reconcileIntervalMs: Long
}

package com.tpl.fast_mover.watch

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * File-API watcher (used for Downloads with "All files access").
 * inotify via [FileObserver] gives near-instant detection; a periodic reconcile scan covers
 * events inotify misses (e.g. files moved in by another process on some kernels).
 */
class FileSourceWatcher(private val dir: File) : SourceWatcher {

    private var observer: FileObserver? = null

    override val reconcileIntervalMs: Long = 15_000L

    override fun snapshot(): List<Entry> {
        if (!dir.isDirectory) throw IOException("Source folder is missing: ${dir.absolutePath}")
        val files = dir.listFiles()
            ?: throw IOException("Cannot list ${dir.absolutePath} (permission missing?)")
        return files.filter { it.isFile }.map {
            Entry.FileEntry(file = it, name = it.name, size = it.length(), lastModified = it.lastModified())
        }
    }

    override fun startNotifications(onChange: () -> Unit) {
        stopNotifications()
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or
            FileObserver.DELETE or FileObserver.MOVED_FROM
        observer = object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) {
                onChange()
            }
        }.also {
            it.startWatching()
            Log.d(TAG, "FileObserver started on ${dir.absolutePath}")
        }
    }

    override fun stopNotifications() {
        observer?.stopWatching()
        observer = null
    }

    private companion object { const val TAG = "FastMover" }
}

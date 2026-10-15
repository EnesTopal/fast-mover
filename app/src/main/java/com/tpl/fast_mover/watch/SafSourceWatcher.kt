package com.tpl.fast_mover.watch

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import com.tpl.fast_mover.core.Location
import java.io.IOException

/**
 * Storage Access Framework watcher.
 *
 * One `query()` per scan returns id, name, mime, size and mtime for every child, instead of the
 * N+1 provider round-trips `DocumentFile.listFiles()` + `.name` + `.type` used to cost.
 * A [ContentObserver] is registered for providers that notify, but the external-storage provider
 * only notifies while a cursor is open, so the short reconcile interval does the real work.
 */
class SafSourceWatcher(
    private val resolver: ContentResolver,
    treeUri: Uri
) : SourceWatcher {

    private val childrenUri: Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, Location.documentIdOf(treeUri))

    private val treeUriForChildren: Uri = treeUri
    private var observer: ContentObserver? = null

    override val reconcileIntervalMs: Long = 4_000L

    override fun snapshot(): List<Entry> {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        // SecurityException propagates when the persisted permission was revoked.
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("Provider returned no cursor for $childrenUri")
        val result = ArrayList<Entry>(cursor.count)
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val size = if (c.isNull(3)) -1L else c.getLong(3)
                val modified = if (c.isNull(4)) 0L else c.getLong(4)
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUriForChildren, id)
                result += Entry.DocEntry(uri = uri, mime = mime, name = name, size = size, lastModified = modified)
            }
        }
        return result
    }

    override fun startNotifications(onChange: () -> Unit) {
        stopNotifications()
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { onChange() }
        }
        try {
            resolver.registerContentObserver(childrenUri, false, obs)
            observer = obs
            Log.d(TAG, "ContentObserver registered on $childrenUri")
        } catch (e: Exception) {
            Log.w(TAG, "registerContentObserver failed: ${e.message}")
        }
    }

    override fun stopNotifications() {
        observer?.let { runCatching { resolver.unregisterContentObserver(it) } }
        observer = null
    }

    private companion object { const val TAG = "FastMover" }
}

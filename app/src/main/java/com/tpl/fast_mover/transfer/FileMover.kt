package com.tpl.fast_mover.transfer

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.tpl.fast_mover.core.Location
import com.tpl.fast_mover.core.TransferRecord
import com.tpl.fast_mover.watch.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Copies one file into the destination, verifies the size, then deletes the source.
 *
 * - File-API destination: written under a temporary name and renamed when complete, so a
 *   crash never leaves a half file with the final name; the result is media-scanned so it
 *   shows up in the gallery immediately.
 * - SAF destination: created via DocumentsContract and deleted again if the copy fails.
 * - Name collisions become "name (1).ext" instead of silently overwriting.
 */
class FileMover(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun move(
        entry: Entry,
        dest: Location,
        onProgress: (copied: Long, total: Long) -> Unit
    ): TransferRecord = withContext(Dispatchers.IO) {
        try {
            val (finalName, bytes) = when (dest) {
                is Location.Path -> copyToPath(entry, File(dest.absolutePath), onProgress)
                is Location.Tree -> copyToTree(entry, dest.uri, onProgress)
            }
            val deleted = deleteSource(entry)
            Log.d(TAG, "moved OK: ${entry.name} → $finalName ($bytes bytes) sourceDeleted=$deleted")
            TransferRecord(
                name = finalName,
                bytes = bytes,
                timeMs = System.currentTimeMillis(),
                ok = true,
                error = if (deleted) null else "copied, but the source file could not be deleted"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "move failed ${entry.name}: ${e.message}")
            TransferRecord(
                name = entry.name,
                bytes = entry.size,
                timeMs = System.currentTimeMillis(),
                ok = false,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    // ── destinations ──────────────────────────────────────────────────────────

    private suspend fun copyToPath(
        entry: Entry, dir: File, onProgress: (Long, Long) -> Unit
    ): Pair<String, Long> {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create ${dir.absolutePath}")
        val finalName = FileNames.uniqueName(entry.name) { File(dir, it).exists() }
        val temp = File(dir, FileNames.tempNameFor(finalName))
        try {
            val copied = openInput(entry).use { input ->
                FileOutputStream(temp).use { out -> copy(input, out, entry.size, onProgress) }
            }
            verifySize(entry, copied)
            val target = File(dir, finalName)
            if (!temp.renameTo(target)) throw IOException("Rename to $finalName failed")
            MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(mimeFor(entry)), null)
            return finalName to copied
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private suspend fun copyToTree(
        entry: Entry, destUri: Uri, onProgress: (Long, Long) -> Unit
    ): Pair<String, Long> {
        val parentDoc = Location.documentUriOf(destUri)
        val existing = childNames(destUri)
        val wantedName = FileNames.uniqueName(entry.name) { it in existing }
        val created = DocumentsContract.createDocument(resolver, parentDoc, mimeFor(entry), wantedName)
            ?: throw IOException("createDocument failed in ${destUri.lastPathSegment}")
        try {
            val copied = openInput(entry).use { input ->
                val out = resolver.openOutputStream(created, "w")
                    ?: throw IOException("Cannot open destination for writing")
                out.use { copy(input, it, entry.size, onProgress) }
            }
            verifySize(entry, copied)
            val actualName = displayNameOf(created) ?: wantedName
            return actualName to copied
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun openInput(entry: Entry): InputStream = when (entry) {
        is Entry.FileEntry -> FileInputStream(entry.file)
        is Entry.DocEntry -> resolver.openInputStream(entry.uri)
            ?: throw IOException("Cannot open source ${entry.name}")
    }

    private fun deleteSource(entry: Entry): Boolean = when (entry) {
        is Entry.FileEntry -> entry.file.delete().also { deleted ->
            // Drops the stale MediaStore row so the gallery stops showing the old location.
            if (deleted) MediaScannerConnection.scanFile(appContext, arrayOf(entry.file.absolutePath), null, null)
        }
        is Entry.DocEntry -> runCatching { DocumentsContract.deleteDocument(resolver, entry.uri) }.getOrDefault(false)
    }

    private fun verifySize(entry: Entry, copied: Long) {
        if (entry.size >= 0 && copied != entry.size) {
            throw IOException("Size mismatch: expected ${entry.size}, copied $copied")
        }
    }

    private fun mimeFor(entry: Entry): String {
        (entry as? Entry.DocEntry)?.mime?.let { if (it.isNotBlank()) return it }
        val ext = FileNames.extensionOf(entry.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun childNames(treeUri: Uri): Set<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, Location.documentIdOf(treeUri))
        val names = HashSet<String>()
        resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> while (c.moveToNext()) c.getString(0)?.let { names += it } }
        return names
    }

    private fun displayNameOf(uri: Uri): String? =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private suspend fun copy(
        input: InputStream, out: OutputStream, total: Long, onProgress: (Long, Long) -> Unit
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        var lastReported = 0L
        val step = if (total > 0) maxOf(total / 50, BUFFER_SIZE.toLong()) else Long.MAX_VALUE
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            copied += n
            if (copied - lastReported >= step) {
                lastReported = copied
                onProgress(copied, total)
            }
        }
        out.flush()
        onProgress(copied, total)
        return copied
    }

    private companion object {
        const val TAG = "FastMover"
        const val BUFFER_SIZE = 256 * 1024
    }
}

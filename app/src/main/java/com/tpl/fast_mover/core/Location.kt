package com.tpl.fast_mover.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.tpl.fast_mover.util.toSimplePath
import java.io.File

/**
 * A folder the app reads from or writes to.
 *
 * - [Path]: absolute filesystem path, used with the File API. Requires "All files access"
 *   (MANAGE_EXTERNAL_STORAGE) for shared storage such as Downloads.
 * - [Tree]: a Storage Access Framework URI (a tree URI from the picker, or a
 *   document-in-tree URI for a sub-folder created under a granted tree).
 */
sealed class Location {

    data class Path(val absolutePath: String) : Location()

    data class Tree(val uri: Uri) : Location()

    /** Short folder name shown in the bubble panel ("Camera", "Download", "2025-10-15_12-00"). */
    fun displayName(): String = when (this) {
        is Path -> File(absolutePath).name.ifEmpty { absolutePath }
        is Tree -> uri.toSimplePath().substringAfterLast('/').ifEmpty { "Storage" }
    }

    /** Longer label for the settings screen ("0/DCIM/Camera", "/storage/emulated/0/Download"). */
    fun fullLabel(): String = when (this) {
        is Path -> absolutePath
        is Tree -> uri.toSimplePath()
    }

    /** Stable key used to store per-source data such as the high-water mark. */
    fun key(): String = when (this) {
        is Path -> "path:$absolutePath"
        is Tree -> "tree:$uri"
    }

    /**
     * True when the app can still use this location. Tree permissions are checked against the
     * persisted URI permissions (they can be revoked by the system or the user at any time).
     */
    fun isAccessible(context: Context): Boolean = when (this) {
        // Every Path location comes from the "All files access" flow; a missing destination
        // folder is recreated by the mover, so the permission is the only thing to check.
        is Path -> Environment.isExternalStorageManager()
        is Tree -> hasPersistedPermission(context, uri)
    }

    companion object {
        /**
         * For a tree URI or a document-in-tree URI returns the document URI that provider calls
         * such as createDocument / buildChildDocumentsUriUsingTree need.
         */
        fun documentUriOf(uri: Uri): Uri {
            val docId = documentIdOf(uri)
            return DocumentsContract.buildDocumentUriUsingTree(uri, docId)
        }

        fun documentIdOf(uri: Uri): String =
            if (uri.pathSegments.contains("document")) DocumentsContract.getDocumentId(uri)
            else DocumentsContract.getTreeDocumentId(uri)

        private fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return false
            return context.contentResolver.persistedUriPermissions.any { p ->
                p.isReadPermission && p.isWritePermission &&
                    p.uri.authority == uri.authority &&
                    runCatching { DocumentsContract.getTreeDocumentId(p.uri) }.getOrNull() == treeId
            }
        }
    }
}

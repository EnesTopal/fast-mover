package com.tpl.fast_mover.util

import android.net.Uri
import android.provider.DocumentsContract

/**
 * "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera" → "0/DCIM/Camera".
 * Document-in-tree URIs (e.g. from DocumentFile.createDirectory()) resolve to the child path.
 */
fun Uri.toSimplePath(): String {
    return try {
        val docId = if (pathSegments.contains("document")) {
            DocumentsContract.getDocumentId(this)
        } else {
            DocumentsContract.getTreeDocumentId(this)
        }
        val parts = docId.split(":", limit = 2)
        val storage = if (parts[0] == "primary") "0" else parts[0]
        val relPath = parts.getOrNull(1).orEmpty().trimEnd('/')
        if (relPath.isNotEmpty()) "$storage/$relPath" else storage
    } catch (e: Exception) {
        lastPathSegment ?: toString()
    }
}

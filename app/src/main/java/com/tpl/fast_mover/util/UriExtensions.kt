package com.tpl.fast_mover.util

import android.net.Uri
import android.provider.DocumentsContract

fun Uri.toSimplePath(): String {
    return try {
        // Document-in-tree URIs (e.g. from DocumentFile.createDirectory()) contain both
        // /tree/ and /document/ segments — use getDocumentId to get the full child path.
        val docId = if (pathSegments.contains("document")) {
            DocumentsContract.getDocumentId(this)
        } else {
            DocumentsContract.getTreeDocumentId(this)
        }
        val parts = docId.split(":", limit = 2)
        val storage = if (parts[0] == "primary") "0" else parts[0]
        val relPath = parts.getOrNull(1).orEmpty()
        if (relPath.isNotEmpty()) "$storage/$relPath" else storage
    } catch (e: Exception) {
        lastPathSegment ?: toString()
    }
}

package com.tpl.fast_mover.util

import android.net.Uri
import android.provider.DocumentsContract

fun Uri.toSimplePath(): String {
    val treeId = DocumentsContract.getTreeDocumentId(this)
    val parts = treeId.split(":", limit = 2)
    val storage = if (parts[0] == "primary") "0" else parts[0]
    val relPath = parts.getOrNull(1).orEmpty()
    return if (relPath.isNotEmpty()) "$storage/$relPath" else storage
}
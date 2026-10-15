package com.tpl.fast_mover.transfer

/** Pure-Kotlin file-name helpers (unit tested, no Android dependencies). */
object FileNames {

    /** Suffixes browsers / downloaders use while a file is still being written. */
    private val IN_PROGRESS_SUFFIXES = listOf(
        ".part", ".crdownload", ".tmp", ".temp", ".download", ".partial",
        ".opdownload", ".!ut", ".bc!", ".aria2"
    )

    /** Our own temporary name while copying into a File-API destination. */
    private const val OWN_TEMP_SUFFIX = ".fastmover.part"

    /** Hidden files and in-progress downloads are never moved. */
    fun isTemporary(name: String): Boolean {
        if (name.isEmpty() || name.startsWith(".")) return true
        val lower = name.lowercase()
        return IN_PROGRESS_SUFFIXES.any { lower.endsWith(it) }
    }

    /** Name of the temp file written next to the final one; always counts as temporary. */
    fun tempNameFor(finalName: String): String = ".$finalName$OWN_TEMP_SUFFIX"

    /**
     * Returns [name] if no file with that name exists, otherwise "name (1).ext", "name (2).ext", ...
     * The extension is kept so the file type survives the rename.
     */
    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }

    /** Lower-case extension without the dot, or "" when there is none. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 0) return "?"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.0f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}

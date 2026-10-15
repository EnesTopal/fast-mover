package com.tpl.fast_mover.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for persisted settings. Both the UI and the service read from here;
 * the service never receives configuration through intent extras.
 *
 * Key names are kept from the first version so existing installs keep their folders.
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var source: Location?
        get() = readLocation(KEY_SOURCE_PATH, KEY_SOURCE_URI)
        set(value) = writeLocation(KEY_SOURCE_PATH, KEY_SOURCE_URI, value)

    var dest: Location?
        get() = readLocation(KEY_DEST_PATH, KEY_DEST_URI)
        set(value) = writeLocation(KEY_DEST_PATH, KEY_DEST_URI, value)

    /** SAF root of Pictures, granted once so dated sub-folders can be created without a picker. */
    var picturesRootUri: Uri?
        get() = prefs.getString(KEY_PICTURES_ROOT, null)?.toUri()
        set(value) = prefs.edit { putString(KEY_PICTURES_ROOT, value?.toString()) }

    /** Last bubble position in window coordinates; -1 means "not saved yet". */
    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, -1)
        set(value) = prefs.edit { putInt(KEY_BUBBLE_X, value) }

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, -1)
        set(value) = prefs.edit { putInt(KEY_BUBBLE_Y, value) }

    /** Total number of files moved since install, shown as a lifetime counter. */
    var movedTotal: Int
        get() = prefs.getInt(KEY_MOVED_TOTAL, 0)
        set(value) = prefs.edit { putInt(KEY_MOVED_TOTAL, value) }

    /**
     * "Everything in [sourceKey] modified up to this time has been processed."
     * Used only after the system restarts the sticky service so files that arrived while
     * the process was dead are still picked up.
     */
    fun highWaterMs(sourceKey: String): Long =
        prefs.getLong(KEY_HIGH_WATER_PREFIX + sourceKey, 0L)

    fun setHighWaterMs(sourceKey: String, value: Long) =
        prefs.edit { putLong(KEY_HIGH_WATER_PREFIX + sourceKey, value) }

    var history: List<TransferRecord>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
            List(arr.length()) { i -> TransferRecord.fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.take(HISTORY_LIMIT).forEach { arr.put(it.toJson()) }
            prefs.edit { putString(KEY_HISTORY, arr.toString()) }
        }

    fun clearHistory() = prefs.edit { remove(KEY_HISTORY) }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun readLocation(pathKey: String, uriKey: String): Location? {
        val path = prefs.getString(pathKey, null)
        if (path != null) return Location.Path(path)
        val uri = prefs.getString(uriKey, null)
        if (uri != null) return Location.Tree(uri.toUri())
        return null
    }

    private fun writeLocation(pathKey: String, uriKey: String, value: Location?) = prefs.edit {
        remove(pathKey); remove(uriKey)
        when (value) {
            is Location.Path -> putString(pathKey, value.absolutePath)
            is Location.Tree -> putString(uriKey, value.uri.toString())
            null -> Unit
        }
    }

    private fun TransferRecord.toJson(): JSONObject = JSONObject()
        .put("n", name).put("b", bytes).put("t", timeMs).put("ok", ok).put("e", error ?: JSONObject.NULL)

    companion object {
        const val HISTORY_LIMIT = 50

        private const val PREFS_NAME = "FastMoverPrefs"
        private const val KEY_SOURCE_URI = "sourceUri"
        private const val KEY_SOURCE_PATH = "sourcePath"
        private const val KEY_DEST_URI = "destUri"
        private const val KEY_DEST_PATH = "destPath"
        private const val KEY_PICTURES_ROOT = "picturesRootUri"
        private const val KEY_BUBBLE_X = "bubbleX"
        private const val KEY_BUBBLE_Y = "bubbleY"
        private const val KEY_MOVED_TOTAL = "movedTotal"
        private const val KEY_HISTORY = "history"
        private const val KEY_HIGH_WATER_PREFIX = "highWater:"

        private fun TransferRecord.Companion.fromJson(o: JSONObject) = TransferRecord(
            name = o.optString("n"),
            bytes = o.optLong("b", -1L),
            timeMs = o.optLong("t", 0L),
            ok = o.optBoolean("ok", false),
            error = if (o.isNull("e")) null else o.optString("e")
        )
    }
}

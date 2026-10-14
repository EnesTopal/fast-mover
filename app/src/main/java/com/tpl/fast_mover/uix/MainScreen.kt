package com.tpl.fast_mover.uix

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.tpl.fast_mover.R
import com.tpl.fast_mover.bubble.BubbleService
import com.tpl.fast_mover.util.toSimplePath
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import androidx.core.content.edit

private const val PREFS_NAME        = "FastMoverPrefs"
private const val KEY_SOURCE_URI    = "sourceUri"
private const val KEY_SOURCE_PATH   = "sourcePath"       // absolute path → File API (Downloads)
private const val KEY_DEST_URI      = "destUri"
private const val KEY_DEST_PATH     = "destPath"         // absolute path → File API (Pictures auto)
private const val KEY_PICTURES_ROOT = "picturesRootUri"  // SAF root for Pictures (fallback)

// Picker hint URIs — these open the SAF picker at the right directory.
// The user taps "Use this folder" once; permission is then persisted.
private val DCIM_CAMERA_TREE_URI =
    "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera".toUri()
private val PICTURES_TREE_URI =
    "content://com.android.externalstorage.documents/tree/primary%3APictures".toUri()

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Overlay permission ────────────────────────────────────────────────────
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasOverlay = Settings.canDrawOverlays(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* service runs regardless */ }

    // ── Source state ─────────────────────────────────────────────────────────
    // sourceFilePath != null  →  File API mode (Downloads)
    // sourceUri != null       →  SAF mode (DCIM/Camera or user-picked)
    // both null               →  DCIM/Camera will be used as default at start time
    var sourceFilePath by remember { mutableStateOf(prefs.getString(KEY_SOURCE_PATH, null)) }
    var sourceUri      by remember {
        mutableStateOf(
            if (sourceFilePath == null) prefs.getString(KEY_SOURCE_URI, null)?.let { it.toUri() }
            else null
        )
    }
    var sourceLabel by remember {
        mutableStateOf(when {
            sourceFilePath != null -> "Downloads"
            sourceUri != null      -> sourceUri!!.toSimplePath()
            else                   -> "DCIM/Camera (default)"
        })
    }

    // ── Dest state ────────────────────────────────────────────────────────────
    // destFilePath != null  →  File API (auto-created Pictures subfolder)
    // destUri != null       →  SAF (user-picked or SAF-created subfolder)
    var destFilePath by remember { mutableStateOf(prefs.getString(KEY_DEST_PATH, null)) }
    var destUri      by remember {
        mutableStateOf(
            if (destFilePath == null) prefs.getString(KEY_DEST_URI, null)?.let { it.toUri() }
            else null
        )
    }
    var destLabel by remember {
        mutableStateOf(when {
            destFilePath != null -> destFilePath!!.substringAfterLast('/')
            destUri != null      -> destUri!!.toSimplePath()
            else                 -> "Not selected"
        })
    }

    var picturesRootUri by remember {
        mutableStateOf(prefs.getString(KEY_PICTURES_ROOT, null)?.let { it.toUri() })
    }

    // ── Source launchers ──────────────────────────────────────────────────────

    // Generic SAF source picker — also used for DCIM/Camera quick-set (passes hint URI)
    val pickSourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sourceUri      = it
            sourceFilePath = null
            sourceLabel    = it.toSimplePath()
            prefs.edit { putString(KEY_SOURCE_URI, it.toString()).remove(KEY_SOURCE_PATH) }
        }
    }

    // Returns from "All files access" settings after the user enables MANAGE_EXTERNAL_STORAGE
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Environment.isExternalStorageManager()) {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            sourceFilePath = dir.absolutePath
            sourceUri      = null
            sourceLabel    = "Downloads"
            prefs.edit { putString(KEY_SOURCE_PATH, dir.absolutePath).remove(KEY_SOURCE_URI) }
        }
    }

    // ── Dest launchers ────────────────────────────────────────────────────────

    // Generic SAF dest picker
    val pickDestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            destUri      = it
            destFilePath = null
            destLabel    = it.toSimplePath()
            prefs.edit { putString(KEY_DEST_URI, it.toString()).remove(KEY_DEST_PATH) }
        }
    }

    // Picker for granting Pictures root access (only needed once, if MANAGE_EXTERNAL_STORAGE absent)
    val pickPicturesRootLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            picturesRootUri = it
            prefs.edit().putString(KEY_PICTURES_ROOT, it.toString()).apply()
            // Now create the dated subfolder under the granted root
            createSafDatedFolder(context, it) { newUri, newLabel ->
                destUri      = newUri
                destFilePath = null
                destLabel    = newLabel
                prefs.edit { putString(KEY_DEST_URI, newUri.toString()).remove(KEY_DEST_PATH) }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement   = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        if (!hasOverlay) {
            Text("Overlay permission is required to display balloons on top of the application.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                overlayLauncher.launch(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                ))
            }) { Text("Allow") }

        } else {
            // Source row
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { pickSourceLauncher.launch(null) }) {
                    Text("Source Folder")
                }
                // Opens picker pre-navigated to DCIM/Camera — user taps "Use this folder" once
                IconButton(onClick = { pickSourceLauncher.launch(DCIM_CAMERA_TREE_URI) }) {
                    Log.e("BubbleService", "${DCIM_CAMERA_TREE_URI}")
                    Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = "Use DCIM/Camera")
                }
                // Sets Downloads as source (File API). Requests MANAGE_EXTERNAL_STORAGE if needed.
                IconButton(onClick = {
                    if (Environment.isExternalStorageManager()) {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        sourceFilePath = dir.absolutePath
                        sourceUri      = null
                        sourceLabel    = "Downloads"
                        prefs.edit().putString(KEY_SOURCE_PATH, dir.absolutePath).remove(KEY_SOURCE_URI).apply()
                    } else {
                        allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = "Use Downloads folder")
                }
                Text(sourceLabel, modifier = Modifier.weight(1f))
            }

            // Dest row
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { pickDestLauncher.launch(null) }) {
                    Text("Dest Folder")
                }
                // Creates a dated subfolder in Pictures.
                // • If MANAGE_EXTERNAL_STORAGE is granted → no picker, done instantly.
                // • If Pictures SAF root is already saved → creates subfolder, no picker.
                // • Otherwise → opens picker at Pictures once for root access.
                IconButton(onClick = {
                    when {
                        Environment.isExternalStorageManager() -> {
                            // File API path — no picker needed
                            val folder = createFileDatedFolder()
                            if (folder != null) {
                                destFilePath = folder.absolutePath
                                destUri      = null
                                destLabel    = folder.name
                                prefs.edit().putString(KEY_DEST_PATH, folder.absolutePath).remove(KEY_DEST_URI).apply()
                            }
                        }
                        picturesRootUri != null -> {
                            // SAF path with saved root — no picker needed
                            createSafDatedFolder(context, picturesRootUri!!) { newUri, newLabel ->
                                destUri      = newUri
                                destFilePath = null
                                destLabel    = newLabel
                                prefs.edit().putString(KEY_DEST_URI, newUri.toString()).remove(KEY_DEST_PATH).apply()
                            }
                        }
                        else -> {
                            // First time, no root — open picker at Pictures
                            pickPicturesRootLauncher.launch(PICTURES_TREE_URI)
                        }
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_collections), contentDescription = "Create dated folder in Pictures")
                }
                Text(destLabel, modifier = Modifier.weight(1f))
            }

            Button(
                enabled = destUri != null || destFilePath != null,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val svcIntent = Intent(context, BubbleService::class.java).apply {
                        when {
                            sourceFilePath != null -> putExtra("sourcePath", sourceFilePath)
                            sourceUri != null      -> putExtra("sourceUri", sourceUri)
                            else                   -> putExtra("sourceUri", DCIM_CAMERA_TREE_URI)
                        }
                        when {
                            destFilePath != null -> putExtra("destPath", destFilePath)
                            destUri != null      -> putExtra("destUri", destUri)
                        }
                    }
                    ContextCompat.startForegroundService(context, svcIntent)
                    Log.d("MainScreen", "src=${sourceFilePath ?: sourceUri}  dst=${destFilePath ?: destUri}")
                    if (context is ComponentActivity) context.finish()
                }
            ) {
                Text("Start Balloon Service")
            }
        }
    }
}

// Creates a dated subfolder in Pictures using the File API (requires MANAGE_EXTERNAL_STORAGE)
private fun createFileDatedFolder(): File? {
    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val folderName  = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val folder      = File(picturesDir, folderName)
    return if (folder.mkdirs() || folder.exists()) folder else null
}

// Creates a dated subfolder via SAF (used when MANAGE_EXTERNAL_STORAGE is not available)
private fun createSafDatedFolder(
    context: Context,
    rootUri: Uri,
    onResult: (Uri, String) -> Unit
) {
    val root       = DocumentFile.fromTreeUri(context, rootUri) ?: return
    val folderName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val newFolder  = root.createDirectory(folderName) ?: return
    onResult(newFolder.uri, newFolder.uri.toSimplePath())
}

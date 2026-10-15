package com.tpl.fast_mover.uix

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tpl.fast_mover.R
import com.tpl.fast_mover.bubble.BubbleService
import com.tpl.fast_mover.core.AppPrefs
import com.tpl.fast_mover.core.Location
import com.tpl.fast_mover.core.TransferState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FastMover"

// Picker hints: the SAF picker opens at these folders; the user confirms with "Use this folder".
private val DCIM_CAMERA_TREE_URI =
    "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera".toUri()
private val PICTURES_TREE_URI =
    "content://com.android.externalstorage.documents/tree/primary%3APictures".toUri()

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val prefs = remember { AppPrefs(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── permissions ──────────────────────────────────────────────────────────
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAllFiles by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var hasNotifications by remember { mutableStateOf(notificationsGranted(context)) }

    // ── folders ──────────────────────────────────────────────────────────────
    var source by remember { mutableStateOf(prefs.source) }
    var dest by remember { mutableStateOf(prefs.dest) }

    // ── service state ────────────────────────────────────────────────────────
    val running by TransferState.serviceRunning.collectAsState()
    val status by TransferState.status.collectAsState()
    val moved by TransferState.movedCount.collectAsState()

    var startAfterNotificationPrompt by remember { mutableStateOf(false) }
    var useDownloadsAfterGrant by remember { mutableStateOf(false) }

    // Settings screens return without a result: re-check everything on resume.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                hasAllFiles = Environment.isExternalStorageManager()
                hasNotifications = notificationsGranted(context)
                source = prefs.source
                dest = prefs.dest
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun setSource(location: Location) { prefs.source = location; source = location }
    fun setDest(location: Location) { prefs.dest = location; dest = location }

    fun useDownloadsAsSource() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        setSource(Location.Path(dir.absolutePath))
    }

    fun launchService() {
        BubbleService.start(context)
        activity?.finish()
    }

    // ── launchers ────────────────────────────────────────────────────────────
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { hasOverlay = Settings.canDrawOverlays(context) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted
        if (startAfterNotificationPrompt) {
            startAfterNotificationPrompt = false
            launchService()        // runs regardless of the answer; the notification is optional
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasAllFiles = Environment.isExternalStorageManager()
        if (hasAllFiles && useDownloadsAfterGrant) useDownloadsAsSource()
        useDownloadsAfterGrant = false
    }

    val pickSourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            takePersistable(context, it)
            setSource(Location.Tree(it))
        }
    }

    val pickDestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            takePersistable(context, it)
            setDest(Location.Tree(it))
        }
    }

    val pickPicturesRootLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            takePersistable(context, it)
            prefs.picturesRootUri = it
            createSafDatedFolder(context, it)?.let { folder -> setDest(Location.Tree(folder)) }
                ?: Toast.makeText(context, "Could not create a folder there", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAllFilesSettings() {
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        try {
            allFilesLauncher.launch(perApp)
        } catch (e: ActivityNotFoundException) {
            allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun onDownloadsShortcut() {
        if (Environment.isExternalStorageManager()) {
            useDownloadsAsSource()
        } else {
            useDownloadsAfterGrant = true
            openAllFilesSettings()
        }
    }

    fun onDatedPicturesFolder() {
        when {
            Environment.isExternalStorageManager() -> {
                val folder = createFileDatedFolder()
                if (folder != null) setDest(Location.Path(folder.absolutePath))
                else Toast.makeText(context, "Could not create the folder", Toast.LENGTH_SHORT).show()
            }
            prefs.picturesRootUri != null -> {
                val root = prefs.picturesRootUri!!
                val folder = createSafDatedFolder(context, root)
                if (folder != null) setDest(Location.Tree(folder))
                else pickPicturesRootLauncher.launch(PICTURES_TREE_URI)   // root permission was lost
            }
            else -> pickPicturesRootLauncher.launch(PICTURES_TREE_URI)
        }
    }

    fun onStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
            startAfterNotificationPrompt = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchService()
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Fast Mover", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Watches a folder and moves every new file to another folder. " +
                "A floating bubble shows what is happening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Permissions ──
        SectionCard(title = "Permissions") {
            PermissionRow(
                label = "Display over other apps",
                detail = "Required for the bubble",
                granted = hasOverlay,
                onGrant = {
                    overlayLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                    )
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    label = "Notifications",
                    detail = "Shows status and Pause / Stop buttons",
                    granted = hasNotifications,
                    onGrant = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }
            PermissionRow(
                label = "All files access",
                detail = "Only needed to watch the whole Downloads folder",
                granted = hasAllFiles,
                onGrant = { openAllFilesSettings() }
            )
        }

        // ── Source ──
        SectionCard(title = "Source folder") {
            FolderLabel(source)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickSourceLauncher.launch(null) }) { Text("Choose folder") }
                FilledTonalIconButton(onClick = { pickSourceLauncher.launch(DCIM_CAMERA_TREE_URI) }) {
                    Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = "Use DCIM/Camera")
                }
                FilledTonalIconButton(onClick = { onDownloadsShortcut() }) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = "Use Downloads")
                }
            }
            Text(
                text = "Android does not let the folder picker grant the Downloads root (only its " +
                    "sub-folders such as Download/Telegram). To watch all of Downloads use the " +
                    "download button, which asks for \"All files access\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Destination ──
        SectionCard(title = "Destination folder") {
            FolderLabel(dest)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDestLauncher.launch(null) }) { Text("Choose folder") }
                FilledTonalIconButton(onClick = { onDatedPicturesFolder() }) {
                    Icon(painterResource(R.drawable.ic_collections), contentDescription = "New dated folder in Pictures")
                }
            }
            Text(
                text = "The collections button creates a new folder named with the current date " +
                    "and time inside Pictures.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Service ──
        SectionCard(title = if (running) "Running" else "Service") {
            if (running) {
                val src = source?.displayName() ?: "?"
                val dst = dest?.displayName() ?: "?"
                Text("$src  →  $dst", style = MaterialTheme.typography.titleMedium)
                Text(statusText(status), style = MaterialTheme.typography.bodyMedium)
                Text("Moved this session: $moved", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val paused = status == TransferState.Status.Paused
                    val canToggle = status !is TransferState.Status.Error && status != TransferState.Status.Idle
                    Button(
                        onClick = { if (paused) BubbleService.resume(context) else BubbleService.pause(context) },
                        enabled = canToggle
                    ) { Text(if (paused) "Resume" else "Pause") }
                    OutlinedButton(
                        onClick = { BubbleService.stop(context) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Stop") }
                    if (status is TransferState.Status.Error) {
                        TextButton(onClick = { BubbleService.start(context) }) { Text("Retry") }
                    }
                }
                Text(
                    "Changing a folder above takes effect when you tap Restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { BubbleService.start(context) }) { Text("Restart with current folders") }
            } else {
                val ready = hasOverlay && source != null && dest != null
                Button(onClick = { onStart() }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("Start bubble service")
                }
                if (!ready) {
                    Text(
                        text = when {
                            !hasOverlay -> "Grant \"Display over other apps\" first."
                            source == null -> "Choose a source folder."
                            else -> "Choose a destination folder."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Files already in the source folder are left alone; only files that arrive " +
                        "while the service is running are moved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, detail: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Text("Granted", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        } else {
            Button(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun FolderLabel(location: Location?) {
    if (location == null) {
        Text("Not selected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    } else {
        Text(location.displayName(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            location.fullLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun statusText(status: TransferState.Status): String = when (status) {
    TransferState.Status.Idle -> "Starting…"
    TransferState.Status.Watching -> "Watching for new files"
    TransferState.Status.Paused -> "Paused – new files are not moved"
    is TransferState.Status.Moving -> "Moving ${status.name}… ${status.percent}%"
    is TransferState.Status.Error -> "Error: ${status.message}"
}

// ── helpers ──────────────────────────────────────────────────────────────────

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun takePersistable(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (e: SecurityException) {
        Log.e(TAG, "takePersistableUriPermission failed: ${e.message}")
    }
}

private fun datedFolderName(): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

/** Dated sub-folder in Pictures via the File API (needs "All files access"). */
private fun createFileDatedFolder(): File? {
    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val folder = File(picturesDir, datedFolderName())
    return if (folder.mkdirs() || folder.isDirectory) folder else null
}

/** Dated sub-folder via SAF under a granted Pictures root; null when the root is unusable. */
private fun createSafDatedFolder(context: Context, rootUri: Uri): Uri? {
    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
    if (!root.canWrite()) return null
    return root.createDirectory(datedFolderName())?.uri
}

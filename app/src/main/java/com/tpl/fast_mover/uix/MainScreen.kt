package com.tpl.fast_mover.uix

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tpl.fast_mover.bubble.BubbleService
import com.tpl.fast_mover.util.toSimplePath

private const val PREFS_NAME = "FastMoverPrefs"
private const val KEY_SOURCE_URI = "sourceUri"
private const val KEY_DEST_URI = "destUri"

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    var sourceUri by remember {
        mutableStateOf(
            prefs.getString(KEY_SOURCE_URI, null)?.let { Uri.parse(it) }
        )
    }
    var sourcePath by remember {
        mutableStateOf(sourceUri?.toSimplePath() ?: "Didn't Selected")
    }

    var destUri by remember {
        mutableStateOf(
            prefs.getString(KEY_DEST_URI, null)?.let { Uri.parse(it) }
        )
    }
    var destPath by remember {
        mutableStateOf(destUri?.toSimplePath() ?: "Didn't Selected")
    }

    val pickSourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sourceUri = it
            sourcePath = it.toSimplePath()
            prefs.edit().putString(KEY_SOURCE_URI, it.toString()).apply()
        }
    }

    val pickDestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            destUri = it
            destPath = it.toSimplePath()
            prefs.edit().putString(KEY_DEST_URI, it.toString()).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (!hasOverlayPermission) {
            Text("Overlay permission is required to display balloons on top of the application.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }) {
                Text("Allow")
            }

        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { pickSourceLauncher.launch(null) }) {
                    Text("Select Source Folder")
                }
                Text(sourcePath, modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { pickDestLauncher.launch(null) }) {
                    Text("Select Destination Folder")
                }
                Text(destPath, modifier = Modifier.weight(1f))
            }

            Button(
                enabled = sourceUri != null && destUri != null,
                onClick = {
                    val svcIntent = Intent(context, BubbleService::class.java).apply {
                        putExtra("sourceUri", sourceUri)
                        putExtra("destUri", destUri)
                    }
                    ContextCompat.startForegroundService(context, svcIntent)
                    Log.d("BubbleService", "Kaynak: $sourceUri, Hedef: $destUri")
                    Log.d("BubbleService", "Balloon started successfully.")
                    if (context is ComponentActivity) context.finish()
                }
            ) {
                Text("Start Balloon Service")
            }
        }
    }
}

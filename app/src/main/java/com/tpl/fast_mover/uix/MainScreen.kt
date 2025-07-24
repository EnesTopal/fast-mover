package com.tpl.fast_mover.uix

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tpl.fast_mover.bubble.BubbleService
import com.tpl.fast_mover.util.toSimplePath

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // 1) İzni reaktif olarak takip et
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    var hasAllFilesAccess by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    Environment.isExternalStorageManager()
        )
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kullanıcı geri döndüğünde kontrolü yenile
        hasAllFilesAccess = Environment.isExternalStorageManager()
    }

    // 2) Kaynak ve hedef klasör URI & path state’leri
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourcePath by remember { mutableStateOf("Seçilmedi") }
    var destUri by remember { mutableStateOf<Uri?>(null) }
    var destPath by remember { mutableStateOf("Seçilmedi") }

    // 3) Klasör seçme launcher’ları
    val pickSourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sourceUri = it
            sourcePath = it.toSimplePath()
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
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasAllFilesAccess) {
            // ———————————————————————————————
            // 1) All files access izni bloğu
            Text("Uygulamanın tüm dosyalara erişimi için izin gerekli.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                allFilesAccessLauncher.launch(intent)
            }) {
                Text("Tüm Dosyalara İzin Ver")
            }

        }

        else if (!hasOverlayPermission) {
            // ———————————————————————————————
            // 2) Overlay izni bloğu
            Text("Uygulamanın üstünde balon gösterebilmek için overlay izni gerekli.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }) {
                Text("İzin Ver")
            }

        } else {
            // ———————————————————————————————
            // ASIL UYGULAMA BLOĞU (izin var)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { pickSourceLauncher.launch(null) }) {
                    Text("Kaynak Klasör Seç")
                }
                Text(sourcePath, modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { pickDestLauncher.launch(null) }) {
                    Text("Hedef Klasör Seç")
                }
                Text(destPath, modifier = Modifier.weight(1f))
            }


            Button(onClick = {
                val svcIntent = Intent(context, BubbleService::class.java).apply {
                    putExtra("sourceUri", sourceUri)
                    putExtra("destUri", destUri)
                }
                ContextCompat.startForegroundService(context, svcIntent)
                Log.d("MainScreen", "Kaynak: $sourceUri, Hedef: $destUri")
                Log.d("MainScreen", "Balon servisi başlatılıyor")
                if (context is ComponentActivity) context.finish()
            }) {
                Text("Balonu Başlat")
            }
        }
    }
}

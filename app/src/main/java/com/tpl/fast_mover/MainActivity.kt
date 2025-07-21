package com.tpl.fast_mover


import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        var isVisible = false
        var isBubbleRunning = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FloatingBubbleScreen()
        }
    }
}

@Composable
fun FloatingBubbleScreen() {
    val context = LocalContext.current
    // Compose içi durumu Activity companion objesiyle senkron tutuyoruz
    var bubbleRunning by remember { mutableStateOf(MainActivity.isBubbleRunning) }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Button(onClick = {
                // 1) Overlay iznini kontrol et
                if (!Settings.canDrawOverlays(context)) {
                    val permIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(permIntent)
                    Toast.makeText(
                        context,
                        "Lütfen balon (overlay) iznini verin",
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }
                if (MainActivity.isBubbleRunning) {
                    // Durdur
                    val stopIntent = Intent(context, BubbleService::class.java)
                    context.stopService(stopIntent)
                    Toast.makeText(context, "stop service", Toast.LENGTH_SHORT).show()
                } else {
                    // Başlat
                    val startIntent = Intent(context, BubbleService::class.java).apply {
                        putExtra("size", 60)
                        putExtra("noti_message", "HELLO FROM MAIN ACT")
                    }
                    ContextCompat.startForegroundService(context, startIntent)
                    MainActivity.isVisible = false
                }
                // Durum değişikliğini kaydet
                MainActivity.isBubbleRunning = !MainActivity.isBubbleRunning
                bubbleRunning = MainActivity.isBubbleRunning
            }) {
                Text(text = if (bubbleRunning) "Balonu Durdur" else "Balonu Başlat")
            }
        }
    }
}
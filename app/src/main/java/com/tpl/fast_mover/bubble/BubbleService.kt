package com.tpl.fast_mover.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.webkit.MimeTypeMap
import androidx.compose.runtime.DisposableEffect
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.torrydo.floatingbubbleview.CloseBubbleBehavior
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.helper.ViewHelper
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandableBubbleService
import com.torrydo.floatingbubbleview.service.expandable.ExpandedBubbleBuilder
import com.tpl.fast_mover.R
import com.tpl.fast_mover.uix.BubbleCompose
import com.tpl.fast_mover.uix.ExpandedScreen
import com.tpl.fast_mover.util.BubbleState.lastMovedFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.hypot

private const val TAG = "FastMover"

private const val PREFS_NAME      = "FastMoverPrefs"
private const val KEY_SOURCE_URI  = "sourceUri"
private const val KEY_SOURCE_PATH = "sourcePath"
private const val KEY_DEST_URI    = "destUri"
private const val KEY_DEST_PATH   = "destPath"

private const val NOTIF_CHANNEL_ID = "fast_mover_service"
private const val NOTIF_ID         = 101
private const val ACTION_STOP      = "com.tpl.fast_mover.ACTION_STOP"

class BubbleService : ExpandableBubbleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    private var sourceUri: Uri?         = null
    private var sourceFilePath: String? = null
    private var destUri: Uri?           = null
    private var destFilePath: String?   = null

    private var touchStartX = 0f
    private var touchStartY = 0f

    // True when we (not the system) triggered the expanded-view close.
    // Used by DisposableEffect to decide whether to call minimize() on Back gesture.
    private var expandedClosedByUs = false

    // Dynamically registered so the notification tap can reach us without a manifest entry.
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_STOP) {
                Log.d(TAG, "stop broadcast received — stopping service")
                stopSelf()
            }
        }
    }

    // ── Foreground notification ───────────────────────────────────────────────

    override fun startNotificationForeground() {
        Log.d(TAG, "startNotificationForeground")

        // Broadcast-based stop: avoids the Android 12+ restriction on starting
        // background services directly from a notification content intent.
        val stopPi = PendingIntent.getBroadcast(
            this, 0,
            Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // minSdk = 30 (Android 11) so NotificationChannel is always available
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID, "Fast Mover", NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fast Mover is running")
            .setContentText("Tap to stop the service")
            .setContentIntent(stopPi)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        try {
            startForeground(NOTIF_ID, notification)
            Log.d(TAG, "startForeground OK")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground FAILED: ${e.message}")
            throw e
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        try {
            super.onCreate()
        } catch (e: Exception) {
            Log.e(TAG, "super.onCreate FAILED: ${e.message}")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP))
        }
        Log.d(TAG, "stopReceiver registered")

        try {
            minimize()
            Log.d(TAG, "bubble shown")
        } catch (e: Exception) {
            Log.e(TAG, "minimize FAILED: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification tap sends ACTION_STOP — stop the service completely.
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "ACTION_STOP received — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "onStartCommand intentNull=${intent == null}")

        // Android restarts START_STICKY services with a null intent after killing them.
        // Fall back to SharedPreferences so the watcher resumes without re-opening the app.
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        sourceFilePath = intent?.getStringExtra("sourcePath")
            ?: prefs.getString(KEY_SOURCE_PATH, null)

        destFilePath = intent?.getStringExtra("destPath")
            ?: prefs.getString(KEY_DEST_PATH, null)

        sourceUri = if (sourceFilePath == null) {
            intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    it.getParcelableExtra("sourceUri", Uri::class.java)
                else @Suppress("DEPRECATION") it.getParcelableExtra("sourceUri")
            } ?: prefs.getString(KEY_SOURCE_URI, null)?.let { Uri.parse(it) }
        } else null

        destUri = if (destFilePath == null) {
            intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    it.getParcelableExtra("destUri", Uri::class.java)
                else @Suppress("DEPRECATION") it.getParcelableExtra("destUri")
            } ?: prefs.getString(KEY_DEST_URI, null)?.let { Uri.parse(it) }
        } else null

        Log.d(TAG, "src  path=$sourceFilePath  uri=$sourceUri")
        Log.d(TAG, "dst  path=$destFilePath  uri=$destUri")

        val hasSrc  = sourceUri != null || sourceFilePath != null
        val hasDest = destUri   != null || destFilePath   != null

        if (!hasSrc)  { Log.w(TAG, "no source — skipping watcher") }
        if (!hasDest) { Log.w(TAG, "no dest — skipping watcher") }

        if (hasSrc && hasDest) {
            watchJob?.cancel()
            watchJob = serviceScope.launch {
                when {
                    sourceFilePath != null -> startWatchingFile(sourceFilePath!!)
                    sourceUri      != null -> startWatchingUri(sourceUri!!)
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        watchJob?.cancel()
        serviceScope.cancel()
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
    }

    // ── Bubble configuration ──────────────────────────────────────────────────

    override fun configBubble(): BubbleBuilder? {
        Log.d(TAG, "configBubble")
        return BubbleBuilder(this)
            .triggerClickablePerimeterPx(5f)
            .bubbleCompose { BubbleCompose(expand = {}) }
            .enableAnimateToEdge(true)
            // forceDragging(true) is required for drag to work with bubbleCompose.
            // Clicks are detected manually in onFingerUp below.
            .forceDragging(true)
            .bubbleStyle(null)
            .startLocation(0, 100)
            .closeBubbleView(
                ViewHelper.fromDrawable(this, R.drawable.ic_launcher_foreground, 60, 60)
            )
            .closeBubbleStyle(null)
            .closeBehavior(CloseBubbleBehavior.FIXED_CLOSE_BUBBLE)
            .distanceToClose(100)
            .bottomBackground(false)
            .addFloatingBubbleListener(object : FloatingBubbleListener {
                override fun onFingerDown(x: Float, y: Float) {
                    touchStartX = x; touchStartY = y
                }
                override fun onFingerMove(x: Float, y: Float) {}
                override fun onFingerUp(x: Float, y: Float) {
                    val dist = hypot((x - touchStartX).toDouble(), (y - touchStartY).toDouble())
                    Log.d(TAG, "fingerUp dist=${"%.1f".format(dist)}")
                    if (dist < 20.0) {
                        Log.d(TAG, "tap → expand()")
                        expand()
                    }
                }
            })
    }

    override fun configExpandedBubble(): ExpandedBubbleBuilder? {
        Log.d(TAG, "configExpandedBubble")
        return ExpandedBubbleBuilder(this)
            .expandedCompose {
                // On Android 12+ the system Back gesture removes the overlay window before
                // onDispatchKeyEvent fires. DisposableEffect detects the disposal and calls
                // minimize() so the bubble reappears, unless we closed it ourselves.
                DisposableEffect(Unit) {
                    onDispose {
                        if (!expandedClosedByUs) {
                            Log.d(TAG, "expanded view closed by system — restoring bubble")
                            serviceScope.launch(Dispatchers.Main) {
                                runCatching { minimize() }
                            }
                        }
                        expandedClosedByUs = false
                    }
                }
                ExpandedScreen(
                    popBack = {
                        expandedClosedByUs = true
                        minimize()
                    },
                    lastMovedFileFlow = lastMovedFileName,
                    onStopService = {
                        expandedClosedByUs = true
                        stopSelf()
                    }
                )
            }
            .onDispatchKeyEvent { event ->
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    expandedClosedByUs = true
                    minimize()
                    true    // consume so system doesn't also handle it
                } else null
            }
            .startLocation(0, 50)
            .draggable(true)
            .style(null)
            .fillMaxWidth(false)
            .enableAnimateToEdge(true)
            .dimAmount(0.5f)
    }

    // ── Watchers ──────────────────────────────────────────────────────────────

    private suspend fun startWatchingUri(sourceDir: Uri) {
        Log.d(TAG, "watcher start — SAF  uri=$sourceDir")

        val sourceDoc = try {
            DocumentFile.fromTreeUri(this, sourceDir)
        } catch (e: SecurityException) {
            Log.e(TAG, "fromTreeUri SecurityException: ${e.message}"); null
        }
        if (sourceDoc == null) { Log.e(TAG, "sourceDoc null — permission missing?"); return }
        Log.d(TAG, "sourceDoc OK  canRead=${sourceDoc.canRead()}")

        val existingNames = try {
            sourceDoc.listFiles().mapNotNull { it.name }.toMutableSet()
                .also { Log.d(TAG, "initial snapshot: ${it.size} files") }
        } catch (e: SecurityException) {
            Log.e(TAG, "initial listFiles failed: ${e.message}"); return
        }

        while (true) {
            delay(2000)
            val current = try {
                sourceDoc.listFiles()
            } catch (e: SecurityException) {
                Log.e(TAG, "listFiles lost permission: ${e.message}"); return
            }
            val newFiles = current.filter { it.name != null && it.name !in existingNames }
            if (newFiles.isNotEmpty()) Log.d(TAG, "${newFiles.size} new file(s) detected")

            for (file in newFiles) {
                val name = file.name ?: continue
                existingNames.add(name)
                Log.d(TAG, "moving (SAF): $name")
                try {
                    val mime = file.type ?: "application/octet-stream"
                    move(
                        input  = { contentResolver.openInputStream(file.uri) },
                        output = { openDestOutput(mime, name) },
                        delete = { file.delete() },
                        name   = name
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "move exception $name: ${e.message}")
                }
            }
        }
    }

    private suspend fun startWatchingFile(sourceDirPath: String) {
        Log.d(TAG, "watcher start — File API  path=$sourceDirPath")

        val sourceDir = File(sourceDirPath)
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            Log.e(TAG, "source dir missing: $sourceDirPath"); return
        }
        Log.d(TAG, "sourceDir OK  canRead=${sourceDir.canRead()}")

        val existingNames = (sourceDir.listFiles()?.map { it.name } ?: emptyList())
            .toMutableSet()
            .also { Log.d(TAG, "initial snapshot: ${it.size} files") }

        while (true) {
            delay(2000)
            val current = sourceDir.listFiles() ?: continue
            val newFiles = current.filter { it.isFile && it.name !in existingNames }
            if (newFiles.isNotEmpty()) Log.d(TAG, "${newFiles.size} new file(s) detected")

            for (file in newFiles) {
                existingNames.add(file.name)
                Log.d(TAG, "moving (File): ${file.name}")
                try {
                    val ext  = file.name.substringAfterLast('.', "").lowercase()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        ?: "application/octet-stream"
                    move(
                        input  = { FileInputStream(file) },
                        output = { openDestOutput(mime, file.name) },
                        delete = { file.delete() },
                        name   = file.name
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "move exception ${file.name}: ${e.message}")
                }
            }
        }
    }

    // ── Move helpers ──────────────────────────────────────────────────────────

    private fun openDestOutput(mimeType: String, fileName: String): OutputStream? {
        val path = destFilePath
        val uri  = destUri
        return when {
            path != null -> {
                val dir = File(path).also { if (!it.exists()) it.mkdirs() }
                Log.d(TAG, "dest File: $path")
                FileOutputStream(File(dir, fileName))
            }
            uri != null -> {
                val destDoc = try {
                    DocumentFile.fromTreeUri(this, uri)
                } catch (e: SecurityException) {
                    Log.e(TAG, "dest SecurityException: ${e.message}"); null
                }
                if (destDoc == null) { Log.e(TAG, "destDoc null"); return null }
                Log.d(TAG, "dest SAF  canWrite=${destDoc.canWrite()}")
                val destFile = destDoc.createFile(mimeType, fileName)
                if (destFile == null) { Log.e(TAG, "createFile returned null"); return null }
                contentResolver.openOutputStream(destFile.uri)
            }
            else -> { Log.e(TAG, "no dest configured"); null }
        }
    }

    private fun move(
        input: () -> InputStream?,
        output: () -> OutputStream?,
        delete: () -> Boolean,
        name: String
    ) {
        val ins = input()  ?: run { Log.e(TAG, "input null: $name");  return }
        val out = output() ?: run { Log.e(TAG, "output null: $name"); ins.close(); return }
        try {
            ins.use { i -> out.use { o -> i.copyTo(o) } }
            val deleted = delete()
            Log.d(TAG, "moved OK: $name  srcDeleted=$deleted")
            lastMovedFileName.value = name
        } catch (e: Exception) {
            Log.e(TAG, "copyTo failed $name: ${e.message}")
        }
    }
}

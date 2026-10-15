package com.tpl.fast_mover.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tpl.fast_mover.MainActivity
import com.tpl.fast_mover.R
import com.tpl.fast_mover.core.AppPrefs
import com.tpl.fast_mover.core.Location
import com.tpl.fast_mover.core.TransferRecord
import com.tpl.fast_mover.core.TransferState
import com.tpl.fast_mover.core.TransferState.Status
import com.tpl.fast_mover.overlay.BubbleController
import com.tpl.fast_mover.transfer.FileMover
import com.tpl.fast_mover.watch.FileSourceWatcher
import com.tpl.fast_mover.watch.SafSourceWatcher
import com.tpl.fast_mover.watch.SourceWatcher
import com.tpl.fast_mover.watch.WatchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foreground service that owns the folder watcher and the floating bubble.
 *
 * Lifecycle rules that keep it stable on real devices:
 * - `startForeground()` is the very first thing in `onCreate()`, before anything that can fail,
 *   so the system never kills the app for a foreground service that did not start in time.
 * - Configuration comes from [AppPrefs] only. A sticky restart (null intent) therefore resumes
 *   with the same folders and, thanks to the high-water mark, also moves files that arrived
 *   while the process was dead.
 * - The bubble is (re)shown on every `onStartCommand`, idempotently.
 */
class BubbleService : Service(), BubbleController.Callbacks, WatchEngine.Listener {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null

    private lateinit var prefs: AppPrefs
    private lateinit var mover: FileMover
    private lateinit var notificationManager: NotificationManager
    private var controller: BubbleController? = null

    private var paused = false
    private var stopping = false
    private var overlayError: String? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "command ${intent.action}")
            when (intent.action) {
                ACTION_PAUSE -> pause()
                ACTION_RESUME -> resume()
                ACTION_STOP -> stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        prefs = AppPrefs(this)
        mover = FileMover(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        TransferState.onServiceStarted(prefs.history)
        prefs.source?.let { s -> prefs.dest?.let { d -> TransferState.setFolders(s.displayName(), d.displayName()) } }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopping = true
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this, commandReceiver,
            IntentFilter().apply { addAction(ACTION_PAUSE); addAction(ACTION_RESUME); addAction(ACTION_STOP) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (!Settings.canDrawOverlays(this)) {
            // Keep the service alive long enough for the notification to explain the problem.
            fail("\"Display over other apps\" permission is missing – open the app and allow it")
            stopping = true
            stopSelf()
            return
        }
        controller = BubbleController(this, prefs, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stopping) return START_NOT_STICKY
        val restartedBySystem = intent == null
        Log.d(TAG, "onStartCommand action=${intent?.action} restartedBySystem=$restartedBySystem")

        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            else -> startWatching(resumeFromHighWater = restartedBySystem)
        }
        controller?.ensureVisible()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopping = true
        watchJob?.cancel()
        ioScope.cancel()
        controller?.hideAll()
        controller = null
        runCatching { unregisterReceiver(commandReceiver) }
        TransferState.onServiceStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ── watching ──────────────────────────────────────────────────────────────

    private fun startWatching(resumeFromHighWater: Boolean) {
        watchJob?.cancel()
        watchJob = null
        paused = false

        val source = prefs.source
        val dest = prefs.dest
        if (source == null || dest == null) {
            fail("Source or destination folder is not set – open the app"); return
        }
        TransferState.setFolders(source.displayName(), dest.displayName())
        if (!source.isAccessible(this)) {
            fail("Lost access to ${source.displayName()} – reselect it in the app"); return
        }
        if (!dest.isAccessible(this)) {
            fail("Lost access to ${dest.displayName()} – reselect it in the app"); return
        }

        val watcher: SourceWatcher = when (source) {
            is Location.Path -> FileSourceWatcher(File(source.absolutePath))
            is Location.Tree -> SafSourceWatcher(contentResolver, source.uri)
        }
        val engine = WatchEngine(
            watcher = watcher,
            mover = mover,
            dest = dest,
            prefs = prefs,
            sourceKey = source.key(),
            resumeFromHighWater = resumeFromHighWater,
            listener = this
        )
        TransferState.setStatus(Status.Watching)
        updateNotification()
        Log.d(TAG, "watching ${source.fullLabel()} → ${dest.fullLabel()} (highWater=$resumeFromHighWater)")

        watchJob = ioScope.launch {
            try {
                engine.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "watcher died: ${e.message}")
                withContext(Dispatchers.Main) {
                    fail(
                        if (e is SecurityException) "Lost access to ${source.displayName()} – reselect it in the app"
                        else (e.message ?: "Watcher stopped unexpectedly")
                    )
                }
            }
        }
    }

    private fun pause() {
        val status = TransferState.status.value
        if (status !is Status.Watching && status !is Status.Moving) return
        watchJob?.cancel()
        watchJob = null
        paused = true
        TransferState.setStatus(Status.Paused)
        updateNotification()
    }

    /** Files that arrived while paused are not moved: the watcher starts from a fresh snapshot. */
    private fun resume() {
        if (!paused) return
        startWatching(resumeFromHighWater = false)
    }

    private fun fail(message: String) {
        Log.w(TAG, "error state: $message")
        watchJob?.cancel()
        watchJob = null
        paused = false
        TransferState.setStatus(Status.Error(message))
        updateNotification()
    }

    // ── WatchEngine.Listener (called from the IO thread) ──────────────────────

    override fun onWatching() {
        if (!paused && !stopping) {
            TransferState.setStatus(Status.Watching)
            updateNotification()
        }
    }

    override fun onMoving(name: String, percent: Int) {
        TransferState.setStatus(Status.Moving(name, percent))
        if (percent == 0) updateNotification()
    }

    override fun onResult(record: TransferRecord) {
        TransferState.record(record)
        prefs.history = TransferState.history.value
        if (record.ok) prefs.movedTotal = prefs.movedTotal + 1
        updateNotification()
    }

    // ── BubbleController.Callbacks (main thread) ──────────────────────────────

    override fun onTogglePause() {
        if (paused) resume() else pause()
    }

    override fun onStop() {
        stopSelf()
    }

    override fun onOverlayError(t: Throwable) {
        // The watcher keeps working; the notification tells the user the bubble is gone.
        overlayError = t.message ?: t.javaClass.simpleName
        updateNotification()
    }

    // ── notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Fast Mover service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows what Fast Mover is watching and lets you pause or stop it"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val status = TransferState.status.value
        val source = TransferState.sourceName.value
        val dest = TransferState.destName.value
        val moved = TransferState.movedCount.value

        val title = when (status) {
            is Status.Paused -> "Fast Mover paused"
            is Status.Error -> "Fast Mover needs attention"
            is Status.Moving -> "Fast Mover is moving a file"
            else -> "Fast Mover is watching"
        }
        val folders = if (source.isNotEmpty()) "$source → $dest" else "No folders selected"
        var text = when (status) {
            is Status.Error -> status.message
            is Status.Moving -> "${status.name} · $folders"
            else -> "$folders · moved $moved"
        }
        overlayError?.let { text = "$text · bubble hidden ($it)" }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (status !is Status.Error) {
            if (status is Status.Paused) {
                builder.addAction(R.drawable.ic_play, "Resume", commandIntent(ACTION_RESUME, 1))
            } else {
                builder.addAction(R.drawable.ic_pause, "Pause", commandIntent(ACTION_PAUSE, 1))
            }
        }
        builder.addAction(R.drawable.ic_stop, "Stop", commandIntent(ACTION_STOP, 2))
        return builder.build()
    }

    private fun commandIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this, requestCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateNotification() {
        if (stopping) return
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "notify failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FastMover"
        private const val CHANNEL_ID = "fast_mover_service"
        private const val NOTIFICATION_ID = 101

        const val ACTION_START = "com.tpl.fast_mover.action.START"
        const val ACTION_PAUSE = "com.tpl.fast_mover.action.PAUSE"
        const val ACTION_RESUME = "com.tpl.fast_mover.action.RESUME"
        const val ACTION_STOP = "com.tpl.fast_mover.action.STOP"

        /** Starts (or re-configures) the service from the settings stored in [AppPrefs]. */
        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        fun pause(context: Context) = sendCommand(context, ACTION_PAUSE)

        fun resume(context: Context) = sendCommand(context, ACTION_RESUME)

        private fun sendCommand(context: Context, action: String) {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        }
    }
}

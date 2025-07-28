package com.tpl.fast_mover.bubble


import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.documentfile.provider.DocumentFile
import com.torrydo.floatingbubbleview.CloseBubbleBehavior
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.helper.NotificationHelper
import com.torrydo.floatingbubbleview.helper.ViewHelper
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandableBubbleService
import com.torrydo.floatingbubbleview.service.expandable.ExpandedBubbleBuilder
import com.tpl.fast_mover.uix.ExpandedScreen
import com.tpl.fast_mover.R
import com.tpl.fast_mover.uix.BubbleCompose
import com.tpl.fast_mover.util.BubbleState.lastMovedFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class BubbleService : ExpandableBubbleService() {

    private var sourceUri: Uri? = null
    private var destUri: Uri? = null

    private var watchJob: Job? = null
    private val seenFileNames = mutableSetOf<String>()


    override fun startNotificationForeground() {

        val noti = NotificationHelper(this)
        noti.createNotificationChannel()
        startForeground(noti.notificationId, noti.defaultNotification())
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sourceUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("sourceUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("sourceUri")
        }

        destUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("destUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("destUri")
        }

        Log.d("BubbleService", "Source URI: $sourceUri, Dest URI: $destUri")

        if (sourceUri != null && destUri != null) {
            watchJob?.cancel()
            watchJob = CoroutineScope(Dispatchers.IO).launch {
                startWatching(sourceUri!!, destUri!!)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }


    override fun onDestroy() {
        super.onDestroy()
        watchJob?.cancel()
    }

    override fun onCreate() {
        super.onCreate()
        minimize()
    }

    override fun configBubble(): BubbleBuilder? {

        return BubbleBuilder(this)

            .triggerClickablePerimeterPx(5f)

            .bubbleCompose {
                BubbleCompose(expand = { expand() })
            }
            // enable auto animate bubble to the left/right side when release, true by default
            .enableAnimateToEdge(true)
            .forceDragging(true)
            // set style for the bubble, fade animation by default
            .bubbleStyle(null)
            // set start location for the bubble, (x=0, y=0) is the top-left
            .startLocation(0,100)    // in dp
//            .startLocationPx(100, 100)  // in px
            // set close-bubble view
            .closeBubbleView(
                ViewHelper.fromDrawable(
                    this,
                    R.drawable.ic_launcher_foreground,
                    60,
                    60
                )
            )
            // set style for close-bubble, null by default
            .closeBubbleStyle(null)
            .closeBehavior(CloseBubbleBehavior.FIXED_CLOSE_BUBBLE)
            .distanceToClose(100)
            .bottomBackground(false)
            .addFloatingBubbleListener(object : FloatingBubbleListener {
                override fun onFingerMove(
                    x: Float,
                    y: Float
                ) {
                }

                override fun onFingerUp(
                    x: Float,
                    y: Float
                ) {
                }

                override fun onFingerDown(x: Float, y: Float) {
                }
            })

    }

    override fun configExpandedBubble(): ExpandedBubbleBuilder? {
        return ExpandedBubbleBuilder(this)
            .expandedCompose {
                ExpandedScreen(
                    popBack = { minimize() },
                    lastMovedFileFlow = lastMovedFileName,
                    onStopService = {
                        stopSelf()
                    }
                )
            }
            .onDispatchKeyEvent {
                if (it.keyCode == KeyEvent.KEYCODE_BACK) {
                    minimize()
                }
                null
            }
            .startLocation(0,50)
            .draggable(true)
            .style(null)
            .fillMaxWidth(false)
            .enableAnimateToEdge(true)
            .dimAmount(0.5f)

    }


    private suspend fun startWatching(sourceDir: Uri, destDir: Uri) {
        val sourceDocDir = DocumentFile.fromTreeUri(this, sourceDir) ?: return
        val destDocDir = DocumentFile.fromTreeUri(this, destDir) ?: return

        val existingFileNames = sourceDocDir.listFiles().mapNotNull { it.name }.toMutableSet()

        while (true) {
            delay(2000) // Check every 2 seconds

            val currentFiles = sourceDocDir.listFiles()
            val newFiles = currentFiles.filter { it.name != null && it.name !in existingFileNames }

            for (file in newFiles) {
//                moveFile(file, destDocDir)
//                file.name?.let { existingFileNames.add(it) }
                try {
                    Log.d("BubbleService", "Moving file: $file")
                    moveFile(file, destDocDir)
                } catch (e: Exception) {
                    Log.e("BubbleService", "Error moving file: $file", e)
                    e.printStackTrace()
                }
            }
        }
    }

    private fun moveFile(sourceFile: DocumentFile, destDir: DocumentFile) {
        val context = applicationContext
        val resolver = context.contentResolver
        val deletingFileName = sourceFile.name ?: "unknown_file"

        val inputStream = resolver.openInputStream(sourceFile.uri)
        if (inputStream == null) {
            Log.d("BubbleService","InputStream null: ${sourceFile.name}")
            return
        }

        val mimeType = sourceFile.type ?: "application/octet-stream"
        val destFile = destDir.createFile(mimeType, sourceFile.name ?: "moved_file")
        if (destFile == null) {
            Log.d("BubbleService","Dest file creation failed for ${sourceFile.name}")
            inputStream.close()
            return
        }

        val outputStream = resolver.openOutputStream(destFile.uri)
        if (outputStream == null) {
            Log.d("BubbleService","OutputStream null: ${destFile.name}")
            inputStream.close()
            return
        }

        try {
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("BubbleService","Moved: ${sourceFile.name} to ${destFile.name}")
            val deleted = sourceFile.delete()
            Log.d("BubbleService","Moved: ${sourceFile.name}, deleted: $deleted")
        } catch (e: Exception) {
            Log.d("BubbleService","Move failed for ${sourceFile.name}: ${e.message}")
        }

        CoroutineScope(Dispatchers.Main).launch {
            lastMovedFileName.value = deletingFileName
        }
    }


}
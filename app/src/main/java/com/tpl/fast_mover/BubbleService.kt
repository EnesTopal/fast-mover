package com.tpl.fast_mover


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.core.app.ServiceCompat.startForeground
import com.torrydo.floatingbubbleview.CloseBubbleBehavior
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.helper.NotificationHelper
import com.torrydo.floatingbubbleview.helper.ViewHelper
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandableBubbleService
import com.torrydo.floatingbubbleview.service.expandable.ExpandedBubbleBuilder


class BubbleService : ExpandableBubbleService() {

    override fun startNotificationForeground() {

        val noti = NotificationHelper(this)
        noti.createNotificationChannel()
        startForeground(noti.notificationId, noti.defaultNotification())
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
            .forceDragging(true)
            // set style for the bubble, fade animation by default
            .bubbleStyle(null)
            // set start location for the bubble, (x=0, y=0) is the top-left
            .startLocation(100, 100)    // in dp
            .startLocationPx(100, 100)  // in px
            // enable auto animate bubble to the left/right side when release, true by default
            .enableAnimateToEdge(true)
            // set close-bubble view
            .closeBubbleView(ViewHelper.fromDrawable(this, R.drawable.ic_launcher_foreground, 60, 60))
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

                override fun onFingerDown(x: Float, y: Float) {}
            })

    }

    override fun configExpandedBubble(): ExpandedBubbleBuilder? {


        return ExpandedBubbleBuilder(this)
            .expandedCompose {
                ExpandedScreen(popBack = {minimize()})
            }
            .onDispatchKeyEvent {
                if(it.keyCode == KeyEvent.KEYCODE_BACK){
                    minimize()
                }
                null
            }
            .startLocation(0, 0)
            .draggable(true)
            .style(null)
            .fillMaxWidth(false)
            .enableAnimateToEdge(true)
            .dimAmount(0.5f)
    }
}
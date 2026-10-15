package com.tpl.fast_mover.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * Raw touch stream of an overlay window in screen coordinates. Used for dragging the bubble:
 * raw coordinates stay consistent while the window itself moves under the finger, which local
 * Compose pointer deltas do not.
 */
internal interface RawTouchHandler {
    fun onDown(rawX: Float, rawY: Float)
    fun onMove(rawX: Float, rawY: Float)
    fun onUp(rawX: Float, rawY: Float, cancelled: Boolean)
}

/**
 * Root view of an overlay window. Catches BACK (only focusable windows receive keys),
 * touches outside the window (FLAG_WATCH_OUTSIDE_TOUCH) and, when a [RawTouchHandler] is set,
 * swallows every touch so the Compose child never sees them.
 */
internal class OverlayRootView(
    context: Context,
    private val onBack: (() -> Unit)?,
    private val onOutsideTouch: (() -> Unit)?,
    private val touchHandler: RawTouchHandler?
) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && onBack != null) {
            if (event.action == KeyEvent.ACTION_UP) onBack.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = touchHandler != null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handler = touchHandler ?: return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handler.onDown(event.rawX, event.rawY)
            MotionEvent.ACTION_MOVE -> handler.onMove(event.rawX, event.rawY)
            MotionEvent.ACTION_UP -> handler.onUp(event.rawX, event.rawY, cancelled = false)
            MotionEvent.ACTION_CANCEL -> handler.onUp(event.rawX, event.rawY, cancelled = true)
        }
        return true
    }
}

/**
 * One WindowManager overlay hosting a Compose tree. Every [show] creates a fresh root view,
 * ComposeView and lifecycle owner; [dismiss] tears all of it down. Nothing is recycled.
 */
internal class OverlayWindow(
    private val context: Context,
    private val focusable: Boolean,
    private val touchable: Boolean = true,
    private val dimBehind: Float = 0f,
    private val onBack: (() -> Unit)? = null,
    private val onOutsideTouch: (() -> Unit)? = null,
    private val touchHandler: RawTouchHandler? = null,
    private val onError: (Throwable) -> Unit
) {
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)

    private var root: OverlayRootView? = null
    private var owner: OverlayLifecycleOwner? = null
    // A fresh LayoutParams per show(): WindowManager keeps a reference to the instance it was
    // given, and a view pending removal must not share it with the next one.
    private var params: WindowManager.LayoutParams? = null

    val isShown: Boolean get() = root != null

    fun show(
        x: Int,
        y: Int,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        content: @Composable () -> Unit
    ): Boolean {
        if (root != null) return true

        var flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (dimBehind > 0f) flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            this.flags = flags
            this.width = width
            this.height = height
            this.x = x
            this.y = y
            dimAmount = dimBehind
            // Default window animations would fade the bubble on every move; keep it immediate.
            windowAnimations = 0
        }

        val newOwner = OverlayLifecycleOwner()
        val view = OverlayRootView(context, onBack, onOutsideTouch, touchHandler)
        newOwner.attachTo(view)
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }
        view.addView(
            compose,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        newOwner.create()
        return try {
            windowManager.addView(view, params)
            root = view
            owner = newOwner
            this.params = params
            true
        } catch (t: Throwable) {
            // BadTokenException when the overlay permission was revoked, or any WM failure.
            Log.e(TAG, "addView failed: ${t.message}")
            newOwner.destroy()
            onError(t)
            false
        }
    }

    fun moveTo(x: Int, y: Int) {
        val view = root ?: return
        val params = params ?: return
        params.x = x
        params.y = y
        try {
            windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Log.w(TAG, "updateViewLayout failed: ${t.message}")
        }
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        params = null
        try {
            // Deferred removal: dismiss() may run inside this very view's touch dispatch.
            windowManager.removeView(view)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed: ${t.message}")
        }
        owner?.destroy()
        owner = null
    }

    private companion object { const val TAG = "FastMover" }
}

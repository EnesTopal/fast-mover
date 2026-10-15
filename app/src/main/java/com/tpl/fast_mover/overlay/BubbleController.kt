package com.tpl.fast_mover.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.mutableStateOf
import androidx.core.animation.doOnEnd
import com.tpl.fast_mover.R
import com.tpl.fast_mover.core.AppPrefs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Owns the three overlay windows (bubble, close target, expanded panel) and the
 * Hidden / Bubble / Expanded state machine. All methods must be called on the main thread.
 */
internal class BubbleController(
    serviceContext: Context,
    private val prefs: AppPrefs,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onTogglePause()
        fun onStop()
        /** The window manager refused us (overlay permission revoked etc.). */
        fun onOverlayError(t: Throwable)
    }

    private enum class State { HIDDEN, BUBBLE, EXPANDED }

    private val context: Context = createUiContext(serviceContext)
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val bubbleSize = dp(BUBBLE_DP)
    private val closeSize = dp(CLOSE_DP)

    private var state = State.HIDDEN
    private var usableWidth = 1
    private var usableHeight = 1

    private var bubbleX = 0
    private var bubbleY = 0
    private var closeX = 0
    private var closeY = 0

    private var snapAnimator: ValueAnimator? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Observed by the bubble / close-target composables.
    private val dragging = mutableStateOf(false)
    private val overClose = mutableStateOf(false)

    private val dragHandler: RawTouchHandler = object : RawTouchHandler {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onDown(rawX: Float, rawY: Float) {
            snapAnimator?.cancel()
            downX = rawX; downY = rawY
            startX = bubbleX; startY = bubbleY
            moved = false
        }

        override fun onMove(rawX: Float, rawY: Float) {
            val dx = rawX - downX
            val dy = rawY - downY
            if (!moved) {
                if (hypot(dx, dy) < touchSlop.toFloat()) return
                moved = true
                dragging.value = true
                overClose.value = false
                showCloseTarget()
            }
            bubbleX = (startX + dx.roundToInt()).coerceIn(0, maxOf(0, usableWidth - bubbleSize))
            bubbleY = (startY + dy.roundToInt()).coerceIn(0, maxOf(0, usableHeight - bubbleSize))
            bubbleWindow.moveTo(bubbleX, bubbleY)
            overClose.value = isOverCloseTarget()
        }

        override fun onUp(rawX: Float, rawY: Float, cancelled: Boolean) {
            // Window changes are posted: we are inside the bubble view's own touch dispatch here.
            if (!moved) {
                if (!cancelled) mainHandler.post { expand() }
                return
            }
            dragging.value = false
            closeWindow.dismiss()
            if (!cancelled && overClose.value) {
                overClose.value = false
                Log.d(TAG, "bubble dropped on close target → stop")
                mainHandler.post { callbacks.onStop() }
                return
            }
            overClose.value = false
            snapToEdge()
        }
    }

    private val bubbleWindow: OverlayWindow = OverlayWindow(
        context, focusable = false, touchHandler = dragHandler, onError = ::onOverlayError
    )
    private val closeWindow: OverlayWindow = OverlayWindow(
        context, focusable = false, touchable = false, onError = ::onOverlayError
    )
    private val panelWindow: OverlayWindow = OverlayWindow(
        context, focusable = true, dimBehind = 0.35f,
        onBack = ::collapse, onOutsideTouch = ::collapse, onError = ::onOverlayError
    )

    // ── public API ────────────────────────────────────────────────────────────

    /** Shows the bubble if nothing is shown; keeps an open panel as is. Safe to call repeatedly. */
    fun ensureVisible() {
        when (state) {
            State.HIDDEN -> showBubble()
            State.BUBBLE -> if (!bubbleWindow.isShown) { state = State.HIDDEN; showBubble() }
            State.EXPANDED -> if (!panelWindow.isShown) { state = State.HIDDEN; showBubble() }
        }
    }

    fun showBubble() {
        panelWindow.dismiss()
        closeWindow.dismiss()
        bubbleWindow.dismiss()
        refreshBounds()
        restorePosition()
        val ok = bubbleWindow.show(bubbleX, bubbleY, bubbleSize, bubbleSize) {
            BubbleContent(dragging = dragging.value, overClose = overClose.value)
        }
        state = if (ok) State.BUBBLE else State.HIDDEN
    }

    fun expand() {
        if (state == State.EXPANDED) return
        closeWindow.dismiss()
        bubbleWindow.dismiss()
        refreshBounds()
        val margin = dp(12)
        val width = minOf(usableWidth - 2 * margin, dp(PANEL_MAX_WIDTH_DP))
        val x = (usableWidth - width) / 2
        val y = dp(PANEL_TOP_DP)
        val ok = panelWindow.show(x, y, width, ViewGroup.LayoutParams.WRAP_CONTENT) {
            ExpandedPanel(
                onCollapse = ::collapse,
                onTogglePause = callbacks::onTogglePause,
                onStop = callbacks::onStop
            )
        }
        if (ok) state = State.EXPANDED else { state = State.HIDDEN; showBubble() }
    }

    fun collapse() {
        if (state != State.EXPANDED) return
        panelWindow.dismiss()
        state = State.HIDDEN
        showBubble()
    }

    fun hideAll() {
        snapAnimator?.cancel()
        closeWindow.dismiss()
        panelWindow.dismiss()
        bubbleWindow.dismiss()
        state = State.HIDDEN
    }

    /** Rotation, fold, font scale, dark mode: re-measure and re-show in the same state. */
    fun onConfigurationChanged() {
        when (state) {
            State.HIDDEN -> Unit
            State.BUBBLE -> {
                val wasOnLeft = bubbleX + bubbleSize / 2 < usableWidth / 2
                refreshBounds()
                bubbleX = if (wasOnLeft) 0 else usableWidth - bubbleSize
                bubbleY = bubbleY.coerceIn(0, maxOf(0, usableHeight - bubbleSize))
                savePosition()
                state = State.HIDDEN
                showBubble()
            }
            State.EXPANDED -> {
                panelWindow.dismiss()
                state = State.HIDDEN
                expand()
            }
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private fun showCloseTarget() {
        refreshBounds()
        closeX = (usableWidth - closeSize) / 2
        closeY = usableHeight - closeSize - dp(CLOSE_BOTTOM_MARGIN_DP)
        closeWindow.show(closeX, closeY, closeSize, closeSize) {
            CloseTarget(active = overClose.value)
        }
    }

    private fun isOverCloseTarget(): Boolean {
        val cx = bubbleX + bubbleSize / 2f
        val cy = bubbleY + bubbleSize / 2f
        val tx = closeX + closeSize / 2f
        val ty = closeY + closeSize / 2f
        return hypot(cx - tx, cy - ty) < dp(CLOSE_RADIUS_DP)
    }

    private fun snapToEdge() {
        snapAnimator?.cancel()
        val targetX = if (bubbleX + bubbleSize / 2 < usableWidth / 2) 0 else usableWidth - bubbleSize
        snapAnimator = ValueAnimator.ofInt(bubbleX, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                bubbleX = it.animatedValue as Int
                bubbleWindow.moveTo(bubbleX, bubbleY)
            }
            doOnEnd { savePosition() }
            start()
        }
    }

    private fun restorePosition() {
        val savedX = prefs.bubbleX
        val savedY = prefs.bubbleY
        if (savedX < 0 || savedY < 0) {
            bubbleX = 0
            bubbleY = dp(DEFAULT_TOP_DP)
        } else {
            // Re-snap to an edge in case the saved value came from another orientation.
            bubbleX = if (savedX + bubbleSize / 2 < usableWidth / 2) 0 else usableWidth - bubbleSize
            bubbleY = savedY
        }
        bubbleX = bubbleX.coerceIn(0, maxOf(0, usableWidth - bubbleSize))
        bubbleY = bubbleY.coerceIn(0, maxOf(0, usableHeight - bubbleSize))
    }

    private fun savePosition() {
        prefs.bubbleX = bubbleX
        prefs.bubbleY = bubbleY
    }

    private fun refreshBounds() {
        try {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            usableWidth = maxOf(1, metrics.bounds.width() - insets.left - insets.right)
            usableHeight = maxOf(1, metrics.bounds.height() - insets.top - insets.bottom)
        } catch (t: Throwable) {
            val dm = context.resources.displayMetrics
            usableWidth = dm.widthPixels
            usableHeight = dm.heightPixels
        }
    }

    private fun onOverlayError(t: Throwable) {
        state = State.HIDDEN
        callbacks.onOverlayError(t)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val TAG = "FastMover"
        /** Window size; the visible circle is 56dp with 4dp of room for the drag scale effect. */
        const val BUBBLE_DP = 64
        const val CLOSE_DP = 72
        const val CLOSE_RADIUS_DP = 72
        const val CLOSE_BOTTOM_MARGIN_DP = 40
        const val DEFAULT_TOP_DP = 120
        const val PANEL_TOP_DP = 72
        const val PANEL_MAX_WIDTH_DP = 380

        /**
         * A window context gives correct metrics and avoids the "non-visual Context" warnings
         * that a bare Service context triggers; the theme wrapper keeps app attributes resolvable.
         */
        fun createUiContext(serviceContext: Context): Context {
            val base = try {
                val display = serviceContext.getSystemService(DisplayManager::class.java)
                    .getDisplay(Display.DEFAULT_DISPLAY)
                serviceContext.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            } catch (t: Throwable) {
                Log.w(TAG, "createWindowContext failed, using service context: ${t.message}")
                serviceContext
            }
            return ContextThemeWrapper(base, R.style.Theme_FastMover)
        }
    }
}

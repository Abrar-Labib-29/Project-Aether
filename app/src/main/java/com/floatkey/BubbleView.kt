package com.floatkey

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator

class BubbleView(context: Context) : View(context) {

    companion object {
        private const val BUBBLE_SIZE_DP = 52f
        private const val INNER_RING_WIDTH_DP = 2f
        private const val DOT_RADIUS_DP = 2.5f
        private const val IDLE_ALPHA = 0.18f
        private const val ACTIVE_ALPHA = 0.92f
        private const val IDLE_TIMEOUT_MS = 3000L
        private const val TAP_THRESHOLD_DP = 8f
        private const val SNAP_DURATION_MS = 200L
        private const val FADE_DURATION_MS = 300L
    }

    var onTapListener: (() -> Unit)? = null
    var onDragStartListener: (() -> Unit)? = null
    var onPositionChanged: ((x: Int, y: Int) -> Unit)? = null

    var layoutParams2: WindowManager.LayoutParams? = null
    var windowManager2: WindowManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private val bubbleSizePx = dpToPx(BUBBLE_SIZE_DP)
    private val tapThresholdPx = dpToPx(TAP_THRESHOLD_DP)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((255 * 0.30f).toInt(), 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(INNER_RING_WIDTH_DP)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((255 * 0.60f).toInt(), 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val dotRadiusPx = dpToPx(DOT_RADIUS_DP)

    private var downRawX = 0f
    private var downRawY = 0f
    private var downParamX = 0
    private var downParamY = 0
    private var totalMovement = 0f
    private var isDragging = false

    private var screenWidth = 0
    private var screenHeight = 0

    private var fadeAnimator: ObjectAnimator? = null
    private var snapAnimator: ValueAnimator? = null

    private val idleRunnable = Runnable {
        fadeToIdle()
    }

    init {
        alpha = ACTIVE_ALPHA
        resetIdleTimer()
    }

    fun setScreenDimensions(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = bubbleSizePx.toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = bubbleSizePx / 2f

        canvas.drawCircle(cx, cy, radius, fillPaint)

        val ringRadius = radius - dpToPx(INNER_RING_WIDTH_DP) / 2f
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        drawDotGrid(canvas, cx, cy)
    }

    private fun drawDotGrid(canvas: Canvas, cx: Float, cy: Float) {
        val gridSpacing = dpToPx(8f)
        for (row in -1..1) {
            for (col in -1..1) {
                val dotX = cx + col * gridSpacing
                val dotY = cy + row * gridSpacing
                canvas.drawCircle(dotX, dotY, dotRadiusPx, dotPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = layoutParams2 ?: return false
        val wm = windowManager2 ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                cancelIdleTimer()
                fadeToActive()

                downRawX = event.rawX
                downRawY = event.rawY
                downParamX = params.x
                downParamY = params.y
                totalMovement = 0f
                isDragging = false

                snapAnimator?.cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                totalMovement = Math.max(totalMovement, Math.abs(dx) + Math.abs(dy))

                if (!isDragging && totalMovement > tapThresholdPx) {
                    isDragging = true
                    onDragStartListener?.invoke()
                }

                params.x = (downParamX + dx).toInt()
                params.y = (downParamY + dy).toInt()

                val statusBarHeight = dpToPx(24f).toInt()
                val navBarHeight = dpToPx(48f).toInt()
                val halfHeight = screenHeight / 2
                params.y = params.y.coerceIn(-(halfHeight - statusBarHeight), halfHeight - navBarHeight - bubbleSizePx.toInt())

                try {
                    wm.updateViewLayout(this, params)
                } catch (e: Exception) { }

                return true
            }

            MotionEvent.ACTION_UP -> {
                resetIdleTimer()

                if (!isDragging && totalMovement < tapThresholdPx) {
                    onTapListener?.invoke()
                } else {
                    snapToEdge(params, wm)
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge(params: WindowManager.LayoutParams, wm: WindowManager) {
        val halfBubble = bubbleSizePx.toInt() / 2
        val currentCenterX = params.x + halfBubble
        val halfScreen = screenWidth / 2

        val targetX = if (currentCenterX > 0) {
            halfScreen - bubbleSizePx.toInt()
        } else {
            -halfScreen
        }

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_DURATION_MS
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                try {
                    wm.updateViewLayout(this@BubbleView, params)
                } catch (e: Exception) { }
            }
            start()
        }

        onPositionChanged?.invoke(targetX, params.y)
    }

    private fun fadeToIdle() {
        fadeAnimator?.cancel()
        fadeAnimator = ObjectAnimator.ofFloat(this, "alpha", alpha, IDLE_ALPHA).apply {
            duration = FADE_DURATION_MS
            start()
        }
    }

    private fun fadeToActive() {
        fadeAnimator?.cancel()
        alpha = ACTIVE_ALPHA
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
    }

    private fun cancelIdleTimer() {
        handler.removeCallbacks(idleRunnable)
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        fadeAnimator?.cancel()
        snapAnimator?.cancel()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        )
    }
}

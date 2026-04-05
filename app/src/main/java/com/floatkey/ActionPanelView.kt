package com.floatkey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout

class ActionPanelView(context: Context) : LinearLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val volumeRepeatDelay = 300L

    private var volumeUpRunnable: Runnable? = null
    private var volumeDownRunnable: Runnable? = null

    var onVolumeUp: (() -> Unit)? = null
    var onVolumeDown: (() -> Unit)? = null
    var onScreenshot: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null

    private val buttonSizeDp = 44
    private val spacingDp = 8
    private val paddingDp = 10
    private val cornerRadiusDp = 16f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F21E1E2E")
        style = Paint.Style.FILL
    }

    private val bgRect = RectF()
    private val cornerRadiusPx: Float

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setWillNotDraw(false)

        cornerRadiusPx = dpToPx(cornerRadiusDp)
        val paddingPx = dpToPx(paddingDp.toFloat()).toInt()
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        val buttonSizePx = dpToPx(buttonSizeDp.toFloat()).toInt()
        val spacingPx = dpToPx(spacingDp.toFloat()).toInt()

        val volUpButton = createButton(R.drawable.ic_vol_up)
        val volDownButton = createButton(R.drawable.ic_vol_down)
        val screenshotButton = createButton(R.drawable.ic_screenshot)

        addView(volUpButton, LayoutParams(buttonSizePx, buttonSizePx))
        addSpacing(spacingPx)
        addView(volDownButton, LayoutParams(buttonSizePx, buttonSizePx))
        addSpacing(spacingPx)
        addView(screenshotButton, LayoutParams(buttonSizePx, buttonSizePx))

        // Horizontal divider
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#3A3A5A"))
        }
        val dividerMargin = dpToPx(4f).toInt()
        val dividerParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(1f).toInt()).apply {
            topMargin = dividerMargin
            bottomMargin = dividerMargin
        }
        addView(divider, dividerParams)

        // Exit button
        val exitButton = createExitButton()
        addView(exitButton, LayoutParams(buttonSizePx, buttonSizePx))

        volUpButton.setOnClickListener { onVolumeUp?.invoke() }
        volDownButton.setOnClickListener { onVolumeDown?.invoke() }
        screenshotButton.setOnClickListener { onScreenshot?.invoke() }
        exitButton.setOnClickListener { onExit?.invoke() }

        setupLongPress(volUpButton, isUp = true)
        setupLongPress(volDownButton, isUp = false)

        alpha = 0f
        scaleX = 0.7f
        scaleY = 0.7f
        visibility = View.GONE
    }

    private fun createButton(drawableRes: Int): ImageButton {
        return ImageButton(context).apply {
            setImageResource(drawableRes)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            imageAlpha = (255 * 0.85f).toInt()
            val innerPad = dpToPx(8f).toInt()
            setPadding(innerPad, innerPad, innerPad, innerPad)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        (v as ImageButton).drawable?.setTint(Color.parseColor("#3D8BFF"))
                        v.imageAlpha = 255
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v as ImageButton).drawable?.setTint(Color.WHITE)
                        v.imageAlpha = (255 * 0.85f).toInt()
                    }
                }
                false
            }

            drawable?.setTint(Color.WHITE)
        }
    }

    private fun createExitButton(): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_exit)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            imageAlpha = (255 * 0.85f).toInt()
            val innerPad = dpToPx(8f).toInt()
            setPadding(innerPad, innerPad, innerPad, innerPad)

            drawable?.setTint(Color.parseColor("#FF5555"))

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        (v as ImageButton).drawable?.setTint(Color.parseColor("#FF8888"))
                        v.imageAlpha = 255
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v as ImageButton).drawable?.setTint(Color.parseColor("#FF5555"))
                        v.imageAlpha = (255 * 0.85f).toInt()
                    }
                }
                false
            }
        }
    }

    private fun addSpacing(spacingPx: Int) {
        val spacer = View(context)
        addView(spacer, LayoutParams(LayoutParams.MATCH_PARENT, spacingPx))
    }

    private fun setupLongPress(button: ImageButton, isUp: Boolean) {
        button.setOnLongClickListener {
            val repeatingRunnable = object : Runnable {
                override fun run() {
                    if (isUp) onVolumeUp?.invoke() else onVolumeDown?.invoke()
                    handler.postDelayed(this, volumeRepeatDelay)
                }
            }
            if (isUp) {
                volumeUpRunnable = repeatingRunnable
            } else {
                volumeDownRunnable = repeatingRunnable
            }
            handler.post(repeatingRunnable)

            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        (v as ImageButton).drawable?.setTint(Color.parseColor("#3D8BFF"))
                        v.imageAlpha = 255
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v as ImageButton).drawable?.setTint(Color.WHITE)
                        v.imageAlpha = (255 * 0.85f).toInt()
                        if (isUp) {
                            volumeUpRunnable?.let { handler.removeCallbacks(it) }
                            volumeUpRunnable = null
                        } else {
                            volumeDownRunnable?.let { handler.removeCallbacks(it) }
                            volumeDownRunnable = null
                        }
                    }
                }
                false
            }
            true
        }
    }

    override fun onDraw(canvas: Canvas) {
        bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, cornerRadiusPx, cornerRadiusPx, bgPaint)
        super.onDraw(canvas)
    }

    fun show() {
        visibility = View.VISIBLE
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(150)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    fun hide(onComplete: (() -> Unit)? = null) {
        animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(120)
            .withEndAction {
                visibility = View.GONE
                onComplete?.invoke()
            }
            .start()
    }

    fun isShowing(): Boolean = visibility == View.VISIBLE && alpha > 0f

    fun cancelRepeats() {
        volumeUpRunnable?.let { handler.removeCallbacks(it) }
        volumeDownRunnable?.let { handler.removeCallbacks(it) }
        volumeUpRunnable = null
        volumeDownRunnable = null
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        )
    }
}

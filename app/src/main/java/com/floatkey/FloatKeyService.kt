package com.floatkey

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class FloatKeyService : Service() {

    companion object {
        private const val CHANNEL_ID = "aether_stealth_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.floatkey.ACTION_STOP"
        private const val ACTION_REVIVE = "com.floatkey.ACTION_REVIVE"
        private const val PREFS_NAME = "floatkey_prefs"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_HAS_SAVED_POS = "has_saved_pos"
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private lateinit var screenshotManager: ScreenshotManager

    private var bubbleView: BubbleView? = null
    private var actionPanelView: ActionPanelView? = null
    private var dismissOverlay: View? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var screenWidth = 0
    private var screenHeight = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private var isUserStopping = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_STOP) {
                isUserStopping = true
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (bubbleView == null) {
            initializeService()
        }

        // Schedule watchdog alarm every time service starts
        scheduleWatchdog()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN  // Invisible in status bar
        ).apply {
            description = getString(R.string.notification_text)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(ACTION_STOP)
        val stopPending = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)  // Lowest priority
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop_action),
                    stopPending
                ).build()
            )
            .build()
    }

    private fun initializeService() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP))

        // Acquire partial wake lock to keep CPU alive
        acquireWakeLock()

        screenshotManager = ScreenshotManager(this)
        screenshotManager.onHideOverlay = { hideOverlayForScreenshot() }
        screenshotManager.onShowOverlay = { showOverlayAfterScreenshot() }

        createBubble()
        createActionPanel()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "aether:service_lock"
        ).apply {
            acquire()
        }
    }

    private fun scheduleWatchdog() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reviveIntent = Intent(this, BootReceiver::class.java).apply {
            action = ACTION_REVIVE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 999, reviveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
            WATCHDOG_INTERVAL_MS,
            pendingIntent
        )
    }

    private fun cancelWatchdog() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reviveIntent = Intent(this, BootReceiver::class.java).apply {
            action = ACTION_REVIVE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 999, reviveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pendingIntent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped app from recents — restart the service
        if (!isUserStopping) {
            val restartIntent = Intent(this, FloatKeyService::class.java)
            val pendingRestart = PendingIntent.getService(
                this, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                pendingRestart
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createBubble() {
        val bubbleSizePx = dpToPx(52f).toInt()

        bubbleParams = WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = getSavedX(screenWidth, bubbleSizePx)
            y = getSavedY(screenHeight)
        }

        bubbleView = BubbleView(this).apply {
            setScreenDimensions(screenWidth, screenHeight)
            layoutParams2 = bubbleParams
            windowManager2 = windowManager

            onTapListener = { togglePanel() }
            onDragStartListener = { hidePanel() }
            onPositionChanged = { x, y -> savePosition(x, y) }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun createActionPanel() {
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        actionPanelView = ActionPanelView(this).apply {
            onVolumeUp = { adjustVolume(AudioManager.ADJUST_RAISE) }
            onVolumeDown = { adjustVolume(AudioManager.ADJUST_LOWER) }
            onScreenshot = {
                hidePanel()
                Handler(Looper.getMainLooper()).postDelayed({
                    screenshotManager.takeScreenshot()
                }, 50)
            }
            onExit = {
                hidePanel()
                isUserStopping = true
                stopSelf()
            }
        }

        windowManager.addView(actionPanelView, panelParams)
    }

    private fun togglePanel() {
        val panel = actionPanelView ?: return
        val bParams = bubbleParams ?: return
        val pParams = panelParams ?: return

        if (panel.isShowing()) {
            hidePanel()
        } else {
            val panelOffset = dpToPx(60f).toInt()

            if (bParams.x > 0) {
                pParams.x = bParams.x - panelOffset
            } else {
                pParams.x = bParams.x + panelOffset
            }
            pParams.y = bParams.y

            try {
                windowManager.updateViewLayout(panel, pParams)
            } catch (e: Exception) { }

            panel.show()
            showDismissOverlay()
        }
    }

    private fun hidePanel() {
        actionPanelView?.let { panel ->
            if (panel.isShowing()) {
                panel.cancelRepeats()
                panel.hide()
            }
        }
        removeDismissOverlay()
    }

    private fun showDismissOverlay() {
        removeDismissOverlay()

        dismissOverlay = View(this).apply {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hidePanel()
                    true
                } else {
                    false
                }
            }
        }

        val dismissParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(dismissOverlay, dismissParams)
            bubbleView?.let { windowManager.removeView(it) }
            windowManager.addView(bubbleView, bubbleParams)
            actionPanelView?.let { windowManager.removeView(it) }
            windowManager.addView(actionPanelView, panelParams)
        } catch (e: Exception) { }
    }

    private fun removeDismissOverlay() {
        dismissOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { }
            dismissOverlay = null
        }
    }

    private fun adjustVolume(direction: Int) {
        try {
            audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) { }
    }

    private fun hideOverlayForScreenshot() {
        Handler(Looper.getMainLooper()).post {
            bubbleView?.alpha = 0f
            actionPanelView?.alpha = 0f
        }
    }

    private fun showOverlayAfterScreenshot() {
        Handler(Looper.getMainLooper()).post {
            bubbleView?.alpha = 0.92f
            actionPanelView?.alpha = 1f
        }
    }

    private fun getSavedX(screenWidth: Int, bubbleSize: Int): Int {
        return if (prefs.getBoolean(KEY_HAS_SAVED_POS, false)) {
            prefs.getInt(KEY_BUBBLE_X, screenWidth / 2 - bubbleSize)
        } else {
            screenWidth / 2 - bubbleSize
        }
    }

    private fun getSavedY(screenHeight: Int): Int {
        return if (prefs.getBoolean(KEY_HAS_SAVED_POS, false)) {
            prefs.getInt(KEY_BUBBLE_Y, -(screenHeight * 10 / 100))
        } else {
            -(screenHeight * 10 / 100)
        }
    }

    private fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_BUBBLE_X, x)
            .putInt(KEY_BUBBLE_Y, y)
            .putBoolean(KEY_HAS_SAVED_POS, true)
            .apply()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: Exception) { }

        actionPanelView?.cancelRepeats()
        bubbleView?.cleanup()

        removeDismissOverlay()

        try {
            actionPanelView?.let { windowManager.removeView(it) }
        } catch (e: Exception) { }

        try {
            bubbleView?.let { windowManager.removeView(it) }
        } catch (e: Exception) { }

        screenshotManager.release()

        // Release wake lock
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) { }

        // If user explicitly stopped, cancel the watchdog
        if (isUserStopping) {
            cancelWatchdog()
        }

        stopForeground(true)
        super.onDestroy()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        )
    }
}

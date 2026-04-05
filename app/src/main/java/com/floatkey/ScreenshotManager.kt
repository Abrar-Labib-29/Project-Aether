package com.floatkey

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class ScreenshotManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    var onHideOverlay: (() -> Unit)? = null
    var onShowOverlay: (() -> Unit)? = null

    private var isCapturing = false

    init {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    fun takeScreenshot() {
        if (isCapturing) return
        isCapturing = true

        // Check storage permission before attempting anything
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            isCapturing = false
            mainHandler.post {
                Toast.makeText(context, "Storage permission needed. Please restart Aether.", Toast.LENGTH_LONG).show()
            }
            return
        }

        if (mediaProjection == null) {
            requestProjectionPermission()
            return
        }
        performCapture()
    }

    fun setProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mediaProjection = null
                    virtualDisplay?.release()
                    virtualDisplay = null
                }
            }, mainHandler)
            performCapture()
        } else {
            isCapturing = false
            mainHandler.post {
                Toast.makeText(context, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestProjectionPermission() {
        ScreenCaptureRequestActivity.onResultCallback = { resultCode, data ->
            setProjectionResult(resultCode, data)
        }
        val intent = Intent(context, ScreenCaptureRequestActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun performCapture() {
        onHideOverlay?.invoke()

        // Give the overlay time to fully hide before capture
        mainHandler.postDelayed({
            try {
                setupImageReader()
                setupVirtualDisplay()

                // Use ImageReader.OnImageAvailableListener instead of fixed delay
                // This way we wait until a frame is actually rendered
                imageReader?.setOnImageAvailableListener({ reader ->
                    // Remove listener immediately so we only capture one frame
                    reader.setOnImageAvailableListener(null, null)

                    // Small delay to ensure the frame is fully rendered
                    mainHandler.postDelayed({
                        captureImage()
                    }, 50)
                }, mainHandler)

                // Fallback: if no image arrives within 2 seconds, give up
                mainHandler.postDelayed({
                    if (isCapturing) {
                        isCapturing = false
                        cleanupCapture()
                        mainHandler.post {
                            onShowOverlay?.invoke()
                            Toast.makeText(context, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }, 2000)
            } catch (e: Exception) {
                isCapturing = false
                cleanupCapture()
                mainHandler.post {
                    onShowOverlay?.invoke()
                    Toast.makeText(context, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }, 150)
    }

    private fun setupImageReader() {
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )
    }

    private fun setupVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AetherCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler
        )
    }

    private fun captureImage() {
        if (!isCapturing) return

        val image: Image? = try {
            imageReader?.acquireLatestImage()
        } catch (e: Exception) {
            null
        }

        if (image == null) {
            isCapturing = false
            mainHandler.post {
                onShowOverlay?.invoke()
                Toast.makeText(context, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
            cleanupCapture()
            return
        }

        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val croppedBitmap = if (bitmap.width > screenWidth) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            Thread {
                val saved = saveBitmapToGallery(croppedBitmap)
                croppedBitmap.recycle()
                cleanupCapture()
                isCapturing = false

                mainHandler.post {
                    onShowOverlay?.invoke()
                    if (saved) {
                        Toast.makeText(context, R.string.screenshot_saved, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            image.close()
            isCapturing = false
            cleanupCapture()
            mainHandler.post {
                onShowOverlay?.invoke()
                Toast.makeText(context, R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val filename = "Aether_screenshot_$timestamp.png"

            // API 28 compatible: write to Pictures directory directly
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val screenshotsDir = File(picturesDir, "Screenshots")
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs()
            }

            val file = File(screenshotsDir, filename)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }

            // Register with MediaStore so it shows in gallery
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_MODIFIED, timestamp / 1000)
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun cleanupCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun release() {
        cleanupCapture()
        mediaProjection?.stop()
        mediaProjection = null
    }
}

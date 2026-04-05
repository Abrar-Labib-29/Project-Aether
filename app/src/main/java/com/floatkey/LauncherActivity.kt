package com.floatkey

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LauncherActivity : Activity() {

    companion object {
        private const val REQUEST_OVERLAY = 1001
        private const val REQUEST_STORAGE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0D0D1A")
        window.navigationBarColor = Color.parseColor("#0D0D1A")

        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D1A"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padH = dpToPx(32f).toInt()
            val padV = dpToPx(48f).toInt()
            setPadding(padH, padV, padH, padV)
        }

        // App Icon
        val iconView = ImageView(this).apply {
            setImageResource(R.drawable.floatkey_icon)
            val iconSize = dpToPx(96f).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(24f).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER

            // Circular clip with subtle glow bg
            val glowBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A1A2E"))
                setStroke(dpToPx(2f).toInt(), Color.parseColor("#2A2A4A"))
            }
            background = glowBg
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            val iconPad = dpToPx(12f).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
        }
        container.addView(iconView)

        // App Name
        val appName = TextView(this).apply {
            text = "Aether"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(6f).toInt()
            }
        }
        container.addView(appName)

        // Tagline
        val tagline = TextView(this).apply {
            text = "Volume & Screenshot Overlay"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#7A7A9E"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(8f).toInt()
            }
        }
        container.addView(tagline)

        // Version
        val version = TextView(this).apply {
            text = "v1.0"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#4A4A6A"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(36f).toInt()
            }
        }
        container.addView(version)

        // Description Card
        val descCard = createCard().apply {
            val descText = TextView(this@LauncherActivity).apply {
                text = "A lightweight floating button that lives on top of all your apps.\n\n" +
                        "• Tap the bubble to adjust volume\n" +
                        "• Take screenshots without hardware buttons\n" +
                        "• Built for low-RAM devices — runs forever"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.parseColor("#B0B0CC"))
                setLineSpacing(dpToPx(3f), 1f)
            }
            addView(descText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(16f).toInt() }
        }
        container.addView(descCard)

        // Creator Card
        val creatorCard = createCard().apply {
            val creatorLabel = TextView(this@LauncherActivity).apply {
                text = "CREATED BY"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Color.parseColor("#3D8BFF"))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.15f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(8f).toInt() }
            }
            addView(creatorLabel)

            val creatorName = TextView(this@LauncherActivity).apply {
                text = "Abrar Labib"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(4f).toInt() }
            }
            addView(creatorName)

            val creatorUni = TextView(this@LauncherActivity).apply {
                text = "Final Year Student at United International University"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(Color.parseColor("#8888AA"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(14f).toInt() }
            }
            addView(creatorUni)

            // Divider
            val divider = View(this@LauncherActivity).apply {
                setBackgroundColor(Color.parseColor("#2A2A3E"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(1f).toInt()
                ).apply {
                    topMargin = dpToPx(2f).toInt()
                    bottomMargin = dpToPx(14f).toInt()
                }
            }
            addView(divider)

            // Email row
            val emailRow = createInfoRow("\uD83D\uDCE7", "abrar.labib2829@gmail.com") {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:abrar.labib2829@gmail.com")
                }
                try { startActivity(emailIntent) } catch (_: Exception) {}
            }
            addView(emailRow)

            // Facebook row
            val fbRow = createInfoRow("\uD83C\uDF10", "facebook.com/Abrar.Labib29") {
                val fbIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://www.facebook.com/Abrar.Labib29/")
                }
                try { startActivity(fbIntent) } catch (_: Exception) {}
            }
            addView(fbRow)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(32f).toInt() }
        }
        container.addView(creatorCard)

        // Start Button
        val startButton = TextView(this).apply {
            text = "Start Aether"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.03f

            val buttonBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14f)
                colors = intArrayOf(
                    Color.parseColor("#2563EB"),
                    Color.parseColor("#3D8BFF")
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            background = buttonBg

            val padV = dpToPx(16f).toInt()
            val padH = dpToPx(24f).toInt()
            setPadding(padH, padV, padH, padV)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(16f).toInt()
            }

            setOnClickListener { handleStart() }
        }
        container.addView(startButton)

        // Footer
        val footer = TextView(this).apply {
            text = "Built with \u2764\uFE0F for the Walton Primo RX7 Mini"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#3A3A5A"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        container.addView(footer)

        root.addView(container)
        return root
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16f)
                setColor(Color.parseColor("#14142A"))
                setStroke(dpToPx(1f).toInt(), Color.parseColor("#1E1E3A"))
            }
            background = bg
            val pad = dpToPx(18f).toInt()
            setPadding(pad, pad, pad, pad)
        }
    }

    private fun createInfoRow(emoji: String, text: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dpToPx(4f).toInt()
            setPadding(0, pad, 0, pad)
            isClickable = true
            isFocusable = true

            val emojiView = TextView(this@LauncherActivity).apply {
                this.text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dpToPx(10f).toInt() }
            }
            addView(emojiView)

            val textView = TextView(this@LauncherActivity).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.parseColor("#8888CC"))
            }
            addView(textView)

            setOnClickListener { onClick() }
        }
    }

    private fun handleStart() {
        // Step 1: Check storage permission first
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE
            )
            return
        }
        // Step 2: Check overlay permission
        checkOverlayAndStart()
    }

    private fun checkOverlayAndStart() {
        if (Settings.canDrawOverlays(this)) {
            startServiceAndFinish()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Storage granted, now check overlay
                checkOverlayAndStart()
            } else {
                Toast.makeText(this, "Storage permission is needed for screenshots", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.floatkey_running, Toast.LENGTH_LONG).show()
                startServiceAndFinish()
            } else {
                Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startServiceAndFinish() {
        // Request battery optimization exemption if not already granted
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val exemptIntent = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(exemptIntent)
            } catch (e: Exception) { }
        }

        val serviceIntent = Intent(this, FloatKeyService::class.java)
        startForegroundService(serviceIntent)
        finish()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        )
    }
}

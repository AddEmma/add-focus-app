package com.addemma.focuslock

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class BlockingOverlayActivity : AppCompatActivity() {

    private var endTimeMillis: Long = 0
    private var isNsfwBlock: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerTextView: TextView

    private val timerRunnable = object : Runnable {
        override fun run() {
            val remainingMillis = endTimeMillis - System.currentTimeMillis()

            if (remainingMillis <= 0) {
                finish()
                return
            }

            updateTimerDisplay(remainingMillis)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        endTimeMillis = intent.getLongExtra(AppBlockerService.EXTRA_END_TIME, 0)
        val pkgName = intent.getStringExtra(AppBlockerService.EXTRA_PACKAGE_NAME)
        isNsfwBlock = intent.getBooleanExtra(AppBlockerService.EXTRA_IS_NSFW_BLOCK, false)
        val nsfwReason = intent.getStringExtra(AppBlockerService.EXTRA_NSFW_REASON)
        val nsfwLayer = intent.getStringExtra(AppBlockerService.EXTRA_NSFW_LAYER)
        val nsfwTrigger = intent.getStringExtra(AppBlockerService.EXTRA_NSFW_TRIGGER)

        // Fetch app name if possible
        val appName = if (pkgName != null) {
            try {
                val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                "this app"
            }
        } else {
            "this app"
        }

        // Make the activity fullscreen and prevent dismissal
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Handle back press — go to home screen, not back to blocked app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToHomeScreen()
            }
        })

        val bgColor = if (isNsfwBlock) 0xFF1E0F1C.toInt() else 0xFF1A1A2E.toInt()
        val accentColor = if (isNsfwBlock) 0xFFFF4B72.toInt() else 0xFF6C63FF.toInt()

        // Create UI programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(56, 56, 56, 56)
        }

        // Shield / Lock icon
        val iconTextView = TextView(this).apply {
            text = if (isNsfwBlock) "🛡️" else "🔒"
            textSize = 64f
            gravity = Gravity.CENTER
        }
        layout.addView(iconTextView)

        if (isNsfwBlock) {
            val layerLabel = when (nsfwLayer) {
                "VISUAL_AI_SCANNER" -> "ON-DEVICE VISUAL AI SCANNER"
                "SEARCH_URL_FILTER" -> "SEARCH & URL INTERCEPTOR"
                else -> "24/7 CONTENT SHIELD"
            }
            val badgeBg = GradientDrawable().apply {
                setColor(0x33FF4B72)
                cornerRadius = 32f
                setStroke(2, accentColor)
            }
            val badgeView = TextView(this).apply {
                text = layerLabel
                textSize = 11f
                setTextColor(accentColor)
                gravity = Gravity.CENTER
                background = badgeBg
                setPadding(28, 12, 28, 12)
            }
            val badgeParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
            layout.addView(badgeView, badgeParams)
        }

        // Title
        val titleTextView = TextView(this).apply {
            text = if (isNsfwBlock) "Explicit / Sexy Content Blocked" else "App Blocked"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 16)
        }
        layout.addView(titleTextView)

        // Description
        val descText = if (isNsfwBlock) {
            val reasonLine = nsfwReason ?: "Restricted visual or search content was detected."
            val triggerLine = if (!nsfwTrigger.isNullOrBlank()) "\nTrigger: $nsfwTrigger" else ""
            "$reasonLine$triggerLine\n\n$appName is temporarily locked on cooldown to protect your focus."
        } else {
            "$appName is blocked for now.\nStay focused!"
        }

        val descTextView = TextView(this).apply {
            text = descText
            textSize = 15f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        layout.addView(descTextView)

        // Timer
        timerTextView = TextView(this).apply {
            text = "00:00"
            textSize = 48f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        layout.addView(timerTextView)

        // Timer label
        val timerLabelTextView = TextView(this).apply {
            text = if (isNsfwBlock) "cooldown lock remaining" else "remaining"
            textSize = 14f
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        layout.addView(timerLabelTextView)

        // Spacer
        val spacer = TextView(this).apply {
            setPadding(0, 56, 0, 0)
        }
        layout.addView(spacer)

        // Go Home button
        val homeButtonBg = GradientDrawable().apply {
            setColor(0xFF2D2D44.toInt())
            cornerRadius = 24f
        }
        val homeButton = TextView(this).apply {
            text = "Go to Home Screen"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(48, 28, 48, 28)
            background = homeButtonBg
            setOnClickListener {
                goToHomeScreen()
            }
        }
        layout.addView(homeButton)

        // Spacer
        val spacer2 = TextView(this).apply {
            setPadding(0, 16, 0, 0)
        }
        layout.addView(spacer2)

        // Return to Focus App button
        val returnButton = TextView(this).apply {
            text = "Open FocusLock Shield"
            textSize = 16f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                val intent = Intent(this@BlockingOverlayActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                finish()
            }
        }
        layout.addView(returnButton)

        setContentView(layout)

        // Start timer updates
        handler.post(timerRunnable)
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun updateTimerDisplay(remainingMillis: Long) {
        val totalSeconds = remainingMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        timerTextView.text = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        endTimeMillis = intent.getLongExtra(AppBlockerService.EXTRA_END_TIME, endTimeMillis)
        isNsfwBlock = intent.getBooleanExtra(AppBlockerService.EXTRA_IS_NSFW_BLOCK, isNsfwBlock)
    }

    override fun onPause() {
        super.onPause()
        if (AppBlockerService.isRunning || isNsfwBlock) {
            finish()
        }
    }
}

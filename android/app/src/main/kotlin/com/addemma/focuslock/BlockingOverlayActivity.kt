package com.addemma.focuslock

import android.content.Intent
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
        
        // Create UI programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(48, 48, 48, 48)
        }
        
        // Lock icon
        val iconTextView = TextView(this).apply {
            text = "🔒"
            textSize = 64f
            gravity = Gravity.CENTER
        }
        layout.addView(iconTextView)
        
        // Title
        val titleTextView = TextView(this).apply {
            text = "App Blocked"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }
        layout.addView(titleTextView)
        
        // Description
        val descTextView = TextView(this).apply {
            text = "$appName is blocked for now.\nStay focused!"
            textSize = 16f
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        layout.addView(descTextView)
        
        // Timer
        timerTextView = TextView(this).apply {
            text = "00:00"
            textSize = 48f
            setTextColor(0xFF6C63FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        layout.addView(timerTextView)
        
        // Timer label
        val timerLabelTextView = TextView(this).apply {
            text = "remaining"
            textSize = 14f
            setTextColor(0xAAFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        layout.addView(timerLabelTextView)

        // Spacer
        val spacer = TextView(this).apply {
            setPadding(0, 64, 0, 0)
        }
        layout.addView(spacer)
        
        // Go Home button
        val homeButton = TextView(this).apply {
            text = "Go to Home Screen"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 24)
            setBackgroundColor(0xFF2D2D44.toInt())
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
            text = "Return to Focus App"
            textSize = 16f
            setTextColor(0xFF6C63FF.toInt())
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
    }

    override fun onPause() {
        super.onPause()
        // If the blocker service is still running, the user tried to dismiss
        // the overlay (e.g., via task switcher). The service's monitoring loop
        // will catch the blocked app in the foreground and re-launch us anyway,
        // so we just finish cleanly here to avoid stacking duplicate activities.
        if (AppBlockerService.isRunning) {
            finish()
        }
    }
}

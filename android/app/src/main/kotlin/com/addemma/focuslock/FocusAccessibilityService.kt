package com.addemma.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors

class FocusAccessibilityService : AccessibilityService() {
    companion object {
        const val TAG = "FocusAccessibility"
        const val PREFS_NAME = "FlutterSharedPreferences"

        var instance: FocusAccessibilityService? = null
            private set

        // Real-time Content Shield configuration synced from Flutter / SharedPreferences
        @Volatile var contentShieldEnabled: Boolean = true
        @Volatile var searchUrlFilterEnabled: Boolean = true
        @Volatile var visualAiScannerEnabled: Boolean = true
        @Volatile var blockSexyContent: Boolean = true // Strict mode by default
        @Volatile var cooldownMinutes: Int = 10
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private var currentForegroundPackage: String = ""
    private var lastTextScanTimeMs: Long = 0
    private var lastVisualScanTimeMs: Long = 0
    private var lastViolationTimeMs: Long = 0
    private var isVisualLoopScheduled = false

    private val visualScanRunnable = object : Runnable {
        override fun run() {
            if (!isVisualLoopScheduled) return

            val pkg = currentForegroundPackage
            if (contentShieldEnabled && visualAiScannerEnabled && shouldRunVisualScanOnPackage(pkg)) {
                // Skip if this app is already locked on cooldown
                if (AppBlockerService.getCooldownRemainingMillis(this@FocusAccessibilityService, pkg) <= 0L) {
                    captureAndAnalyzeScreen(pkg)
                }
            }

            mainHandler.postDelayed(this, 1600L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        loadShieldConfigFromPrefs(this)
        startVisualScanLoop()
        Log.d(TAG, "Accessibility Service Connected (ContentShield=$contentShieldEnabled, VisualAI=$visualAiScannerEnabled)")
    }

    fun loadShieldConfigFromPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            contentShieldEnabled = prefs.getBoolean("flutter.shield_enabled", true)
            searchUrlFilterEnabled = prefs.getBoolean("flutter.shield_search_url_enabled", true)
            visualAiScannerEnabled = prefs.getBoolean("flutter.shield_visual_ai_enabled", true)
            blockSexyContent = prefs.getBoolean("flutter.shield_block_sexy", true)
            cooldownMinutes = prefs.getLong("flutter.shield_cooldown_minutes", 10L).toInt()

            val customCsv = prefs.getString("flutter.shield_custom_keywords_csv", null)
            if (!customCsv.isNullOrBlank()) {
                NsfwContentFilter.updateCustomKeywords(
                    customCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Content Shield prefs", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Ignore our own overlay / app UI and system keyboards for foreground switching
        if (pkg != packageName &&
            !pkg.contains("inputmethod", ignoreCase = true) &&
            !pkg.contains("honeyboard", ignoreCase = true) &&
            pkg != "com.android.systemui"
        ) {
            currentForegroundPackage = pkg
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "Window state changed: $pkg")
                // 1. Check active cooldown lock or focus session
                AppBlockerService.checkAndEnforceBlock(this, pkg)

                // 2. Run immediate Search/URL/Handle check on window open
                if (contentShieldEnabled && searchUrlFilterEnabled) {
                    inspectScreenTextNow(pkg, immediate = true)
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (!contentShieldEnabled || !searchUrlFilterEnabled) return
                if (NsfwContentFilter.SAFE_SYSTEM_PACKAGES.contains(pkg)) return

                // Directly inspect what the user is typing into any search bar or input field!
                val typedText = event.text?.joinToString(" ") ?: ""
                if (typedText.isNotBlank()) {
                    val directMatch = NsfwContentFilter.evaluateText(
                        text = typedText,
                        isUrlOrSearchInput = true,
                        blockSexyContent = blockSexyContent
                    )
                    if (directMatch != null) {
                        handleNsfwViolation(pkg, directMatch)
                        return
                    }
                }
                inspectScreenTextNow(pkg, immediate = true)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (!contentShieldEnabled) return
                if (NsfwContentFilter.SAFE_SYSTEM_PACKAGES.contains(pkg)) return

                // Check cooldown lock first
                if (AppBlockerService.getCooldownRemainingMillis(this, pkg) > 0L) {
                    AppBlockerService.checkAndEnforceBlock(this, pkg)
                    return
                }

                // Throttled text/URL scan
                if (searchUrlFilterEnabled) {
                    inspectScreenTextNow(pkg, immediate = false)
                }
            }
        }
    }

    private fun inspectScreenTextNow(pkg: String, immediate: Boolean) {
        if (NsfwContentFilter.SAFE_SYSTEM_PACKAGES.contains(pkg)) return
        val now = System.currentTimeMillis()
        if (!immediate && (now - lastTextScanTimeMs) < 400L) return
        lastTextScanTimeMs = now

        try {
            val root = rootInActiveWindow ?: return
            val rootPkg = root.packageName?.toString() ?: pkg
            if (NsfwContentFilter.SAFE_SYSTEM_PACKAGES.contains(rootPkg)) return

            val detection = NsfwContentFilter.inspectWindowTree(
                rootNode = root,
                packageName = rootPkg,
                blockSexyContent = blockSexyContent
            )
            if (detection != null && detection.isBlocked) {
                handleNsfwViolation(rootPkg, detection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting window tree", e)
        }
    }

    private fun shouldRunVisualScanOnPackage(pkg: String): Boolean {
        if (pkg.isBlank() || NsfwContentFilter.SAFE_SYSTEM_PACKAGES.contains(pkg)) return false
        if (NsfwContentFilter.MONITORED_VISUAL_AND_SEARCH_PACKAGES.contains(pkg)) return true

        val lower = pkg.lowercase()
        return lower.contains("instagram") ||
            lower.contains("browser") ||
            lower.contains("chrome") ||
            lower.contains("tiktok") ||
            lower.contains("musically") ||
            lower.contains("twitter") ||
            lower.contains("reddit") ||
            lower.contains("gallery") ||
            lower.contains("photos") ||
            lower.contains("video") ||
            lower.contains("telegram") ||
            lower.contains("facebook") ||
            lower.contains("snapchat")
    }

    private fun captureAndAnalyzeScreen(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = System.currentTimeMillis()
        if (now - lastVisualScanTimeMs < 1300L) return
        lastVisualScanTimeMs = now

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            hardwareBuffer.close()

                            if (bitmap != null) {
                                NsfwVisualScanner.analyzeScreenBitmap(
                                    rawBitmap = bitmap,
                                    blockSexyContent = blockSexyContent
                                ) { detection ->
                                    if (detection != null && detection.isBlocked) {
                                        mainHandler.post {
                                            // Verify user is still in that app before firing block
                                            if (currentForegroundPackage == pkg) {
                                                handleNsfwViolation(pkg, detection)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot buffer", e)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.v(TAG, "takeScreenshot skipped/failed with code: $errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling takeScreenshot", e)
        }
    }

    /**
     * Triggers an on-demand visual scan test or handles a detected NSFW / Sexy content violation:
     * 1. Navigates BACK immediately to exit the explicit search/profile/video
     * 2. Navigates HOME
     * 3. Locks the offending app for [cooldownMinutes] and displays BlockingOverlayActivity
     */
    fun handleNsfwViolation(pkg: String, detection: NsfwDetectionResult) {
        val now = System.currentTimeMillis()
        if (now - lastViolationTimeMs < 1500L) return
        lastViolationTimeMs = now

        Log.w(
            TAG,
            "!!! CONTENT SHIELD TRIGGERED in $pkg -> [${detection.layer}] " +
                "${detection.category}: ${detection.details} !!!"
        )

        // Step 1: Perform global BACK action to exit the search/post/profile inside the offending app
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (_: Exception) {
        }

        // Step 2: Register cooldown lock & show Content Shield overlay via AppBlockerService
        AppBlockerService.triggerNsfwCooldownLock(
            context = this,
            packageName = pkg,
            detection = detection,
            cooldownMinutes = cooldownMinutes
        )
    }

    private fun startVisualScanLoop() {
        if (!isVisualLoopScheduled) {
            isVisualLoopScheduled = true
            mainHandler.postDelayed(visualScanRunnable, 1500L)
        }
    }

    private fun stopVisualScanLoop() {
        isVisualLoopScheduled = false
        mainHandler.removeCallbacks(visualScanRunnable)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopVisualScanLoop()
        instance = null
        return super.onUnbind(intent)
    }
}

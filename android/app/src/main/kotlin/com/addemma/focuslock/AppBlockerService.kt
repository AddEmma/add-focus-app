package com.addemma.focuslock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class NsfwLockEntry(
    val packageName: String,
    val lockUntilMillis: Long,
    val layer: String,
    val category: String,
    val matchedTrigger: String,
    val details: String
)

class AppBlockerService : Service() {

    companion object {
        const val CHANNEL_ID = "app_blocker_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.addemma.focuslock.START_BLOCKING"
        const val ACTION_STOP = "com.addemma.focuslock.STOP_BLOCKING"
        const val EXTRA_BLOCKED_APPS = "blocked_apps"
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_PACKAGE_NAME = "package_name"

        // Extras for NSFW / Content Shield Overlay
        const val EXTRA_IS_NSFW_BLOCK = "is_nsfw_block"
        const val EXTRA_NSFW_REASON = "nsfw_reason"
        const val EXTRA_NSFW_LAYER = "nsfw_layer"
        const val EXTRA_NSFW_CATEGORY = "nsfw_category"
        const val EXTRA_NSFW_TRIGGER = "nsfw_trigger"

        const val TAG = "AppBlockerService"

        var isRunning = false
            private set

        // Real-time monitoring data accessible from MainActivity
        var blockedAppsList: List<String> = emptyList()
            private set
        var blockedAttempts: Int = 0
            private set
        var sessionEndTimeMillis: Long = 0
            private set

        // NSFW Content Shield cooldown locks & intercept history
        val nsfwCooldownLocks = ConcurrentHashMap<String, NsfwLockEntry>()
        val nsfwInterceptHistory = CopyOnWriteArrayList<Map<String, Any>>()
        @Volatile var nsfwBlockedCount: Int = 0

        // Listener for real-time focus events
        var onBlockedAttempt: ((String, Int, Boolean) -> Unit)? = null

        // Listener for app usage tracking (real-time)
        var onAppActivity: ((String) -> Unit)? = null

        // Listener for real-time NSFW Content Shield block events
        var onNsfwBlocked: ((Map<String, Any>) -> Unit)? = null

        var instance: AppBlockerService? = null

        fun getCooldownRemainingMillis(context: Context, pkg: String): Long {
            val now = System.currentTimeMillis()
            val entry = nsfwCooldownLocks[pkg] ?: return 0L
            val remaining = entry.lockUntilMillis - now
            if (remaining <= 0L) {
                nsfwCooldownLocks.remove(pkg)
                return 0L
            }
            return remaining
        }

        fun checkAndEnforceBlock(context: Context, foregroundApp: String) {
            if (foregroundApp == context.packageName ||
                foregroundApp == "com.android.systemui" ||
                foregroundApp == "com.android.settings"
            ) {
                return
            }

            // 1. Check active NSFW Cooldown Lock (works 24/7 even without a Focus Timer!)
            val remainingCooldown = getCooldownRemainingMillis(context, foregroundApp)
            if (remainingCooldown > 0L) {
                val entry = nsfwCooldownLocks[foregroundApp]
                Log.w(TAG, "Enforcing active NSFW Cooldown Lock on $foregroundApp (${remainingCooldown / 1000}s left)")
                sendUserHomeStatic(context)
                showNsfwOverlayStatic(
                    context = context,
                    pkgName = foregroundApp,
                    lockUntilMillis = entry?.lockUntilMillis ?: (System.currentTimeMillis() + remainingCooldown),
                    layer = entry?.layer ?: "CONTENT_SHIELD",
                    category = entry?.category ?: "SEXY_HALF_NAKED",
                    trigger = entry?.matchedTrigger ?: "Restricted Content",
                    reason = entry?.details ?: "Locked by Content Shield cooldown."
                )
                return
            }

            // 2. Check active Focus Session via AppBlockerService instance
            instance?.handleForegroundAppChange(foregroundApp)
        }

        fun triggerNsfwCooldownLock(
            context: Context,
            packageName: String,
            detection: NsfwDetectionResult,
            cooldownMinutes: Int
        ) {
            val now = System.currentTimeMillis()
            val durationMillis = (cooldownMinutes.coerceIn(1, 120)) * 60_000L
            val lockUntilMillis = now + durationMillis

            val entry = NsfwLockEntry(
                packageName = packageName,
                lockUntilMillis = lockUntilMillis,
                layer = detection.layer,
                category = detection.category,
                matchedTrigger = detection.matchedTrigger,
                details = detection.details
            )
            nsfwCooldownLocks[packageName] = entry
            nsfwBlockedCount++

            val eventMap = mapOf(
                "event" to "nsfw_blocked",
                "packageName" to packageName,
                "layer" to detection.layer,
                "category" to detection.category,
                "matchedTrigger" to detection.matchedTrigger,
                "details" to detection.details,
                "confidence" to detection.confidence,
                "lockUntilMillis" to lockUntilMillis,
                "cooldownMinutes" to cooldownMinutes,
                "timestamp" to now,
                "totalNsfwBlocked" to nsfwBlockedCount
            )

            nsfwInterceptHistory.add(0, eventMap)
            while (nsfwInterceptHistory.size > 50) {
                nsfwInterceptHistory.removeAt(nsfwInterceptHistory.size - 1)
            }

            onNsfwBlocked?.invoke(eventMap)

            // Persist count in SharedPreferences
            try {
                val prefs = context.getSharedPreferences(
                    FocusAccessibilityService.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                prefs.edit().putLong("flutter.shield_blocked_count", nsfwBlockedCount.toLong()).apply()
            } catch (_: Exception) {
            }

            // Immediately send user to Home Screen and launch the Content Shield Overlay
            sendUserHomeStatic(context)
            showNsfwOverlayStatic(
                context = context,
                pkgName = packageName,
                lockUntilMillis = lockUntilMillis,
                layer = detection.layer,
                category = detection.category,
                trigger = detection.matchedTrigger,
                reason = detection.details
            )
        }

        fun sendUserHomeStatic(context: Context) {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send user home", e)
            }
        }

        fun showNsfwOverlayStatic(
            context: Context,
            pkgName: String,
            lockUntilMillis: Long,
            layer: String,
            category: String,
            trigger: String,
            reason: String
        ) {
            try {
                val intent = Intent(context, BlockingOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    putExtra(EXTRA_END_TIME, lockUntilMillis)
                    putExtra(EXTRA_PACKAGE_NAME, pkgName)
                    putExtra(EXTRA_IS_NSFW_BLOCK, true)
                    putExtra(EXTRA_NSFW_LAYER, layer)
                    putExtra(EXTRA_NSFW_CATEGORY, category)
                    putExtra(EXTRA_NSFW_TRIGGER, trigger)
                    putExtra(EXTRA_NSFW_REASON, reason)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch NSFW BlockingOverlayActivity", e)
            }
        }
    }

    private var blockedApps: List<String> = emptyList()
    private var endTimeMillis: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var lastForegroundApp: String? = null
    private var launcherPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        updateLauncherPackage()
    }

    override fun onDestroy() {
        instance = null
        stopMonitoring()
        isRunning = false
        blockedAppsList = emptyList()
        blockedAttempts = 0
        sessionEndTimeMillis = 0
        onBlockedAttempt = null
        super.onDestroy()
    }

    private fun updateLauncherPackage() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        launcherPackage = resolveInfo?.activityInfo?.packageName
        Log.d(TAG, "Launcher resolved to: $launcherPackage")
    }

    val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) {
                Log.d(TAG, "Monitoring stopped")
                return
            }

            // Check if session has ended
            if (System.currentTimeMillis() >= endTimeMillis) {
                Log.d(TAG, "Session ended, stopping service")
                stopSelf()
                return
            }

            // Only use UsageStats fallback if AccessibilityService is NOT active
            if (FocusAccessibilityService.instance == null) {
                val foregroundApp = getForegroundApp()
                if (foregroundApp != null) {
                    handleForegroundAppChange(foregroundApp)
                }
            }

            // Schedule next check (2000ms for battery-friendly fallback)
            handler.postDelayed(this, 2000)
        }
    }

    fun handleForegroundAppChange(foregroundApp: String) {
        // Check NSFW Cooldown Lock first
        if (getCooldownRemainingMillis(this, foregroundApp) > 0L) {
            checkAndEnforceBlock(this, foregroundApp)
            return
        }

        val isBlocked = shouldBlock(foregroundApp)
        val isNewApp = foregroundApp != lastForegroundApp

        if (isNewApp) {
            Log.d(TAG, "New Foreground App: $foregroundApp, shouldBlock=$isBlocked")

            // Report all app activity for stats tracking
            onAppActivity?.invoke(foregroundApp)

            // Increment blocked attempts on first detection of a blocked app
            if (isBlocked) {
                blockedAttempts++
            }

            lastForegroundApp = foregroundApp
            // Stream detection event with blocking status
            onBlockedAttempt?.invoke(foregroundApp, blockedAttempts, isBlocked)
        }

        // ALWAYS block on every check — not just on app change.
        if (isBlocked) {
            Log.i(TAG, "!!! SHIELD ACTIVE: Blocking $foregroundApp !!!")
            sendUserHome()
            showBlockingOverlay(foregroundApp)
            if (!isNewApp) {
                onBlockedAttempt?.invoke(foregroundApp, blockedAttempts, true)
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now)
        var latestStatsApp: String? = null
        var latestStatsTime: Long = 0

        if (stats != null) {
            for (stat in stats) {
                if (stat.lastTimeUsed > latestStatsTime) {
                    latestStatsApp = stat.packageName
                    latestStatsTime = stat.lastTimeUsed
                }
            }
        }

        val usageEvents = usageStatsManager.queryEvents(now - 10000, now)
        val event = android.app.usage.UsageEvents.Event()
        var latestEventApp: String? = null
        var latestEventTime: Long = 0

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.timeStamp > latestEventTime) {
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestEventApp = event.packageName
                    latestEventTime = event.timeStamp
                }
            }
        }

        val resolvedApp = if (latestEventTime > latestStatsTime - 500) latestEventApp else latestStatsApp
        return resolvedApp ?: latestStatsApp ?: latestEventApp
    }

    private fun shouldBlock(pkg: String): Boolean {
        if (pkg == packageName) return false

        val launcher = launcherPackage ?: updateLauncherPackage().let { launcherPackage }
        if (pkg == launcher) return false

        if (pkg == "com.android.settings" || pkg == "com.android.systemui") return false
        if (pkg == "com.miui.securitycenter") return false

        val isBlockedList = blockedApps.any { it.equals(pkg, ignoreCase = true) }
        if (isBlockedList) {
            Log.d(TAG, "Shield check: $pkg IS BLOCKED (Matches in list of ${blockedApps.size} apps)")
        }
        return isBlockedList
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val newBlockedApps = intent.getStringArrayListExtra(EXTRA_BLOCKED_APPS) ?: emptyList()
                val newEndTimeMillis = intent.getLongExtra(EXTRA_END_TIME, 0)

                val isUpdate = isRunning && Math.abs(newEndTimeMillis - endTimeMillis) < 2000

                blockedApps = newBlockedApps
                endTimeMillis = newEndTimeMillis

                blockedAppsList = blockedApps
                sessionEndTimeMillis = endTimeMillis

                if (!isUpdate) {
                    lastForegroundApp = null
                    blockedAttempts = 0
                }

                if (endTimeMillis > System.currentTimeMillis()) {
                    startForegroundService()
                    if (!isUpdate) {
                        startMonitoring()
                        isRunning = true
                    }
                } else {
                    if (!isRunning) stopSelf()
                }
            }
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when focus mode is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Mode Active")
            .setContentText("Blocking ${blockedApps.size} apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    fun showBlockingOverlay(pkgName: String? = null) {
        val intent = Intent(this, BlockingOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(EXTRA_END_TIME, endTimeMillis)
            putExtra(EXTRA_PACKAGE_NAME, pkgName)
            putExtra(EXTRA_IS_NSFW_BLOCK, false)
        }
        startActivity(intent)
    }

    private fun sendUserHome() {
        sendUserHomeStatic(this)
    }
}

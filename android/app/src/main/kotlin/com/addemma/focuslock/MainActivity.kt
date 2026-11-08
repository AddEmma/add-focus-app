package com.addemma.focuslock

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Base64
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MainActivity: FlutterActivity() {
    private val APPS_CHANNEL = "com.addemma.focuslock/apps"
    private val BLOCKING_CHANNEL = "com.addemma.focuslock/blocking"
    private val EVENTS_CHANNEL = "com.addemma.focuslock/blocking_events"
    private val VPN_REQUEST_CODE = 202
    private val executor = Executors.newSingleThreadExecutor()

    private var eventSink: EventChannel.EventSink? = null
    private var pendingVpnResult: MethodChannel.Result? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize Content Shield config from SharedPreferences on startup
        FocusAccessibilityService.instance?.loadShieldConfigFromPrefs(this)

        // Apps channel - for getting installed apps
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, APPS_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "getInstalledApps") {
                executor.execute {
                    val apps = getInstalledApps()
                    runOnUiThread {
                        result.success(apps)
                    }
                }
            } else {
                result.notImplemented()
            }
        }

        // Blocking & Content Shield channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BLOCKING_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startBlocking" -> {
                    val blockedApps = call.argument<List<String>>("blockedApps") ?: emptyList()
                    val endTimeMillis = call.argument<Long>("endTimeMillis") ?: 0L
                    startBlocking(blockedApps, endTimeMillis)
                    result.success(true)
                }
                "stopBlocking" -> {
                    stopBlocking()
                    result.success(true)
                }
                "isBlockingActive" -> {
                    result.success(AppBlockerService.isRunning)
                }
                "getBlockingStatus" -> {
                    val status = mapOf(
                        "isActive" to AppBlockerService.isRunning,
                        "blockedApps" to AppBlockerService.blockedAppsList,
                        "blockedAttempts" to AppBlockerService.blockedAttempts,
                        "endTimeMillis" to AppBlockerService.sessionEndTimeMillis
                    )
                    result.success(status)
                }
                "configureContentShield" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    val searchUrlEnabled = call.argument<Boolean>("searchUrlFilterEnabled") ?: true
                    val visualAiEnabled = call.argument<Boolean>("visualAiScannerEnabled") ?: true
                    val blockSexy = call.argument<Boolean>("blockSexyContent") ?: true
                    val cooldownMins = call.argument<Int>("cooldownMinutes") ?: 10
                    val customKeywords = call.argument<List<String>>("customKeywords") ?: emptyList()

                    FocusAccessibilityService.contentShieldEnabled = enabled
                    FocusAccessibilityService.searchUrlFilterEnabled = searchUrlEnabled
                    FocusAccessibilityService.visualAiScannerEnabled = visualAiEnabled
                    FocusAccessibilityService.blockSexyContent = blockSexy
                    FocusAccessibilityService.cooldownMinutes = cooldownMins
                    NsfwContentFilter.updateCustomKeywords(customKeywords)

                    result.success(true)
                }
                "getContentShieldStatus" -> {
                    val now = System.currentTimeMillis()
                    val activeLocks = mutableListOf<Map<String, Any>>()
                    val iterator = AppBlockerService.nsfwCooldownLocks.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next().value
                        val remainingMs = entry.lockUntilMillis - now
                        if (remainingMs <= 0L) {
                            iterator.remove()
                        } else {
                            activeLocks.add(
                                mapOf(
                                    "packageName" to entry.packageName,
                                    "lockUntilMillis" to entry.lockUntilMillis,
                                    "remainingSeconds" to (remainingMs / 1000L).toInt(),
                                    "layer" to entry.layer,
                                    "category" to entry.category,
                                    "matchedTrigger" to entry.matchedTrigger,
                                    "details" to entry.details
                                )
                            )
                        }
                    }

                    val status = mapOf(
                        "enabled" to FocusAccessibilityService.contentShieldEnabled,
                        "searchUrlFilterEnabled" to FocusAccessibilityService.searchUrlFilterEnabled,
                        "visualAiScannerEnabled" to FocusAccessibilityService.visualAiScannerEnabled,
                        "blockSexyContent" to FocusAccessibilityService.blockSexyContent,
                        "cooldownMinutes" to FocusAccessibilityService.cooldownMinutes,
                        "vpnActive" to SafeSearchVpnService.isRunning,
                        "accessibilityActive" to hasAccessibilityPermission(),
                        "nsfwBlockedCount" to AppBlockerService.nsfwBlockedCount,
                        "activeLocks" to activeLocks,
                        "interceptHistory" to AppBlockerService.nsfwInterceptHistory.toList(),
                        "customKeywords" to NsfwContentFilter.customBlockedKeywords.toList()
                    )
                    result.success(status)
                }
                "startSafeSearchVpn" -> {
                    startSafeSearchVpn(result)
                }
                "stopSafeSearchVpn" -> {
                    stopSafeSearchVpn()
                    result.success(true)
                }
                "isSafeSearchVpnActive" -> {
                    result.success(SafeSearchVpnService.isRunning)
                }
                "testNsfwShield" -> {
                    val sampleDetection = NsfwDetectionResult(
                        isBlocked = true,
                        layer = "VISUAL_AI_SCANNER",
                        category = "SEXY_HALF_NAKED",
                        matchedTrigger = "Bikini / Cleavage Exposure",
                        confidence = 0.91f,
                        details = "Visual AI Scanner test: Detected revealing/half-naked content on screen."
                    )
                    AppBlockerService.showNsfwOverlayStatic(
                        context = this,
                        pkgName = "com.instagram.android",
                        lockUntilMillis = System.currentTimeMillis() + (FocusAccessibilityService.cooldownMinutes * 60_000L),
                        layer = sampleDetection.layer,
                        category = sampleDetection.category,
                        trigger = sampleDetection.matchedTrigger,
                        reason = sampleDetection.details
                    )
                    result.success(true)
                }
                "clearCooldownLocks" -> {
                    AppBlockerService.nsfwCooldownLocks.clear()
                    result.success(true)
                }
                "hasUsageStatsPermission" -> {
                    result.success(hasUsageStatsPermission())
                }
                "hasOverlayPermission" -> {
                    result.success(hasOverlayPermission())
                }
                "hasNotificationPermission" -> {
                    result.success(hasNotificationPermission())
                }
                "hasAccessibilityPermission" -> {
                    result.success(hasAccessibilityPermission())
                }
                "requestUsageStatsPermission" -> {
                    requestUsageStatsPermission()
                    result.success(true)
                }
                "requestOverlayPermission" -> {
                    requestOverlayPermission()
                    result.success(true)
                }
                "requestNotificationPermission" -> {
                    requestNotificationPermission()
                    result.success(true)
                }
                "requestAccessibilityPermission" -> {
                    requestAccessibilityPermission()
                    result.success(true)
                }
                "getRecentUsageEvents" -> {
                    val events = getRecentUsageEvents()
                    result.success(events)
                }
                "test_overlay" -> {
                    AppBlockerService.instance?.showBlockingOverlay("com.example.test")
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }

        // Event channel - for real-time blocking & NSFW shield events streamed to Flutter
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENTS_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    AppBlockerService.onBlockedAttempt = { packageName, totalAttempts, isBlocked ->
                        handler.post {
                            eventSink?.success(mapOf(
                                "event" to "blocked_attempt",
                                "packageName" to packageName,
                                "totalAttempts" to totalAttempts,
                                "isBlocked" to isBlocked,
                                "remainingMillis" to (AppBlockerService.sessionEndTimeMillis - System.currentTimeMillis())
                            ))
                        }
                    }

                    AppBlockerService.onAppActivity = { packageName ->
                        handler.post {
                            eventSink?.success(mapOf(
                                "event" to "app_activity",
                                "packageName" to packageName,
                                "timestamp" to System.currentTimeMillis()
                            ))
                        }
                    }

                    AppBlockerService.onNsfwBlocked = { nsfwEventMap ->
                        handler.post {
                            eventSink?.success(nsfwEventMap)
                        }
                    }
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    AppBlockerService.onBlockedAttempt = null
                    AppBlockerService.onAppActivity = null
                    AppBlockerService.onNsfwBlocked = null
                }
            }
        )
    }

    private fun startSafeSearchVpn(result: MethodChannel.Result) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingVpnResult = result
            @Suppress("DEPRECATION")
            startActivityForResult(prepareIntent, VPN_REQUEST_CODE)
        } else {
            launchSafeSearchVpnService()
            result.success(true)
        }
    }

    private fun launchSafeSearchVpnService() {
        val intent = Intent(this, SafeSearchVpnService::class.java).apply {
            action = SafeSearchVpnService.ACTION_START_VPN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSafeSearchVpn() {
        val intent = Intent(this, SafeSearchVpnService::class.java).apply {
            action = SafeSearchVpnService.ACTION_STOP_VPN
        }
        startService(intent)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            val callback = pendingVpnResult
            pendingVpnResult = null
            if (resultCode == Activity.RESULT_OK) {
                launchSafeSearchVpnService()
                callback?.success(true)
            } else {
                callback?.success(false)
            }
        }
    }

    private fun getRecentUsageEvents(): List<Map<String, Any>> {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 2 // Last 2 minutes

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        val eventList = mutableListOf<Map<String, Any>>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == 7 /* USER_INTERACTION */) {

                eventList.add(mapOf(
                    "packageName" to event.packageName,
                    "eventType" to event.eventType,
                    "timestamp" to event.timeStamp
                ))
            }
        }
        return eventList.reversed().take(50)
    }

    private fun startBlocking(blockedApps: List<String>, endTimeMillis: Long) {
        val intent = Intent(this, AppBlockerService::class.java).apply {
            action = AppBlockerService.ACTION_START
            putStringArrayListExtra(AppBlockerService.EXTRA_BLOCKED_APPS, ArrayList(blockedApps))
            putExtra(AppBlockerService.EXTRA_END_TIME, endTimeMillis)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBlocking() {
        val intent = Intent(this, AppBlockerService::class.java).apply {
            action = AppBlockerService.ACTION_STOP
        }
        startService(intent)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun hasAccessibilityPermission(): Boolean {
        var accessibilityEnabled = 0
        val service = "$packageName/${FocusAccessibilityService::class.java.canonicalName}"
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                applicationContext.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("MainActivity", "Error finding setting, default accessibility to not found: " + e.message)
        }
        val mStringColonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun getInstalledApps(): List<Map<String, Any>> {
        val appsList = mutableListOf<Map<String, Any>>()
        val packageManager = context.packageManager

        val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        Log.d("MainActivity", "Found ${packages.size} packages installed on device")

        for (packageInfo in packages) {
            val appInfo = packageInfo.applicationInfo ?: continue

            val launchIntent = packageManager.getLaunchIntentForPackage(packageInfo.packageName)
            if (launchIntent != null) {
                if (packageInfo.packageName == context.packageName) continue

                val appName = packageManager.getApplicationLabel(appInfo).toString()
                try {
                    val icon = getAppIconBase64(packageManager.getApplicationIcon(appInfo))
                    val appMap = mapOf(
                        "appName" to appName,
                        "packageName" to packageInfo.packageName,
                        "icon" to icon
                    )
                    appsList.add(appMap)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to get icon for ${packageInfo.packageName}", e)
                }
            }
        }
        return appsList
    }

    private fun getAppIconBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}

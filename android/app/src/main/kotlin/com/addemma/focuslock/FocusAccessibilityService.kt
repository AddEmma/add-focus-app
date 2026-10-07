package com.addemma.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    companion object {
        var instance: FocusAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("FocusAccessibility", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        Log.d("FocusAccessibility", "Window state changed: $packageName")
        
        // Notify the AppBlockerService about the foreground app change
        AppBlockerService.instance?.handleForegroundAppChange(packageName)
    }

    override fun onInterrupt() {
        Log.d("FocusAccessibility", "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}

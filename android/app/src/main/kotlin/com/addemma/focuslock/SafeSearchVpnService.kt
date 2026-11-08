package com.addemma.focuslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Layer 3: System-Wide SafeSearch & Adult Domain Blocker VPN
 * Enforces CleanBrowsing Adult Filter (185.228.168.10 / 185.228.169.11) and
 * Cloudflare Family (1.1.1.3 / 1.0.0.3) DNS across the device to block porn domains,
 * video CDNs, and force Strict SafeSearch on Google, Bing, and YouTube without
 * slowing down regular network traffic.
 */
class SafeSearchVpnService : VpnService() {

    companion object {
        const val TAG = "SafeSearchVpnService"
        const val ACTION_START_VPN = "com.addemma.focuslock.START_VPN"
        const val ACTION_STOP_VPN = "com.addemma.focuslock.STOP_VPN"
        const val VPN_CHANNEL_ID = "safesearch_vpn_channel"
        const val VPN_NOTIFICATION_ID = 1002

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_VPN -> {
                stopVpnTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startVpnTunnel()
                return START_STICKY
            }
        }
    }

    private fun startVpnTunnel() {
        if (vpnInterface != null && isRunning) {
            Log.d(TAG, "SafeSearch DNS VPN already active")
            return
        }

        try {
            startForeground(VPN_NOTIFICATION_ID, buildNotification())

            val builder = Builder()
                .setSession("FocusLock SafeSearch & Adult Shield")
                .setMtu(1500)
                // Virtual tunnel address
                .addAddress("10.111.222.1", 30)
                // Route only the virtual endpoint so all normal traffic uses native Wi-Fi/Cellular speed
                // while Android routes system DNS resolution through the family-safe DNS servers below
                .addRoute("10.111.222.2", 32)
                // CleanBrowsing Adult Filter (Blocks porn domains + enforces Google/Bing/YouTube SafeSearch)
                .addDnsServer("185.228.168.10")
                .addDnsServer("185.228.169.11")
                // Cloudflare for Families (Blocks adult content & malware)
                .addDnsServer("1.1.1.3")
                .addDnsServer("1.0.0.3")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            isRunning = vpnInterface != null
            Log.i(TAG, "SafeSearch & Adult Content DNS VPN established: isRunning=$isRunning")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish SafeSearch DNS VPN", e)
            isRunning = false
            stopSelf()
        }
    }

    private fun stopVpnTunnel() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        } finally {
            vpnInterface = null
            isRunning = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            Log.i(TAG, "SafeSearch DNS VPN stopped")
        }
    }

    override fun onRevoke() {
        Log.w(TAG, "SafeSearch DNS VPN revoked by system/user")
        stopVpnTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VPN_CHANNEL_ID,
                "SafeSearch & Adult DNS Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active when SafeSearch & Porn Domain DNS blocking is enabled"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VPN_CHANNEL_ID)
            .setContentTitle("SafeSearch & Adult Shield Active")
            .setContentText("Blocking adult domains & enforcing strict SafeSearch")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

package com.orbit.browser.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.net.VpnService
import androidx.core.app.NotificationCompat

/**
 * NEXUS VPN lifecycle scaffold.
 * This service deliberately does not establish a TUN or claim a connection until
 * the OpenVPN engine bridge is integrated.
 */
class NexusVpnService : VpnService() {
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                // Engine integration is pending. Do not call Builder.establish()
                // with placeholder addresses or present this as a VPN connection.
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "NEXUS VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, NexusVpnService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NEXUS VPN setup")
            .setContentText("OpenVPN engine integration is in progress")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.orbit.browser.NEXUS_VPN_START"
        const val ACTION_STOP = "com.orbit.browser.NEXUS_VPN_STOP"
        private const val CHANNEL_ID = "nexus_vpn_engine"
        private const val NOTIFICATION_ID = 4107
    }
}

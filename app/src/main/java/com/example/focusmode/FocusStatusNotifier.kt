package com.example.focusmode

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// Posts/cancels the ongoing low-priority notification that's the only way to put a persistent
// Focus Mode icon in the status bar and notification shade. The channel bypasses DND so the icon
// stays visible even though Focus Mode itself sets the system filter to PRIORITY.
object FocusStatusNotifier {
    private const val CHANNEL_ID = "focus_mode_status"
    private const val NOTIFICATION_ID = 1

    fun update(context: Context, enabled: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!enabled) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(nm)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_status)
            .setContentTitle("Masjid Call Block is on")
            .setContentText("Blocking notifications & calls from anyone not on your allowed list")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Masjid Call Block status", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows an icon while Masjid Call Block is blocking notifications and calls"
            setBypassDnd(true)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }
}

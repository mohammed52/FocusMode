package com.example.focusmode

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Date

// Posts one notification per contact that's repeatedly blocked, rather than one per call: each
// new block updates the existing notification for that contact with the latest timestamp and a
// running count. Unlike FocusStatusNotifier's channel, this one does not bypass DND and isn't in the
// calls/alarms categories Focus Mode's policy exempts, so while Focus Mode is on these stay
// silent in the shade rather than alerting.
object BlockedCallNotifier {
    // _v3: channel settings (like showBadge) can't be changed on an already-created channel, so
    // bumping the id is what actually re-enables the badge for already-installed users (v2 had
    // showBadge disabled).
    private const val CHANNEL_ID = "focus_mode_blocked_calls_v3"
    private val LEGACY_CHANNEL_IDS = listOf("focus_mode_blocked_calls", "focus_mode_blocked_calls_v2")

    // Identifies a notification "group" of one contact across channels (WhatsApp vs Phone calls
    // from the same person are tracked separately, since they're blocked by different services).
    fun contactKey(event: BlockedEvent): String = "${event.appName}|${event.from}"

    fun notify(context: Context, event: BlockedEvent, count: Int) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(nm)

        val key = contactKey(event)
        val notifId = key.hashCode()

        val openLogIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_LOG_TAB, true)
            putExtra(MainActivity.EXTRA_RESET_CONTACT_KEY, key)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            openLogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            Intent(context, BlockedCallDismissReceiver::class.java).apply {
                putExtra(BlockedCallDismissReceiver.EXTRA_CONTACT_KEY, key)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = DateFormat.getTimeFormat(context).format(Date(event.timestamp))
        val timesWord = if (count == 1) "time" else "times"
        val text = "${event.from} • blocked $count $timesWord • last at $timeText"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_status)
            .setContentTitle("Blocked call")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .build()
        nm.notify(notifId, notification)
    }

    // Clears the app-icon badge by cancelling the actual notifications driving it — called when
    // the app is opened, since these notifications have no other "seen it" signal otherwise.
    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID }
            .forEach { nm.cancel(it.id) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        LEGACY_CHANNEL_IDS.forEach { nm.deleteNotificationChannel(it) }
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Blocked calls", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "One updating notification per contact Focus Mode repeatedly blocks"
        }
        nm.createNotificationChannel(channel)
    }
}

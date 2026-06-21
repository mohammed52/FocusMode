package com.example.focusmode

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NotificationBlockerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsManager by lazy { PreferencesManager(applicationContext) }

    // In-memory de-dup so a notification that reposts/updates repeatedly (download progress,
    // media playback, etc.) doesn't flood the block log with one entry per repost.
    private val recentlyLogged = mutableMapOf<String, Long>()

    // Key of the allowed WhatsApp call notification currently keeping DND lifted, so we know
    // when it disappears (call ended/declined/missed) and can restore DND right away.
    @Volatile private var activeWhatsAppCallKey: String? = null

    // In-memory mirrors of the two DataStore flows onNotificationPosted needs on every event, kept
    // current by a long-lived collector started in onCreate(). dumpsys audio showed a blocked
    // WhatsApp call's system-played ring running for ~330ms before our code even finished its first
    // prefsManager.isEnabled.first() suspend read — reading these as plain fields instead removes
    // that read (and the later allowedContacts one) from the latency-sensitive mute path entirely.
    @Volatile private var cachedIsEnabled = false
    @Volatile private var cachedAllowedContacts: List<Contact> = emptyList()

    companion object {
        const val TAG = "FocusMode"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val LOG_DEDUP_WINDOW_MS = 60_000L
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "NotificationBlockerService started")
        scope.launch { prefsManager.isEnabled.collect { cachedIsEnabled = it } }
        scope.launch { prefsManager.allowedContacts.collect { cachedAllowedContacts = it } }
        scope.launch {
            if (!hasActiveWhatsAppCallNotification()) {
                DndController.resyncIfSafe(applicationContext, prefsManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == activeWhatsAppCallKey) {
            activeWhatsAppCallKey = null
            scope.launch { DndController.restore(applicationContext, prefsManager) }
        }
    }

    // Defensive check for resync(): a WhatsApp call notification still showing after a process
    // restart means a call may still be in progress, even though activeWhatsAppCallKey was lost.
    private fun hasActiveWhatsAppCallNotification(): Boolean = try {
        activeNotifications.orEmpty().any { sbn ->
            (sbn.packageName == WHATSAPP_PACKAGE || sbn.packageName == WHATSAPP_BUSINESS_PACKAGE) &&
                    isWhatsAppCall(sbn.notification)
        }
    } catch (e: Exception) {
        false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!cachedIsEnabled) return

        scope.launch {
            val pkg = sbn.packageName
            if (pkg == WHATSAPP_PACKAGE || pkg == WHATSAPP_BUSINESS_PACKAGE) {
                handleWhatsAppNotification(sbn)
            } else if (pkg == packageName) {
                // Never block our own notifications (status icon, blocked-call alerts) —
                // otherwise this generic "block everything but WhatsApp" branch cancels them
                // within milliseconds of being posted.
            } else if (sbn.notification.category == Notification.CATEGORY_ALARM) {
                // Leave alarms alone — Focus Mode's DND policy specifically exempts these so they
                // still fire, so don't second-guess that by cancelling the notification ourselves.
            } else {
                val title = sbn.notification.extras
                    ?.getString(Notification.EXTRA_TITLE) ?: "Unknown"
                cancelNotification(sbn.key)
                logBlockedEvent(
                    dedupKey(sbn),
                    BlockedEvent(
                        timestamp = System.currentTimeMillis(),
                        type = "notification",
                        from = title,
                        appName = getAppName(pkg)
                    )
                )
            }
        }
    }

    private suspend fun handleWhatsAppNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Unknown"

        val isCall = isWhatsAppCall(notification)
        Log.d(TAG, "WhatsApp: title=$title, isCall=$isCall")

        if (!isCall) {
            cancelNotification(sbn.key)
            logBlockedEvent(
                dedupKey(sbn),
                BlockedEvent(System.currentTimeMillis(), "notification", title, "WhatsApp")
            )
            return
        }

        val isAllowed = cachedAllowedContacts.any { contact ->
            contact.name.equals(title, ignoreCase = true) ||
                    contact.phoneNumbers.any { phoneNumbersMatch(it, title) }
        }

        if (!isAllowed) {
            Log.d(TAG, "Blocking WhatsApp call from: $title")
            // No reactive mute here: STREAM_RING/STREAM_NOTIFICATION are already muted as Focus
            // Mode's permanent baseline (see DndController), so this call is silent already.
            // Reactively muting again here would risk cutting off a *different*, currently-allowed
            // call that's mid-unmute-window if one happens to overlap with this blocked one.
            // Deliberately not pressing the decline action: that sends WhatsApp's own reject
            // signal to the caller (same "feels rejected" problem fixed in
            // FocusCallScreeningService). Leaving it alone lets WhatsApp's own no-answer timeout
            // end the call instead, so the caller sees a missed call, not a decline.
            cancelNotification(sbn.key)
            val blockedEvent = BlockedEvent(System.currentTimeMillis(), "call", title, "WhatsApp")
            if (logBlockedEvent(dedupKey(sbn), blockedEvent)) {
                val count = prefsManager.incrementBlockCount(BlockedCallNotifier.contactKey(blockedEvent))
                BlockedCallNotifier.notify(applicationContext, blockedEvent, count)
            }
        } else {
            Log.d(TAG, "Allowing WhatsApp call from: $title — lifting DND")
            activeWhatsAppCallKey = sbn.key
            DndController.unmuteRingerStreams(applicationContext)
            DndController.liftDnd(applicationContext, prefsManager)
            scope.launch { DndController.reassertLiftBriefly(applicationContext) }
        }
    }

    // Notification id stays fixed across distinct WhatsApp calls (e.g. always "23"), but
    // notification.`when` is the call's start time, which differs between calls and stays
    // constant across reposts of the same ringing call — combining both lets the dedup window
    // collapse reposts of one call without also collapsing a genuinely new call that reuses the
    // same notification id.
    private fun dedupKey(sbn: StatusBarNotification): String = "${sbn.key}:${sbn.notification.`when`}"

    // Returns true if the event was actually logged, false if it was suppressed as a duplicate
    // repost within LOG_DEDUP_WINDOW_MS — callers use this to avoid notifying on reposts too.
    private suspend fun logBlockedEvent(key: String, event: BlockedEvent): Boolean {
        val now = System.currentTimeMillis()
        val last = recentlyLogged[key]
        if (last != null && now - last < LOG_DEDUP_WINDOW_MS) return false
        recentlyLogged[key] = now
        if (recentlyLogged.size > 200) {
            val cutoff = now - LOG_DEDUP_WINDOW_MS
            recentlyLogged.entries.removeAll { it.value < cutoff }
        }
        prefsManager.addBlockedEvent(event)
        return true
    }

    private fun isWhatsAppCall(notification: Notification): Boolean {
        if (notification.category == Notification.CATEGORY_CALL) return true
        val actions = notification.actions
        if (!actions.isNullOrEmpty()) {
            val labels = actions.mapNotNull { it.title?.toString()?.lowercase() }
            val keywords = listOf("answer", "accept", "decline", "reject", "ignore")
            if (labels.any { l -> keywords.any { l.contains(it) } }) return true
        }
        val channelId = notification.channelId?.lowercase() ?: ""
        if (channelId.contains("call") || channelId.contains("voip") ||
            channelId.contains("incoming")) return true
        val extras = notification.extras ?: return false
        val text = extras.getString(Notification.EXTRA_TEXT)?.lowercase() ?: ""
        val sub = extras.getString(Notification.EXTRA_SUB_TEXT)?.lowercase() ?: ""
        val combined = "$text $sub"
        return combined.contains("incoming") || combined.contains("calling") ||
                combined.contains("voice call") || combined.contains("video call")
    }

    private fun getAppName(packageName: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    } catch (e: Exception) { packageName }
}

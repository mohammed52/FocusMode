package com.example.focusmode

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// Cellular calls' DND handling no longer goes through liftDnd()/reassertLiftBriefly(): DND
// permanently exempts calls via NotificationManager.Policy (see FocusModeController.setEnabled),
// so there's nothing to reactively lift/restore there. liftDnd()/reassertLiftBriefly() are now
// WhatsApp-only, since WhatsApp calls arrive as plain notifications/a self-managed Telecom
// connection, not a screened Telecom call, so they aren't covered by the Policy calls-exemption.
//
// Both WhatsApp's call sound AND the real phone ringtone play on STREAM_RING (and WhatsApp's own
// sustained ring loop also uses STREAM_NOTIFICATION) — there's no per-app volume control, only
// per-stream. dumpsys audio showed a blocked WhatsApp call's notification arriving ~300ms+ *after*
// the system had already started playing its ring, so reactively muting on "this call is blocked"
// can never fully win that race. Instead, both streams are kept muted as Focus Mode's permanent
// baseline (set once in FocusModeController.setEnabled, like the DND policy), and unmuted only for
// the duration of a specific allowed call (WhatsApp via the notification-removed event below, real
// calls via CallStateWatcher.watchForCallEnd in FocusCallScreeningService) — so a blocked call's
// ring/notification sound is silent from the very first millisecond instead of racing to catch up.
//
// liftDnd()/unmuteRingerStreams() schedule an AlarmManager backstop that survives process death
// and guarantees both DND and the ringer-stream mute are eventually restored even if the
// event-driven restore path never fires (e.g. the host process was killed mid-call).
object DndController {
    private const val BACKSTOP_DELAY_MS = 10 * 60 * 1000L // 10 minutes
    private const val BACKSTOP_REQUEST_CODE = 1001

    fun liftDnd(context: Context, prefsManager: PreferencesManager) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        // On some OEM skins (e.g. Samsung's SamsungRingerModeDelegate) the audible ringer mode
        // is tied to zen state independently of the interruption filter, and lifting the filter
        // alone doesn't reliably un-silence the ring stream — force it back to normal explicitly.
        val audioManager = context.getSystemService(AudioManager::class.java)
        Log.d("FocusMode", "liftDnd: ringerMode before=${audioManager?.ringerMode}, filter now=${nm.currentInterruptionFilter}")
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            try {
                audioManager?.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Log.d("FocusMode", "liftDnd: ringerMode after set=${audioManager?.ringerMode}")
            } catch (e: SecurityException) {
                Log.e("FocusMode", "liftDnd: setRingerMode threw", e)
            }
        }
        scheduleBackstop(context)
    }

    // dumpsys audio shows the WhatsApp ringtone player starting fine right after liftDnd() but
    // then getting muted ~100-300ms later by an AppOps restriction tied to zen state (event:muted
    // updated source:appOps) — a stale recalculation that lands asynchronously after our
    // setInterruptionFilter() call, despite that call itself reporting success immediately.
    // Re-asserting ALL a few more times over the next second or so gives a fresh recalculation a
    // chance to land after the stale one and re-clear the mute, instead of just losing the race once.
    suspend fun reassertLiftBriefly(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        repeat(8) {
            delay(150)
            if (!nm.isNotificationPolicyAccessGranted) return
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    // Focus Mode's permanent baseline while it's on: both streams stay muted except for the
    // duration of a specific allowed call. Mute/unmute is a flag layered on top of whatever volume
    // index the user already had — it doesn't need to save/restore an exact volume level.
    fun muteRingerStreams(context: Context) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
    }

    fun unmuteRingerStreams(context: Context) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
    }

    suspend fun restore(context: Context, prefsManager: PreferencesManager) {
        cancelBackstop(context)
        val stillEnabled = prefsManager.isEnabled.first()
        if (stillEnabled) {
            muteRingerStreams(context)
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (stillEnabled) {
            // PRIORITY is Focus Mode's steady-state filter (set once in FocusModeController,
            // with a policy that permanently exempts calls and alarms) — restoring to it after a
            // call just re-applies that already-set policy, no per-call DND change needed.
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
    }

    // Self-heal check run from each service's onCreate: if Focus Mode is enabled, force the ringer
    // streams and DND back to Focus Mode's steady state in case either was lost (process restart,
    // reboot) while no call was in progress to explain the gap. Callers with their own notion of
    // "call in progress" (e.g. an active WhatsApp call notification) should check that first and
    // skip calling this while one is active.
    suspend fun resyncIfSafe(context: Context, prefsManager: PreferencesManager) {
        if (!prefsManager.isEnabled.first()) return
        if (CallStateWatcher.hasActiveCellularCall(context)) return
        muteRingerStreams(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) return
        restore(context, prefsManager)
    }

    // Exposed so FocusCallScreeningService can guarantee a real call's ringer-stream unmute is
    // eventually undone even if CallStateWatcher.watchForCallEnd never fires for it.
    fun scheduleBackstop(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = backstopPendingIntent(context)
        am.cancel(pi)
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + BACKSTOP_DELAY_MS,
            pi
        )
    }

    private fun cancelBackstop(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(backstopPendingIntent(context))
    }

    private fun backstopPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DndRestoreReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, BACKSTOP_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

package com.example.focusmode

import android.app.NotificationManager
import android.content.Context
import com.example.focusmode.widget.FocusGlanceWidget
import androidx.glance.appwidget.updateAll

// Single place that applies a Focus Mode on/off change to persisted state, the system DND
// filter, the status-bar indicator notification, and the home-screen widget — so MainViewModel
// and the Glance widget's toggle action can't drift out of sync with each other.
object FocusModeController {
    suspend fun setEnabled(context: Context, enabled: Boolean) {
        val prefsManager = PreferencesManager(context)
        prefsManager.setEnabled(enabled)

        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.isNotificationPolicyAccessGranted) {
            if (enabled) {
                // PRIORITY + a policy that exempts calls (from anyone) and alarms, set once and
                // left in place for the whole time Focus Mode is on — NOT toggled per call. dumpsys
                // telecom showed that reactively lifting DND when a call arrives (the old approach,
                // via ALARMS_ONLY + a per-call flip to ALL) is too late: telecom's own ring-eligibility
                // check (FILTERING_COMPLETED) already records "DND suppressed" and RINGER_INFO shows
                // isRingerAudible=0 before our reactive flip can land, no matter how fast or how many
                // times we re-assert it. Exempting calls from DND permanently means telecom never has
                // a reason to suppress the ring in the first place — the actual per-contact allow/block
                // decision still happens entirely in FocusCallScreeningService's respondToCall().
                nm.notificationPolicy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                        NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    0
                )
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }

        // STREAM_RING/STREAM_NOTIFICATION carry both WhatsApp's call sound and the real phone
        // ringtone, with no per-app volume control — muting them as a permanent baseline (instead
        // of reactively muting once a blocked call is detected) is what makes a blocked call
        // silent from the first millisecond instead of racing notification-delivery latency. They're
        // unmuted only for the duration of a specific allowed call (see DndController).
        if (enabled) {
            DndController.muteRingerStreams(context)
        } else {
            DndController.unmuteRingerStreams(context)
        }

        FocusStatusNotifier.update(context, enabled)
        FocusGlanceWidget().updateAll(context)
        Analytics.logFocusModeToggled(enabled)
    }
}

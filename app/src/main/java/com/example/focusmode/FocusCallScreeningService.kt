package com.example.focusmode

import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FocusCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsManager by lazy { PreferencesManager(applicationContext) }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val callStart = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - callStart

        scope.launch {
            val isEnabled = prefsManager.isEnabled.first()
            Log.d("FocusMode", "onScreenCall: isEnabled=$isEnabled at +${elapsed()}ms")

            if (!isEnabled) {
                respondToCall(callDetails, CallResponse.Builder().build())
                return@launch
            }

            // Calls are exempted from DND permanently via NotificationManager.Policy
            // (FocusModeController.setEnabled), so there's no DND state to lift here — see
            // DndController's header comment for why the old per-call lift/restore approach was
            // replaced. STREAM_RING/STREAM_NOTIFICATION are a separate mechanism (see below): they
            // stay muted as Focus Mode's baseline and only get unmuted for this specific call.
            val incomingNumber = callDetails.handle?.schemeSpecificPart ?: ""
            val allowed = prefsManager.allowedContacts.first()
            Log.d("FocusMode", "onScreenCall: allowedContacts read at +${elapsed()}ms")

            val isAllowed = allowed.any { contact ->
                contact.phoneNumbers.any { phone ->
                    phoneNumbersMatch(phone, incomingNumber)
                }
            }

            if (isAllowed) {
                Log.d("FocusMode", "Allowing call from: $incomingNumber — responding at +${elapsed()}ms")
                // Unmute first so the ringtone is audible from the moment Telecom starts playing
                // it, not from whenever this coroutine happens to reach respondToCall(). Re-mute
                // once this call ends (event-driven via CallStateWatcher, with an AlarmManager
                // backstop in case the watcher never fires — e.g. no READ_PHONE_STATE, or this
                // process dies mid-call).
                DndController.unmuteRingerStreams(applicationContext)
                DndController.scheduleBackstop(applicationContext)
                CallStateWatcher.watchForCallEnd(applicationContext) {
                    scope.launch { DndController.restore(applicationContext, prefsManager) }
                }
                respondToCall(callDetails, CallResponse.Builder().build())
            } else {
                Log.d("FocusMode", "Blocking call from: $incomingNumber")
                val blockedEvent = BlockedEvent(
                    timestamp = System.currentTimeMillis(),
                    type = "call",
                    from = incomingNumber.ifEmpty { "Unknown number" },
                    appName = "Phone"
                )
                prefsManager.addBlockedEvent(blockedEvent)
                val count = prefsManager.incrementBlockCount(BlockedCallNotifier.contactKey(blockedEvent))
                BlockedCallNotifier.notify(applicationContext, blockedEvent, count)
                // Silence only — no setDisallowCall/setRejectCall. Those send an explicit
                // network-level reject, which is what makes the caller's phone show "call
                // declined"/hang up fast. Silencing alone lets Telecom keep ringing normally on
                // the caller's end (full incoming-call UI on our side too, just inaudible) until
                // it naturally times out to voicemail/missed call — indistinguishable from us
                // just not answering.
                respondToCall(
                    callDetails,
                    CallResponse.Builder()
                        .setSilenceCall(true)
                        .build()
                )
            }
        }
    }
}

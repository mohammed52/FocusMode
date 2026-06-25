package com.example.focusmode

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

// Every event logged here is a bare, anonymous behavioral signal — a count or an on/off state,
// never a contact name, a phone number, or anything from BlockedEvent.from. That boundary is not
// negotiable: this app's whole premise is that your family's contact/call data never leaves the
// device, and these events are the only thing that now does.
//
// Firebase isn't wired up yet — there's no app/google-services.json until the Firebase Console
// app registration step is done (see CLAUDE.md). Firebase.analytics throws if FirebaseApp never
// initialized, so every call here is defensive until then; once google-services.json exists and
// the plugin is applied in app/build.gradle.kts, these start working with no further changes.
object Analytics {
    private fun safeLog(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // No Firebase config yet, or analytics otherwise unavailable — never let a metrics
            // call crash the app that's supposed to be silencing distractions, not causing one.
        }
    }

    fun logOnboardingComplete() = safeLog {
        Firebase.analytics.logEvent("onboarding_complete", null)
    }

    fun logFocusModeToggled(enabled: Boolean) = safeLog {
        Firebase.analytics.logEvent("focus_mode_toggled") {
            param("enabled", if (enabled) 1L else 0L)
        }
    }

    fun logBlocked(type: String) = safeLog {
        Firebase.analytics.logEvent(if (type == "call") "call_blocked" else "notification_blocked", null)
    }
}

package com.example.focusmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fired when the user swipes away a blocked-call notification (tap-to-open is handled directly
// in MainActivity instead, since that case already launches an Activity). Both paths reset the
// per-contact count so the next block starts fresh at 1.
class BlockedCallDismissReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_CONTACT_KEY = "contact_key"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_CONTACT_KEY) ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PreferencesManager(appContext).resetBlockCount(key)
            } finally {
                pending.finish()
            }
        }
    }
}

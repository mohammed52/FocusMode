package com.example.focusmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Fires when DndController's backstop alarm reaches its deadline without the event-driven
// call-end watcher having already restored DND (e.g. the host process was killed mid-call).
class DndRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val prefsManager = PreferencesManager(appContext)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DndController.restore(appContext, prefsManager)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

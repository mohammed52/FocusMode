package com.example.focusmode

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

// Watches the cellular call state so DndController can restore DND the moment a screened call
// actually ends, instead of after a fixed timeout. Requires READ_PHONE_STATE — it's an optional
// permission (see MainActivity's "Call Sync" row), so callers must tolerate this being a no-op.
object CallStateWatcher {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasActiveCellularCall(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
        return tm.callState != TelephonyManager.CALL_STATE_IDLE
    }

    // Invokes [onIdle] the next time the call state transitions to IDLE, then stops watching.
    // No-op if READ_PHONE_STATE isn't granted — caller's AlarmManager backstop is the fallback.
    fun watchForCallEnd(context: Context, onIdle: () -> Unit) {
        if (!hasPermission(context)) return
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return

        val immediateExecutor = Executor { command -> command.run() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lateinit var callback: TelephonyCallback
            callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        tm.unregisterTelephonyCallback(callback)
                        onIdle()
                    }
                }
            }
            tm.registerTelephonyCallback(immediateExecutor, callback)
        } else {
            lateinit var listener: PhoneStateListener
            @Suppress("DEPRECATION")
            listener = object : PhoneStateListener(immediateExecutor) {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        tm.listen(listener, LISTEN_NONE)
                        onIdle()
                    }
                }
            }
            @Suppress("DEPRECATION")
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }
}

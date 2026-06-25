package com.example.focusmode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

// Lets the support WhatsApp number be changed from the Firebase Console (Remote Config) without
// shipping a new app version. DEFAULT_NUMBER is the fallback used before the first successful
// fetch, and permanently if the "support_whatsapp_number" parameter is never set server-side or
// Remote Config is otherwise unreachable — the app always has a working number either way.
private const val WHATSAPP_NUMBER_KEY = "support_whatsapp_number"
private const val DEFAULT_NUMBER = "923137590210"

object SupportContact {
    fun init() {
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        )
        remoteConfig.setDefaultsAsync(mapOf(WHATSAPP_NUMBER_KEY to DEFAULT_NUMBER))
        remoteConfig.fetchAndActivate()
    }

    // wa.me opens the chat screen for that number directly inside WhatsApp, pre-filled with a
    // starter message; if WhatsApp isn't installed it falls back to a browser instead of failing.
    fun openWhatsAppChat(context: Context) {
        val number = Firebase.remoteConfig.getString(WHATSAPP_NUMBER_KEY)
            .ifBlank { DEFAULT_NUMBER }
            .filter { it.isDigit() }
        val message = Uri.encode("Hi! I have a suggestion for Masjid Call Block: ")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$message"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Nothing on the device can handle it — nothing sensible to fall back to.
        }
    }
}

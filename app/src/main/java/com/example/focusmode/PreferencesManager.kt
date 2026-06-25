package com.example.focusmode

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_prefs")

class PreferencesManager(private val context: Context) {

    private val gson = Gson()

    companion object {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val ALLOWED_CONTACTS = stringPreferencesKey("allowed_contacts")
        val BLOCK_LOG = stringPreferencesKey("block_log")
        val BLOCK_COUNTS = stringPreferencesKey("block_counts")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val isEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_ENABLED] ?: false
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETE] = completed }
    }

    val allowedContacts: Flow<List<Contact>> = context.dataStore.data.map { prefs ->
        parseJsonList(prefs[ALLOWED_CONTACTS])
    }

    val blockLog: Flow<List<BlockedEvent>> = context.dataStore.data.map { prefs ->
        parseJsonList(prefs[BLOCK_LOG])
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_ENABLED] = enabled }
    }

    suspend fun saveAllowedContacts(contacts: List<Contact>) {
        context.dataStore.edit { prefs ->
            prefs[ALLOWED_CONTACTS] = gson.toJson(contacts)
        }
    }

    suspend fun addBlockedEvent(event: BlockedEvent) {
        context.dataStore.edit { prefs ->
            val list = parseJsonList<BlockedEvent>(prefs[BLOCK_LOG]).toMutableList()
            list.add(0, event)
            if (list.size > 100) list.removeAt(list.lastIndex)
            prefs[BLOCK_LOG] = gson.toJson(list)
        }
        // Single chokepoint for all 3 call sites (real calls, WhatsApp calls, other notifications)
        // — logs only event.type (a count), never event.from.
        Analytics.logBlocked(event.type)
    }

    // Defensive against corrupted or schema-incompatible JSON (e.g. left over from a future
    // data-model change) — falls back to an empty list instead of crashing every flow collector.
    private inline fun <reified T> parseJsonList(json: String?): List<T> {
        if (json == null) return emptyList()
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Clearing the log also wipes the per-contact counters below — otherwise a contact's count
    // would silently carry over a clear and resurface inflated (e.g. "blocked 4 times") the next
    // time they're blocked, even though the visible log shows nothing before that point.
    suspend fun clearLog() {
        context.dataStore.edit { prefs ->
            prefs[BLOCK_LOG] = "[]"
            prefs[BLOCK_COUNTS] = "{}"
        }
    }

    // Per-contact "blocked since you last saw it" counter, keyed by "appName|from". Reset to
    // zero whenever the user taps or dismisses the blocked-call notification for that contact,
    // so the next block starts the count fresh at 1 rather than accumulating forever.
    private fun parseCounts(json: String?): MutableMap<String, Int> {
        if (json == null) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            gson.fromJson<MutableMap<String, Int>>(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    suspend fun incrementBlockCount(key: String): Int {
        var newCount = 1
        context.dataStore.edit { prefs ->
            val counts = parseCounts(prefs[BLOCK_COUNTS])
            newCount = (counts[key] ?: 0) + 1
            counts[key] = newCount
            prefs[BLOCK_COUNTS] = gson.toJson(counts)
        }
        return newCount
    }

    suspend fun resetBlockCount(key: String) {
        context.dataStore.edit { prefs ->
            val counts = parseCounts(prefs[BLOCK_COUNTS])
            if (counts.remove(key) != null) {
                prefs[BLOCK_COUNTS] = gson.toJson(counts)
            }
        }
    }
}

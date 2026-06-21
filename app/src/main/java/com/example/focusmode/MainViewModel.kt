package com.example.focusmode

import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val isEnabled: StateFlow<Boolean> = prefs.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allowedContacts: StateFlow<List<Contact>> = prefs.allowedContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Only surface call blocks — they're tied to a contact's phone number. Blocked
    // notifications (chat messages, other apps) aren't about a specific caller's number,
    // so they're excluded from the log shown to the user.
    val blockLog: StateFlow<List<BlockedEvent>> = prefs.blockLog
        .map { events -> events.filter { it.type == "call" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    val deviceContacts: StateFlow<List<Contact>> = _deviceContacts.asStateFlow()

    fun toggleEnabled() {
        viewModelScope.launch {
            // Read the persisted value directly rather than the cached StateFlow's `.value`,
            // which can still be the default until the first DataStore emission lands.
            val newEnabled = !prefs.isEnabled.first()
            FocusModeController.setEnabled(getApplication(), newEnabled)
        }
    }

    fun addContact(contact: Contact) {
        viewModelScope.launch {
            val updated = allowedContacts.value.toMutableList()
            if (updated.none { it.id == contact.id }) {
                updated.add(contact)
                prefs.saveAllowedContacts(updated)
            }
        }
    }

    fun removeContact(contact: Contact) {
        viewModelScope.launch {
            val updated = allowedContacts.value.filter { it.id != contact.id }
            prefs.saveAllowedContacts(updated)
        }
    }

    fun clearLog() {
        viewModelScope.launch { prefs.clearLog() }
    }

    fun loadDeviceContacts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, Contact>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                while (it.moveToNext()) {
                    val id = it.getString(idIdx) ?: continue
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numIdx) ?: continue
                    val photo = if (photoIdx >= 0) it.getString(photoIdx) else null
                    val existing = map[id]
                    map[id] = if (existing != null)
                        existing.copy(phoneNumbers = existing.phoneNumbers + number)
                    else
                        Contact(id, name, listOf(number), photo)
                }
            }
            _deviceContacts.value = map.values.toList()
        }
    }
}

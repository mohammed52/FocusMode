package com.example.focusmode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed class CallBackAction {
    data class Dial(val number: String) : CallBackAction()
    data class OpenWhatsApp(val number: String) : CallBackAction()
    data object None : CallBackAction()
}

// WhatsApp call entries identify the caller by display name (WhatsApp's notification title, not
// a number); real phone-call entries identify them by the actual dialed number instead
// (BlockedEvent.from holds whichever one it is). Both directions resolve through the same device
// contacts list already loaded for the contact picker.
private fun findMatchingDeviceContact(event: BlockedEvent, deviceContacts: List<Contact>): Contact? =
    if (event.appName == "WhatsApp") {
        deviceContacts.firstOrNull { it.name.equals(event.from, ignoreCase = true) }
    } else {
        deviceContacts.firstOrNull { contact -> contact.phoneNumbers.any { phoneNumbersMatch(it, event.from) } }
    }

// WhatsApp call entries only ever carry the caller's display name in BlockedEvent.from — the
// notification listener has no access to WhatsApp's own contact data, just whatever title it
// put in the notification. Best-effort fix: match that name against the phone's own contacts and
// use its first number. Real phone-call entries already carry the actual dialed number in
// `from`, so they need no resolution at all — the only thing that can't be dialed there is the
// literal "Unknown number" placeholder, which has no digits in it at all.
fun resolveCallBackAction(event: BlockedEvent, deviceContacts: List<Contact>): CallBackAction {
    if (event.appName == "WhatsApp") {
        val number = findMatchingDeviceContact(event, deviceContacts)?.phoneNumbers?.firstOrNull()
        return if (number != null) CallBackAction.OpenWhatsApp(number) else CallBackAction.None
    }
    return if (event.from.any { it.isDigit() }) CallBackAction.Dial(event.from) else CallBackAction.None
}

// Real phone-call entries store the raw incoming number in `from`, with no contact-name lookup
// applied — shown as-is, the Log tab and the blocked-call notification would display a number
// even for someone saved in the phone's own contacts. Resolve it the same way the call-back
// action does, falling back to the raw number when there's no match.
fun resolveDisplayName(event: BlockedEvent, deviceContacts: List<Contact>): String =
    if (event.appName == "Phone") {
        findMatchingDeviceContact(event, deviceContacts)?.name ?: event.from
    } else {
        event.from
    }

fun launchCallBackAction(context: Context, action: CallBackAction) {
    val intent = when (action) {
        is CallBackAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.number}"))
        // wa.me opens the chat/call screen for that number directly inside WhatsApp; if WhatsApp
        // isn't installed it falls back to a browser instead of failing outright.
        is CallBackAction.OpenWhatsApp -> {
            val digits = action.number.filter { it.isDigit() }
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
        }
        CallBackAction.None -> return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing on the device can handle it — nothing sensible to fall back to.
    }
}

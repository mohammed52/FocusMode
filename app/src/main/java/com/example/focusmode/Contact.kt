package com.example.focusmode

data class Contact(
    val id: String,
    val name: String,
    val phoneNumbers: List<String>,
    val photoUri: String? = null
)

data class BlockedEvent(
    val timestamp: Long,
    val type: String,       // "call" or "notification"
    val from: String,       // caller name or notification title
    val appName: String = ""
)

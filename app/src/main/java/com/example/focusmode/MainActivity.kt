package com.example.focusmode

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focusmode.ui.theme.FocusModeTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    // Bumped whenever a notification arrives requesting the log tab, including while this
    // Activity instance is already running (onNewIntent) — Compose observes it to switch tabs.
    private val openLogTabTrigger = mutableIntStateOf(0)

    companion object {
        const val EXTRA_OPEN_LOG_TAB = "open_log_tab"
        const val EXTRA_RESET_CONTACT_KEY = "reset_contact_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        setContent {
            FocusModeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FocusApp(openLogTabTrigger = openLogTabTrigger.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    // Cancelling the blocked-call notifications is what clears the app-icon badge — there's no
    // separate "badge count" API to zero out, the badge just reflects active notifications.
    override fun onResume() {
        super.onResume()
        BlockedCallNotifier.cancelAll(applicationContext)
    }

    // Tapping a blocked-call notification both opens the Log tab and resets that contact's
    // running block count — the swipe-to-dismiss path resets the same count via
    // BlockedCallDismissReceiver instead, since there's no Activity launch to hook into there.
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_LOG_TAB, false) == true) {
            openLogTabTrigger.intValue++
        }
        intent?.getStringExtra(EXTRA_RESET_CONTACT_KEY)?.let { key ->
            lifecycleScope.launch { PreferencesManager(applicationContext).resetBlockCount(key) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusApp(openLogTabTrigger: Int = 0) {
    val context = LocalContext.current
    val vm: MainViewModel = viewModel()

    val isEnabled by vm.isEnabled.collectAsState()
    val allowedContacts by vm.allowedContacts.collectAsState()
    val blockLog by vm.blockLog.collectAsState()
    val deviceContacts by vm.deviceContacts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(openLogTabTrigger) {
        if (openLogTabTrigger > 0) selectedTab = 1
    }
    var showContactPicker by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    // Increment this whenever we resume so permissions are re-evaluated
    var permRefreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Re-evaluate permissions every time the app resumes
    val hasContacts = remember(permRefreshKey) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    val hasNotificationListener = remember(permRefreshKey) { isNotificationListenerEnabled(context) }
    val hasCallScreening = remember(permRefreshKey) { hasCallScreeningRole(context) }
    val hasDnd = remember(permRefreshKey) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted
    }
    // Optional: lets DND be restored the instant an allowed call ends instead of relying solely
    // on the backstop timer, so it doesn't block allPermsGranted like the four core grants do.
    val hasPhoneState = remember(permRefreshKey) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
    // Optional: needed to show the status-bar icon while Focus Mode is on. Pre-Tiramisu this
    // permission doesn't require a runtime grant, so checkSelfPermission reports it as granted.
    val hasPostNotifications = remember(permRefreshKey) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val allPermsGranted = hasContacts && hasNotificationListener && hasCallScreening && hasDnd

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.loadDeviceContacts(context) }

    val phoneStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val roleActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    LaunchedEffect(hasContacts) {
        if (hasContacts) vm.loadDeviceContacts(context)
    }

    // Show permissions dialog at startup if anything is missing
    LaunchedEffect(allPermsGranted) {
        if (!allPermsGranted) showPermissionsDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Mode", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Toggle card
            val cardColor = if (isEnabled)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isEnabled) "Focus Mode ON" else "Focus Mode OFF",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnabled)
                                "Blocking all notifications & calls"
                            else
                                "Tap to activate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            if (!allPermsGranted && !isEnabled) {
                                showPermissionsDialog = true
                            } else {
                                vm.toggleEnabled()
                            }
                        }
                    )
                }
            }

            // Permission warning banner
            if (!allPermsGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showPermissionsDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("Setup required — tap to grant permissions",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Allowed (${allowedContacts.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Blocked Log (${blockLog.size})") })
            }

            when (selectedTab) {
                0 -> ContactsTab(
                    contacts = allowedContacts,
                    onAdd = { showContactPicker = true },
                    onRemove = { vm.removeContact(it) }
                )
                1 -> LogTab(log = blockLog, onClear = { vm.clearLog() })
            }
        }
    }

    // Contact picker dialog
    if (showContactPicker) {
        ContactPickerDialog(
            deviceContacts = deviceContacts,
            allowedContacts = allowedContacts,
            hasPermission = hasContacts,
            onSelect = { vm.addContact(it); showContactPicker = false },
            onDismiss = { showContactPicker = false },
            onRequestPermission = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
        )
    }

    // Permissions dialog
    if (showPermissionsDialog) {
        PermissionsDialog(
            hasContacts = hasContacts,
            hasNotificationListener = hasNotificationListener,
            hasCallScreening = hasCallScreening,
            hasDnd = hasDnd,
            hasPhoneState = hasPhoneState,
            hasPostNotifications = hasPostNotifications,
            onContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onNotificationListener = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onCallScreening = {
                val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                roleActivityLauncher.launch(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                )
            },
            onDnd = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
            onPhoneState = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
            onPostNotifications = {
                postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { showPermissionsDialog = false }
        )
    }
}

// Contacts tab
@Composable
fun ContactsTab(
    contacts: List<Contact>,
    onAdd: () -> Unit,
    onRemove: (Contact) -> Unit
) {
    if (contacts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PersonAdd, null,
                    Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No allowed contacts yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAdd) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Contact")
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    contact.name.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(contact.phoneNumbers.firstOrNull() ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onRemove(contact) }) {
                                Icon(Icons.Default.RemoveCircle, "Remove",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.PersonAdd, "Add contact")
            }
        }
    }
}

// One entry per distinct caller rather than per call — BlockedCallNotifier.contactKey is the
// same "appName|from" identity already used for that notification's running per-contact count,
// so a contact's grouping here matches what they'd see in the notification shade.
private data class GroupedBlock(val latest: BlockedEvent, val count: Int)

// Log tab
@Composable
fun LogTab(log: List<BlockedEvent>, onClear: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    if (log.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.NotificationsOff, null,
                    Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Nothing blocked yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        // log is most-recent-first (PreferencesManager.addBlockedEvent), so groupBy keeps each
        // key's first/latest occurrence as the group's representative without a separate sort.
        val grouped = remember(log) {
            log.groupBy { BlockedCallNotifier.contactKey(it) }
                .map { (_, events) -> GroupedBlock(events.first(), events.size) }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${grouped.size} contacts • ${log.size} blocked",
                    style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onClear) { Text("Clear All") }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(grouped, key = { BlockedCallNotifier.contactKey(it.latest) }) { group ->
                    val event = group.latest
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (event.type == "call") Icons.Default.CallEnd
                                else Icons.Default.NotificationsOff,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${event.from} (${group.count})", fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sourceLabel = if (event.type == "call") {
                                    "${event.appName} call"
                                } else {
                                    event.appName
                                }
                                Text(
                                    "$sourceLabel • last at ${fmt.format(Date(event.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Contact picker dialog
@Composable
fun ContactPickerDialog(
    deviceContacts: List<Contact>,
    allowedContacts: List<Contact>,
    hasPermission: Boolean,
    onSelect: (Contact) -> Unit,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Contact") },
        text = {
            if (!hasPermission) {
                Column {
                    Text("Contacts permission needed.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) { Text("Grant Permission") }
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search contacts...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    val filtered = deviceContacts.filter {
                        query.isEmpty() ||
                                it.name.contains(query, true) ||
                                it.phoneNumbers.any { p -> p.contains(query) }
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.id }) { contact ->
                            val alreadyAdded = allowedContacts.any { it.id == contact.id }
                            ListItem(
                                headlineContent = { Text(contact.name) },
                                supportingContent = {
                                    Text(contact.phoneNumbers.firstOrNull() ?: "")
                                },
                                trailingContent = {
                                    if (alreadyAdded)
                                        Icon(Icons.Default.Check, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable(enabled = !alreadyAdded) {
                                    onSelect(contact)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// Permissions dialog
@Composable
fun PermissionsDialog(
    hasContacts: Boolean,
    hasNotificationListener: Boolean,
    hasCallScreening: Boolean,
    hasDnd: Boolean,
    hasPhoneState: Boolean,
    hasPostNotifications: Boolean,
    onContacts: () -> Unit,
    onNotificationListener: () -> Unit,
    onCallScreening: () -> Unit,
    onDnd: () -> Unit,
    onPhoneState: () -> Unit,
    onPostNotifications: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setup Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Focus Mode needs the following permissions:")
                PermRow("Read Contacts", "Identify callers from your contacts",
                    hasContacts, onContacts)
                PermRow("Notification Access", "Block notifications from all apps",
                    hasNotificationListener, onNotificationListener)
                PermRow("Call Screening", "Block calls from unknown numbers",
                    hasCallScreening, onCallScreening)
                PermRow("Do Not Disturb Access", "Silence sounds immediately when Focus Mode is ON",
                    hasDnd, onDnd)
                PermRow("Call Sync (optional)",
                    "Restore Do Not Disturb the instant an allowed call ends",
                    hasPhoneState, onPhoneState)
                PermRow("Status Icon (optional)",
                    "Show an icon in the notification shade while Focus Mode is on",
                    hasPostNotifications, onPostNotifications)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun PermRow(label: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            null,
            tint = if (granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!granted) {
            TextButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

// Helpers
fun isNotificationListenerEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
}

fun hasCallScreeningRole(context: Context): Boolean {
    val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

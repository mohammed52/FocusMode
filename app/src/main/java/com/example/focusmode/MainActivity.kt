package com.example.focusmode

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focusmode.ui.theme.FocusModeTheme
import com.example.focusmode.widget.FocusGlanceWidgetReceiver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    // Bumped whenever a notification arrives requesting the log tab, including while this
    // Activity instance is already running (onNewIntent) — Compose observes it to switch tabs.
    private val openLogTabTrigger = mutableIntStateOf(0)

    // Plain var, not Compose state: it's always updated in the same synchronous call as
    // openLogTabTrigger below, right before Compose gets a chance to recompose, so the
    // recomposition triggered by that counter always sees this value already up to date.
    private var pendingCallBackKey: String? = null

    companion object {
        const val EXTRA_OPEN_LOG_TAB = "open_log_tab"
        const val EXTRA_RESET_CONTACT_KEY = "reset_contact_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupportContact.init()
        Ads.init(applicationContext)
        handleNotificationIntent(intent)
        setContent {
            FocusModeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FocusApp(
                        openLogTabTrigger = openLogTabTrigger.intValue,
                        pendingCallBackKey = pendingCallBackKey
                    )
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

    // Tapping a blocked-call notification opens the Log tab, resets that contact's running block
    // count, and (see FocusApp's LaunchedEffect(openLogTabTrigger)) launches the same call-back
    // action a tap on that entry would trigger in-app. The swipe-to-dismiss path only resets the
    // count, via BlockedCallDismissReceiver, since there's no Activity launch to hook into there.
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_LOG_TAB, false) == true) {
            openLogTabTrigger.intValue++
        }
        intent?.getStringExtra(EXTRA_RESET_CONTACT_KEY)?.let { key ->
            lifecycleScope.launch { PreferencesManager(applicationContext).resetBlockCount(key) }
            pendingCallBackKey = key
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusApp(openLogTabTrigger: Int = 0, pendingCallBackKey: String? = null) {
    val context = LocalContext.current
    val vm: MainViewModel = viewModel()

    val onboardingComplete by vm.onboardingComplete.collectAsState()
    if (!onboardingComplete) {
        OnboardingScreen(onFinish = { vm.completeOnboarding() })
        return
    }

    val isEnabled by vm.isEnabled.collectAsState()
    val allowedContacts by vm.allowedContacts.collectAsState()
    val blockLog by vm.blockLog.collectAsState()
    val deviceContacts by vm.deviceContacts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(openLogTabTrigger) {
        if (openLogTabTrigger > 0) {
            selectedTab = 1
            pendingCallBackKey?.let { key ->
                vm.findLogEntry(key)?.let { event ->
                    launchCallBackAction(context, resolveCallBackAction(event, deviceContacts))
                }
            }
        }
    }
    var showContactPicker by remember { mutableStateOf(false) }
    var showPermissionsFlow by remember { mutableStateOf(false) }

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
    val hasWidget = remember(permRefreshKey) {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, FocusGlanceWidgetReceiver::class.java))
            .isNotEmpty()
    }

    val allPermsGranted = hasContacts && hasNotificationListener && hasCallScreening && hasDnd

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.loadDeviceContacts(context) }

    val roleActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    LaunchedEffect(hasContacts) {
        if (hasContacts) vm.loadDeviceContacts(context)
    }

    // Show the permissions flow at startup if anything is missing
    LaunchedEffect(allPermsGranted) {
        if (!allPermsGranted) showPermissionsFlow = true
    }

    if (showPermissionsFlow) {
        PermissionsFlowScreen(
            steps = buildPermissionSteps(
                hasContacts = hasContacts,
                hasNotificationListener = hasNotificationListener,
                hasCallScreening = hasCallScreening,
                hasDnd = hasDnd,
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
                }
            ),
            onDone = { showPermissionsFlow = false }
        )
    } else {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Masjid Call Block", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { SupportContact.openWhatsAppChat(context) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Suggestions & feedback on WhatsApp"
                        )
                    }
                },
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
                            text = if (isEnabled) "Masjid Call Block ON" else "Masjid Call Block OFF",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnabled)
                                "Only your allowed contacts can reach you"
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
                                showPermissionsFlow = true
                            } else {
                                vm.toggleEnabled()
                            }
                        }
                    )
                }
            }

            // Widget suggestion card — disappears once the widget is placed
            if (!hasWidget) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Add the widget to your home screen for quick access",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { requestPinFocusWidget(context) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Permission warning banner
            if (!allPermsGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showPermissionsFlow = true },
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
                1 -> LogTab(log = blockLog, deviceContacts = deviceContacts, onClear = { vm.clearLog() })
            }
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
fun LogTab(log: List<BlockedEvent>, deviceContacts: List<Contact>, onClear: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val context = LocalContext.current
    val adsEnabled by Ads.enabledFlow.collectAsState()

    Column(Modifier.fillMaxSize()) {
    if (log.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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

        Column(Modifier.weight(1f)) {
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
                    val displayName = resolveDisplayName(event, deviceContacts)
                    val callBackAction = resolveCallBackAction(event, deviceContacts)
                    Card(
                        if (callBackAction != CallBackAction.None) {
                            Modifier.fillMaxWidth().clickable {
                                launchCallBackAction(context, callBackAction)
                            }
                        } else {
                            Modifier.fillMaxWidth()
                        },
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
                                Text("$displayName (${group.count})", fontWeight = FontWeight.Medium,
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
                            if (callBackAction != CallBackAction.None) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "Call back",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (adsEnabled) {
        BannerAdView(modifier = Modifier.fillMaxWidth())
    }
    }
}

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = Ads.bannerUnitId()
                loadAd(Ads.buildBannerRequest())
            }
        }
    )
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

// Helpers
fun requestPinFocusWidget(context: Context): Boolean {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
    val provider = ComponentName(context, FocusGlanceWidgetReceiver::class.java)
    return appWidgetManager.requestPinAppWidget(provider, null, null)
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
}

fun hasCallScreeningRole(context: Context): Boolean {
    val rm = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

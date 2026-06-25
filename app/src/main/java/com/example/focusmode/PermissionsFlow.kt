package com.example.focusmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusmode.ui.theme.ArchShape

// One required permission, in plain language, with a concrete hint for what to do on the
// system screen the action button sends the user to. Deliberately only the 4 permissions that
// the blocking engine actually needs to function — the 2 optional ones (READ_PHONE_STATE for
// instant DND restore, POST_NOTIFICATIONS for the status icon) are left out of this flow
// entirely: both degrade gracefully on their own, and for a non-technical family audience,
// fewer required taps to get the core feature working matters more than those nice-to-haves.
data class PermissionStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val hint: String,
    val buttonLabel: String,
    val granted: Boolean,
    val onAction: () -> Unit
)

fun buildPermissionSteps(
    hasContacts: Boolean,
    hasNotificationListener: Boolean,
    hasCallScreening: Boolean,
    hasDnd: Boolean,
    onContacts: () -> Unit,
    onNotificationListener: () -> Unit,
    onCallScreening: () -> Unit,
    onDnd: () -> Unit
): List<PermissionStep> = listOf(
    PermissionStep(
        icon = Icons.Default.Contacts,
        title = "Allow access to Contacts",
        description = "So Masjid Call Block can recognize your family and let them through.",
        hint = "Tap \"Allow\" on the prompt that appears.",
        buttonLabel = "Allow Contacts Access",
        granted = hasContacts,
        onAction = onContacts
    ),
    PermissionStep(
        icon = Icons.Default.NotificationsActive,
        title = "Turn on Notification Access",
        description = "This lets Masjid Call Block silence notifications from other apps while it's on.",
        hint = "Find \"Masjid Call Block\" in the list and turn it on, then come back here.",
        buttonLabel = "Open Notification Settings",
        granted = hasNotificationListener,
        onAction = onNotificationListener
    ),
    PermissionStep(
        icon = Icons.AutoMirrored.Filled.PhoneForwarded,
        title = "Set Masjid Call Block to screen calls",
        description = "This lets it silence calls from numbers that aren't on your allowed list.",
        hint = "Tap \"Masjid Call Block\" on the next screen to confirm.",
        buttonLabel = "Open Call Screening Settings",
        granted = hasCallScreening,
        onAction = onCallScreening
    ),
    PermissionStep(
        icon = Icons.Default.DoNotDisturbOn,
        title = "Allow Do Not Disturb access",
        description = "This silences sounds the instant Masjid Call Block turns on.",
        hint = "Find \"Masjid Call Block\" in the list and allow it, then come back here.",
        buttonLabel = "Open Do Not Disturb Settings",
        granted = hasDnd,
        onAction = onDnd
    )
)

@Composable
fun PermissionsFlowScreen(
    steps: List<PermissionStep>,
    onDone: () -> Unit
) {
    val currentIndex = steps.indexOfFirst { !it.granted }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (currentIndex == -1) {
            AllSetStep(onDone = onDone)
        } else {
            PermissionStepContent(
                step = steps[currentIndex],
                stepNumber = currentIndex + 1,
                totalSteps = steps.size,
                onFinishLater = onDone
            )
        }
    }
}

@Composable
private fun PermissionStepContent(
    step: PermissionStep,
    stepNumber: Int,
    totalSteps: Int,
    onFinishLater: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Step $stepNumber of $totalSteps",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(ArchShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                step.icon, contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            step.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = step.onAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(step.buttonLabel)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            step.hint,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onFinishLater) {
            Text("Finish later")
        }
    }
}

@Composable
private fun AllSetStep(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(ArchShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle, contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "You're all set",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Masjid Call Block is ready. Turn it on before you head in, and your family can still reach you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

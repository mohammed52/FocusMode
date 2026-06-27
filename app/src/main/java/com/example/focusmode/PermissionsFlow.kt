package com.example.focusmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
        description = "So Masjid Call Block can recognise your family and friends and let them through.",
        hint = "Tap \"Allow\" on the prompt that appears.",
        buttonLabel = "Allow Contacts Access",
        granted = hasContacts,
        onAction = onContacts
    ),
    PermissionStep(
        icon = Icons.Default.NotificationsActive,
        title = "Turn on Notification Access",
        description = "So it can silence notifications from other apps. Nothing is ever read " +
            "or shared — everything stays on your phone.",
        hint = "Find \"Masjid Call Block\" in the list and turn it on, then come back here.",
        buttonLabel = "Open Notification Settings",
        granted = hasNotificationListener,
        onAction = onNotificationListener
    ),
    PermissionStep(
        icon = Icons.AutoMirrored.Filled.PhoneForwarded,
        title = "Screen incoming calls",
        description = "So it can silence calls from numbers that aren't on your allowed list.",
        hint = "Tap \"Masjid Call Block\" on the next screen to confirm.",
        buttonLabel = "Open Call Screening Settings",
        granted = hasCallScreening,
        onAction = onCallScreening
    ),
    PermissionStep(
        icon = Icons.Default.DoNotDisturbOn,
        title = "Allow Do Not Disturb access",
        description = "This lets the app silence all sounds when you turn it on, so nothing rings or buzzes while you're inside.",
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
        Row(horizontalArrangement = Arrangement.Center) {
            repeat(totalSteps) { index ->
                val isCurrent = index == stepNumber - 1
                val isDone = index < stepNumber - 1
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isCurrent) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent || isDone) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
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
        Spacer(Modifier.height(16.dp))
        Text(
            step.hint,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = step.onAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(step.buttonLabel)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onFinishLater) {
            Text("Skip for now — app won't work yet")
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
            "Masjid Call Block is ready. Turn it on before you head in to the masjid — and when the session ends, your family and friends can still reach you to coordinate.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Have a suggestion or feature idea? Tap the chat icon at the top of the main screen anytime to message us on WhatsApp.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

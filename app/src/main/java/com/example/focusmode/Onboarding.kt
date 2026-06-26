package com.example.focusmode

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.focusmode.ui.theme.ArchShape
import com.example.focusmode.ui.theme.BohraMaroon
import com.example.focusmode.ui.theme.BohraSurfaceVariantDark
import com.example.focusmode.widget.FocusGlanceWidgetReceiver
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastPage) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> WidgetTutorialPage()
                    2 -> ContactsIntroPage()
                    else -> PermissionsIntroPage()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(ONBOARDING_PAGE_COUNT) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(if (isLastPage) "Get Started" else "Next")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingPageScaffold(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
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
                icon, contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun WelcomePage() {
    OnboardingPageScaffold(icon = Icons.Default.DoNotDisturbOn, title = "Welcome to Masjid Call Block") {
        Text(
            "Silence your phone for waaz or majlis without going completely dark. Masjid Call " +
                "Block keeps everyone else quiet while the family you choose can still reach you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Have a suggestion or feature idea? Tap the chat icon at the top of the main " +
                "screen anytime to message us on WhatsApp.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactsIntroPage() {
    OnboardingPageScaffold(icon = Icons.Default.Contacts, title = "Choose who gets through") {
        Text(
            "On the next screen, add your family — the people who need to reach you to " +
                "coordinate leaving together. Everyone else stays silent while Masjid Call " +
                "Block is on.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionsIntroPage() {
    OnboardingPageScaffold(icon = Icons.Default.Security, title = "One last step") {
        Text(
            "Masjid Call Block needs a few permissions to screen calls and notifications. " +
                "We'll walk you through granting them right after this.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WidgetTutorialPage() {
    val context = LocalContext.current
    var pinSupported by remember { mutableStateOf<Boolean?>(null) }

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
                Icons.Default.Widgets, contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Add the Home screen widget",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This is the fastest way to use Masjid Call Block — tap it on your way into masjid, " +
                "tap it again on your way out. It turns red when Masjid Call Block is on.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WidgetPreviewChip(label = "OFF", color = BohraSurfaceVariantDark)
            WidgetPreviewChip(label = "ON", color = BohraMaroon)
        }
        Spacer(Modifier.height(24.dp))

        Button(onClick = { pinSupported = requestPinFocusWidget(context) }) {
            Icon(Icons.Default.Widgets, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add widget to Home screen")
        }

        pinSupported?.let { supported ->
            Spacer(Modifier.height(12.dp))
            Text(
                if (supported)
                    "Confirm on the next prompt to drop it on your Home screen."
                else
                    "Your launcher doesn't support adding it this way. Long-press an empty " +
                        "spot on your Home screen, choose Widgets, then find Masjid Call Block.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WidgetPreviewChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(ArchShape)
                    .background(Color.White)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label, color = Color.White, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// Returns whether the launcher accepted the pin request — false means we fell back to
// instructing the user to add the widget manually via the launcher's widget picker.
fun requestPinFocusWidget(context: Context): Boolean {
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return false
    val provider = ComponentName(context, FocusGlanceWidgetReceiver::class.java)
    return appWidgetManager.requestPinAppWidget(provider, null, null)
}

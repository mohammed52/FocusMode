package com.example.focusmode.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.focusmode.PreferencesManager

class FocusGlanceWidget : GlanceAppWidget() {
    // We drive recomposition by collecting PreferencesManager's flow directly inside the
    // composition (see provideGlance below) instead of through Glance's own state mechanism,
    // so there's no GlanceStateDefinition-backed state to track here.
    override val stateDefinition: GlanceStateDefinition<*>? = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // update()/updateAll() only signal an already-running session — they do not
            // re-invoke provideGlance. Collecting the flow here, inside the composition, is
            // what makes the widget actually recompose on every change, from any caller.
            val enabled by PreferencesManager(context).isEnabled.collectAsState(initial = false)
            GlanceTheme {
                WidgetContent(enabled)
            }
        }
    }
}

@Composable
private fun WidgetContent(enabled: Boolean) {
    val backgroundColor = if (enabled) Color(0xFFB3261E) else Color(0xFF49454F)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(4.dp)
            .background(ColorProvider(day = backgroundColor, night = backgroundColor))
            .clickable(actionRunCallback<ToggleFocusModeAction>()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (enabled) "ON" else "OFF",
            style = TextStyle(
                fontSize = 14.sp,
                color = ColorProvider(day = Color.White, night = Color.White),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

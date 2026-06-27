package com.example.focusmode.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.focusmode.PreferencesManager
import com.example.focusmode.R
import com.example.focusmode.ui.theme.BohraGold
import com.example.focusmode.ui.theme.BohraIvory
import com.example.focusmode.ui.theme.BohraMaroon

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
    // ON: gold — warm premium feel; maroon arch and text for contrast.
    // OFF: maroon — recedes to a calm dark state; gold arch and ivory text for contrast.
    val backgroundColor = if (enabled) BohraGold else BohraMaroon
    val archRes = if (enabled) R.drawable.ic_widget_arch_on else R.drawable.ic_widget_arch
    val textColor = if (enabled) BohraMaroon else BohraIvory
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(4.dp)
            .cornerRadius(16.dp)
            .background(ColorProvider(day = backgroundColor, night = backgroundColor))
            .clickable(actionRunCallback<ToggleFocusModeAction>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(archRes),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp)
        )
        Spacer(modifier = GlanceModifier.width(5.dp))
        Text(
            text = if (enabled) "ON" else "OFF",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = ColorProvider(day = textColor, night = textColor),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

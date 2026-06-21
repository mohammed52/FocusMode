package com.example.focusmode.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.focusmode.FocusModeController
import com.example.focusmode.PreferencesManager
import kotlinx.coroutines.flow.first

class ToggleFocusModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentlyEnabled = PreferencesManager(context).isEnabled.first()
        FocusModeController.setEnabled(context, !currentlyEnabled)
    }
}

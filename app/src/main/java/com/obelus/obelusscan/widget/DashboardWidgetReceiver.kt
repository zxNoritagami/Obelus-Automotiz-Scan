package com.obelus.obelusscan.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()

    companion object {
        fun updateAllWidgets(context: Context, rpm: String, speed: String, temp: String, fuel: String) {
            CoroutineScope(Dispatchers.IO).launch {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIds(DashboardWidget::class.java).firstOrNull()
                
                if (glanceId != null) {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[DashboardWidget.RPM_KEY] = rpm
                            this[DashboardWidget.SPEED_KEY] = speed
                            this[DashboardWidget.TEMP_KEY] = temp
                            this[DashboardWidget.FUEL_KEY] = fuel
                        }
                    }
                    DashboardWidget().update(context, glanceId)
                }
            }
        }
    }
}

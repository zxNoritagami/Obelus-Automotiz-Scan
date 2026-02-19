package com.obelus.obelusscan.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.obelus.MainActivity

class DashboardWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val rpm = prefs[RPM_KEY] ?: "--"
                val speed = prefs[SPEED_KEY] ?: "--"
                val temp = prefs[TEMP_KEY] ?: "--"
                val fuel = prefs[FUEL_KEY] ?: "--"

                WidgetContent(context, rpm, speed, temp, fuel)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, rpm: String, speed: String, temp: String, fuel: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WidgetDataItem("RPM", rpm, "rpm")
                Spacer(modifier = GlanceModifier.width(16.dp))
                WidgetDataItem("SPEED", speed, "km/h")
                Spacer(modifier = GlanceModifier.width(16.dp))
                WidgetDataItem("TEMP", temp, "°C")
                Spacer(modifier = GlanceModifier.width(16.dp))
                WidgetDataItem("FUEL", fuel, "L/100")
            }
            
            Spacer(modifier = GlanceModifier.height(8.dp))
            
            Text(
                text = "Toca para abrir Obelus",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
        }
    }

    @Composable
    private fun WidgetDataItem(label: String, value: String, unit: String) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = value,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = unit,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
        }
    }

    companion object {
        val RPM_KEY = stringPreferencesKey("rpm_key")
        val SPEED_KEY = stringPreferencesKey("speed_key")
        val TEMP_KEY = stringPreferencesKey("temp_key")
        val FUEL_KEY = stringPreferencesKey("fuel_key")
    }
}

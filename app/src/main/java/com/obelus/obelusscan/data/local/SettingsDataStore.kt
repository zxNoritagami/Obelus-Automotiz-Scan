package com.obelus.obelusscan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension property para acceso singleton al DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Configuración de unidades de medida.
 */
data class UnitsConfig(
    val distanceUnit: String, // "km" or "miles"
    val consumptionUnit: String // "l_100km", "mpg_us", or "mpg_uk"
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UNITS_DISTANCE = stringPreferencesKey("units_distance")
        val UNITS_CONSUMPTION = stringPreferencesKey("units_consumption")
        val REFRESH_RATE_MS = intPreferencesKey("refresh_rate_ms")
    }

    // --- Theme ---
    
    val themeMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = theme
        }
    }

    // --- Units ---

    val unitsConfig: Flow<UnitsConfig> = dataStore.data.map { preferences ->
        UnitsConfig(
            distanceUnit = preferences[UNITS_DISTANCE] ?: "km",
            consumptionUnit = preferences[UNITS_CONSUMPTION] ?: "l_100km"
        )
    }

    suspend fun setUnits(units: UnitsConfig) {
        dataStore.edit { preferences ->
            preferences[UNITS_DISTANCE] = units.distanceUnit
            preferences[UNITS_CONSUMPTION] = units.consumptionUnit
        }
    }

    // --- Refresh Rate ---

    val refreshRateMs: Flow<Int> = dataStore.data.map { preferences ->
        preferences[REFRESH_RATE_MS] ?: 500
    }

    suspend fun setRefreshRate(ms: Int) {
        dataStore.edit { preferences ->
            preferences[REFRESH_RATE_MS] = ms
        }
    }
}

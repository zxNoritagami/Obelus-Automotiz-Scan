package com.obelus.obelusscan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    val distanceUnit: String,    // "km" or "miles"
    val consumptionUnit: String  // "l_100km", "mpg_us", or "mpg_uk"
)

/**
 * Configuración de telemetría MQTT.
 */
data class TelemetryConfig(
    val brokerUrl: String,       // ej: "tcp://broker.hivemq.com:1883"
    val clientId: String,        // UUID único por instalación
    val publishIntervalMs: Long, // Default: 5000ms
    val isTelemetryEnabled: Boolean
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val THEME_MODE            = stringPreferencesKey("theme_mode")
        val UNITS_DISTANCE        = stringPreferencesKey("units_distance")
        val UNITS_CONSUMPTION     = stringPreferencesKey("units_consumption")
        val REFRESH_RATE_MS       = intPreferencesKey("refresh_rate_ms")

        // MQTT / Telemetría
        val MQTT_BROKER_URL       = stringPreferencesKey("mqtt_broker_url")
        val MQTT_CLIENT_ID        = stringPreferencesKey("mqtt_client_id")
        val MQTT_PUBLISH_INTERVAL = longPreferencesKey("mqtt_publish_interval_ms")
        val MQTT_ENABLED          = booleanPreferencesKey("mqtt_telemetry_enabled")

        // Race Mode
        val VEHICLE_WEIGHT_KG     = intPreferencesKey("vehicle_weight_kg")

        // Defaults
        const val DEFAULT_BROKER_URL       = "tcp://broker.hivemq.com:1883"
        const val DEFAULT_PUBLISH_INTERVAL = 5_000L
        const val DEFAULT_VEHICLE_WEIGHT   = 1200
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

    // --- Telemetría MQTT ---

    val telemetryConfig: Flow<TelemetryConfig> = dataStore.data.map { preferences ->
        TelemetryConfig(
            brokerUrl          = preferences[MQTT_BROKER_URL] ?: DEFAULT_BROKER_URL,
            clientId           = preferences[MQTT_CLIENT_ID]
                ?: generateClientId().also { setClientId(it) },
            publishIntervalMs  = preferences[MQTT_PUBLISH_INTERVAL] ?: DEFAULT_PUBLISH_INTERVAL,
            isTelemetryEnabled = preferences[MQTT_ENABLED] ?: false
        )
    }

    suspend fun setTelemetryEnabled(enabled: Boolean) {
        dataStore.edit { it[MQTT_ENABLED] = enabled }
    }

    suspend fun setBrokerUrl(url: String) {
        dataStore.edit { it[MQTT_BROKER_URL] = url }
    }

    suspend fun setPublishInterval(ms: Long) {
        dataStore.edit { it[MQTT_PUBLISH_INTERVAL] = ms }
    }

    private suspend fun setClientId(id: String) {
        dataStore.edit { it[MQTT_CLIENT_ID] = id }
    }

    private fun generateClientId(): String =
        "obelus_" + java.util.UUID.randomUUID().toString().take(8)

    // --- Race Mode: Vehicle Weight ---

    val vehicleWeightKg: Flow<Int> = dataStore.data.map { preferences ->
        preferences[VEHICLE_WEIGHT_KG] ?: DEFAULT_VEHICLE_WEIGHT
    }

    suspend fun setVehicleWeight(kg: Int) {
        dataStore.edit { it[VEHICLE_WEIGHT_KG] = kg.coerceIn(100, 5000) }
    }
}

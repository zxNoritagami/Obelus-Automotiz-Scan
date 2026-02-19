package com.obelus.obelusscan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.repository.TelemetryRepository
import com.obelus.obelusscan.data.local.SettingsDataStore
import com.obelus.obelusscan.data.local.TelemetryConfig
import com.obelus.obelusscan.data.local.UnitsConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val telemetryRepository: TelemetryRepository
) : ViewModel() {

    // --- StateFlows expuestos a la UI ---

    val theme = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val units = settingsDataStore.unitsConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UnitsConfig("km", "l_100km")
        )

    val refreshRate = settingsDataStore.refreshRateMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 500)

    val vehicleWeight = settingsDataStore.vehicleWeightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1200)

    val telemetryConfig: StateFlow<TelemetryConfig> = settingsDataStore.telemetryConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TelemetryConfig(
                brokerUrl          = SettingsDataStore.DEFAULT_BROKER_URL,
                clientId           = "",
                publishIntervalMs  = SettingsDataStore.DEFAULT_PUBLISH_INTERVAL,
                isTelemetryEnabled = false
            )
        )

    // Estado del test de conexión: null = idle, true = OK, false = ERROR
    private val _testConnectionResult = MutableStateFlow<Boolean?>(null)
    val testConnectionResult: StateFlow<Boolean?> = _testConnectionResult

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection

    // --- Métodos de actualización ---

    fun setTheme(themeMode: String) {
        viewModelScope.launch {
            settingsDataStore.setTheme(themeMode)
        }
    }

    fun setDistanceUnit(unit: String) {
        viewModelScope.launch {
            val current = units.value
            settingsDataStore.setUnits(current.copy(distanceUnit = unit))
        }
    }

    fun setConsumptionUnit(unit: String) {
        viewModelScope.launch {
            val current = units.value
            settingsDataStore.setUnits(current.copy(consumptionUnit = unit))
        }
    }

    fun setRefreshRate(ms: Int) {
        viewModelScope.launch {
            settingsDataStore.setRefreshRate(ms)
        }
    }

    fun setVehicleWeight(kg: Int) {
        viewModelScope.launch {
            settingsDataStore.setVehicleWeight(kg)
        }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            settingsDataStore.setTheme("system")
            settingsDataStore.setUnits(UnitsConfig("km", "l_100km"))
            settingsDataStore.setRefreshRate(500)
        }
    }

    // --- MQTT / Telemetría ---

    fun setTelemetryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setTelemetryEnabled(enabled)
            if (!enabled) {
                telemetryRepository.stopTelemetry()
            }
        }
    }

    fun setBrokerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setBrokerUrl(url)
        }
    }

    /**
     * Prueba la conexión MQTT con la URL provista.
     * El resultado queda en [testConnectionResult] para mostrarlo en UI.
     */
    fun testConnection(brokerUrl: String) {
        if (_isTestingConnection.value) return
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionResult.value = null
            val result = telemetryRepository.testConnection(brokerUrl)
            _testConnectionResult.value = result
            _isTestingConnection.value = false
        }
    }

    /** Resetea el resultado del test para no mostrarlo de nuevo */
    fun clearTestResult() {
        _testConnectionResult.value = null
    }
}

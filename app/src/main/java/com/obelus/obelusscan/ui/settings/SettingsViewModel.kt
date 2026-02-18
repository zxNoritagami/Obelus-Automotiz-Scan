package com.obelus.obelusscan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.obelusscan.data.local.SettingsDataStore
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
    private val settingsDataStore: SettingsDataStore
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

    fun restoreDefaults() {
        viewModelScope.launch {
            settingsDataStore.setTheme("system")
            settingsDataStore.setUnits(UnitsConfig("km", "l_100km"))
            settingsDataStore.setRefreshRate(500)
        }
    }
}

package com.obelus.obelusscan.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.data.repository.ObdRepository
import com.obelus.data.repository.TelemetryRepository
import com.obelus.domain.model.ObdPid
import com.obelus.mqtt.ObdTelemetry
import com.obelus.obelusscan.data.protocol.ObdProtocol
import com.obelus.protocol.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ObdRepository,
    private val telemetryRepository: TelemetryRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {


    private val protocol = ObdProtocol()
    private var scanJob: Job? = null

    // --- Telemetría MQTT: auto-start/stop con conexión OBD2 ---
    // Observamos el estado de conexión del ELM327; cuando se conecta
    // iniciamos telemetría, cuando se desconecta la detenemos.
    private var connectionWatchJob: Job? = null

    init {
        startConnectionWatch()
    }

    private fun startConnectionWatch() {
        connectionWatchJob = viewModelScope.launch {
            repository.connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        telemetryRepository.startTelemetry(dtcCount = 0)
                    }
                    ConnectionState.DISCONNECTED,
                    ConnectionState.ERROR -> {
                        telemetryRepository.stopTelemetry()
                    }
                    else -> { /* CONNECTING — no hacer nada */ }
                }
            }
        }
    }

    // --- StateFlows para PIDs (Existentes) ---
    private val _rpm = MutableStateFlow(0f)
    val rpm: StateFlow<Float> = _rpm.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _coolantTemp = MutableStateFlow(0f)
    val coolantTemp: StateFlow<Float> = _coolantTemp.asStateFlow()

    private val _engineLoad = MutableStateFlow(0f)
    val engineLoad: StateFlow<Float> = _engineLoad.asStateFlow()

    private val _throttlePos = MutableStateFlow(0f)
    val throttlePos: StateFlow<Float> = _throttlePos.asStateFlow()

    // --- StateFlows para PIDs (Nuevos - Requerimiento) ---
    private val _mafRate = MutableStateFlow(0f)
    val mafRate: StateFlow<Float> = _mafRate.asStateFlow()

    private val _intakePressure = MutableStateFlow(0f)
    val intakePressure: StateFlow<Float> = _intakePressure.asStateFlow()

    private val _barometricPressure = MutableStateFlow(0f)
    val barometricPressure: StateFlow<Float> = _barometricPressure.asStateFlow()

    private val _fuelLevel = MutableStateFlow(0f)
    val fuelLevel: StateFlow<Float> = _fuelLevel.asStateFlow()

    private val _ambientTemp = MutableStateFlow(0f)
    val ambientTemp: StateFlow<Float> = _ambientTemp.asStateFlow()

    // --- StateFlows para Consumo de Combustible (Calculados) ---
    private val calculator = com.obelus.obelusscan.domain.FuelConsumptionCalculator()

    // Consumo Instantáneo (L/100km)
    private val _fuelConsumption = MutableStateFlow(0f)
    val fuelConsumption: StateFlow<Float> = _fuelConsumption.asStateFlow()

    // Consumo Promedio (L/100km) - Últimos 10 valores
    private val _averageConsumption = MutableStateFlow(0f)
    val averageConsumption: StateFlow<Float> = _averageConsumption.asStateFlow()

    // Estado de cálculo (Solo true si Speed > 0)
    private val _isCalculatingConsumption = MutableStateFlow(false)
    val isCalculatingConsumption: StateFlow<Boolean> = _isCalculatingConsumption.asStateFlow()

    private val consumptionHistory = ArrayDeque<Float>()
    private val HISTORY_SIZE = 10

    // --- Lógica de Scanning ---

    fun startScanning() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            // "High Priority" loop counter
            var loopCounter = 0

            while (isActive) {
                // 1. Alta Prioridad (Cada ciclo ~500ms)
                // RPM y Velocidad son críticos para el dashboard
                updatePid(ObdPid.RPM, _rpm)
                updatePid(ObdPid.SPEED, _speed)

                // 2. Baja Prioridad (Cada 2 ciclos ~1000ms)
                // Sensores de temperatura, presión, etc.
                if (loopCounter % 2 == 0) {
                    updatePid(ObdPid.COOLANT_TEMP, _coolantTemp)
                    updatePid(ObdPid.ENGINE_LOAD, _engineLoad)
                    updatePid(ObdPid.THROTTLE_POS, _throttlePos)
                    
                    updatePid(ObdPid.MAF_RATE, _mafRate) // Necesario para consumo!
                    updatePid(ObdPid.INTAKE_PRESSURE, _intakePressure)
                    updatePid(ObdPid.BAROMETRIC_PRESSURE, _barometricPressure)
                    updatePid(ObdPid.FUEL_LEVEL, _fuelLevel)
                    updatePid(ObdPid.AMBIENT_TEMP, _ambientTemp)
                }
                if (repository.isConnected()) {
                    var needsDelay = false
                    try {
                        val rpmRaw = repository.requestPid("0C")?.value?.toIntOrNull()
                        if (rpmRaw != null) _rpm.value = rpmRaw.toFloat() // Convert to Float

                        val speedRaw = repository.requestPid("0D")?.value?.toIntOrNull()
                        if (speedRaw != null) _speed.value = speedRaw.toFloat() // Convert to Float

                        val engineTempRaw = repository.requestPid("05")?.value?.toIntOrNull()
                        if (engineTempRaw != null) _engineLoad.value = engineTempRaw.toFloat() // Temp hack for load ui, convert to Float
                        
                        // Modificacion PROMPT 13: Limite de 10Hz por bateria (throttle)
                        needsDelay = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        needsDelay = true
                    }
                    if(needsDelay) kotlinx.coroutines.delay(100) // 10Hz limit
                } else {
                    kotlinx.coroutines.delay(1000)
                }
            }    // Update Widget (Throttled)
                checkWidgetUpdate(_rpm.value, _speed.value, _coolantTemp.value, _fuelConsumption.value)
                
                delay(500) // Base delay
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        _isCalculatingConsumption.value = false
    }

    /**
     * Calcula el consumo instantáneo basado en MAF y Velocidad actuales.
     */
    private fun calculateConsumption() {
        val currentMaf = _mafRate.value
        val currentSpeed = _speed.value

        // Solo calculamos si el coche se mueve (> 0.1 km/h)
        if (currentSpeed > 0.1f) {
            _isCalculatingConsumption.value = true
            
            // 1. Cálculo Instantáneo
            val instant = calculator.calculateInstant(currentMaf, currentSpeed)
            _fuelConsumption.value = instant

            // 2. Cálculo Promedio (Moving Average)
            if (consumptionHistory.size >= HISTORY_SIZE) {
                consumptionHistory.removeFirst()
            }
            consumptionHistory.addLast(instant)
            _averageConsumption.value = calculator.calculateAverage(consumptionHistory.toList())
            
        } else {
            _isCalculatingConsumption.value = false
            _fuelConsumption.value = 0f
            // No reseteamos el promedio al parar en un semáforo
        }
    }

    /**
     * Solicita y actualiza un PID específico.
     * Usa ObdProtocol para construir comando y parsear respuesta.
     */
    private suspend fun requestPid(pid: ObdPid): Float? {
        if (!repository.isConnected()) return null

        return try {
            // 1. Construir comando (ej: "010C")
            val command = protocol.buildCommand(pid)
            
            // 2. Enviar via Repository (Bluetooth)
            val rawResponse = repository.sendCommand(command)
            
            // 3. Validar respuesta vacía o error del adaptador
            if (rawResponse.contains("NO DATA") || rawResponse.contains("ERROR")) {
                return null
            }

            // 4. Parsear con lógica de dominio
            protocol.parseResponse(pid, rawResponse)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun updatePid(pid: ObdPid, flow: MutableStateFlow<Float>) {
        val value = requestPid(pid)
        if (value != null) {
            flow.value = value
        }
    }

    private var lastWidgetUpdate = 0L

    private fun checkWidgetUpdate(rpm: Float, speed: Float, temp: Float, fuel: Float) {
        val now = System.currentTimeMillis()
        if (now - lastWidgetUpdate > 60000) { // 1 minuto debounce
            lastWidgetUpdate = now
            
            val rpmStr = String.format("%.0f", rpm)
            val speedStr = String.format("%.0f", speed)
            val tempStr = String.format("%.0f", temp)
            val fuelStr = String.format("%.1f", fuel)

            com.obelus.obelusscan.widget.DashboardWidgetReceiver.updateAllWidgets(
                context = context,
                rpm = rpmStr,
                speed = speedStr,
                temp = tempStr,
                fuel = fuelStr
            )
        }
    }
}

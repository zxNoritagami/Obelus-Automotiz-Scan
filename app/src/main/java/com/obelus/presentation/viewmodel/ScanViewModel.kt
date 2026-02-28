package com.obelus.presentation.viewmodel

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.actuator.ActuatorResult
import com.obelus.actuator.SafetyChecker
import com.obelus.actuator.SafetyResult
import com.obelus.actuator.SafetyViolation
import com.obelus.bluetooth.BluetoothManager
import com.obelus.bluetooth.ConnectionState
import com.obelus.data.cache.CacheManager
import com.obelus.data.dbc.DbcImporter
import com.obelus.data.dbc.DbcParser
import com.obelus.data.export.ExportProgress
import com.obelus.data.export.ExportRange
import com.obelus.data.export.ReportFormat
import com.obelus.data.export.ReportGenerator
import com.obelus.data.export.ShareManager
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DatabaseFile
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.data.local.model.FileType
import com.obelus.data.local.model.SignalStats
import com.obelus.data.protocol.actuator.ActuatorTestRepository
import com.obelus.data.repository.ObelusRepository
import com.obelus.domain.model.ActuatorTest
import com.obelus.domain.model.DangerLevel
import com.obelus.manufacturer.Manufacturer
import com.obelus.manufacturer.ManufacturerData
import com.obelus.manufacturer.ManufacturerDataRepository
import com.obelus.manufacturer.ManufacturerReading
import com.obelus.manufacturer.VINDecoder
import com.obelus.chart.ChartAnalyzer
import com.obelus.chart.ChartBufferRegistry
import com.obelus.chart.ChartEvent
import com.obelus.chart.ChartPoint
import com.obelus.freezeframe.AnalysisResult
import com.obelus.freezeframe.FreezeFrameRepository
import com.obelus.domain.model.FreezeFrameData
import com.obelus.domain.model.FreezeFrameResult
import com.obelus.cylinder.CylinderBalance
import com.obelus.cylinder.CylinderTestRepository
import com.obelus.cylinder.CylinderTestResult
import com.obelus.cylinder.Diagnosis
import com.obelus.protocol.ATCommand
import com.obelus.protocol.DetectionResult
import com.obelus.protocol.IsoTPHandler
import com.obelus.protocol.OBD2Protocol
import com.obelus.protocol.OBD2Response
import com.obelus.protocol.ProtocolDetector
import com.obelus.protocol.DTCDecoder
import com.obelus.protocol.utils.hexToBytes
import com.obelus.protocol.utils.toHex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ScanViewModel.kt
// ViewModel principal de la sesión de diagnóstico OBD2.
// Integra: BluetoothManager + ProtocolDetector + IsoTPHandler + DBC + Room.
// ─────────────────────────────────────────────────────────────────────────────

private const val DEFAULT_ECU_TX_ID  = 0x7E0   // OBD2 functional request (broadcast)
private const val DEFAULT_ECU_RX_ID  = 0x7E8   // ECU#1 (Engine) response
private const val SCAN_LOOP_DELAY_MS = 100L     // 10Hz máx
private const val PERSIST_EVERY_MS   = 1000L    // flush to Room cada segundo
private const val MAX_READINGS_IN_STATE = 500   // límite de lecturas en memoria

/**
 * ViewModel central del módulo de escaneo OBD2.
 *
 * Responsabilidades:
 *  - Gestión del ciclo de vida de la conexión Bluetooth via [BluetoothManager].
 *  - Negociación de protocolo OBD2 via [ProtocolDetector].
 *  - Envío/recepción ISO-TP via [IsoTPHandler].
 *  - Importación de archivos DBC via [DbcImporter].
 *  - Lectura de DTCs (Modo 03) y borrado individual.
 *  - Persistencia de sesiones y lecturas en Room via [ObelusRepository].
 *
 * @param bluetoothManager Gestiona conexión BT y descubrimiento.
 * @param repository       Acceso a Room (sesiones, lecturas, DTCs, DBC).
 * @param dbcImporter      Importador de archivos .dbc → Room.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val repository: ObelusRepository,
    private val dbcImporter: DbcImporter,
    private val reportGenerator: ReportGenerator,
    private val shareManager: ShareManager,
    private val cacheManager: CacheManager,
    private val actuatorRepository: ActuatorTestRepository,
    private val safetyChecker: SafetyChecker,
    private val manufacturerDataRepo: ManufacturerDataRepository,
    private val vinDecoder: VINDecoder,
    private val freezeFrameRepo: FreezeFrameRepository,
    private val cylinderTestRepo: CylinderTestRepository
) : ViewModel() {

    // ── Estado único de la UI ─────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // ── Export ────────────────────────────────────────────────────────────────
    private val _isExporting  = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
    private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
    val exportProgress: StateFlow<ExportProgress?> = _exportProgress.asStateFlow()
    private var lastExportedFile: File? = null

    // ── Manufacturer data ────────────────────────────────────────────────────
    private val _manufacturerState = MutableStateFlow(ManufacturerState())
    val manufacturerState: StateFlow<ManufacturerState> = _manufacturerState.asStateFlow()
    private var manufacturerScanJob: Job? = null

    // ── Chart / recording ───────────────────────────────────────────────
    private val chartRegistry       = ChartBufferRegistry()
    private val _chartData          = MutableStateFlow<Map<String, List<ChartPoint>>>(emptyMap())
    val chartData: StateFlow<Map<String, List<ChartPoint>>> = _chartData.asStateFlow()
    private val _chartEvents        = MutableStateFlow<List<ChartEvent>>(emptyList())
    val chartEvents: StateFlow<List<ChartEvent>> = _chartEvents.asStateFlow()
    private var chartRecordingJob: Job? = null
    private val chartActiveSignals  = mutableSetOf<String>()

    // ── Freeze Frame ──────────────────────────────────────────────────
    private val _freezeFrames       = MutableStateFlow<List<FreezeFrameData>>(emptyList())
    val freezeFrames: StateFlow<List<FreezeFrameData>> = _freezeFrames.asStateFlow()
    private val _seenDtcCodes       = mutableSetOf<String>()   // para auto-captura

    // ── Cylinder balance ──────────────────────────────────────────────
    private val _cylinderBalance    = MutableStateFlow<CylinderBalance?>(null)
    val cylinderBalance: StateFlow<CylinderBalance?> = _cylinderBalance.asStateFlow()
    private val _misfireData        = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val misfireData: StateFlow<Map<Int, Int>> = _misfireData.asStateFlow()
    private var idleStableSecs: Int = 0      // contador de segundos en ralentí estable
    private var cylinderTestJob: Job? = null

    // ── Estado interno ─────────────────────────────────────────────────────────
    private var protocolDetector: ProtocolDetector? = null
    private var isoTPHandler: IsoTPHandler? = null
    private var scanJob: Job? = null
    private var persistJob: Job? = null
    private val readingsBuffer = mutableListOf<SignalReading>()

    // Señales activas: las del DBC cargado o las genéricas OBD2
    private var activeSignals: List<CanSignal> = defaultObd2Signals()

    // ── Init: observar estado BT ──────────────────────────────────────────────
    init {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { btState ->
                _uiState.update { it.copy(connectionState = btState) }

                when (btState) {
                    is ConnectionState.Disconnected -> {
                        if (_uiState.value.isScanning) stopScan()
                    }
                    is ConnectionState.Error -> {
                        stopScan()
                        setError(btState.message)
                        // Auto-reconexión si fue inesperada
                        bluetoothManager.scheduleReconnection()
                    }
                    else -> Unit
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONEXIÓN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Conecta al adaptador BT, detecta el protocolo OBD2 y configura el ELM327.
     *
     * @param device Dispositivo Bluetooth a conectar.
     */
    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            clearError()
            println("[ScanViewModel] Conectando a ${device.address}…")

            val result = bluetoothManager.connect(device)
            if (result.isFailure) {
                setError("No se pudo conectar: ${result.exceptionOrNull()?.message}")
                return@launch
            }

            val connection = result.getOrNull() ?: return@launch

            // Crear detector y handler usando la conexión BT activa
            protocolDetector = ProtocolDetector(connection)
            isoTPHandler     = IsoTPHandler(connection)

            // Detectar protocolo automáticamente
            println("[ScanViewModel] Detectando protocolo OBD2…")
            when (val detection = protocolDetector!!.autoDetect()) {
                is DetectionResult.Success -> {
                    _uiState.update { it.copy(detectedProtocol = detection.protocol) }
                    println("[ScanViewModel] Protocolo: ${detection.protocol.protocolName}")
                }
                is DetectionResult.Failure -> {
                    val suggestion = "Pruebe seleccionar manualmente:\n" +
                                     "CAN 11-bit/500K → CAN 29-bit/500K → CAN 11-bit/250K"
                    _uiState.update {
                        it.copy(
                            protocolSuggestion = suggestion,
                            errorMessage = "No se detectó protocolo automáticamente"
                        )
                    }
                    println("[ScanViewModel] Detección fallida: ${detection.reason}")
                }
            }
        }
    }

    /**
     * Cierra la conexión BT limpiamente y detiene el escaneo si está activo.
     */
    fun disconnect() {
        viewModelScope.launch {
            println("[ScanViewModel] Desconectando…")
            if (_uiState.value.isScanning) stopScan()
            bluetoothManager.disconnect()
            protocolDetector = null
            isoTPHandler     = null
            _uiState.update {
                it.copy(
                    detectedProtocol   = null,
                    protocolSuggestion = null,
                    connectionState    = ConnectionState.Disconnected
                )
            }
        }
    }

    /**
     * Solicita una reconexión manual al último dispositivo conocido.
     */
    fun reconnect() {
        println("[ScanViewModel] Reconectando manualmente…")
        bluetoothManager.scheduleReconnection()
    }

    /**
     * Fuerza un protocolo específico cuando la auto-detección falla.
     *
     * @param protocol Protocolo a intentar manualmente.
     */
    fun tryProtocolManually(protocol: OBD2Protocol) {
        viewModelScope.launch {
            val detector = protocolDetector ?: run {
                setError("Sin conexión activa para probar protocolo.")
                return@launch
            }
            clearError()
            println("[ScanViewModel] Forzando protocolo: ${protocol.protocolName}…")
            val ok = detector.tryProtocol(protocol)
            if (ok) {
                _uiState.update {
                    it.copy(detectedProtocol = protocol, protocolSuggestion = null)
                }
            } else {
                setError("El protocolo ${protocol.protocolName} no respondió.")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ESCANEO
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inicia una sesión de escaneo continuo con las señales activas.
     *
     * @param sessionName Nombre descriptivo de la sesión (default = timestamp).
     */
    fun startScan(sessionName: String = "") {
        if (_uiState.value.isScanning) {
            println("[ScanViewModel] Ya escaneando, ignorado.")
            return
        }
        if (!isConnected()) {
            setError("No hay conexión activa.")
            return
        }

        viewModelScope.launch {
            println("[ScanViewModel] Iniciando sesión de escaneo…")

            // Crear sesión en Room
            val session = ScanSession(
                startTime = System.currentTimeMillis(),
                protocol  = _uiState.value.detectedProtocol?.protocolName ?: "Unknown",
                notes     = sessionName.ifBlank { "Sesión ${System.currentTimeMillis()}" },
                isActive  = true
            )
            repository.startSession(session)
            val activeSession = repository.getActiveSession()

            _uiState.update {
                it.copy(isScanning = true, sessionId = activeSession?.id, errorMessage = null)
            }

            startReadingLoop(activeSignals)
            startPersistenceLoop()
        }
    }

    /**
     * Detiene el escaneo, cierra la sesión y persiste los datos pendientes.
     */
    fun stopScan() {
        scanJob?.cancel()
        persistJob?.cancel()

        viewModelScope.launch {
            println("[ScanViewModel] Deteniendo escaneo…")
            flushReadingsBuffer()

            _uiState.value.sessionId?.let { sid ->
                val session = repository.getActiveSession()
                session?.let {
                    repository.endSession(it.copy(endTime = System.currentTimeMillis(), isActive = false))
                }
            }

            _uiState.update { it.copy(isScanning = false, sessionId = null) }
            println("[ScanViewModel] Sesión finalizada.")
        }
    }

    /**
     * Lee un PID OBD2 en modo single-shot y devuelve la respuesta cruda.
     *
     * @param mode Modo OBD2 en hex (ej. "01").
     * @param pid  PID en hex (ej. "0C" = RPM).
     * @return Respuesta en formato [OBD2Response].
     */
    suspend fun readPid(mode: String, pid: String): OBD2Response {
        val handler = isoTPHandler ?: return OBD2Response.Error("Sin conexión.")
        println("[ScanViewModel] readPid(mode=$mode pid=$pid)")
        val requestBytes = "$mode$pid".hexToBytes()
        val responseBytes = handler.sendAndReceive(requestBytes, DEFAULT_ECU_TX_ID, DEFAULT_ECU_RX_ID)
        return if (responseBytes.isEmpty()) OBD2Response.NoData
               else OBD2Response.Success(responseBytes, responseBytes.toHex())
    }

    /**
     * Lanza el loop de lectura continua para las [signals] dadas.
     *
     * Cadencia máxima: 10 Hz (100ms entre iteraciones completas).
     */
    fun readContinuous(signals: List<CanSignal>) {
        activeSignals = signals.ifEmpty { defaultObd2Signals() }
        if (_uiState.value.isScanning) {
            // Reiniciar el job con las nuevas señales
            scanJob?.cancel()
            startReadingLoop(activeSignals)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DBC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Importa un archivo .dbc desde la ruta indicada, lo persiste en Room
     * y actualiza el estado con las señales disponibles.
     *
     * @param filePath Ruta absoluta al archivo .dbc.
     */
    fun loadDatabase(filePath: String) {
        viewModelScope.launch {
            println("[ScanViewModel] Cargando DBC: $filePath")
            clearError()

            val importResult = dbcImporter.importFromFile(filePath)
            importResult.fold(
                onSuccess = { signalCount ->
                    val file = File(filePath)
                    val dbFile = DatabaseFile(
                        fileName    = file.name,
                        fileType    = FileType.DBC,
                        filePath    = filePath,
                        vehicleMake = null,
                        vehicleModel = null,
                        vehicleYear = null,
                        signalCount = signalCount,
                        importedAt  = System.currentTimeMillis(),
                        isActive    = true
                    )
                    repository.importDatabaseFile(dbFile)
                    val signals = repository.getSignals()
                    activeSignals = signals

                    _uiState.update {
                        it.copy(
                            selectedDatabase = dbFile,
                            dbcSignalCount   = signalCount,
                            errorMessage     = null
                        )
                    }
                    println("[ScanViewModel] DBC cargado: $signalCount señales.")
                },
                onFailure = { e ->
                    // DBC falló: usar PIDs genéricos OBD2 como fallback
                    activeSignals = defaultObd2Signals()
                    setError("Error al cargar DBC: ${e.message}. Usando PIDs genéricos OBD2.")
                    println("[ScanViewModel] Fallback a PIDs genéricos.")
                }
            )
        }
    }

    /**
     * Devuelve las señales disponibles: del DBC cargado (si existe) o los PIDs genéricos.
     */
    fun getAvailableSignals(): List<CanSignal> = activeSignals

    // ═══════════════════════════════════════════════════════════════════════════
    // DTCs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lee los códigos de falla del vehículo (Modo 03 OBD2) y actualiza la UI.
     */
    fun readDtcs() {
        viewModelScope.launch {
            if (!isConnected()) { setError("Sin conexión para leer DTCs."); return@launch }
            println("[ScanViewModel] Leyendo DTCs (Modo 03)…")

            val handler = isoTPHandler ?: return@launch
            val request = "03".hexToBytes()  // Modo 03 = Read Stored DTCs

            val responseBytes = handler.sendAndReceive(request, DEFAULT_ECU_TX_ID, DEFAULT_ECU_RX_ID)
            val rawResponse   = responseBytes.toHex()
            println("[ScanViewModel] Respuesta DTCs: $rawResponse")

            // Decodificar usando el DTCDecoder existente
            val decoded = DTCDecoder.decodeDTCs(rawResponse)
            val now = System.currentTimeMillis()

            val dtcEntities = decoded.map { dtc ->
                DtcCode(
                    code        = dtc.code,
                    description = dtc.description,
                    category    = dtc.code.firstOrNull() ?: 'P',
                    isActive    = true,
                    isPending   = false,
                    isPermanent = false,
                    firstSeen   = now,
                    lastSeen    = now,
                    clearedAt   = null,
                    sessionId   = _uiState.value.sessionId?.toString()
                )
            }

            dtcEntities.forEach { repository.saveDtc(it) }
            _uiState.update { it.copy(dtcs = dtcEntities) }
            println("[ScanViewModel] ${dtcEntities.size} DTC(s) encontrado(s).")
        }
    }

    /**
     * Borra un código DTC específico del vehículo (Modo 04) y lo marca como limpio en BD.
     *
     * @param code Código DTC a borrar (ej. "P0133").
     */
    fun clearDtc(code: String) {
        viewModelScope.launch {
            if (!isConnected()) { setError("Sin conexión para borrar DTC."); return@launch }
            println("[ScanViewModel] Borrando DTC $code…")

            // Modo 04: Clear/Reset Emission-Related Diagnostic Information
            val handler = isoTPHandler ?: return@launch
            val request = "04".hexToBytes()
            handler.sendAndReceive(request, DEFAULT_ECU_TX_ID, DEFAULT_ECU_RX_ID)

            // Marcar en BD
            repository.clearDtc(code, System.currentTimeMillis())

            // Actualizar estado local
            _uiState.update { state ->
                state.copy(dtcs = state.dtcs.filter { it.code != code })
            }
            println("[ScanViewModel] DTC $code borrado.")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVADOS – Loops internos
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Loop principal de lectura continua.
     * Itera sobre [signals], decodifica cada respuesta y alimenta el estado UI.
     * Throttle: máximo 10Hz (100ms entre ciclos completos).
     */
    private fun startReadingLoop(signals: List<CanSignal>) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            println("[ScanViewModel] Loop de lectura iniciado (${signals.size} señales).")
            while (isActive && _uiState.value.isScanning) {
                signals.forEach { signal ->
                    if (!isActive || !_uiState.value.isScanning) return@forEach

                    val response = readSignalSafe(signal)
                    response?.let { reading ->
                        appendReading(reading)
                        updateStats(reading)
                    }
                }
                delay(SCAN_LOOP_DELAY_MS)
            }
            println("[ScanViewModel] Loop de lectura terminado.")
        }
    }

    /** Intenta leer una señal via ISO-TP; devuelve null en caso de error. */
    private suspend fun readSignalSafe(signal: CanSignal): SignalReading? {
        return try {
            val handler  = isoTPHandler ?: return null
            val canIdInt = signal.canId.toIntOrNull(16) ?: DEFAULT_ECU_TX_ID
            val rxId     = if (signal.isExtended) canIdInt + 8 else DEFAULT_ECU_RX_ID
            val request  = buildRequest(signal)

            val responseBytes = handler.sendAndReceive(request, canIdInt, rxId)
            if (responseBytes.isEmpty()) return null

            val physicalValue = signal.scale * decodeRawValue(responseBytes) + signal.offset
            val sessionId     = _uiState.value.sessionId ?: 0L

            SignalReading(
                sessionId = sessionId,
                pid       = signal.canId,
                name      = signal.name,
                value     = physicalValue.toFloat(),
                unit      = signal.unit ?: "",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            println("[ScanViewModel] Error leyendo señal '${signal.name}': ${e.message}")
            null
        }
    }

    /** Construye el payload de solicitud ISO-TP para una señal. */
    private fun buildRequest(signal: CanSignal): ByteArray {
        // Formato OBD2 genérico: "01 XX" donde XX es el PID en hex
        val pidHex = signal.canId.uppercase().padStart(2, '0').takeLast(2)
        return "01$pidHex".hexToBytes()
    }

    /** Interpreta los primeros bytes de la respuesta como valor crudo. */
    private fun decodeRawValue(bytes: ByteArray): Long {
        if (bytes.isEmpty()) return 0L
        var result = 0L
        for (b in bytes.take(4)) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }

    /** Agrega la lectura al estado y al buffer de persistencia. */
    private fun appendReading(reading: SignalReading) {
        synchronized(readingsBuffer) { readingsBuffer.add(reading) }
        _uiState.update { state ->
            val updated = (listOf(reading) + state.readings).take(MAX_READINGS_IN_STATE)
            state.copy(readings = updated)
        }
    }

    /** Actualiza el mapa de estadísticas en tiempo real. */
    private fun updateStats(reading: SignalReading) {
        _uiState.update { state ->
            val current = state.stats[reading.name]
            val newStats = SignalStats(
                avg = current?.let { (it.avg + reading.value) / 2f } ?: reading.value,
                max = maxOf(current?.max ?: Float.MIN_VALUE, reading.value),
                min = minOf(current?.min ?: Float.MAX_VALUE, reading.value)
            )
            state.copy(stats = state.stats + (reading.name to newStats))
        }
    }

    /** Loop de persistencia: vuelca el buffer a Room cada [PERSIST_EVERY_MS]. */
    private fun startPersistenceLoop() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            while (isActive && _uiState.value.isScanning) {
                delay(PERSIST_EVERY_MS)
                flushReadingsBuffer()
            }
        }
    }

    private suspend fun flushReadingsBuffer() {
        val toSave = synchronized(readingsBuffer) {
            val copy = readingsBuffer.toList()
            readingsBuffer.clear()
            copy
        }
        if (toSave.isNotEmpty()) {
            toSave.forEach { repository.saveReading(it) }
            println("[ScanViewModel] ${toSave.size} lectura(s) persistidas en Room.")
        }
    }

    private fun setError(msg: String) {
        println("[ScanViewModel] ERROR: $msg")
        _uiState.update { it.copy(errorMessage = msg) }
    }

    private fun isConnected(): Boolean =
        _uiState.value.connectionState is ConnectionState.Connected

    /**
     * Señales OBD2 genéricas de fallback cuando no hay DBC cargado.
     * Cubre los PIDs estándar más comunes (Modo 01).
     */
    private fun defaultObd2Signals(): List<CanSignal> = listOf(
        obdSignal("0C", "RPM",              scale = 0.25, unit = "rpm"),
        obdSignal("0D", "Vehicle Speed",    scale = 1.0,  unit = "km/h"),
        obdSignal("05", "Coolant Temp",     scale = 1.0,  offset = -40.0, unit = "°C"),
        obdSignal("04", "Engine Load",      scale = 100.0 / 255.0, unit = "%"),
        obdSignal("11", "Throttle Pos",     scale = 100.0 / 255.0, unit = "%"),
        obdSignal("0F", "Intake Air Temp",  scale = 1.0,  offset = -40.0, unit = "°C"),
        obdSignal("10", "MAF Air Flow",     scale = 0.01, unit = "g/s"),
        obdSignal("0B", "MAP",              scale = 1.0,  unit = "kPa"),
        obdSignal("1F", "Runtime",          scale = 1.0,  unit = "s")
    )

    private fun obdSignal(
        pid: String,
        name: String,
        scale: Double = 1.0,
        offset: Double = 0.0,
        unit: String = ""
    ): CanSignal = CanSignal(
        name          = name,
        description   = "OBD2 Modo 01 PID 0x$pid",
        canId         = pid,
        isExtended    = false,
        startByte     = 0,
        startBit      = 0,
        bitLength     = 16,
        endianness    = com.obelus.data.local.model.Endian.BIG,
        scale         = scale.toFloat(),
        offset        = offset.toFloat(),
        signed        = offset < 0,
        unit          = unit,
        minValue      = null,
        maxValue      = null,
        source        = com.obelus.data.local.model.SignalSource.OBD_STANDARD,
        sourceFile    = null,
        category      = "OBD2_GENERIC"
    )

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        persistJob?.cancel()
        println("[ScanViewModel] onCleared() – jobs cancelados.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXPORTACIÓN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Exporta la sesión actual en el formato especificado.
     *
     * @param format  Formato deseado ([ReportFormat]: PDF, CSV, JSON, EXCEL).
     * @param context Contexto necesario para FileProvider y almacenamiento.
     * @param range   Rango de datos a exportar (default = sesión completa).
     * @return [Result] con el [File] generado o un error.
     */
    fun exportSession(
        format: ReportFormat,
        context: Context,
        range: ExportRange = ExportRange.LastSession
    ) {
        val state   = _uiState.value
        val session = ScanSession(
            id           = state.sessionId ?: 0L,
            startTime    = System.currentTimeMillis() - 60_000L,
            endTime      = System.currentTimeMillis(),
            notes        = null,
            averageSpeed = null
        )
        val readings = reportGenerator.filterReadings(state.readings, range)

        viewModelScope.launch {
            _isExporting.value = true
            _exportProgress.value = null
            try {
                reportGenerator.generate(
                    context     = context,
                    format      = format,
                    session     = session,
                    readings    = readings,
                    dtcs        = state.dtcs,
                    protocol    = state.detectedProtocol,
                    dbcFileName = state.selectedDatabase?.fileName
                ).collect { progress ->
                    _exportProgress.value = progress
                    if (progress is ExportProgress.Done) {
                        lastExportedFile = java.io.File(progress.uri.path ?: "")
                        println("[ScanViewModel] Export completado: ${progress.uri}")
                    }
                }
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * Comparte el último reporte exportado.
     *
     * @param context Contexto de la Activity para lanzar el Intent.
     * @return true si había un archivo para compartir.
     */
    fun shareLastReport(context: Context): Boolean {
        val file = lastExportedFile ?: return false
        if (!file.exists()) return false
        return when (file.extension.lowercase()) {
            "pdf"  -> { shareManager.sharePdf(context, file);  true }
            "csv"  -> { shareManager.shareCsv(context, file);  true }
            "json" -> { shareManager.shareJson(context, file); true }
            else   -> false
        }
    }

    /** Lista las señales disponibles para el modal AddSignalDialog. */
    fun getAvailableSignals(): List<CanSignal> = activeSignals

    // ═══════════════════════════════════════════════════════════════════════════
    // TESTS DE ACTUADORES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retorna la lista de tests de actuador disponibles en el catálogo.
     * Filtrado futuro por protocolo detectado puede hacerse aquí.
     */
    fun getAvailableActuatorTests(): List<ActuatorTest> =
        actuatorRepository.getAvailableTests()

    /**
     * Valida las condiciones de seguridad usando el estado actual del escaneo.
     *
     * @return [SafetyResult.Safe] si es seguro ejecutar, [SafetyResult.Unsafe] con detalle.
     */
    fun validateSafetyConditions(): SafetyResult {
        val result = safetyChecker.validate(_uiState.value)
        safetyChecker.printLog(result)
        return result
    }

    /**
     * Ejecuta un test de actuador de forma segura.
     *
     * Flujo:
     *  1. Valida condiciones de seguridad.
     *  2. Si hay violaciones en tests HIGH/MEDIUM, emite [ActuatorResult.Danger].
     *  3. Ejecuta el test vía [ActuatorTestRepository] y mapea [TestResult] → [ActuatorResult].
     *
     * @param testId Id del test a ejecutar (debe existir en [getAvailableActuatorTests]).
     * @return Flow de [ActuatorResult].
     */
    fun executeActuatorTest(testId: String): Flow<ActuatorResult> = flow {
        val test = actuatorRepository.getAvailableTests()
            .firstOrNull { it.id == testId }
            ?: run {
                emit(ActuatorResult.Failed(
                    test = ActuatorTest(
                        id = testId, name = testId, description = "",
                        command = "",
                        category = com.obelus.domain.model.ActuatorCategory.ENGINE
                    ),
                    reason = "Test '$testId' no encontrado en el catálogo"
                ))
                return@flow
            }

        // ── Verificar seguridad para tests MEDIUM o HIGH ───────────────────────────
        if (test.dangerLevel != DangerLevel.LOW) {
            val safetyResult = safetyChecker.validate(_uiState.value)
            if (safetyResult is SafetyResult.Unsafe) {
                emit(ActuatorResult.Danger(test, safetyResult.violations))
                return@flow
            }
        }

        // ── Emitir Running inicial ───────────────────────────────────────────────────
        emit(ActuatorResult.Running(test, "Iniciando test '${test.name}'…", 0L, 0f))
        println("[ScanViewModel] Ejecutando actuador: ${test.id} (${test.dangerLevel.label})")

        // ── Necesita conexión activa ───────────────────────────────────────────────
        if (!isConnected()) {
            emit(ActuatorResult.NotSupported(test, "No hay conexión activa con el adaptador"))
            return@flow
        }

        // ── Delegar al repositorio de actuadores ──────────────────────────────
        val connection = isoTPHandler?.elmConnection ?: run {
            emit(ActuatorResult.NotSupported(test, "Conexión ELM no disponible"))
            return@flow
        }
        actuatorRepository.executeTest(testId, connection).collect { testResult ->
            val mapped: ActuatorResult = when (testResult) {
                is com.obelus.domain.model.TestResult.Progress -> ActuatorResult.Running(
                    test, testResult.message, testResult.elapsedMs,
                    (testResult.elapsedMs / 10_000f).coerceIn(0f, 0.99f)
                )
                is com.obelus.domain.model.TestResult.Success -> ActuatorResult.Success(
                    test, testResult.rawResponse, testResult.parsedValue, testResult.elapsedMs
                )
                is com.obelus.domain.model.TestResult.Failure -> ActuatorResult.Failed(
                    test, testResult.reason, testResult.rawResponse
                )
                is com.obelus.domain.model.TestResult.Timeout -> ActuatorResult.Timeout(test, 10_000L)
            }
            emit(mapped)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATOS DE FABRICANTE (Modo 21/22)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Detecta el fabricante decodificando el VIN. */
    fun detectManufacturer(vin: String): Manufacturer =
        vinDecoder.detectManufacturer(vin)

    /** Carga los datos de la base de datos para el fabricante/modelo/año dados. */
    fun loadManufacturerData(
        manufacturer: Manufacturer,
        model: String? = null,
        year: Int? = null
    ) {
        val data = manufacturerDataRepo.getSupportedData(manufacturer, model, year)
        _manufacturerState.value = _manufacturerState.value.copy(
            detectedManufacturer = manufacturer,
            detectedYear = year,
            availableData = data,
            readings = emptyMap()
        )
        println("[ScanViewModel] Fabricante cargado: ${manufacturer.displayName} - ${data.size} PIDs")
    }

    /** Carga los datos de fabricante decodificando el VIN. */
    fun loadManufacturerDataForVin(vin: String) {
        if (vin.length < 3) return
        val vinInfo = vinDecoder.decodeVin(vin)
        _manufacturerState.value = _manufacturerState.value.copy(lastVin = vin)
        loadManufacturerData(vinInfo.manufacturer, year = vinInfo.modelYear)
    }

    /** Lee un valor de fabricante específico desde el ECU. */
    fun readManufacturerValue(dataId: String): Flow<ManufacturerReading> = flow {
        if (!isConnected()) {
            val data = com.obelus.manufacturer.ManufacturerDatabase.findById(dataId) ?: return@flow
            emit(ManufacturerReading.NotSupported(data, "Sin conexion activa"))
            return@flow
        }
        val handler = isoTPHandler ?: run {
            val data = com.obelus.manufacturer.ManufacturerDatabase.findById(dataId) ?: return@flow
            emit(ManufacturerReading.Error(data, "ISO-TP handler no inicializado"))
            return@flow
        }
        val sender: com.obelus.manufacturer.ElmCommandSender = { cmd ->
            handler.elmConnection.send(cmd)
        }
        val result = manufacturerDataRepo.readData(dataId, sender)
        _manufacturerState.value = _manufacturerState.value.copy(
            readings = _manufacturerState.value.readings + (dataId to result)
        )
        emit(result)
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    /** Inicia scan completo de todos los PIDs disponibles para el fabricante actual. */
    fun startManufacturerScan() {
        val state   = _manufacturerState.value
        val mfr     = state.detectedManufacturer ?: return
        val handler = isoTPHandler ?: return
        manufacturerScanJob?.cancel()
        _manufacturerState.value = state.copy(isScanning = true)
        manufacturerScanJob = viewModelScope.launch {
            val sender: com.obelus.manufacturer.ElmCommandSender = { cmd ->
                handler.elmConnection.send(cmd)
            }
            manufacturerDataRepo.scanAll(mfr, year = state.detectedYear, sender = sender)
                .collect { reading ->
                    val dataId = when (reading) {
                        is ManufacturerReading.Value        -> reading.data.dataId
                        is ManufacturerReading.NotSupported -> reading.data.dataId
                        is ManufacturerReading.Error        -> reading.data.dataId
                    }
                    _manufacturerState.value = _manufacturerState.value.copy(
                        readings = _manufacturerState.value.readings + (dataId to reading)
                    )
                }
            _manufacturerState.value = _manufacturerState.value.copy(isScanning = false)
            println("[ScanViewModel] Scan de fabricante completado.")
        }
    }

    /** Detiene el scan de fabricante en curso. */
    fun stopManufacturerScan() {
        manufacturerScanJob?.cancel()
        _manufacturerState.value = _manufacturerState.value.copy(isScanning = false)
        println("[ScanViewModel] Scan de fabricante detenido.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRABACIÓN Y ANÁLISIS DE GRÁFICOS (Tiempo Real)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inicia la grabación de datos de gráfico para las señales indicadas.
     * Cada 50ms lee las N lecturas más recientes del estado de UI,
     * las agrega al [ChartBufferRegistry] y emite el snapshot en [chartData].
     * Cada 2 s ejecuta [ChartAnalyzer] en Dispatchers.Default y emite
     * los eventos detectados en [chartEvents].
     *
     * @param signals Lista de signalId (PID) a monitorizar.
     */
    fun startChartRecording(signals: List<String>) {
        if (signals.isEmpty()) return
        chartRecordingJob?.cancel()
        chartActiveSignals.clear()
        chartActiveSignals.addAll(signals)
        println("[ScanViewModel] Iniciando grabación de gráfico: $signals")

        chartRecordingJob = viewModelScope.launch {
            var analysisTick = 0

            while (isActive) {
                kotlinx.coroutines.delay(50L)     // 20 Hz máx (UI safe)
                val nowMs  = System.currentTimeMillis()

                // Leer lecturas actuales del estado
                val currentReadings = _uiState.value.readings

                for (sigId in chartActiveSignals) {
                    val reading = currentReadings.filter { it.pid.equals(sigId, ignoreCase = true) }
                        .maxByOrNull { it.timestamp } ?: continue
                    val buf = chartRegistry.getOrCreate(sigId)
                    val isAlert = reading.value < 0f  // heuristic; replace with actual threshold
                    buf.addPoint(reading.timestamp, reading.value, "", isAlert)
                }

                // Publicar snapshot downsampled cada frame
                val snapshot = chartActiveSignals.associateWith { sigId ->
                    chartRegistry.getOrCreate(sigId).getDownsampled(500)
                }
                _chartData.value = snapshot

                // Análisis cada 2 s (40 ticks)
                analysisTick++
                if (analysisTick % 40 == 0) {
                    val events = mutableListOf<ChartEvent>()
                    for ((sigId, points) in snapshot) {
                        if (points.size < 10) continue
                        val detected = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            ChartAnalyzer.analyze(points)
                        }
                        events.addAll(detected)
                    }
                    if (events.isNotEmpty()) {
                        _chartEvents.value = (_chartEvents.value + events).takeLast(50)
                    }
                }
            }
        }
    }

    /** Detiene la grabación de gráfico y limpia el registry. */
    fun stopChartRecording() {
        chartRecordingJob?.cancel()
        chartRecordingJob = null
        chartActiveSignals.clear()
        viewModelScope.launch { chartRegistry.clear() }
        _chartData.value  = emptyMap()
        println("[ScanViewModel] Grabación de gráfico detenida.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FREEZE FRAME (Modo 02)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lee el freeze frame del ECU para un DTC específico.
     * Emite [FreezeFrameResult] y actualiza [freezeFrames] al finalizar.
     *
     * @param dtcCode DTC a consultar (ej. "P0420").
     */
    fun readFreezeFrame(dtcCode: String): Flow<FreezeFrameResult> = flow {
        val handler = isoTPHandler
        if (handler == null) {
            emit(FreezeFrameResult.ReadError(dtcCode, "Sin conexión al ECU"))
            return@flow
        }
        val sender: com.obelus.freezeframe.ElmSender = { cmd ->
            handler.elmConnection.send(cmd)
        }
        val sessionId = _uiState.value.sessionId?.toString() ?: "live"
        freezeFrameRepo.requestFreezeFrame(dtcCode, sessionId, sender).collect { result ->
            emit(result)
            if (result is FreezeFrameResult.Success) {
                _freezeFrames.value = freezeFrameRepo.getAllFreezeFrames()
                println("[ScanViewModel] Freeze frame guardado para $dtcCode")
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    /**
     * Analiza el freeze frame más reciente para un DTC.
     * Retorna el [AnalysisResult] o null si no hay freeze frame guardado.
     */
    suspend fun analyzeFreezeFrame(dtcCode: String): AnalysisResult? =
        freezeFrameRepo.analyze(dtcCode)

    /**
     * Captura automáticamente el freeze frame cuando se detecta un DTC nuevo.
     * Llamar desde el loop de escaneo de DTCs cuando se detecta un nuevo DTC.
     * No bloquea — lanza una corrutina en segundo plano.
     *
     * @param dtcCode DTC recién detectado.
     */
    fun autoCaptureFreezeFrame(dtcCode: String) {
        // Solo capturar si es un DTC nunca visto en esta sesión
        if (dtcCode in _seenDtcCodes) return
        _seenDtcCodes += dtcCode
        println("[ScanViewModel] Auto-capturando freeze frame para DTC nuevo: $dtcCode")
        viewModelScope.launch {
            readFreezeFrame(dtcCode).collect { result ->
                when (result) {
                    is FreezeFrameResult.Success    -> println("[ScanViewModel] ✓ Freeze frame auto-capturado: $dtcCode")
                    is FreezeFrameResult.NotAvailable -> println("[ScanViewModel] ⚠ Sin freeze frame disponible: $dtcCode")
                    is FreezeFrameResult.ReadError  -> println("[ScanViewModel] ✗ Error auto-captura FF $dtcCode: ${result.message}")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BALANCE DE CILINDROS (Modo 06)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lanza el test de balance de cilindros.
     * Actualiza [cylinderBalance] y [misfireData] al finalizar.
     * Emite [CylinderTestResult.Progress] en tiempo real; la UI debe colectar.
     *
     * @param cylinderCount Número de cilindros del motor (default: auto-detectado o 4).
     */
    fun runCylinderBalanceTest(cylinderCount: Int = 4) {
        val conn = isoTPHandler?.elmConnection
        if (conn == null) {
            println("[ScanViewModel] runCylinderBalanceTest: sin conexión")
            return
        }
        cylinderTestJob?.cancel()
        println("[ScanViewModel] Iniciando test de balance de cilindros ($cylinderCount cil.)")

        val rpm      = _uiState.value.readings.firstOrNull { it.pid == "0C" }?.value
        val coolant  = _uiState.value.readings.firstOrNull { it.pid == "05" }?.value

        cylinderTestJob = viewModelScope.launch {
            val sender: CylinderElmSender = { cmd -> conn.send(cmd) }
            cylinderTestRepo.runCylinderBalanceTest(
                cylinderCount = cylinderCount,
                rpm           = rpm,
                coolantTemp   = coolant,
                sender        = sender
            ).collect { result ->
                when (result) {
                    is CylinderTestResult.BalanceReady  -> {
                        _cylinderBalance.value = result.balance
                        println("[ScanViewModel] Balance listo: ${result.balance.imbalancePercent}% desvío")
                    }
                    is CylinderTestResult.DiagnosisReady -> {
                        println("[ScanViewModel] Diagnóstico: ${result.diagnosis.summary}")
                    }
                    is CylinderTestResult.NotSupported  -> {
                        println("[ScanViewModel] Balance no soportado: ${result.reason}")
                    }
                    is CylinderTestResult.Error         -> {
                        println("[ScanViewModel] Error balance: ${result.message}")
                    }
                    is CylinderTestResult.Progress -> { /* UI colecta aparte */ }
                    CylinderTestResult.EngineNotReady -> {
                        println("[ScanViewModel] Motor no listo para balance")
                    }
                }
            }
        }
    }

    /**
     * Analiza el resultado de balance y retorna el diagnóstico.
     * Para ser llamado desde la UI después de obtener [cylinderBalance].
     */
    fun identifyWeakCylinder(balance: CylinderBalance): Diagnosis =
        cylinderTestRepo.identifyWeakCylinder(balance)

    /**
     * Auto-detecta desbalance durante ralentí estable.
     * Llamar cada segundo desde el loop de escaneo.
     * Dispara el test automáticamente al llegar a 30 segundos de ralentí estable.
     */
    fun detectCylinderImbalanceIfIdle() {
        val rpm     = _uiState.value.readings.firstOrNull { it.pid == "0C" }?.value ?: return
        val coolant = _uiState.value.readings.firstOrNull { it.pid == "05" }?.value ?: return
        val prec    = cylinderTestRepo.checkPreconditions(rpm, coolant, idleStableSecs)

        val idleNow = rpm in 550f..1100f
        if (idleNow) idleStableSecs++ else idleStableSecs = 0

        // Solo lanzar si se cumplen todas las condiciones y aún no hay resultado
        if (prec.isRpmStable && prec.isEngineWarm && _cylinderBalance.value == null) {
            println("[ScanViewModel] Auto-iniciando balance de cilindros (idle ${idleStableSecs}s)")
            idleStableSecs = 0   // reset para no disparar de nuevo hasta el próximo ciclo
            runCylinderBalanceTest()
        }
    }

    override fun onCleared() {
        super.onCleared()
        chartRecordingJob?.cancel()
        manufacturerScanJob?.cancel()
        cylinderTestJob?.cancel()
    }
}



// ─── ManufacturerState ────────────────────────────────────────────────────────

/** Estado del modulo de datos de fabricante (Modo 21/22). */
data class ManufacturerState(
    val detectedManufacturer: Manufacturer? = null,
    val detectedYear: Int? = null,
    val lastVin: String = "",
    val availableData: List<ManufacturerData> = emptyList(),
    val readings: Map<String, ManufacturerReading> = emptyMap(),
    val isScanning: Boolean = false
)


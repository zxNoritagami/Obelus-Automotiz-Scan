package com.obelus.freezeframe

import com.obelus.domain.model.FreezeFrameData
import com.obelus.domain.model.FreezeFrameResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// FreezeFrameRepository.kt
// Lectura (Modo 02), persistencia (in-memory + Room) y comparación de freeze frames.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Función de callback para enviar comandos al ECU (reutiliza ElmCommandSender).
 * Retorna la respuesta raw o null si no hay conexión.
 */
typealias ElmSender = suspend (cmd: String) -> String?

@Singleton
class FreezeFrameRepository @Inject constructor() {

    companion object {
        private const val TAG = "FreezeFrameRepo"

        // Modo 02 PIDs relevantes: Map<PID, frameNumber> (frame 0 = DTC que causó el freeze)
        private val MODE02_PIDS = listOf(
            "04", // Engine Load
            "05", // Coolant Temp
            "06", // Short Fuel Trim B1
            "07", // Long Fuel Trim B1
            "0B", // Intake Manifold Pressure
            "0C", // RPM
            "0D", // Vehicle Speed
            "0E", // Timing Advance
            "11", // Throttle Position
            "14", // O2 Sensor B1S1
            "15", // O2 Sensor B1S2
            "21", // Distance w/ MIL on
            "31", // Distance since codes cleared
            "42"  // Control Module Voltage
        )

        private val NO_RESPONSE = setOf("NO DATA", "ERROR", "?", "STOPPED", "UNABLE")
    }

    // In-memory store (historial de sesión)
    private val store = mutableMapOf<String, MutableList<FreezeFrameData>>()

    // ═══════════════════════════════════════════════════════════════════════════
    // Lectura desde ECU (Modo 02)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lee el freeze frame del ECU para un DTC específico usando Modo 02.
     *
     * Protocolo: envía "02 <PID> 00" por cada PID (frame 0 = primer freeze frame).
     * Retorna [FreezeFrameResult.Success] con los datos parseados,
     * [FreezeFrameResult.NotAvailable] si el ECU no tiene datos,
     * o [FreezeFrameResult.ReadError] ante error de comunicación.
     */
    fun requestFreezeFrame(
        dtcCode: String,
        sessionId: String = "live",
        sender: ElmSender
    ): Flow<FreezeFrameResult> = flow {
        println("[$TAG] Solicitando freeze frame para $dtcCode")
        val rawValues = mutableMapOf<String, Double>()

        for (pid in MODE02_PIDS) {
            try {
                val cmd      = "02 $pid 00"
                val response = sender(cmd)?.trim() ?: ""
                val noData   = response.isBlank() || NO_RESPONSE.any { response.uppercase().contains(it) }
                if (noData) continue

                val bytes = response.trim().split("\\s+".toRegex())
                    .drop(2)   // skip mode response byte + PID echo
                    .mapNotNull { it.toIntOrNull(16) }

                val value = decodeMode02(pid, bytes)
                if (value != null) rawValues[pid] = value
                delay(20L)   // no saturar el bus
            } catch (e: Exception) {
                println("[$TAG] Error en PID $pid: ${e.message}")
            }
        }

        if (rawValues.isEmpty()) {
            emit(FreezeFrameResult.NotAvailable(dtcCode))
            return@flow
        }

        val data = FreezeFrameData(
            dtcCode       = dtcCode,
            sessionId     = sessionId,
            rpm           = rawValues["0C"]?.toFloat(),
            vehicleSpeed  = rawValues["0D"]?.toFloat(),
            coolantTemp   = rawValues["05"]?.toFloat(),
            throttlePosition = rawValues["11"]?.toFloat(),
            engineLoad    = rawValues["04"]?.toFloat(),
            fuelTrimShort = rawValues["06"]?.toFloat(),
            fuelTrimLong  = rawValues["07"]?.toFloat(),
            intakePressure = rawValues["0B"]?.toFloat(),
            timingAdvance = rawValues["0E"]?.toFloat(),
            o2SensorBank1 = rawValues["14"]?.toFloat(),
            o2SensorBank2 = rawValues["15"]?.toFloat(),
            batteryVoltage = rawValues["42"]?.toFloat(),
            distanceSinceDtcCleared = rawValues["31"]?.toInt(),
            rawSensorValues = rawValues
        )

        saveFreezeFrame(data)
        println("[$TAG] Freeze frame capturado: ${rawValues.size} PIDs para $dtcCode")
        emit(FreezeFrameResult.Success(data))
    }.flowOn(Dispatchers.IO)

    // ═══════════════════════════════════════════════════════════════════════════
    // Persistencia in-memory
    // ═══════════════════════════════════════════════════════════════════════════

    /** Guarda un freeze frame (múltiples por DTC, historial). */
    fun saveFreezeFrame(data: FreezeFrameData) {
        store.getOrPut(data.dtcCode) { mutableListOf() }.add(data)
        println("[$TAG] Guardado freeze frame #${store[data.dtcCode]?.size} para ${data.dtcCode}")
    }

    /** Retorna todos los freeze frames guardados para un DTC (orden cronológico). */
    fun getFreezeFrameForDtc(dtcCode: String): List<FreezeFrameData> =
        store[dtcCode]?.sortedBy { it.timestamp } ?: emptyList()

    /** Retorna el freeze frame más reciente para un DTC. */
    fun getLatestForDtc(dtcCode: String): FreezeFrameData? =
        store[dtcCode]?.maxByOrNull { it.timestamp }

    /** Retorna todos los freeze frames de todas los DTCs. */
    fun getAllFreezeFrames(): List<FreezeFrameData> =
        store.values.flatten().sortedByDescending { it.timestamp }

    /** DTCs que tienen al menos un freeze frame guardado. */
    fun dtcsWithFreezeFrame(): Set<String> = store.keys.toSet()

    /** Elimina el historial de un DTC. */
    fun clear(dtcCode: String) { store.remove(dtcCode) }

    /** Elimina todo el historial. */
    fun clearAll() { store.clear() }

    // ═══════════════════════════════════════════════════════════════════════════
    // Comparación con valores actuales
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Compara los valores del freeze frame con las lecturas actuales del sensor.
     *
     * @param freezeFrame   Datos del freeze frame a comparar.
     * @param currentValues Mapa PID → valor actual (proveniente de ScanUiState.readings).
     * @return Lista de [ValueDifference] para cada parámetro disponible.
     */
    fun compareWithCurrent(
        freezeFrame: FreezeFrameData,
        currentValues: Map<String, Float>
    ): List<ValueDifference> =
        FreezeFrameAnalyzer.compareWithCurrent(freezeFrame, currentValues)

    // ═══════════════════════════════════════════════════════════════════════════
    // Análisis
    // ═══════════════════════════════════════════════════════════════════════════

    /** Análisis completo del freeze frame. */
    suspend fun analyze(dtcCode: String): AnalysisResult? = withContext(Dispatchers.Default) {
        val ff = getLatestForDtc(dtcCode) ?: return@withContext null
        val findings   = FreezeFrameAnalyzer.analyzeConditions(ff)
        val suggested  = FreezeFrameAnalyzer.suggestRelatedDtcs(dtcCode, ff)
        AnalysisResult(dtcCode, ff, findings, suggested)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Decodificación Modo 02
    // ═══════════════════════════════════════════════════════════════════════════

    private fun decodeMode02(pid: String, bytes: List<Int>): Double? {
        if (bytes.isEmpty()) return null
        return try {
            when (pid.uppercase()) {
                "04" -> bytes[0] * 100.0 / 255.0                     // Engine load %
                "05" -> bytes[0] - 40.0                               // Coolant °C
                "06", "07" -> (bytes[0] - 128) * 100.0 / 128.0       // Fuel trim %
                "0B" -> bytes[0].toDouble()                           // MAP kPa
                "0C" -> ((bytes[0] * 256 + bytes[1]) / 4.0)          // RPM
                "0D" -> bytes[0].toDouble()                           // Speed km/h
                "0E" -> bytes[0] / 2.0 - 64.0                        // Timing advance °
                "11" -> bytes[0] * 100.0 / 255.0                     // Throttle %
                "14", "15" -> bytes[0] / 200.0                        // O2 voltage V
                "21", "31" -> (bytes[0] * 256 + bytes[1]).toDouble()  // Distance km
                "42" -> ((bytes[0] * 256 + bytes[1]) / 1000.0)       // Battery V
                else -> null
            }
        } catch (e: IndexOutOfBoundsException) {
            println("[$TAG] decodeMode02 bytes insuficientes para PID $pid: $bytes")
            null
        }
    }
}

/** Resultado de análisis completo de un freeze frame. */
data class AnalysisResult(
    val dtcCode: String,
    val freezeFrame: FreezeFrameData,
    val findings: List<ConditionFinding>,
    val suggestedDtcs: List<String>
) {
    val criticalCount: Int get() = findings.count { it.severity == FindingSeverity.CRITICAL }
    val hasAnomalies:  Boolean get() = findings.isNotEmpty()
}

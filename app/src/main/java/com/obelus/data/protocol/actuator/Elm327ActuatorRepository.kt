package com.obelus.data.protocol.actuator

import android.util.Log
import com.obelus.domain.model.ActuatorCategory
import com.obelus.domain.model.ActuatorTest
import com.obelus.domain.model.DangerLevel
import com.obelus.domain.model.TestResult
import com.obelus.protocol.ElmConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * ELM327-based implementation of [ActuatorTestRepository].
 *
 * Each test sends one or more OBD2 Mode 01 or AT commands and interprets the ECU reply.
 * The test definition catalogue is hardcoded here. A future version can load from a JSON
 * asset to support protocol-specific overrides.
 *
 * Safety note: Mode 09 and Mode 0A commands are read-only.
 * Write-access actuator commands (Mode 08/09 Hyundai, Mode 30 etc.) are intentionally
 * not implemented here — they require security access (0x27/0x29) and are out of scope
 * for a general-purpose scanner.
 */
@Singleton
class Elm327ActuatorRepository @Inject constructor() : ActuatorTestRepository {

    companion object {
        private const val TAG = "Elm327ActuatorRepo"
        private const val TEST_TIMEOUT_MS = 10_000L
    }

    private @Volatile var cancelled = false

    // ── Test catalogue ────────────────────────────────────────────────────────

    private val catalogue: List<ActuatorTest> = listOf(
        // ENGINE
        ActuatorTest(
            id          = "RPM_READ",
            name        = "Lectura de RPM del motor",
            description = "Lee las RPM actuales del motor vía PID 010C. Útil para verificar la señal del sensor CKP y que el ECU responde.",
            command     = "010C",
            expectedResponse = "41 0C",
            category    = ActuatorCategory.ENGINE
        ),
        ActuatorTest(
            id          = "ENGINE_LOAD",
            name        = "Carga calculada del motor",
            description = "Solicita el porcentaje de carga calculada del motor (PID 0104). Valores bajos en ralentí indican motor sano.",
            command     = "0104",
            expectedResponse = "41 04",
            category    = ActuatorCategory.ENGINE
        ),
        ActuatorTest(
            id          = "THROTTLE_POS",
            name        = "Posición de la mariposa",
            description = "Lee la apertura actual de la mariposa (PID 0111). Con acelerador en reposo debe leer ~0-15%.",
            command     = "0111",
            expectedResponse = "41 11",
            category    = ActuatorCategory.ENGINE
        ),
        ActuatorTest(
            id          = "DTC_STATUS",
            name        = "Estado de diagnóstico (MIL)",
            description = "Lee el estado del módulo de diagnóstico y si la luz MIL está activa. Muestra número de DTCs pendientes.",
            command     = "0101",
            expectedResponse = "41 01",
            category    = ActuatorCategory.ENGINE
        ),
        ActuatorTest(
            id          = "FREEZE_FRAME",
            name        = "Lectura de freeze frame (cilindro 1)",
            description = "Solicita el freeze frame del cilindro 1. Captura los valores del motor en el momento que se generó el último DTC.",
            command     = "0201",
            expectedResponse = "42",
            category    = ActuatorCategory.ENGINE
        ),
        // FUEL
        ActuatorTest(
            id          = "FUEL_PRESSURE",
            name        = "Presión del sistema de combustible",
            description = "Lee la presión del riel de combustible (PID 010A). Anomalías indican bomba débil o regulador defectuoso.",
            command     = "010A",
            expectedResponse = "41 0A",
            safetyWarning = "Este test lee la presión del combustible. Asegúrate de que el motor esté sin fugas visibles antes de proceder.",
            category    = ActuatorCategory.FUEL,
            dangerLevel = DangerLevel.HIGH
        ),
        ActuatorTest(
            id          = "SHORT_FUEL_TRIM_B1",
            name        = "Corrección de mezcla corto plazo (banco 1)",
            description = "Lee el trim de combustible a corto plazo banco 1 (PID 0106). Valores fuera de ±10% indican problema de mezcla.",
            command     = "0106",
            expectedResponse = "41 06",
            category    = ActuatorCategory.FUEL
        ),
        ActuatorTest(
            id          = "LONG_FUEL_TRIM_B1",
            name        = "Corrección de mezcla largo plazo (banco 1)",
            description = "Lee el trim de combustible a largo plazo banco 1 (PID 0107). Valores fuera de ±15% indican envejecimiento del sistema.",
            command     = "0107",
            expectedResponse = "41 07",
            category    = ActuatorCategory.FUEL
        ),
        ActuatorTest(
            id          = "MAF_SENSOR",
            name        = "Caudal del sensor MAF",
            description = "Lee el flujo másico de aire (PID 0110). En ralentí: ~2-7 g/s. Acelerando: proporcional al pedal.",
            command     = "0110",
            expectedResponse = "41 10",
            category    = ActuatorCategory.FUEL
        ),
        // COOLING
        ActuatorTest(
            id          = "COOLANT_TEMP",
            name        = "Temperatura del refrigerante (ECT)",
            description = "Lee la temperatura del motor en tiempo real (PID 0105). Temperatura normal de operación: 85–105 °C.",
            command     = "0105",
            expectedResponse = "41 05",
            safetyWarning = "Verifica que el nivel de refrigerante sea correcto antes de ejecutar este test. No abras el tapón del radiador con el motor caliente.",
            category    = ActuatorCategory.COOLING,
            dangerLevel = DangerLevel.MEDIUM
        ),
        ActuatorTest(
            id          = "INTAKE_AIR_TEMP",
            name        = "Temperatura del aire de admisión (IAT)",
            description = "Lee la temperatura del aire entrante al motor (PID 010F). Compara con temperatura ambiente para validar sensor IAT.",
            command     = "010F",
            expectedResponse = "41 0F",
            category    = ActuatorCategory.COOLING
        ),
        ActuatorTest(
            id          = "AMBIENT_AIR_TEMP",
            name        = "Temperatura del aire exterior",
            description = "Lee la temperatura exterior reportada por el ECU (PID 0146). Permite comparar con temperatura de admisión.",
            command     = "0146",
            expectedResponse = "41 46",
            category    = ActuatorCategory.COOLING
        ),
        // ELECTRICAL
        ActuatorTest(
            id          = "BATTERY_VOLTAGE",
            name        = "Voltaje del sistema eléctrico",
            description = "Lee el voltaje de la batería/alternador reportado por el ECU (PID 0142). Rango normal: 13.5–14.5 V en marcha.",
            command     = "0142",
            expectedResponse = "41 42",
            category    = ActuatorCategory.ELECTRICAL
        ),
        ActuatorTest(
            id          = "OBD_STANDARDS",
            name        = "Estándares OBD compatibles",
            description = "Solicita con qué protocolo OBD2 cumple el vehículo (PID 011C). Útil para diagnosticar problemas de comunicación.",
            command     = "011C",
            expectedResponse = "41 1C",
            category    = ActuatorCategory.ELECTRICAL
        ),
        ActuatorTest(
            id          = "RUNTIME",
            name        = "Tiempo de marcha desde arranque",
            description = "Lee el tiempo transcurrido desde el arranque del motor (PID 011F). Útil para pruebas de ciclo de conducción.",
            command     = "011F",
            expectedResponse = "41 1F",
            category    = ActuatorCategory.ELECTRICAL
        ),
        // EMISSIONS
        ActuatorTest(
            id          = "O2_SENSOR_B1S1",
            name        = "Sensor O2 banco 1, sensor 1 (aguas arriba)",
            description = "Lee la tensión del sensor lambda antes del catalizador (PID 0114). Debe oscilar entre 0.1 V y 0.9 V activamente.",
            command     = "0114",
            expectedResponse = "41 14",
            category    = ActuatorCategory.EMISSIONS
        ),
        ActuatorTest(
            id          = "O2_SENSOR_B1S2",
            name        = "Sensor O2 banco 1, sensor 2 (aguas abajo)",
            description = "Lee la tensión del sensor lambda detrás del catalizador (PID 0115). Debe ser estable si el catalizador funciona bien.",
            command     = "0115",
            expectedResponse = "41 15",
            category    = ActuatorCategory.EMISSIONS
        ),
        ActuatorTest(
            id          = "EVAP_PURGE",
            name        = "Porcentaje de apertura válvula EVAP (purga)",
            description = "Lee el ciclo de trabajo de la válvula de purga del cánister EVAP (PID 0145). 0% = cerrada, 100% = abierta.",
            command     = "0145",
            expectedResponse = "41 45",
            safetyWarning = "El test de purga EVAP puede activar olores a combustible si el cánister está saturado. Realízalo en área ventilada.",
            category    = ActuatorCategory.EMISSIONS,
            dangerLevel = DangerLevel.HIGH
        ),
        ActuatorTest(
            id          = "EGR_COMMANDED",
            name        = "Apertura comandada de la válvula EGR",
            description = "Lee el porcentaje de apertura que el ECU ha ordenado a la válvula EGR (PID 012C). Útil para validar control EGR.",
            command     = "012C",
            expectedResponse = "41 2C",
            category    = ActuatorCategory.EMISSIONS
        ),
        ActuatorTest(
            id          = "CATALYST_TEMP",
            name        = "Temperatura del catalizador banco 1",
            description = "Lee la temperatura del catalizador banco 1 sensor 1 (PID 013C). Rango normal de operación: 400–900 °C.",
            command     = "013C",
            expectedResponse = "41 3C",
            safetyWarning = "El catalizador puede alcanzar temperaturas superiores a 800°C. No toques el escape ni trabajes debajo del vehículo durante este test.",
            category    = ActuatorCategory.EMISSIONS,
            dangerLevel = DangerLevel.HIGH
        )
    )

    // ── Interface implementation ──────────────────────────────────────────────

    override fun getAvailableTests(): List<ActuatorTest> = catalogue

    override fun executeTest(testId: String, connection: ElmConnection): Flow<TestResult> = flow {
        cancelled = false
        val startMs = System.currentTimeMillis()

        val test = catalogue.firstOrNull { it.id == testId }
        if (test == null) {
            emit(TestResult.Failure("Test '$testId' no encontrado en el catálogo"))
            return@flow
        }

        if (!connection.isConnected()) {
            emit(TestResult.Failure("No hay conexión activa con el adaptador OBD2"))
            return@flow
        }

        emit(TestResult.Progress("Enviando comando: ${test.command}", 0L))

        try {
            val result = withTimeoutOrNull(TEST_TIMEOUT_MS) {
                try {
                    // Step 1: send command
                    emit(TestResult.Progress("Esperando respuesta del ECU...", System.currentTimeMillis() - startMs))
                    val raw = connection.send(test.command)
                    val elapsed = System.currentTimeMillis() - startMs

                    if (cancelled || !coroutineContext.isActive) {
                        emit(TestResult.Failure("Test cancelado por el usuario"))
                        return@withTimeoutOrNull null
                    }

                    if (raw.isBlank() || raw == "NO DATA" || raw.startsWith("ERROR") || raw.startsWith("?")) {
                        emit(TestResult.Failure("ECU no respondió al comando ${test.command}. Respuesta: $raw", raw))
                        return@withTimeoutOrNull null
                    }

                    // Step 2: validate expected prefix
                    val expectedOk = test.expectedResponse == null ||
                        raw.replace(" ", "").contains(
                            test.expectedResponse.replace(" ", ""),
                            ignoreCase = true
                        )

                    if (!expectedOk) {
                        emit(TestResult.Failure(
                            "Respuesta inesperada. Esperado: ${test.expectedResponse} — Recibido: $raw",
                            raw
                        ))
                        return@withTimeoutOrNull null
                    }

                    // Step 3: parse value
                    val parsed = parseResponse(test, raw)
                    emit(TestResult.Success(raw, parsed, elapsed))
                    raw

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Test ${test.id} failed: ${e.message}", e)
                    emit(TestResult.Failure("Error de comunicación: ${e.message}", null))
                    null
                }
            }

            if (result == null && !cancelled) {
                emit(TestResult.Timeout)
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Test ${test.id} cancelled")
            throw e
        }
    }

    override fun stopCurrentTest() {
        cancelled = true
        Log.i(TAG, "Test cancelled by user request")
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private fun parseResponse(test: ActuatorTest, raw: String): String {
        return try {
            val bytes = raw.trim().split("\\s+".toRegex())
                .mapNotNull { it.toIntOrNull(16) }

            when (test.id) {
                "RPM_READ"         -> if (bytes.size >= 4) "${(bytes[2] * 256 + bytes[3]) / 4} RPM" else raw
                "ENGINE_LOAD"      -> if (bytes.size >= 3) "${bytes[2] * 100 / 255}%" else raw
                "THROTTLE_POS"     -> if (bytes.size >= 3) "${bytes[2] * 100 / 255}%" else raw
                "COOLANT_TEMP"     -> if (bytes.size >= 3) "${bytes[2] - 40} °C" else raw
                "INTAKE_AIR_TEMP"  -> if (bytes.size >= 3) "${bytes[2] - 40} °C" else raw
                "AMBIENT_AIR_TEMP" -> if (bytes.size >= 3) "${bytes[2] - 40} °C" else raw
                "FUEL_PRESSURE"    -> if (bytes.size >= 3) "${bytes[2] * 3} kPa" else raw
                "SHORT_FUEL_TRIM_B1",
                "LONG_FUEL_TRIM_B1" -> if (bytes.size >= 3) "${String.format("%.1f", (bytes[2] - 128) * 100.0 / 128)}%" else raw
                "MAF_SENSOR"       -> if (bytes.size >= 4) "${String.format("%.2f", (bytes[2] * 256 + bytes[3]) / 100.0)} g/s" else raw
                "BATTERY_VOLTAGE"  -> if (bytes.size >= 4) "${String.format("%.2f", (bytes[2] * 256 + bytes[3]) / 1000.0)} V" else raw
                "O2_SENSOR_B1S1",
                "O2_SENSOR_B1S2"   -> if (bytes.size >= 3) "${String.format("%.3f", bytes[2] / 200.0)} V" else raw
                "EVAP_PURGE",
                "EGR_COMMANDED"    -> if (bytes.size >= 3) "${bytes[2] * 100 / 255}%" else raw
                "CATALYST_TEMP"    -> if (bytes.size >= 4) "${((bytes[2] * 256 + bytes[3]) / 10) - 40} °C" else raw
                "DTC_STATUS"       -> {
                    if (bytes.size >= 3) {
                        val milOn = (bytes[2] and 0x80) != 0
                        val numDtcs = bytes[2] and 0x7F
                        "MIL: ${if (milOn) "ENCENDIDA 🔴" else "APAGADA ✅"} | DTCs: $numDtcs"
                    } else raw
                }
                "RUNTIME"          -> if (bytes.size >= 4) "${bytes[2] * 256 + bytes[3]} segundos" else raw
                "FREEZE_FRAME"     -> "Freeze frame: $raw"
                "OBD_STANDARDS"    -> {
                    val std = if (bytes.size >= 3) bytes[2] else 0
                    "Estándar OBD: ${obdStandardName(std)}"
                }
                else               -> raw
            }
        } catch (e: Exception) {
            raw // Fallback: show raw if parsing fails
        }
    }

    private fun obdStandardName(value: Int): String = when (value) {
        1  -> "OBD-II (CARB)"
        2  -> "OBD (EPA)"
        3  -> "OBD y OBD-II"
        4  -> "OBD-I"
        5  -> "No OBD"
        6  -> "EOBD (Europa)"
        7  -> "EOBD y OBD-II"
        9  -> "EOBD y OBD"
        11 -> "EMD (Japón)"
        else -> "OBD estándar ($value)"
    }
}

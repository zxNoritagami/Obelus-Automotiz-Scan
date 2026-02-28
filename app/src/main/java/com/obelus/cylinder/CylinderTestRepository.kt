package com.obelus.cylinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// CylinderTestRepository.kt
// Test de balance de cilindros vía Modo 06 OBD2.
// Lectura de contadores de misfire y diagnóstico diferencial.
// ─────────────────────────────────────────────────────────────────────────────

typealias CylinderElmSender = suspend (cmd: String) -> String?

/** Validación de precondiciones para el test. */
data class TestPreconditions(
    val rpm: Float?,
    val coolantTemp: Float?,
    val isRpmStable: Boolean,        // RPM ≈ ralentí durante ≥30s
    val isEngineWarm: Boolean        // coolant ≥ 75°C
)

@Singleton
class CylinderTestRepository @Inject constructor() {

    companion object {
        private const val TAG = "CylinderTestRepo"

        // Modo 06 TID $01–$22: desequilibrio de cilindro
        // Los PIDs varían por protocolo — usamos los más comunes:
        // 0106 = misfire #1, 0206 = misfire #2, etc.
        private val MISFIRE_PIDS = mapOf(
            1 to "05",    // Cylinder 1 misfire counter (Modo 06)
            2 to "06",
            3 to "07",
            4 to "08",
            5 to "09",
            6 to "0A",
            7 to "0B",
            8 to "0C"
        )

        // PIDs adicionales para contribución al ralentí
        // GM/Ford usan Modo 06 TID $10–$17 para desequilibrio en el ralentí
        private val BALANCE_PIDS = mapOf(
            1 to "10",
            2 to "11",
            3 to "12",
            4 to "13",
            5 to "14",
            6 to "15",
            7 to "16",
            8 to "17"
        )

        private val NO_RESPONSE = setOf("NO DATA", "ERROR", "?", "STOPPED", "UNABLE")
        private const val RPM_IDLE_MIN = 550f
        private const val RPM_IDLE_MAX = 1100f
        private const val MIN_COOLANT   = 75f
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validación de precondiciones
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Verifica si el motor está listo para el test de balance.
     * Requiere ralentí estable (RPM entre 550–1100) y motor caliente (≥75°C).
     */
    fun checkPreconditions(
        rpm: Float?,
        coolantTemp: Float?,
        rpmStableSecs: Int = 0
    ): TestPreconditions {
        val idleOk   = rpm != null && rpm in RPM_IDLE_MIN..RPM_IDLE_MAX
        val warmOk   = coolantTemp != null && coolantTemp >= MIN_COOLANT
        return TestPreconditions(
            rpm = rpm, coolantTemp = coolantTemp,
            isRpmStable  = idleOk && rpmStableSecs >= 30,
            isEngineWarm = warmOk
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Test de balance (Modo 06)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ejecuta el test de balance de cilindros vía Modo 06.
     * Emite [CylinderTestResult.Progress] durante el test y
     * [CylinderTestResult.BalanceReady] al finalizar.
     *
     * El test envía comandos "06 <PID>" para cada cilindro
     * y parsea la respuesta de desequilibrio.
     *
     * @param cylinderCount Número de cilindros del motor (4, 6 u 8).
     * @param rpm           RPM al momento de llamar (para incluir en resultado).
     * @param coolantTemp   Temperatura para incluir en resultado.
     * @param sender        Función de envío de comandos ELM327.
     */
    fun runCylinderBalanceTest(
        cylinderCount: Int = 4,
        rpm: Float? = null,
        coolantTemp: Float? = null,
        sender: CylinderElmSender
    ): Flow<CylinderTestResult> = flow {
        emit(CylinderTestResult.Progress("Iniciando test de balance…", 0.05f))
        delay(300L)

        val cylinders = mutableListOf<CylinderData>()
        val total = cylinderCount.coerceIn(1, 8)

        for (cyl in 1..total) {
            val balancePid = BALANCE_PIDS[cyl] ?: continue
            val fraction = 0.1f + (cyl.toFloat() / total) * 0.7f
            emit(CylinderTestResult.Progress("Leyendo cilindro #$cyl…", fraction))

            // Leer contribución al ralentí (desequilibrio)
            val contribution = readMode06Value(sender, balancePid)

            // Leer contador de misfire
            val misfirePid = MISFIRE_PIDS[cyl]
            val misfireCount = if (misfirePid != null) readMode06Count(sender, misfirePid) else null

            cylinders += CylinderData(
                cylinderNumber     = cyl,
                contributionToIdle = contribution,
                misfireCount       = misfireCount
            )
            delay(80L)
        }

        if (cylinders.all { it.contributionToIdle == null }) {
            emit(CylinderTestResult.NotSupported("El vehículo no reportó datos de balance de cilindros (Modo 06 no soportado)"))
            return@flow
        }

        emit(CylinderTestResult.Progress("Calculando balance…", 0.9f))
        delay(200L)

        val balance = computeBalance(cylinders, rpm, coolantTemp)
        emit(CylinderTestResult.BalanceReady(balance))

        // Diagnóstico automático
        val diagnosis = identifyWeakCylinder(balance)
        emit(CylinderTestResult.DiagnosisReady(diagnosis))
        emit(CylinderTestResult.Progress("Test completado", 1.0f))
    }.flowOn(Dispatchers.IO)

    // ═══════════════════════════════════════════════════════════════════════════
    // Lectura de contadores de misfire
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lee los contadores de misfire para todos los cilindros disponibles.
     *
     * @param cylinderCount Número de cilindros del motor.
     */
    suspend fun readMisfireCounters(
        cylinderCount: Int = 4,
        sender: CylinderElmSender
    ): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        for (cyl in 1..cylinderCount.coerceIn(1, 8)) {
            val pid = MISFIRE_PIDS[cyl] ?: continue
            val count = readMode06Count(sender, pid)
            if (count != null) result[cyl] = count
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Diagnóstico diferencial
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Analiza el balance y genera un diagnóstico con causas y tests sugeridos.
     *
     * @param balance Resultado del test de balance.
     */
    fun identifyWeakCylinder(balance: CylinderBalance): Diagnosis {
        if (balance.cylinders.isEmpty()) {
            return Diagnosis(Severity.OK, null, emptyList(), emptyList(), "Sin datos para analizar")
        }

        val weakNum = balance.weakestCylinder
        val imbal   = balance.imbalancePercent
        val cyl     = balance.cylinders.firstOrNull { it.cylinderNumber == weakNum }
        val hasMis  = balance.totalMisfires > 0

        // Determinar severidad
        val severity = when {
            imbal <= 10f -> Severity.OK
            imbal <= 25f -> Severity.MILD
            else         -> Severity.SEVERE
        }

        if (severity == Severity.OK && !hasMis) {
            return Diagnosis(
                severity          = Severity.OK,
                affectedCylinder  = null,
                probableCauses    = emptyList(),
                suggestedTests    = listOf("Motor balanceado. Realizar mantenimiento preventivo regular."),
                summary           = "Motor balanceado (desviación ${"%.1f".format(imbal)}%)"
            )
        }

        // Causas probables por nivel de desviación
        val causes = mutableListOf<String>()
        val tests  = mutableListOf<String>()

        when {
            severity == Severity.SEVERE -> {
                causes += "Inyector del cilindro #${weakNum} obstruido o fallado"
                causes += "Bujía defectuosa o cable de bujía con alta resistencia en cilindro #${weakNum}"
                causes += "Válvula quemada o con fugas en cilindro #${weakNum}"
                causes += "Falla en bobina de encendido (ignición individual) del cilindro #${weakNum}"
                if (hasMis) causes += "Fallo de encendido confirmado: ${cyl?.misfireCount} misfires registrados"
                tests  += "Medir resistencia del cable de bujía del cilindro #${weakNum} (< 10 kΩ)"
                tests  += "Intercambiar bujía del cilindro #${weakNum} con otro cilindro y repetir test"
                tests  += "Medir flujo del inyector del cilindro #${weakNum} (lavado ultrasónico si < especificación)"
                tests  += "Test de compresión mecánica en cilindro #${weakNum} (mínimo 1000 kPa)"
                tests  += "Test de fugas del cilindro #${weakNum} (máximo 10% de fuga)"
            }
            severity == Severity.MILD -> {
                causes += "Bujía desgastada en cilindro #${weakNum} (electrodo gastado)"
                causes += "Depósito de carbón en inyector del cilindro #${weakNum}"
                causes += "Compresión levemente reducida"
                tests  += "Inspeccionar y reemplazar bujías (especialmente cilindro #${weakNum})"
                tests  += "Limpieza química de inyectores"
                tests  += "Verificar presión de compresión en cilindro #${weakNum}"
            }
            else -> {
                causes += "Variación normal dentro del margen de tolerancia"
                tests  += "Repetir test en caliente (temperatura ≥ 85°C)"
            }
        }

        // Misfire adicional
        if (hasMis && severity != Severity.SEVERE) {
            causes += "Fallo de encendido intermitente (${balance.totalMisfires} eventos totales)"
            tests  += "Verificar DTCs P030x relacionados con misfires"
        }

        return Diagnosis(
            severity         = severity,
            affectedCylinder = weakNum,
            probableCauses   = causes,
            suggestedTests   = tests,
            summary          = when (severity) {
                Severity.OK     -> "Balance aceptable (desviación ${"%.1f".format(imbal)}%)"
                Severity.MILD   -> "Desbalance leve en cilindro #${weakNum} (${"%.1f".format(imbal)}%)"
                Severity.SEVERE -> "Desbalance severo en cilindro #${weakNum} (${"%.1f".format(imbal)}%)"
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private suspend fun readMode06Value(sender: CylinderElmSender, pid: String): Float? {
        return try {
            val raw = sender("06 $pid")?.trim() ?: return null
            if (NO_RESPONSE.any { raw.uppercase().contains(it) }) return null
            val bytes = raw.split("\\s+".toRegex())
                .drop(2)
                .mapNotNull { it.toIntOrNull(16) }
            if (bytes.size < 2) return null
            // Desequilibrio: signed 16-bit, 0 = neutro, negativo = bajo
            val raw16 = (bytes[0].shl(8) or bytes[1]).toShort()
            // Escalar a contribución porcentual centrada en 100%
            100f + (raw16 * 0.061f)    // 0.061 = ~25/410 escala típica GM
        } catch (e: Exception) { null }
    }

    private suspend fun readMode06Count(sender: CylinderElmSender, pid: String): Int? {
        return try {
            val raw = sender("06 $pid")?.trim() ?: return null
            if (NO_RESPONSE.any { raw.uppercase().contains(it) }) return null
            val bytes = raw.split("\\s+".toRegex())
                .drop(2)
                .mapNotNull { it.toIntOrNull(16) }
            if (bytes.isEmpty()) return null
            if (bytes.size >= 2) bytes[0].shl(8) or bytes[1] else bytes[0]
        } catch (e: Exception) { null }
    }

    private fun computeBalance(
        cylinders: List<CylinderData>,
        rpm: Float?,
        coolantTemp: Float?
    ): CylinderBalance {
        val contributions = cylinders.mapNotNull { it.contributionToIdle }
        val avg = if (contributions.isEmpty()) 0f else contributions.average().toFloat()

        val weakest   = cylinders.minByOrNull { it.contributionToIdle ?: Float.MAX_VALUE }
        val strongest = cylinders.maxByOrNull { it.contributionToIdle ?: Float.MIN_VALUE }
        val maxDev    = cylinders.mapNotNull { it.deviationPercent(avg) }
            .maxByOrNull { abs(it) } ?: 0f

        return CylinderBalance(
            cylinders            = cylinders,
            averageContribution  = avg,
            weakestCylinder      = weakest?.cylinderNumber,
            strongestCylinder    = strongest?.cylinderNumber,
            imbalancePercent     = abs(maxDev),
            rpm                  = rpm,
            coolantTempC         = coolantTemp
        )
    }
}

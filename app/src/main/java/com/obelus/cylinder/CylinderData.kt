package com.obelus.cylinder

// ─────────────────────────────────────────────────────────────────────────────
// CylinderData.kt   —  Datos por cilindro
// CylinderBalance.kt—  Análisis de balance del motor
// Diagnosis.kt      —  Resultado del diagnóstico
// CylinderTestResult (sealed) para Flow de progreso
// ─────────────────────────────────────────────────────────────────────────────

// ════════════════════════════════════════════════════════════════════════════════
// CylinderData
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Datos medidos o estimados para un cilindro individual.
 *
 * @param cylinderNumber        Número de cilindro (1-based, hasta 8).
 * @param contributionToIdle    Contribución al ralentí (%). 100 = promedio normal.
 *                              Obtenido vía Modo 06 (cilindro "apagado" momentáneamente).
 * @param ignitionTiming        Avance de encendido relativo para este cilindro (°CA).
 * @param fuelInjectorPulse     Ancho de pulso del inyector (ms).
 * @param compressionTest       Presión de compresión medida externamente (kPa), si aplica.
 * @param misfireCount          Contador de fallos de encendido (Modo 06 / 0x14 + offset).
 */
data class CylinderData(
    val cylinderNumber: Int,
    val contributionToIdle: Float? = null,
    val ignitionTiming: Float?     = null,
    val fuelInjectorPulse: Float?  = null,
    val compressionTest: Float?    = null,
    val misfireCount: Int?         = null
) {
    /** Desviación absoluta del cilindro respecto al promedio del banco. */
    fun deviationFrom(average: Float): Float? =
        contributionToIdle?.let { it - average }

    /** Desviación porcentual respecto al promedio. */
    fun deviationPercent(average: Float): Float? =
        if (average == 0f || contributionToIdle == null) null
        else ((contributionToIdle - average) / average) * 100f

    /** Estado visual del cilindro basado en la desviación del promedio. */
    fun status(average: Float): CylinderStatus {
        val pct = deviationPercent(average) ?: return CylinderStatus.UNKNOWN
        return when {
            kotlin.math.abs(pct) <= 10f -> CylinderStatus.OK
            kotlin.math.abs(pct) <= 25f -> CylinderStatus.MILD
            else                        -> CylinderStatus.SEVERE
        }
    }
}

/** Estado cualitativo de un cilindro comparado con el promedio. */
enum class CylinderStatus {
    OK,       // ±10%  — verde
    MILD,     // ±25%  — amarillo
    SEVERE,   // >25%  — rojo
    UNKNOWN   // Sin dato
}

// ════════════════════════════════════════════════════════════════════════════════
// CylinderBalance
// ════════════════════════════════════════════════════════════════════════════════

/**
 * Resultado del test de balance de cilindros.
 *
 * @param cylinders             Lista de datos por cilindro (orden 1…N).
 * @param averageContribution   Media de contribución de todos los cilindros (%).
 * @param weakestCylinder       Número del cilindro con menor contribución (1-based).
 * @param strongestCylinder     Número del cilindro con mayor contribución (1-based).
 * @param imbalancePercent      Desviación máxima del promedio, en % (valores altos = problema).
 * @param testTimestampMs       Epoch ms del momento del test.
 * @param rpm                   RPM al momento del test (para validar ralentí estable).
 * @param coolantTempC          Temperatura del refrigerante al inicio del test.
 */
data class CylinderBalance(
    val cylinders: List<CylinderData>,
    val averageContribution: Float,
    val weakestCylinder: Int?,
    val strongestCylinder: Int?,
    val imbalancePercent: Float,
    val testTimestampMs: Long = System.currentTimeMillis(),
    val rpm: Float? = null,
    val coolantTempC: Float? = null
) {
    val cylinderCount: Int get() = cylinders.size

    /** true si el motor está suficientemente balanceado (imbalance < 10%). */
    val isBalanced: Boolean get() = imbalancePercent <= 10f

    /** Cilindro con la desviación más alta (absoluta). */
    val mostDeviantCylinder: CylinderData?
        get() = cylinders.maxByOrNull {
            kotlin.math.abs(it.deviationPercent(averageContribution) ?: 0f)
        }

    /** Total de misfires detectados en todos los cilindros. */
    val totalMisfires: Int get() = cylinders.sumOf { it.misfireCount ?: 0 }

    companion object {
        fun empty(): CylinderBalance = CylinderBalance(
            cylinders = emptyList(), averageContribution = 0f,
            weakestCylinder = null, strongestCylinder = null, imbalancePercent = 0f
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// Diagnosis
// ════════════════════════════════════════════════════════════════════════════════

enum class Severity { OK, MILD, SEVERE }

/**
 * Diagnóstico del estado de cilindros generado por [CylinderTestRepository].
 *
 * @param severity          Gravedad global del problema.
 * @param affectedCylinder  Cilindro más afectado (null = todos OK o global).
 * @param probableCauses    Lista de causas probables en lenguaje técnico.
 * @param suggestedTests    Pasos de diagnóstico recomendados.
 * @param summary           Texto breve para mostrar en la UI.
 */
data class Diagnosis(
    val severity: Severity,
    val affectedCylinder: Int?,
    val probableCauses: List<String>,
    val suggestedTests: List<String>,
    val summary: String
)

// ════════════════════════════════════════════════════════════════════════════════
// CylinderTestResult  —  para Flow de progreso
// ════════════════════════════════════════════════════════════════════════════════

sealed class CylinderTestResult {
    data class Progress(val message: String, val fraction: Float) : CylinderTestResult()
    data class BalanceReady(val balance: CylinderBalance)         : CylinderTestResult()
    data class DiagnosisReady(val diagnosis: Diagnosis)           : CylinderTestResult()
    data class NotSupported(val reason: String)                   : CylinderTestResult()
    data class Error(val message: String)                         : CylinderTestResult()
    object EngineNotReady : CylinderTestResult()   // Motor frío o RPM inestable
}

package com.obelus.freezeframe

import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// ValueDifference.kt
// Comparativa entre el valor del freeze frame y la lectura actual del sensor.
// ─────────────────────────────────────────────────────────────────────────────

/** Estado cualitativo de la diferencia entre freeze frame y valor actual. */
enum class DifferenceStatus {
    /** El parámetro mejoró respecto al momento de la falla. */
    IMPROVED,
    /** El parámetro empeoró respecto al momento de la falla. */
    WORSENED,
    /** Sin cambio significativo (|Δ%| < 5%). */
    STABLE,
    /** No hay dato actual para comparar. */
    UNKNOWN
}

/**
 * Delta entre un valor de freeze frame y el valor actual en tiempo real.
 *
 * @param parameterName     Nombre legible del parámetro (ej. "RPM", "Temp refrigerante °C").
 * @param pid               PID OBD2 (ej. "0C").
 * @param unit              Unidad de medida (ej. "rpm", "°C", "%").
 * @param freezeFrameValue  Valor en el momento del fallo.
 * @param currentValue      Valor actual (puede ser NaN si no hay dato).
 * @param normalMin         Límite inferior del rango normal (null = no aplica).
 * @param normalMax         Límite superior del rango normal (null = no aplica).
 */
data class ValueDifference(
    val parameterName: String,
    val pid: String,
    val unit: String,
    val freezeFrameValue: Float,
    val currentValue: Float,
    val normalMin: Float? = null,
    val normalMax: Float? = null
) {
    /** Delta absoluto (currentValue − freezeFrameValue). */
    val difference: Float
        get() = if (currentValue.isNaN()) Float.NaN else currentValue - freezeFrameValue

    /** Cambio porcentual |Δ / freeze|×100. */
    val percentChange: Float
        get() = if (currentValue.isNaN() || freezeFrameValue == 0f) Float.NaN
                else ((currentValue - freezeFrameValue) / abs(freezeFrameValue)) * 100f

    /** Estado cualitativo basado en si el parámetro mejoró o empeoró. */
    val status: DifferenceStatus
        get() {
            if (currentValue.isNaN()) return DifferenceStatus.UNKNOWN
            val pct = percentChange
            if (pct.isNaN() || abs(pct) < 5f) return DifferenceStatus.STABLE
            return when (pid.uppercase()) {
                // Para RPM, carga, trim: más bajo suele ser mejor
                "0C", "04", "06", "07" ->
                    if (pct < 0) DifferenceStatus.IMPROVED else DifferenceStatus.WORSENED
                // Para temperatura: más bajo es mejor si estaba alta
                "05" -> if (freezeFrameValue > 100f && pct < 0) DifferenceStatus.IMPROVED
                        else if (pct > 0 && freezeFrameValue > 80f) DifferenceStatus.WORSENED
                        else DifferenceStatus.STABLE
                // Para O2: variabilidad es buena si antes estaba fija
                "14", "15" -> DifferenceStatus.STABLE  // necesita lógica de oscilación
                else -> if (abs(pct) < 10f) DifferenceStatus.STABLE
                        else DifferenceStatus.UNKNOWN
            }
        }

    /** true si el valor de freeze frame estaba fuera del rango normal. */
    val wasFreezeFrameAbnormal: Boolean
        get() = (normalMin != null && freezeFrameValue < normalMin) ||
                (normalMax != null && freezeFrameValue > normalMax)

    /** true si el valor actual está fuera del rango normal. */
    val isCurrentAbnormal: Boolean
        get() = !currentValue.isNaN() &&
                ((normalMin != null && currentValue < normalMin) ||
                 (normalMax != null && currentValue > normalMax))
}

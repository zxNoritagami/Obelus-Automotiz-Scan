package com.obelus.chart

import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// ChartAnalyzer.kt
// Detección de patrones en tiempo real sin bloquear la UI.
// Todas las funciones son puras (sin estado) y ejecutables en Dispatchers.Default.
// ─────────────────────────────────────────────────────────────────────────────

object ChartAnalyzer {

    // ═══════════════════════════════════════════════════════════════════════════
    // Detección de picos (Spike detection)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Detecta picos anómalos usando umbral Z-score (> ±2.5σ = WARNING, > ±3.5σ = CRITICAL).
     *
     * @param data      Lista de puntos en orden cronológico.
     * @param zWarning  Z-score para WARNING (default 2.5).
     * @param zCritical Z-score para CRITICAL (default 3.5).
     */
    fun detectSpikes(
        data: List<ChartPoint>,
        zWarning: Float = 2.5f,
        zCritical: Float = 3.5f
    ): List<ChartEvent.SpikeEvent> {
        if (data.size < 5) return emptyList()

        val values = data.map { it.value }
        val mean   = values.average().toFloat()
        val sigma  = stdDev(values, mean)
        if (sigma < 1e-6f) return emptyList()   // señal constante

        return data.mapNotNull { point ->
            val z = abs(point.value - mean) / sigma
            when {
                z > zCritical -> ChartEvent.SpikeEvent(
                    signalId       = point.signalId,
                    timestamp      = point.timestamp,
                    value          = point.value,
                    deltaFromMean  = point.value - mean,
                    severity       = SpikeSeverity.CRITICAL
                )
                z > zWarning  -> ChartEvent.SpikeEvent(
                    signalId       = point.signalId,
                    timestamp      = point.timestamp,
                    value          = point.value,
                    deltaFromMean  = point.value - mean,
                    severity       = SpikeSeverity.WARNING
                )
                else -> null
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Detección de oscilaciones (FFT simplificada por cruce de cero)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Detecta oscilaciones calculando la frecuencia por cruces de cero
     * respecto a la media y la amplitud pico a pico.
     *
     * Útil para sensores O2 (lambda) que oscilan normalmente a 0.5–2 Hz.
     *
     * @param data  Lista de puntos en orden cronológico.
     * @return [OscillationInfo] o null si la duración es insuficiente.
     */
    fun detectOscillation(data: List<ChartPoint>): OscillationInfo? {
        if (data.size < 10) return null
        val durationSec = (data.last().timestamp - data.first().timestamp) / 1000.0
        if (durationSec < 1.0) return null

        val values  = data.map { it.value }
        val mean    = values.average().toFloat()
        val minVal  = values.min()
        val maxVal  = values.max()
        val ampPP   = maxVal - minVal

        // Contar cruces de cero respecto a la media
        var crossings = 0
        for (i in 1 until values.size) {
            val prev = values[i - 1] - mean
            val curr = values[i]    - mean
            if (prev * curr < 0f) crossings++     // cambio de signo
        }
        // Cada ciclo completo = 2 cruces
        val cycles    = crossings / 2.0
        val freqHz    = (cycles / durationSec).toFloat()

        // O2 sano: 0.5–2.0 Hz, amplitud ≥ 0.4 V (valores típicos 0.1–0.9 V)
        val isHealthy = freqHz in 0.3f..3.0f && ampPP >= 0.35f

        return OscillationInfo(freqHz, ampPP, mean, isHealthy)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Detección de tendencia (regresión lineal simple)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Detecta la tendencia usando regresión lineal sobre los últimos N puntos.
     *
     * @param data              Puntos en orden cronológico.
     * @param stableThreshold   |pendiente| por segundo menor a este valor = STABLE.
     */
    fun detectTrend(
        data: List<ChartPoint>,
        stableThreshold: Float = 0.05f
    ): TrendDirection {
        if (data.size < 4) return TrendDirection.UNKNOWN

        // Normalizar tiempo a segundos desde el primer punto
        val t0 = data.first().timestamp
        val xs = data.map { (it.timestamp - t0) / 1000.0 }
        val ys = data.map { it.value.toDouble() }

        val n    = xs.size.toDouble()
        val sumX = xs.sum()
        val sumY = ys.sum()
        val sumXY = xs.zip(ys).sumOf { (x, y) -> x * y }
        val sumX2 = xs.sumOf { it * it }
        val denom = n * sumX2 - sumX * sumX
        if (abs(denom) < 1e-9) return TrendDirection.STABLE

        val slope = ((n * sumXY - sumX * sumY) / denom).toFloat()    // unidad / s

        return when {
            slope >  stableThreshold -> TrendDirection.RISING
            slope < -stableThreshold -> TrendDirection.FALLING
            else                     -> TrendDirection.STABLE
        }
    }

    /**
     * Retorna la pendiente (tasa de cambio por segundo) de la señal.
     * Positivo = sube, negativo = baja.
     */
    fun slopePerSecond(data: List<ChartPoint>): Float {
        if (data.size < 2) return 0f
        val dt = (data.last().timestamp - data.first().timestamp) / 1000.0
        if (dt < 1e-3) return 0f
        return ((data.last().value - data.first().value) / dt).toFloat()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Análisis completo (devuelve todos los eventos detectados en una pasada)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Ejecuta todo el análisis y retorna la lista de eventos detectados.
     * Llamar en Dispatchers.Default para no bloquear la UI.
     */
    fun analyze(data: List<ChartPoint>): List<ChartEvent> {
        if (data.isEmpty()) return emptyList()
        val events = mutableListOf<ChartEvent>()

        // Spikes
        events.addAll(detectSpikes(data))

        // Oscillation → OscillationDetected event
        detectOscillation(data)?.let { info ->
            events.add(
                ChartEvent.OscillationDetected(
                    signalId    = data.first().signalId,
                    timestamp   = System.currentTimeMillis(),
                    frequencyHz = info.frequencyHz,
                    amplitudePP = info.amplitudePeakToPeak
                )
            )
        }

        // Trend
        val trend = detectTrend(data)
        if (trend != TrendDirection.STABLE && trend != TrendDirection.UNKNOWN) {
            events.add(
                ChartEvent.TrendEvent(
                    signalId      = data.first().signalId,
                    timestamp     = System.currentTimeMillis(),
                    direction     = trend,
                    ratePerSecond = slopePerSecond(data)
                )
            )
        }
        return events
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun stdDev(values: List<Float>, mean: Float): Float {
        if (values.size < 2) return 0f
        val variance = values.sumOf { ((it - mean) * (it - mean)).toDouble() } / values.size
        return sqrt(variance).toFloat()
    }

    /** Estadísticas rápidas de la ventana visible. */
    fun stats(data: List<ChartPoint>): ChartStats? {
        if (data.isEmpty()) return null
        val values = data.map { it.value }
        val mean   = values.average().toFloat()
        return ChartStats(
            min    = values.min(),
            max    = values.max(),
            mean   = mean,
            stdDev = stdDev(values, mean),
            count  = values.size
        )
    }
}

data class ChartStats(
    val min: Float,
    val max: Float,
    val mean: Float,
    val stdDev: Float,
    val count: Int
)

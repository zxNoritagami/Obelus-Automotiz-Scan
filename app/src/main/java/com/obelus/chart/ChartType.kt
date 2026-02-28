package com.obelus.chart

// ─────────────────────────────────────────────────────────────────────────────
// ChartType.kt  –  Tipos de visualización soportados
// ChartPoint.kt –  Dato individual de un gráfico
// ChartEvent.kt –  Evento detectado automáticamente (pico / oscilación / tendencia)
// ─────────────────────────────────────────────────────────────────────────────

/** Tipo de gráfico a renderizar. */
enum class ChartType(val displayName: String, val icon: String) {
    LINE_CHART       ("Línea temporal",          "〜"),
    BAR_CHART        ("Barras comparativas",     "▌▌"),
    GAUGE_CHART      ("Velocímetro circular",    "◎"),
    SCOPE_CHART      ("Osciloscopio (O2/XY)",    "≋"),
    COMPARISON_CHART ("Señales superpuestas",    "≡")
}

/** Punto de datos en el tiempo. */
data class ChartPoint(
    val timestamp: Long,          // epoch ms
    val value: Float,
    val signalId: String,
    val unit: String = "",
    /** true si estaba fuera del rango normal en el momento de la captura. */
    val isAlert: Boolean = false
)

/** Dirección de tendencia detectada. */
enum class TrendDirection { RISING, FALLING, STABLE, UNKNOWN }

/** Evento detectado por ChartAnalyzer en un buffer de señal. */
sealed class ChartEvent {
    abstract val signalId: String
    abstract val timestamp: Long

    data class SpikeEvent(
        override val signalId: String,
        override val timestamp: Long,
        val value: Float,
        val deltaFromMean: Float,      // desviación respecto a la media
        val severity: SpikeSeverity
    ) : ChartEvent()

    data class OscillationDetected(
        override val signalId: String,
        override val timestamp: Long,
        val frequencyHz: Float,
        val amplitudePP: Float          // amplitud pico a pico
    ) : ChartEvent()

    data class TrendEvent(
        override val signalId: String,
        override val timestamp: Long,
        val direction: TrendDirection,
        val ratePerSecond: Float        // tasa de cambio (unidad/s)
    ) : ChartEvent()
}

enum class SpikeSeverity { WARNING, CRITICAL }

/** Información de oscilación para señales cíclicas como el sensor O2. */
data class OscillationInfo(
    val frequencyHz: Float,
    val amplitudePeakToPeak: Float,
    val meanValue: Float,
    val isHealthyLambdaCycle: Boolean   // ≈ 1–2 Hz, amplitud 0.5 V para O2 sano
)

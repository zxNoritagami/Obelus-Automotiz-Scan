package com.obelus.analysis.stream

import com.obelus.domain.model.AnomalyDetectionResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Encargado de procesar el flujo de datos OBD2 en tiempo real.
 * Gestiona buffers circulares por PID y detecta anomalías estadísticas.
 */
@Singleton
class DataStreamAnalyzer @Inject constructor() {

    // Capacidad del buffer (60 segundos a una frecuencia de ~10Hz = 600 puntos)
    private val bufferCapacity = 600
    private val buffers = ConcurrentHashMap<String, CircularBuffer<ObdDataPoint>>()

    /**
     * Agrega una nueva lectura al buffer correspondiente al PID.
     */
    fun addDataPoint(dataPoint: ObdDataPoint) {
        val buffer = buffers.getOrPut(dataPoint.pid) { CircularBuffer(bufferCapacity) }
        buffer.add(dataPoint)
    }

    /**
     * Retorna la lista de puntos de datos para un PID.
     */
    fun getBuffer(pid: String): List<ObdDataPoint> {
        return buffers[pid]?.toList() ?: emptyList()
    }

    /**
     * Calcula la media aritmética de los valores en el buffer.
     * Fórmula: Σx / n
     */
    fun calculateMean(pid: String): Double? {
        val values = buffers[pid]?.toList()?.map { it.value } ?: return null
        if (values.isEmpty()) return null
        return values.average()
    }

    /**
     * Calcula la desviación estándar poblacional.
     * Fórmula: sqrt( Σ(x - media)² / n )
     */
    fun calculateStandardDeviation(pid: String): Double? {
        val values = buffers[pid]?.toList()?.map { it.value } ?: return null
        if (values.size < 2) return 0.0
        
        val mean = values.average()
        val sumOfSquares = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumOfSquares / values.size)
    }

    /**
     * Calcula la diferencia entre el último y el primer valor del buffer (tendencia).
     */
    fun calculateDelta(pid: String): Double? {
        val buffer = buffers[pid] ?: return null
        val last = buffer.peekLast()?.value ?: return null
        val first = buffer.peekFirst()?.value ?: return null
        return last - first
    }

    /**
     * Detecta anomalías basándose en la desviación estándar.
     * Regla: |valor actual - media| > (stdDev * multiplicador)
     */
    fun analyzeAnomaly(pid: String, thresholdStdMultiplier: Double = 2.0): AnomalyDetectionResult? {
        val buffer = buffers[pid] ?: return null
        val lastValue = buffer.peekLast()?.value ?: return null
        val values = buffer.toList().map { it.value }
        
        if (values.size < 5) return null // Mínimo de muestras para evitar falsos positivos iniciales

        val mean = values.average()
        val sumOfSquares = values.sumOf { (it - mean) * (it - mean) }
        val stdDev = sqrt(sumOfSquares / values.size)
        
        val isAnomalous = if (stdDev > 0) {
            abs(lastValue - mean) > (stdDev * thresholdStdMultiplier)
        } else {
            false
        }

        return AnomalyDetectionResult(
            pid = pid,
            mean = mean,
            stdDeviation = stdDev,
            lastValue = lastValue,
            isAnomalous = isAnomalous
        )
    }

    /**
     * Limpia los datos de un PID específico o todos si es nulo.
     */
    fun clear(pid: String? = null) {
        if (pid != null) {
            buffers[pid]?.clear()
        } else {
            buffers.clear()
        }
    }
}

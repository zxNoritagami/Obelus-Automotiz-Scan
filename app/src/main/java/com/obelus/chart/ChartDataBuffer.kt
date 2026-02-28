package com.obelus.chart

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// ChartDataBuffer.kt
// RingBuffer de alta frecuencia para puntos de gráfico.
// Capacidad máxima: 1000 puntos por señal.
// LTTB downsampling automático cuando hay más de 500 puntos en pantalla.
// Thread-safe mediante Mutex (corrutinas).
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_BUFFER_SIZE  = 1_000
private const val DOWNSAMPLE_THRESHOLD = 500

/**
 * Buffer de anillo seguro para hilos para una única señal.
 *
 * @param signalId     Identificador de la señal (PID u otro).
 * @param capacity     Capacidad máxima del buffer (default 1000).
 */
class ChartDataBuffer(
    val signalId: String,
    private val capacity: Int = MAX_BUFFER_SIZE
) {
    private val buffer = ArrayDeque<ChartPoint>(capacity + 1)
    private val mutex  = Mutex()

    // ── Escritura ─────────────────────────────────────────────────────────────

    /** Agrega un punto. Si el buffer está lleno, descarta el punto más antiguo. */
    suspend fun addPoint(timestamp: Long, value: Float, unit: String = "", isAlert: Boolean = false) {
        mutex.withLock {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(ChartPoint(timestamp, value, signalId, unit, isAlert))
        }
    }

    /** Agrega múltiples puntos en un solo lock. */
    suspend fun addAll(points: List<ChartPoint>) {
        mutex.withLock {
            for (p in points) {
                if (buffer.size >= capacity) buffer.removeFirst()
                buffer.addLast(p)
            }
        }
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /** Retorna todos los puntos como lista inmutable. */
    suspend fun snapshot(): List<ChartPoint> = mutex.withLock { buffer.toList() }

    /**
     * Retorna los puntos dentro de un rango de tiempo.
     *
     * @param start  Timestamp de inicio (inclusivo).
     * @param end    Timestamp de fin   (inclusivo).
     */
    suspend fun getWindow(start: Long, end: Long): List<ChartPoint> = mutex.withLock {
        buffer.filter { it.timestamp in start..end }
    }

    /** Retorna los últimos [n] puntos. */
    suspend fun getLast(n: Int): List<ChartPoint> = mutex.withLock {
        if (buffer.size <= n) buffer.toList() else buffer.takeLast(n)
    }

    /** Número de puntos actualmente en el buffer. */
    suspend fun size(): Int = mutex.withLock { buffer.size }

    /** Vacía el buffer. */
    suspend fun clear() = mutex.withLock { buffer.clear() }

    // ── Downsampling LTTB ─────────────────────────────────────────────────────

    /**
     * Retorna los datos con downsampling inteligente (LTTB) si hay más de
     * [DOWNSAMPLE_THRESHOLD] puntos en la ventana de visualización.
     *
     * @param targetCount Número de puntos objetivo para la pantalla.
     */
    suspend fun getDownsampled(targetCount: Int = DOWNSAMPLE_THRESHOLD): List<ChartPoint> {
        val data = snapshot()
        return if (data.size <= targetCount) data else lttb(data, targetCount)
    }

    /**
     * Largest-Triangle-Three-Buckets (LTTB) downsampling.
     * Preserva los picos visuales mientras reduce el número de puntos.
     *
     * Referencia: Sveinn Steinarsson (2013).
     */
    private fun lttb(data: List<ChartPoint>, threshold: Int): List<ChartPoint> {
        if (threshold >= data.size || threshold < 3) return data
        val result = ArrayList<ChartPoint>(threshold)
        val bucketSize = (data.size - 2).toDouble() / (threshold - 2)

        var a = 0
        result.add(data[a])

        for (i in 0 until threshold - 2) {
            // Calculate the range for the current bucket
            val bucketStart = (i * bucketSize + 1).toInt()
            val bucketEnd   = ((i + 1) * bucketSize + 1).toInt().coerceAtMost(data.size - 1)

            // Calculate the range for the next bucket (for the average point)
            val nextBucketStart = bucketEnd
            val nextBucketEnd   = (((i + 1) * bucketSize + 1).toInt()).coerceAtMost(data.size - 1)
            val nextCount = nextBucketEnd - nextBucketStart + 1

            // Average of next bucket
            var avgX = 0.0; var avgY = 0.0
            for (j in nextBucketStart..nextBucketEnd) {
                avgX += data[j].timestamp.toDouble()
                avgY += data[j].value.toDouble()
            }
            avgX /= nextCount; avgY /= nextCount

            // Point a
            val ax = data[a].timestamp.toDouble()
            val ay = data[a].value.toDouble()

            // Find the point with the biggest triangle area in current bucket
            var maxArea = -1.0; var nextA = bucketStart
            for (j in bucketStart until bucketEnd) {
                val area = abs(
                    (ax - avgX) * (data[j].value.toDouble() - ay) -
                    (ax - data[j].timestamp.toDouble()) * (avgY - ay)
                ) * 0.5
                if (area > maxArea) { maxArea = area; nextA = j }
            }
            result.add(data[nextA])
            a = nextA
        }
        result.add(data[data.size - 1])
        return result
    }
}

/**
 * Registro centralizado de buffers por señal.
 * Utilizado por ScanViewModel y ChartScreen.
 */
class ChartBufferRegistry {
    private val buffers = mutableMapOf<String, ChartDataBuffer>()
    private val mutex   = Mutex()

    /** Obtiene o crea el buffer para una señal. */
    suspend fun getOrCreate(signalId: String): ChartDataBuffer = mutex.withLock {
        buffers.getOrPut(signalId) { ChartDataBuffer(signalId) }
    }

    /** Retorna snapshot de todas las señales. */
    suspend fun allSnapshots(): Map<String, List<ChartPoint>> = mutex.withLock {
        buffers.mapValues { (_, buf) ->
            // No suspend en mapValues, leer sin lock interno
            buf.buffer.toList()
        }
    }

    suspend fun remove(signalId: String) = mutex.withLock { buffers.remove(signalId) }
    suspend fun clear()                   = mutex.withLock { buffers.values.forEach { it.buffer.clear() }; buffers.clear() }
    suspend fun activeSignals(): Set<String> = mutex.withLock { buffers.keys.toSet() }

    // Acceso a buffer interno (solo para allSnapshots)
    private val ChartDataBuffer.buffer: ArrayDeque<ChartPoint>
        get() = this::class.java.getDeclaredField("buffer")
            .also { it.isAccessible = true }
            .get(this) as ArrayDeque<ChartPoint>
}

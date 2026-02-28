package com.obelus.data.cache

import com.obelus.data.local.dao.SignalReadingDao
import com.obelus.data.local.entity.SignalReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// ReadingBuffer.kt
// Buffer de alta frecuencia para lecturas de señales OBD2.
//
// Arquitectura:
//   ELM327 → [ReadingBuffer (RAM, ring)] → UI / Gráficas
//                      ↓ flush automático (5s o 100 elementos)
//                   Room (SignalReadingDao)
//
// Beneficio: la UI siempre lee del buffer en RAM; Room solo recibe escrituras
// en lotes, reduciendo contención y latencia de I/O.
// ─────────────────────────────────────────────────────────────────────────────

/** Capacidad máxima del ring buffer por señal (lecturas). */
private const val RING_CAPACITY_PER_SIGNAL = 1_000

/** Umbral de elementos para flush inmediato (sin esperar timer). */
private const val FLUSH_THRESHOLD = 100

/** Intervalo de flush a Room en ms. */
private const val FLUSH_INTERVAL_MS = 5_000L

/**
 * Buffer circular de lecturas de señales con flush controlado a Room.
 *
 * @param readingDao    DAO de Room para persistir las lecturas.
 * @param cacheManager  CacheManager para actualizar el historial RAM.
 */
@Singleton
class ReadingBuffer @Inject constructor(
    private val readingDao: SignalReadingDao,
    private val cacheManager: CacheManager
) {
    // ── RingBuffer interno ────────────────────────────────────────────────────
    /** Buffer pending de flush → Room. */
    private val pendingBuffer = ArrayDeque<SignalReading>(FLUSH_THRESHOLD * 2)

    /** Mapa PID → RingBuffer de historial en RAM para UI. */
    private val ringBuffers = HashMap<String, ArrayDeque<SignalReading>>()

    private val mutex  = Mutex()
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null

    // ── Estadísticas ──────────────────────────────────────────────────────────
    @Volatile private var totalAppended = 0L
    @Volatile private var totalFlushed  = 0L

    // ═══════════════════════════════════════════════════════════════════════════
    // API pública
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Agrega una lectura al buffer.
     * - Actualiza el ring de UI inmediatamente.
     * - Encola en el buffer de flush.
     * - Dispara flush si se supera [FLUSH_THRESHOLD].
     */
    suspend fun append(reading: SignalReading) {
        mutex.withLock {
            // Ring UI
            val ring = ringBuffers.getOrPut(reading.pid) { ArrayDeque(RING_CAPACITY_PER_SIGNAL) }
            ring.addFirst(reading)
            while (ring.size > RING_CAPACITY_PER_SIGNAL) ring.removeLast()

            // Buffer de flush
            pendingBuffer.add(reading)
            totalAppended++

            // Actualizar CacheManager para sparklines
            cacheManager.appendReading(reading.pid, reading)
        }

        // Flush inmediato si se llega al umbral
        if (pendingBuffer.size >= FLUSH_THRESHOLD) {
            flush()
        }
    }

    /** Agrega múltiples lecturas en lote (más eficiente que llamar [append] N veces). */
    suspend fun appendAll(readings: List<SignalReading>) {
        mutex.withLock {
            readings.forEach { reading ->
                val ring = ringBuffers.getOrPut(reading.pid) { ArrayDeque(RING_CAPACITY_PER_SIGNAL) }
                ring.addFirst(reading)
                while (ring.size > RING_CAPACITY_PER_SIGNAL) ring.removeLast()
                pendingBuffer.add(reading)
                cacheManager.appendReading(reading.pid, reading)
            }
            totalAppended += readings.size
        }
        if (pendingBuffer.size >= FLUSH_THRESHOLD) flush()
    }

    /**
     * Retorna las últimas [limit] lecturas de un PID directamente desde el ring RAM.
     * No toca Room → latencia ~0μs.
     */
    fun getLatest(pid: String, limit: Int = 50): List<SignalReading> {
        return ringBuffers[pid]?.take(limit) ?: emptyList()
    }

    /** Retorna todas las lecturas actualmente en el buffer de flush (sin persistir). */
    fun peekPending(): List<SignalReading> = pendingBuffer.toList()

    /** Tamaño actual del buffer de flush (elementos pendientes de escritura en Room). */
    val pendingCount: Int get() = pendingBuffer.size

    // ═══════════════════════════════════════════════════════════════════════════
    // Flush a Room
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Persiste inmediatamente todas las lecturas pendientes en Room.
     * Hilo-seguro: usa [Mutex] para evitar escrituras dobles.
     */
    suspend fun flush() {
        val toSave: List<SignalReading>
        mutex.withLock {
            if (pendingBuffer.isEmpty()) return
            toSave = pendingBuffer.toList()
            pendingBuffer.clear()
        }
        try {
            readingDao.insertAll(toSave)
            totalFlushed += toSave.size
            println("[ReadingBuffer] Flush: ${toSave.size} lecturas → Room (total=$totalFlushed)")
        } catch (e: Exception) {
            // Reencolar si falla Room (ej. disco lleno)
            mutex.withLock { pendingBuffer.addAll(0, toSave) }
            println("[ReadingBuffer] Error en flush: ${e.message}. Reencolas ${toSave.size} lecturas.")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Timer automático de flush
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Inicia el timer de flush cada [FLUSH_INTERVAL_MS].
     * Llamar al inicio de una sesión de escaneo.
     */
    fun startAutoFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            println("[ReadingBuffer] Auto-flush iniciado (cada ${FLUSH_INTERVAL_MS}ms).")
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    /**
     * Detiene el timer y persiste lo que quede pendiente.
     * Llamar al finalizar la sesión de escaneo.
     */
    suspend fun stopAndFlush() {
        flushJob?.cancel()
        flushJob = null
        flush()
        println("[ReadingBuffer] Detenido. Total appended=$totalAppended flushed=$totalFlushed.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Reset
    // ═══════════════════════════════════════════════════════════════════════════

    /** Vacía el buffer en RAM y los rings sin persistir. */
    suspend fun reset() {
        mutex.withLock {
            pendingBuffer.clear()
            ringBuffers.clear()
        }
        cacheManager.clearAllReadingHistory()
        totalAppended = 0L
        totalFlushed  = 0L
        println("[ReadingBuffer] RESET – buffer y rings limpiados.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Métricas
    // ═══════════════════════════════════════════════════════════════════════════

    fun printMetrics() {
        println(
            "[ReadingBuffer] appended=$totalAppended flushed=$totalFlushed " +
            "pending=${pendingBuffer.size} signals=${ringBuffers.size}"
        )
    }
}

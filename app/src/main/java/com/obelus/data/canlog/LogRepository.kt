package com.obelus.data.canlog

import android.content.Context
import android.net.Uri
import android.util.Log
import com.obelus.data.local.dao.CanFrameDao
import com.obelus.data.local.entity.CanFrameEntity
import com.obelus.data.protocol.DbcMessage
import com.obelus.data.protocol.DbcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Formatos de log CAN soportados */
enum class LogFormat { CRTD, CSV, PCAN_TRC, UNKNOWN }

/** Estado del proceso de importación */
sealed class ImportState {
    object Idle                            : ImportState()
    data class Loading(val progress: Float): ImportState()
    data class Success(
        val sessionId: String,
        val frameCount: Int,
        val format: LogFormat
    ) : ImportState()
    data class Error(val message: String)  : ImportState()
}

/**
 * Repository para importar, almacenar y recuperar logs CAN.
 *
 * Soporta tres formatos de importación:
 * - **CRTD** (Canalyzer): `<timestamp> <type> <id> <data>`
 * - **CSV**: `timestamp_us,canId,data_hex`
 * - **PCAN .trc**: `  263)      000.0  00A  8  30 2B A8 00 00 00 00 00`
 *
 * Todos los frames importados se persisten en Room (`can_frames_log`).
 */
@Singleton
class LogRepository @Inject constructor(
    private val canFrameDao: CanFrameDao
) {
    companion object { private const val TAG = "LogRepository" }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** DBC actualmente cargado — null si no hay ninguno */
    private var _dbcDatabase: Map<Int, DbcMessage> = emptyMap()
    val dbcDatabase: Map<Int, DbcMessage> get() = _dbcDatabase

    private val _dbcLoaded = MutableStateFlow(false)
    val dbcLoaded: StateFlow<Boolean> = _dbcLoaded.asStateFlow()

    // =========================================================================
    // IMPORTACIÓN DE LOGS
    // =========================================================================

    /**
     * Importa un archivo de log CAN desde un URI (SAF).
     * Detecta el formato automáticamente por contenido (no extensión).
     *
     * @param context ApplicationContext
     * @param fileUri  URI del archivo seleccionado
     * @param fileName Nombre original del archivo (para metadatos)
     * @return sessionId de la importación
     */
    suspend fun importLog(
        context: Context,
        fileUri: Uri,
        fileName: String = "log"
    ): String? = withContext(Dispatchers.IO) {
        _importState.value = ImportState.Loading(0f)

        return@withContext try {
            val lines = context.contentResolver.openInputStream(fileUri)
                ?.bufferedReader()
                ?.readLines()
                ?: run {
                    _importState.value = ImportState.Error("No se pudo abrir el archivo")
                    return@withContext null
                }

            if (lines.isEmpty()) {
                _importState.value = ImportState.Error("Archivo vacío")
                return@withContext null
            }

            // Detectar formato
            val format = detectFormat(lines)
            Log.i(TAG, "Formato detectado: $format para $fileName")

            // Parsear según formato
            val frames = when (format) {
                LogFormat.CRTD     -> parseCrtd(lines)
                LogFormat.CSV      -> parseCsv(lines)
                LogFormat.PCAN_TRC -> parsePcanTrc(lines)
                LogFormat.UNKNOWN  -> {
                    _importState.value = ImportState.Error("Formato no reconocido")
                    return@withContext null
                }
            }

            if (frames.isEmpty()) {
                _importState.value = ImportState.Error("No se encontraron frames válidos")
                return@withContext null
            }

            // Generar sessionId único
            val sessionId = UUID.randomUUID().toString().take(8)

            // Persistir en Room en batches de 500 para no bloquear la DB
            val entities = frames.map { frame ->
                CanFrameEntity(
                    sessionId   = sessionId,
                    timestamp   = frame.timestamp,
                    canId       = frame.canId,
                    dataHex     = frame.dataHex,
                    bus         = frame.bus,
                    isExtended  = frame.isExtended,
                    sourceFile  = fileName
                )
            }

            entities.chunked(500).forEachIndexed { i, batch ->
                canFrameDao.insertAll(batch)
                val progress = (i + 1).toFloat() / (entities.size / 500f + 1)
                _importState.value = ImportState.Loading(progress.coerceIn(0f, 1f))
            }

            Log.i(TAG, "✅ Importados ${frames.size} frames (sessionId=$sessionId)")
            _importState.value = ImportState.Success(sessionId, frames.size, format)
            sessionId

        } catch (e: Exception) {
            val msg = "Error al importar: ${e.message}"
            Log.e(TAG, msg, e)
            _importState.value = ImportState.Error(msg)
            null
        }
    }

    /**
     * Carga un archivo .dbc desde URI.
     * Los frames se decodificarán automáticamente usando este DBC.
     */
    suspend fun loadDbc(context: Context, fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val content = context.contentResolver.openInputStream(fileUri)
                ?.bufferedReader()
                ?.readText()
                ?: return@withContext false

            _dbcDatabase = DbcParser.parse(content)
            _dbcLoaded.value = _dbcDatabase.isNotEmpty()
            Log.i(TAG, "DBC cargado: ${_dbcDatabase.size} mensajes")
            _dbcLoaded.value
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando DBC: ${e.message}")
            false
        }
    }

    /** Obtiene los frames de una sesión como Flow (reactivo) */
    fun getFramesForSession(sessionId: String): Flow<List<CanFrameEntity>> =
        canFrameDao.getFramesForSession(sessionId)

    /** Consulta paginada para LazyColumn de alto rendimiento */
    suspend fun getFramesPaged(
        sessionId: String,
        minId: Int = 0,
        maxId: Int = 0x1FFFFFFF,
        limit: Int = 200,
        offset: Int = 0
    ): List<CanFrameEntity> =
        canFrameDao.getFramesPaged(sessionId, minId, maxId, limit, offset)

    suspend fun countFrames(sessionId: String): Int = canFrameDao.countFrames(sessionId)

    suspend fun getAllSessionIds(): List<String> = canFrameDao.getAllSessionIds()

    fun resetImportState() { _importState.value = ImportState.Idle }

    // =========================================================================
    // PARSERS POR FORMATO
    // =========================================================================

    private fun detectFormat(lines: List<String>): LogFormat {
        val sample = lines.take(5).joinToString("\n")
        return when {
            // CRTD: empieza con timestamp decimal y luego "R" o "T" o "2"
            sample.contains(Regex("""^\d+\.\d+\s+[RT2]\w+""", RegexOption.MULTILINE)) -> LogFormat.CRTD
            // PCAN .trc: líneas con número de secuencia entre paréntesis
            sample.contains(Regex("""^\s*\d+\)\s+\d+\.\d""", RegexOption.MULTILINE))   -> LogFormat.PCAN_TRC
            // CSV: primera línea con "timestamp" o columnas numéricas separadas por coma
            sample.contains("timestamp", ignoreCase = true) ||
                sample.lines().firstOrNull { !it.startsWith("#") }
                    ?.split(",")?.size?.let { it >= 3 } == true                        -> LogFormat.CSV
            else -> LogFormat.UNKNOWN
        }
    }

    /**
     * Parser CRTD (Canalyzer Real-Time Data)
     * Formato: `<timestamp_s> <type><id> <byte0> <byte1> ...`
     * Ejemplo: `1705316400.123456 2AAAA 8 00 FF 10 20 30 40 50 60`
     */
    private fun parseCrtd(lines: List<String>): List<CanFrame> {
        val frames = mutableListOf<CanFrame>()
        // CRTD: <float_ts> <type+id_hex> <dlc?> <bytes...>
        // type: R1/R2 = CAN 1/2 Rx, T = Tx
        val regex = Regex("""^(\d+\.\d+)\s+([RT]?)([0-9A-Fa-f]+)\s+(\d+)?((?:\s+[0-9A-Fa-f]{2})+)""")

        for (line in lines) {
            if (line.startsWith("#") || line.isBlank()) continue
            val match = regex.find(line.trim()) ?: continue
            val g = match.groupValues
            try {
                val ts     = (g[1].toDouble() * 1_000_000).toLong()  // segundos → microsegundos
                val id     = g[3].toInt(16)
                val isExt  = id > 0x7FF
                val bus    = if (g[2].contains("2")) 1 else 0
                val bytes  = g[5].trim().split(Regex("\\s+"))
                    .filter { it.length == 2 }
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                frames.add(CanFrame(ts, id, bytes, bus, isExt))
            } catch (e: Exception) { /* saltar líneas malformadas */ }
        }
        return frames
    }

    /**
     * Parser CSV genérico.
     * Formato esperado: `timestamp_us,canId_hex,data_hex[,bus]`
     * También acepta: `timestamp_s,canId_dec,data_hex`
     */
    private fun parseCsv(lines: List<String>): List<CanFrame> {
        val frames = mutableListOf<CanFrame>()
        var headerSkipped = false

        for (line in lines) {
            if (line.startsWith("#") || line.isBlank()) continue
            if (!headerSkipped && line.contains("timestamp", ignoreCase = true)) {
                headerSkipped = true; continue
            }
            headerSkipped = true

            val cols = line.split(",").map { it.trim() }
            if (cols.size < 3) continue
            try {
                val ts    = cols[0].toLongOrNull()
                    ?: (cols[0].toDouble() * 1_000_000).toLong()
                val id    = cols[1].toIntOrNull()
                    ?: cols[1].removePrefix("0x").removePrefix("0X").toInt(16)
                val data  = cols[2].replace(" ", "")
                    .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val bus   = cols.getOrNull(3)?.toIntOrNull() ?: 0
                frames.add(CanFrame(ts, id, data, bus, id > 0x7FF))
            } catch (e: Exception) { /* saltar */ }
        }
        return frames
    }

    /**
     * Parser PCAN .trc (PEAK System Trace)
     * Formato (v1.1): `  <seq>)     <time_ms>  <id>  <dlc>  <bytes>`
     * Ejemplo: `  263)      000.1  00A  8  FF 00 AB CD 00 00 00 00`
     */
    private fun parsePcanTrc(lines: List<String>): List<CanFrame> {
        val frames = mutableListOf<CanFrame>()
        // Match:  seq)   time.ms   id   dlc   bytes...
        val regex = Regex(
            """^\s*\d+\)\s+(\d+\.\d+)\s+([0-9A-Fa-f]+)\s+(\d+)\s+((?:[0-9A-Fa-f]{2}\s*)+)"""
        )

        for (line in lines) {
            if (line.startsWith(";") || line.isBlank()) continue
            val match = regex.find(line) ?: continue
            val g = match.groupValues
            try {
                val ts    = (g[1].toDouble() * 1_000).toLong()  // ms → microsegundos
                val id    = g[2].toInt(16)
                val data  = g[4].trim().split(Regex("\\s+"))
                    .filter { it.length == 2 }
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                frames.add(CanFrame(ts, id, data, 0, id > 0x7FF))
            } catch (e: Exception) { /* saltar */ }
        }
        return frames
    }
}

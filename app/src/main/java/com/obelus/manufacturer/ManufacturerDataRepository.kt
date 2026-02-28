package com.obelus.manufacturer

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// ManufacturerDataRepository.kt
// Lectura y decodificación de PIDs específicos de fabricante (Modo 21/22).
// Utiliza la base de datos estática ManufacturerDatabase.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Función de callback inyectada para enviar comandos al ECU.
 * Retorna la respuesta cruda (ej. "62 01 F4 05 DC") o null si no hay conexión.
 */
typealias ElmCommandSender = suspend (cmd: String) -> String?

@Singleton
class ManufacturerDataRepository @Inject constructor(
    private val vinDecoder: VINDecoder
) {

    companion object {
        private const val TAG      = "ManufacturerDataRepo"
        private const val TIMEOUT  = 5_000L             // ms
        private val NO_RESPONSE_MARKERS = setOf(
            "NO DATA", "ERROR", "?", "STOPPED", "UNABLE", "BUS BUSY"
        )
    }

    // Caché de PIDs que no respondieron para evitar consultas repetidas
    private val unavailableCache = LruCache<String, Boolean>(200)

    // ═══════════════════════════════════════════════════════════════════════════
    // Filtrado por vehículo
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retorna los datos disponibles en la base de datos para el vehículo dado.
     * NO hace comunicación con el ECU.
     *
     * @param manufacturer Fabricante detectado.
     * @param model        Modelo del vehículo (null = sin filtro de modelo).
     * @param year         Año del vehículo (null = sin filtro de año).
     */
    fun getSupportedData(
        manufacturer: Manufacturer,
        model: String? = null,
        year: Int? = null
    ): List<ManufacturerData> =
        ManufacturerDatabase.getFor(manufacturer, model, year)

    /**
     * Variante que acepta el VIN directamente.
     * Decodifica fabricante y año desde el VIN automáticamente.
     */
    fun getSupportedDataForVin(vin: String, model: String? = null): List<ManufacturerData> {
        val vinInfo = vinDecoder.decodeVin(vin)
        return getSupportedData(vinInfo.manufacturer, model, vinInfo.modelYear)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lectura individual
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lee un valor específico de fabricante desde el ECU.
     *
     * @param dataId    ID del dato a leer (ej. "TOYOTA_OIL_PRESSURE").
     * @param sender    Función que envía el comando y retorna la respuesta raw.
     * @return          [ManufacturerReading.Value], [ManufacturerReading.NotSupported] o [ManufacturerReading.Error].
     */
    suspend fun readData(
        dataId: String,
        sender: ElmCommandSender
    ): ManufacturerReading = withContext(Dispatchers.IO) {
        val data = ManufacturerDatabase.findById(dataId)
            ?: return@withContext ManufacturerReading.Error(
                ManufacturerData("?", Manufacturer.GENERIC, description = "", mode = "", pid = "",
                    responseParser = ResponseType.SINGLE_BYTE),
                "DataId '$dataId' no encontrado en la base de datos"
            )
        readDataInternal(data, sender)
    }

    /**
     * Lee continuamente una señal y emite actualizaciones.
     *
     * @param dataId    ID del dato.
     * @param intervalMs Intervalo entre lecturas en ms.
     * @param sender    Función de envío de comandos.
     */
    fun readDataContinuous(
        dataId: String,
        intervalMs: Long = 500L,
        sender: ElmCommandSender
    ): Flow<ManufacturerReading> = flow {
        val data = ManufacturerDatabase.findById(dataId) ?: return@flow
        while (true) {
            emit(readDataInternal(data, sender))
            kotlinx.coroutines.delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Escanea todos los datos disponibles para un fabricante y emite resultados.
     * Útil para el "scan completo" de la UI.
     */
    fun scanAll(
        manufacturer: Manufacturer,
        model: String? = null,
        year: Int? = null,
        sender: ElmCommandSender
    ): Flow<ManufacturerReading> = flow {
        val list = getSupportedData(manufacturer, model, year)
        println("[$TAG] Iniciando scan completo: ${list.size} PIDs para ${manufacturer.displayName}")
        for (item in list) {
            emit(readDataInternal(item, sender))
        }
    }.flowOn(Dispatchers.IO)

    // ═══════════════════════════════════════════════════════════════════════════
    // Disponibilidad
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retorna si el dataId es conocido en la base de datos estática.
     * NO verifica comunicación real con el ECU.
     */
    fun isDataAvailable(dataId: String): Boolean =
        ManufacturerDatabase.findById(dataId) != null

    /**
     * Verifica si el ECU responde al PID consultando el caché de no-respuestas.
     * Si no está en caché, considera que podría estar disponible.
     */
    fun isEcuResponding(dataId: String): Boolean =
        unavailableCache.get(dataId) != true

    // ═══════════════════════════════════════════════════════════════════════════
    // Decodificación
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Decodifica los bytes brutos del ECU a Float usando la estrategia de [ResponseType].
     *
     * @param data  Definición del dato con parser y scale/offset.
     * @param rawBytes  Bytes de datos (sin el echo ni el modo de respuesta).
     * @return Valor flotante decodificado.
     */
    fun decodeResponse(data: ManufacturerData, rawBytes: ByteArray): Float {
        if (rawBytes.isEmpty()) return Float.NaN
        val raw = try {
            when (data.responseParser) {
                ResponseType.SINGLE_BYTE        -> (rawBytes[0].toInt() and 0xFF).toFloat()
                ResponseType.TWO_BYTES_BIG_ENDIAN -> {
                    if (rawBytes.size < 2) return Float.NaN
                    ((rawBytes[0].toInt() and 0xFF) * 256 +
                     (rawBytes[1].toInt() and 0xFF)).toFloat()
                }
                ResponseType.TWO_BYTES_LITTLE_ENDIAN -> {
                    if (rawBytes.size < 2) return Float.NaN
                    ((rawBytes[1].toInt() and 0xFF) * 256 +
                     (rawBytes[0].toInt() and 0xFF)).toFloat()
                }
                ResponseType.FOUR_BYTES -> {
                    if (rawBytes.size < 4) return Float.NaN
                    ((rawBytes[0].toInt() and 0xFF).toLong() shl 24 or
                     ((rawBytes[1].toInt() and 0xFF).toLong() shl 16) or
                     ((rawBytes[2].toInt() and 0xFF).toLong() shl 8) or
                     (rawBytes[3].toInt() and 0xFF).toLong()).toFloat()
                }
                ResponseType.BITFIELD,
                ResponseType.ENCODED    -> (rawBytes[0].toInt() and 0xFF).toFloat()
            }
        } catch (e: Exception) {
            println("[$TAG] decodeResponse error: ${e.message}")
            return Float.NaN
        }
        return raw * data.scale + data.offset
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers internos
    // ═══════════════════════════════════════════════════════════════════════════

    private suspend fun readDataInternal(
        data: ManufacturerData,
        sender: ElmCommandSender
    ): ManufacturerReading {
        val cmd = data.buildRequest()
        return try {
            val rawResponse = sender(cmd)?.trim() ?: ""

            // Detectar no-respuesta
            val noResponse = rawResponse.isBlank() ||
                NO_RESPONSE_MARKERS.any { rawResponse.uppercase().contains(it) }

            if (noResponse) {
                unavailableCache.put(data.dataId, true)
                println("[$TAG] Sin respuesta → ${data.dataId}")
                return ManufacturerReading.NotSupported(data)
            }

            // Parsear hex → bytes (saltar modo + PID echo: 3 tokens)
            val tokens  = rawResponse.trim().split("\\s+".toRegex())
            val dataBytes = tokens.drop(3)
                .mapNotNull { it.toIntOrNull(16)?.and(0xFF) }
                .map { it.toByte() }
                .toByteArray()

            val decoded = decodeResponse(data, dataBytes)
            if (decoded.isNaN()) {
                return ManufacturerReading.Error(data, "Error al decodificar respuesta: $rawResponse")
            }

            val display = formatValue(data, decoded)
            val isNormal = when {
                data.normalMin != null && decoded < data.normalMin -> false
                data.normalMax != null && decoded > data.normalMax -> false
                else -> true
            }
            println("[$TAG] ${data.dataId} = $display (normal=$isNormal)")
            ManufacturerReading.Value(data, dataBytes, decoded, display, isNormal)

        } catch (e: Exception) {
            println("[$TAG] Error leyendo ${data.dataId}: ${e.message}")
            ManufacturerReading.Error(data, e.message ?: "Error desconocido")
        }
    }

    private fun formatValue(data: ManufacturerData, decoded: Float): String {
        return when (data.responseParser) {
            ResponseType.ENCODED -> {
                val idx = decoded.toInt()
                data.encodingTable[idx] ?: "Índice $idx"
            }
            ResponseType.BITFIELD -> {
                val bits = decoded.toInt()
                data.encodingTable[bits] ?: "0b${Integer.toBinaryString(bits).padStart(8, '0')}"
            }
            else -> {
                val fmt = if (data.scale < 0.01f) "%.4f" else if (data.scale < 0.1f) "%.2f" else "%.1f"
                "${fmt.format(decoded)} ${data.unit}".trim()
            }
        }
    }
}

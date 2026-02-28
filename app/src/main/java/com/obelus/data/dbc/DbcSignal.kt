package com.obelus.data.dbc

// ─────────────────────────────────────────────────────────────────────────────
// Endian.kt (alias local – evita acoplamiento con com.obelus.data.local.model)
// ─────────────────────────────────────────────────────────────────────────────
// NOTE: Se usa el enum ya existente en com.obelus.data.local.model, por lo que
// este archivo NO se crea; DbcSignal importará desde ese paquete directamente.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// DbcSignal.kt
// Representación en memoria de una señal CAN leída de un archivo .dbc.
// Pure Kotlin – sin dependencias de Android ni de Room.
// ─────────────────────────────────────────────────────────────────────────────

import com.obelus.data.local.model.Endian

/**
 * Señal CAN definida dentro de un [DbcMessage] en un archivo .dbc.
 *
 * Formato de referencia (Vector/PEAK DBC):
 * ```
 * SG_ SignalName : startBit|length@byteOrder(+/-) (scale,offset) [min|max] "unit" Receiver
 * ```
 * Donde:
 *  - `@1` = Intel/Little-Endian, `@0` = Motorola/Big-Endian
 *  - `+`  = unsigned, `-` = signed
 *
 * @param name        Identificador único de la señal dentro del mensaje.
 * @param messageId   CAN ID del [DbcMessage] al que pertenece.
 * @param startBit    Bit inicial (0-63) según convención del byte order.
 * @param bitLength   Número de bits (1-64).
 * @param endianness  [Endian.LITTLE] = Intel (@1), [Endian.BIG] = Motorola (@0).
 * @param signed      `true` si la señal puede tener valor negativo.
 * @param scale       Factor de escala física: valor_físico = raw * scale + offset.
 * @param offset      Desplazamiento físico.
 * @param min         Valor físico mínimo declarado en el .dbc (puede ser null).
 * @param max         Valor físico máximo declarado en el .dbc (puede ser null).
 * @param unit        Unidad física ("km/h", "°C", etc.), null si no se declaró.
 * @param receiver    Nodo(s) receptor(es) separados por coma (ej. "ECM,TCM").
 * @param comment     Comentario asociado (sección CM_), null si no existe.
 */
data class DbcSignal(
    val name: String,
    val messageId: Long,
    val startBit: Int,
    val bitLength: Int,
    val endianness: Endian,
    val signed: Boolean,
    val scale: Double,
    val offset: Double,
    val min: Double? = null,
    val max: Double? = null,
    val unit: String? = null,
    val receiver: String = "",
    val comment: String? = null
) {
    // ── Helpers de decodificación ────────────────────────────────────────────

    /**
     * Extrae el valor crudo (entero) de la señal a partir del payload CAN.
     *
     * @param payload ByteArray con hasta 8 bytes del frame CAN.
     * @return Valor entero sin signo (o con signo si [signed] = true).
     */
    fun extractRaw(payload: ByteArray): Long {
        var raw = 0L
        if (endianness == Endian.LITTLE) {
            // Intel/Little-Endian
            for (i in 0 until bitLength) {
                val bitPos   = startBit + i
                val byteIdx  = bitPos / 8
                val bitIdx   = bitPos % 8
                if (byteIdx < payload.size) {
                    val bit = (payload[byteIdx].toInt() ushr bitIdx) and 1
                    raw = raw or (bit.toLong() shl i)
                }
            }
        } else {
            // Motorola/Big-Endian
            var bitPos = startBit
            for (i in 0 until bitLength) {
                val byteIdx = bitPos / 8
                val bitIdx  = 7 - (bitPos % 8)
                if (byteIdx < payload.size) {
                    val bit = (payload[byteIdx].toInt() ushr bitIdx) and 1
                    raw = (raw shl 1) or bit.toLong()
                }
                bitPos = if ((bitPos % 8) == 0) bitPos + 15 else bitPos - 1
            }
        }

        // Extensión de signo
        if (signed && bitLength > 0) {
            val signBit = 1L shl (bitLength - 1)
            if (raw and signBit != 0L) raw -= signBit shl 1
        }
        return raw
    }

    /**
     * Decodifica el payload CAN al valor físico aplicando escala y offset.
     */
    fun decode(payload: ByteArray): Double = extractRaw(payload) * scale + offset

    /**
     * Devuelve el valor físico formateado con su unidad.
     */
    fun format(payload: ByteArray): String {
        val v = decode(payload)
        return if (unit.isNullOrBlank()) "%.3f".format(v) else "%.3f $unit".format(v)
    }

    /** Rango de bits ocupados por esta señal en el frame (para detección de overlap). */
    fun bitRange(): IntRange = startBit until (startBit + bitLength)
}

package com.obelus.data.protocol

import android.util.Log

/**
 * Señal CAN decodificada desde un archivo .dbc
 */
data class DbcSignal(
    val name: String,
    val startBit: Int,
    val bitLength: Int,
    val factor: Double,
    val offset: Double,
    val min: Double,
    val max: Double,
    val unit: String,
    val isSigned: Boolean,
    val isBigEndian: Boolean  // True = Motorola/Big-Endian, False = Intel/Little-Endian
) {
    /**
     * Decodifica el valor de esta señal a partir del payload de un frame CAN.
     * @param data ByteArray con el payload del frame (max 8 bytes)
     * @return Valor físico decodificado
     */
    fun decode(data: ByteArray): Double {
        val rawValue = extractRawValue(data)
        return rawValue * factor + offset
    }

    /**
     * Formatea el valor decodificado con su unidad.
     */
    fun formatValue(data: ByteArray): String {
        val value = decode(data)
        return if (unit.isEmpty()) "%.3f".format(value) else "%.3f %s".format(value, unit)
    }

    private fun extractRawValue(data: ByteArray): Long {
        var rawValue = 0L
        if (isBigEndian) {
            // Motorola byte order
            var bitPos = startBit
            for (i in 0 until bitLength) {
                val byteIndex = bitPos / 8
                val bitIndex  = 7 - (bitPos % 8)
                if (byteIndex < data.size) {
                    val bit = (data[byteIndex].toInt() ushr bitIndex) and 1
                    rawValue = (rawValue shl 1) or bit.toLong()
                }
                bitPos = if ((bitPos % 8) == 0) bitPos + 15 else bitPos - 1
            }
        } else {
            // Intel byte order (little-endian)
            for (i in 0 until bitLength) {
                val bitPos    = startBit + i
                val byteIndex = bitPos / 8
                val bitIndex  = bitPos % 8
                if (byteIndex < data.size) {
                    val bit = (data[byteIndex].toInt() ushr bitIndex) and 1
                    rawValue = rawValue or (bit.toLong() shl i)
                }
            }
        }

        // Sign extension si es señal signed
        if (isSigned && bitLength > 0) {
            val signBit = 1L shl (bitLength - 1)
            if (rawValue and signBit != 0L) {
                rawValue = rawValue - (signBit shl 1)
            }
        }
        return rawValue
    }
}

/**
 * Mensaje CAN definido en el .dbc
 */
data class DbcMessage(
    val id: Int,             // CAN ID (sin el bit EFF)
    val name: String,
    val dlc: Int,            // Data Length Code (0-8)
    val signals: List<DbcSignal>
)

/**
 * Parser para archivos .dbc (CAN Database files).
 *
 * Soporta el formato estándar Vector/PEAK:
 * ```
 * BO_ 1234 MessageName: 8 Vector__XXX
 *  SG_ SignalName : 0|16@1+ (0.1,0) [0|100] "km/h" Vector__XXX
 * ```
 *
 * Formato compacto del atributo SG_:
 * `SG_ name : startBit|length@byteOrder(+/-) (factor,offset) [min|max] "unit" receivers`
 */
object DbcParser {
    private const val TAG = "DbcParser"

    // Regex para parsear línea BO_ (Message)
    // BO_ <id> <name>: <dlc> <sender>
    private val MSG_REGEX = Regex("""^BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)\s+\w+""")

    // Regex para parsear línea SG_ (Signal)
    // SG_ <name> : <startBit>|<length>@<byteOrder><signed> (<factor>,<offset>) [<min>|<max>] "<unit>"
    private val SIG_REGEX = Regex(
        """^\s*SG_\s+(\w+)\s+:\s*(\d+)\|(\d+)@([01])([+-])\s*\(([-\d.Ee+]+),([-\d.Ee+]+)\)\s*\[([-\d.Ee+]+)\|([-\d.Ee+]+)\]\s*"([^"]*)""""
    )

    /**
     * Parsea un archivo .dbc y retorna un mapa ID → DbcMessage.
     * @param content Contenido completo del archivo .dbc como String
     */
    fun parse(content: String): Map<Int, DbcMessage> {
        val messages = mutableMapOf<Int, DbcMessage>()
        var currentMsg: Triple<Int, String, Int>? = null  // (id, name, dlc)
        val currentSignals = mutableListOf<DbcSignal>()

        fun saveCurrentMessage() {
            currentMsg?.let { (id, name, dlc) ->
                messages[id] = DbcMessage(id, name, dlc, currentSignals.toList())
            }
            currentSignals.clear()
            currentMsg = null
        }

        for (line in content.lines()) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("BO_ ") -> {
                    saveCurrentMessage()
                    MSG_REGEX.find(trimmed)?.let { match ->
                        val id   = match.groupValues[1].toIntOrNull() ?: return@let
                        val name = match.groupValues[2]
                        val dlc  = match.groupValues[3].toIntOrNull() ?: 8
                        currentMsg = Triple(id, name, dlc)
                    }
                }

                trimmed.startsWith("SG_ ") -> {
                    SIG_REGEX.find(trimmed)?.let { match ->
                        val g = match.groupValues
                        try {
                            currentSignals.add(
                                DbcSignal(
                                    name         = g[1],
                                    startBit     = g[2].toInt(),
                                    bitLength    = g[3].toInt(),
                                    isBigEndian  = g[4] == "0",  // 0 = Motorola, 1 = Intel
                                    isSigned     = g[5] == "-",
                                    factor       = g[6].toDouble(),
                                    offset       = g[7].toDouble(),
                                    min          = g[8].toDouble(),
                                    max          = g[9].toDouble(),
                                    unit         = g[10]
                                )
                            )
                        } catch (e: NumberFormatException) {
                            Log.w(TAG, "Error parseando señal: ${g[1]}: ${e.message}")
                        }
                    }
                }

                // Fin de bloque (línea vacía o nueva sección) — guardar msg acumulado
                trimmed.isEmpty() || trimmed.startsWith("CM_") || trimmed.startsWith("BA_") -> {
                    if (currentMsg != null) saveCurrentMessage()
                }
            }
        }
        saveCurrentMessage() // Último mensaje

        Log.i(TAG, "DBC parseado: ${messages.size} mensajes, ${messages.values.sumOf { it.signals.size }} señales")
        return messages
    }
}

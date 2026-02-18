package com.obelus.data.obd2

import java.util.Locale

/**
 * Decodes raw OBD2 responses into usable [ObdReading] objects.
 * Supports standard PID decoding formulas.
 */
object Obd2Decoder {

    private const val MODE_01_RESPONSE_PREFIX = "41"

    // PID 010C - Engine RPM
    // Fórmula: ((A * 256) + B) / 4
    // Respuesta: 41 0C [A] [B]
    val RPM = PidDefinition(
        pid = "0C",
        name = "Engine RPM",
        description = "Engine Revolutions Per Minute",
        unit = "rpm",
        minValue = 0f,
        maxValue = 16383.75f,
        decoder = { data ->
            require(data.size >= 2) { "RPM requires 2 bytes" }
            ((data[0].toInt() and 0xFF) * 256 + (data[1].toInt() and 0xFF)) / 4f
        }
    )

    // PID 010D - Vehicle Speed
    // Fórmula: A
    // Respuesta: 41 0D [A]
    val SPEED = PidDefinition(
        pid = "0D",
        name = "Vehicle Speed",
        description = "Vehicle Speed",
        unit = "km/h",
        minValue = 0f,
        maxValue = 255f,
        decoder = { data ->
            require(data.size >= 1) { "Speed requires 1 byte" }
            (data[0].toInt() and 0xFF).toFloat()
        }
    )

    // PID 0105 - Engine Coolant Temperature
    // Fórmula: A - 40
    // Respuesta: 41 05 [A]
    val COOLANT_TEMP = PidDefinition(
        pid = "05",
        name = "Coolant Temperature",
        description = "Engine Coolant Temperature",
        unit = "°C",
        minValue = -40f,
        maxValue = 215f,
        decoder = { data ->
            require(data.size >= 1) { "Temp requires 1 byte" }
            (data[0].toInt() and 0xFF) - 40f
        }
    )

    // PID 0104 - Calculated Engine Load
    // Fórmula: (A * 100) / 255
    // Respuesta: 41 04 [A]
    val ENGINE_LOAD = PidDefinition(
        pid = "04",
        name = "Calculated Engine Load",
        description = "Calculated Engine Load",
        unit = "%",
        minValue = 0f,
        maxValue = 100f,
        decoder = { data ->
            require(data.size >= 1) { "Load requires 1 byte" }
            ((data[0].toInt() and 0xFF) * 100f) / 255f
        }
    )

    // PID 0111 - Throttle Position
    // Fórmula: (A * 100) / 255
    // Respuesta: 41 11 [A]
    val THROTTLE_POS = PidDefinition(
        pid = "11",
        name = "Throttle Position",
        description = "Absolute Throttle Position",
        unit = "%",
        minValue = 0f,
        maxValue = 100f,
        decoder = { data ->
            require(data.size >= 1) { "Throttle requires 1 byte" }
            ((data[0].toInt() and 0xFF) * 100f) / 255f
        }
    )

    // Mapa de todos los PIDs soportados
    val SUPPORTED_PIDS = mapOf(
        "0C" to RPM,
        "0D" to SPEED,
        "05" to COOLANT_TEMP,
        "04" to ENGINE_LOAD,
        "11" to THROTTLE_POS
    )

    /**
     * Decodifica respuesta cruda del ELM327
     * @param rawResponse Respuesta completa ej: "41 0C 1B 56"
     * @return ObdReading con valor decodificado o null si error
     */
    fun decode(rawResponse: String): ObdReading? {
        try {
            // 1. Clean response: remove spaces, trim, uppercase
            val cleanResponse = rawResponse.replace(" ", "").trim().uppercase(Locale.ROOT)

            // 2. Basic validation: Must start with 41 (Mode 01 Response)
            if (!cleanResponse.startsWith(MODE_01_RESPONSE_PREFIX)) {
                return null
            }

            // 3. Extract PID (next 2 chars)
            if (cleanResponse.length < 4) return null
            val pidHex = cleanResponse.substring(2, 4)

            // 4. Find definition
            val definition = SUPPORTED_PIDS[pidHex] ?: return null

            // 5. Extract data bytes
            // Remaining string part. Note: ignore trailing checksum or garbage if any?
            // Usually ELM returns just the data bytes after the PID. 
            // "41 0C 1B 56" -> clean "410C1B56" -> subs(4) = "1B56"
            val dataHex = cleanResponse.substring(4)
            
            // Convert to byte array
            val dataBytes = hexStringToByteArray(dataHex)

            // 6. Decode
            val value = definition.decoder(dataBytes)

            return ObdReading(
                pid = definition.pid,
                name = definition.name,
                value = value,
                unit = definition.unit,
                rawData = rawResponse,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Convierte string hex a ByteArray
     * Ej: "1B 56" -> byteArrayOf(0x1B, 0x56)
     * Handles variable length
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        // If odd length, might be an issue, but let's assume valid pairs for now or drop last nibble logic if needed
        // For robustness, parse pairs.
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4)
                    + Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

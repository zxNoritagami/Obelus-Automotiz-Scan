package com.obelus.protocol

import com.obelus.data.model.DTC
import com.obelus.data.model.DTCCategory
import com.obelus.data.model.DTCSeverity
import com.obelus.data.model.COMMON_DTCS

/**
 * Utility for decoding Diagnostic Trouble Codes (DTCs) from raw OBD2 responses.
 */
object DTCDecoder {

    private const val MODE_03_RESPONSE = "43"
    private const val MODE_07_RESPONSE = "47"
    private const val MODE_0A_RESPONSE = "4A"
    private val VALID_MODES = listOf(MODE_03_RESPONSE, MODE_07_RESPONSE, MODE_0A_RESPONSE)

    /**
     * Decodifica respuesta de modo 03, 07 o 0A
     * Respuesta ejemplo: "43 01 33 00 00 00 00" -> P0133
     */
    fun decodeDTCs(rawResponse: String): List<DTC> {
        // Limpiar espacios y normalizar
        val cleanedResponse = rawResponse.replace(" ", "").uppercase()
        
        // Validación básica de longitud (al menos modo + cantidad = 2 bytes = 4 caracteres)
        if (cleanedResponse.length < 4) return emptyList()

        // Verificar respuesta positiva (43, 47, 4A)
        val mode = cleanedResponse.substring(0, 2)
        if (mode !in VALID_MODES) return emptyList()

        val dtcList = mutableListOf<DTC>()
        
        // El resto de la respuesta son los DTCs (cada DTC son 4 caracteres hex = 2 bytes)
        // A veces el segundo byte es la cantidad de DTCs, o la cantidad de bytes. 
        // En CAN (ISO 15765), el formato suele ser: 43 [Cant DTCs] [DTC1] [DTC2]...
        // Pero en implementaciones simples de ELM327, a veces devuelve directamente los bytes.
        // Asumiremos formato estándar OBD2 sobre CAN: 
        // Byte 0: Mode response (43)
        // Byte 1: Number of DTCs (a veces no presente en modos legacy)
        // Bytes 2..N: DTCs (2 bytes cada uno)
        
        // Estrategia robusta: Ignorar el primer byte (43) y procesar pares de bytes (4 caracteres)
        // Ignorando bytes de relleno (00) al final si es necesario.
        
        var startIndex = 2 // Saltar "43"
        
        // Intentar detectar si el siguiente byte es un contador o parte de un DTC.
        // Un DTC nunca empieza con 00 si es un código válido almacenado estándar (P0000 es raro como primer código).
        // Sin embargo, para seguridad, procesaremos bloques de 4 caracteres.
        
        while (startIndex + 3 < cleanedResponse.length) {
            val byte1Hex = cleanedResponse.substring(startIndex, startIndex + 2)
            val byte2Hex = cleanedResponse.substring(startIndex + 2, startIndex + 4)
            
            // Avanzar cursor
            startIndex += 4
            
            // Si encontramos 00 00, es padding o fin de lista en muchos casos
            if (byte1Hex == "00" && byte2Hex == "00") continue
            
            try {
                val byte1 = byte1Hex.toInt(16)
                val byte2 = byte2Hex.toInt(16)
                
                val code = bytesToDTC(byte1, byte2)
                
                // Mapear información
                val description = COMMON_DTCS[code] ?: "Código de error desconocido"
                val category = getCategoryFromCode(code)
                
                dtcList.add(
                    DTC(
                        code = code,
                        category = category,
                        description = description,
                        severity = DTCSeverity.MEDIUM // Por defecto
                    )
                )
            } catch (e: NumberFormatException) {
                // Ignorar datos corruptos
                continue
            }
        }
        
        return dtcList
    }

    private fun bytesToDTC(byte1: Int, byte2: Int): String {
        // Bits 7-6: Categoría
        val categoryIndex = (byte1 shr 6) and 0x03
        val categoryChar = when (categoryIndex) {
            0 -> 'P' // 00 = Powertrain
            1 -> 'C' // 01 = Chassis
            2 -> 'B' // 10 = Body
            3 -> 'U' // 11 = Network
            else -> 'P'
        }

        // Bits 5-4: Primer dígito (0-3)
        val firstDigit = (byte1 shr 4) and 0x03

        // Bits 3-0: Segundo dígito (0-9, A-F)
        val secondDigitChar = Integer.toHexString(byte1 and 0x0F).uppercase()

        // Byte 2: Tercer y cuarto dígito
        val thirdDigitChar = Integer.toHexString((byte2 shr 4) and 0x0F).uppercase()
        val fourthDigitChar = Integer.toHexString(byte2 and 0x0F).uppercase()

        return "$categoryChar$firstDigit$secondDigitChar$thirdDigitChar$fourthDigitChar"
    }
    
    private fun getCategoryFromCode(code: String): DTCCategory {
        return when (code.firstOrNull()) {
            'P' -> DTCCategory.POWERTRAIN
            'C' -> DTCCategory.CHASSIS
            'B' -> DTCCategory.BODY
            'U' -> DTCCategory.NETWORK
            else -> DTCCategory.POWERTRAIN
        }
    }
}

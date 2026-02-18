package com.obelus.obelusscan.data.protocol

import com.obelus.domain.model.ObdPid
import java.util.Locale

/**
 * Maneja la construcción de comandos y el parseo de respuestas OBD2
 * utilizando modelos de dominio fuertemente tipados.
 */
class ObdProtocol {

    /**
     * Construye el comando hexadecimal para solicitar un PID.
     * Ejemplo: ATZ + 010C (para RPM)
     * Nota: En esta implementación básica, asumimos el modo 01 estándar.
     */
    fun buildCommand(pid: ObdPid): String {
        // Estándar: "01" + PID Code
        // Ejemplo resultado: "010C"
        return "01${pid.pidCode}"
    }

    /**
     * Parsea la respuesta cruda del ELM327 utilizando la lógica del PID.
     * 
     * @param pid El PID que se solicitó
     * @param rawResponse Respuesta cruda, ej: "41 0C 0B 54"
     * @return El valor físico calculado (Float)
     * @throws IllegalArgumentException si la respuesta es inválida
     */
    fun parseResponse(pid: ObdPid, rawResponse: String): Float {
        // 1. Limpieza básica
        val cleanResponse = rawResponse.replace(" ", "").trim().uppercase(Locale.ROOT)
        
        // 2. Validar prefijo de respuesta positiva (41 + PID)
        // Modo 01 request (01) -> Modo 01 response (41)
        val expectedPrefix = "41${pid.pidCode}"
        
        if (!cleanResponse.startsWith(expectedPrefix)) {
             // Podría ser un error, NO DATA, o respuesta multilínea no manejada aún
             throw IllegalArgumentException("Respuesta inválida para PID ${pid.pidCode}: $rawResponse")
        }

        // 3. Extraer bytes de datos
        // La respuesta "41 0C AA BB" tiene 4 bytes de encabezado (4 chars)
        // El resto son datos.
        val dataHex = cleanResponse.substring(4)
        
        if (dataHex.isEmpty()) {
            return 0f
        }

        // 4. Convertir a lista de enteros
        val bytes = hexStringToBytes(dataHex)

        // 5. Calcular valor usando la fórmula del PID
        return pid.calculate(bytes)
    }

    private fun hexStringToBytes(hexString: String): List<Int> {
        val result = mutableListOf<Int>()
        var i = 0
        while (i < hexString.length - 1) {
            val byteHex = hexString.substring(i, i + 2)
            try {
                result.add(Integer.parseInt(byteHex, 16))
            } catch (e: NumberFormatException) {
                // Si hay basura, ignoramos o devolvemos lo que tenemos
            }
            i += 2
        }
        return result
    }
}

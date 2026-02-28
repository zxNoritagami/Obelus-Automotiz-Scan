package com.obelus.protocol.utils

// ─────────────────────────────────────────────────────────────────────────────
// ProtocolUtils.kt
// Extensiones de utilidad para conversión hexadecimal en la capa de protocolo.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convierte un ByteArray a su representación hexadecimal en mayúsculas con espacios.
 * Ej: byteArrayOf(0x41, 0x00) → "41 00"
 */
fun ByteArray.toHex(): String =
    joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

/**
 * Convierte un ByteArray a su representación hexadecimal sin separadores.
 * Ej: byteArrayOf(0x41, 0x00) → "4100"
 */
fun ByteArray.toHexCompact(): String =
    joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }

/**
 * Convierte una cadena hexadecimal (con o sin espacios) a ByteArray.
 * Ej: "41 00 BE 3F E8 11" → byteArrayOf(0x41, 0x00, 0xBE.toByte(), ...)
 * Ignora caracteres no-hex (espacios, saltos de línea) de forma segura.
 */
fun String.hexToBytes(): ByteArray {
    val clean = filter { it.isLetterOrDigit() }
    if (clean.length % 2 != 0) {
        println("[WARN] hexToBytes: longitud impar en '$this', ignorando último nibble.")
    }
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

/**
 * Devuelve la representación hexadecimal de un Int con el número de dígitos indicado.
 * Ej: 255.toHex(4) → "00FF"
 */
fun Int.toHex(digits: Int = 2): String =
    "%0${digits}X".format(this)

/**
 * Devuelve true si la cadena parece ser una respuesta positiva OBD2 para el servicio dado.
 * Ej: "41 00 BE 3F E8 11".isPositiveResponse(0x01) → true
 */
fun String.isPositiveResponse(service: Int): Boolean {
    val responseService = (service + 0x40).toHex(2)
    return replace(" ", "").uppercase().contains(responseService)
}

/**
 * Convierte una respuesta OBD2 cruda en lista de bytes de datos (sin PID ni service byte).
 * Ej: "41 00 BE 3F E8 11" → [0xBE, 0x3F, 0xE8, 0x11] (bytes a partir del byte 3)
 */
fun String.extractOBD2DataBytes(serviceResponse: Int, pidByteCount: Int = 1): ByteArray {
    val bytes = replace(" ", "").hexToBytes()
    val skipBytes = 1 + pidByteCount // service byte + PID byte(s)
    return if (bytes.size > skipBytes) bytes.drop(skipBytes).toByteArray() else byteArrayOf()
}

// ─────── Verificación en consola ───────────────────────────────────────────
fun main() {
    val sample = byteArrayOf(0x41, 0x00, 0xBE.toByte(), 0x3F, 0xE8.toByte(), 0x11)
    println("toHex()        : ${sample.toHex()}")
    println("toHexCompact() : ${sample.toHexCompact()}")
    println("hexToBytes()   : ${"41 00 BE 3F E8 11".hexToBytes().toHex()}")
    println("255.toHex(4)   : ${255.toHex(4)}")
    println("[OK] ProtocolUtils.kt (extensiones hex)")
}

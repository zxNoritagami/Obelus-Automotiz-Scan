package com.obelus.protocol

// ─────────────────────────────────────────────────────────────────────────────
// OBD2Response.kt
// Sealed class que modela todas las respuestas posibles de un comando OBD2.
// Pure Kotlin – sin dependencias de Android.
// ─────────────────────────────────────────────────────────────────────────────

sealed class OBD2Response {

    /**
     * El comando se ejecutó correctamente.
     * @param data  Bytes de datos devueltos (sin encabezado de servicio ni PID).
     * @param raw   Cadena hexadecimal cruda tal como llegó del ELM327.
     */
    data class Success(
        val data: ByteArray,
        val raw: String
    ) : OBD2Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            if (!data.contentEquals(other.data)) return false
            if (raw != other.raw) return false
            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + raw.hashCode()
            return result
        }

        override fun toString(): String =
            "OBD2Response.Success(raw=\"$raw\", bytes=${data.size})"
    }

    /**
     * El vehículo o el ELM327 devolvió un error explícito.
     * @param message Descripción legible del error.
     * @param raw     Cadena cruda si está disponible (puede ser null).
     */
    data class Error(
        val message: String,
        val raw: String? = null
    ) : OBD2Response() {
        override fun toString(): String =
            "OBD2Response.Error(message=\"$message\", raw=$raw)"
    }

    /**
     * El vehículo respondió "NO DATA" o "?" — sin datos disponibles para el PID.
     */
    object NoData : OBD2Response() {
        override fun toString(): String = "OBD2Response.NoData"
    }

    /**
     * No se recibió ninguna respuesta dentro del tiempo de espera máximo.
     */
    object Timeout : OBD2Response() {
        override fun toString(): String = "OBD2Response.Timeout"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Devuelve true si la respuesta indica éxito. */
    fun isSuccess(): Boolean = this is Success

    /** Devuelve true si hubo algún fallo (error, sin datos o timeout). */
    fun isFailure(): Boolean = this !is Success

    /** Devuelve los bytes de datos en caso de éxito, null en cualquier otro caso. */
    fun dataOrNull(): ByteArray? = (this as? Success)?.data

    /** Devuelve la cadena cruda en caso de éxito, null en cualquier otro caso. */
    fun rawOrNull(): String? = (this as? Success)?.raw
}

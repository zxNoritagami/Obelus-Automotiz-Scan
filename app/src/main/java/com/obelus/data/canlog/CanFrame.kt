package com.obelus.data.canlog

/**
 * Representación en memoria de un frame CAN importado.
 * Formato agnóstico — compatible con CRTD, CSV y PCAN .trc
 */
data class CanFrame(
    val timestamp: Long,       // Microsegundos desde inicio del log
    val canId: Int,            // CAN ID (11 o 29 bits)
    val data: ByteArray,       // Payload (0–8 bytes)
    val bus: Int = 0,          // Bus index (0, 1, …)
    val isExtended: Boolean = false,  // True = 29-bit ID (EFF)
    val isRemote: Boolean = false     // True = RTR frame
) {
    /** CAN ID formateado en HEX con padding según longitud */
    val canIdHex: String get() = if (isExtended)
        "%08X".format(canId) else "%03X".format(canId)

    /** Payload formateado en HEX separado por espacios */
    val dataHex: String get() = data.joinToString(" ") { "%02X".format(it) }

    /** Timestamp relativo en formato "S.sss" (segundos.milisegundos) */
    fun relativeTimestamp(baseTimestamp: Long): String {
        val deltaMicros = timestamp - baseTimestamp
        val totalMs = deltaMicros / 1_000.0
        val seconds = (totalMs / 1000).toLong()
        val ms = (totalMs % 1000).toInt()
        return "%d.%03d".format(seconds, ms)
    }

    // ByteArray necesita equals/hashCode manual
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        return timestamp == other.timestamp && canId == other.canId &&
               data.contentEquals(other.data) && bus == other.bus
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + canId
        result = 31 * result + data.contentHashCode()
        result = 31 * result + bus
        return result
    }
}

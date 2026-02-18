package com.obelus.protocol

object DTCCommand {
    const val GET_CURRENT_DTCS = "03"      // Códigos actuales (check engine on)
    const val GET_PENDING_DTCS = "07"      // Códigos pendientes
    const val GET_PERMANENT_DTCS = "0A"    // Códigos permanentes
    const val CLEAR_DTCS = "04"            // Borrar códigos (requiere confirmación)
}

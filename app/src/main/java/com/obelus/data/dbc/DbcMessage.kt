package com.obelus.data.dbc

// ─────────────────────────────────────────────────────────────────────────────
// DbcMessage.kt
// Representación de un mensaje CAN (BO_) leído de un archivo .dbc.
// Pure Kotlin – sin dependencias de Android ni de Room.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mensaje CAN definido en un archivo .dbc.
 *
 * Formato de referencia:
 * ```
 * BO_ 1234 EngineData: 8 PowertrainControl
 *  SG_ ...
 *  SG_ ...
 * ```
 *
 * @param id       CAN ID canónico en decimal. Para frames extendidos (29-bit),
 *                 el bit más significativo (0x80000000) se elimina antes de guardar.
 * @param name     Nombre del mensaje tal como aparece en el .dbc.
 * @param length   Longitud del payload en bytes (DLC, 0–8).
 * @param sender   Nodo transmisor declarado en el .dbc.
 * @param signals  Lista de señales contenidas en este mensaje.
 * @param comment  Comentario opcional de la sección CM_.
 * @param isExtended `true` si el ID original tenía el bit de frame extendido.
 */
data class DbcMessage(
    val id: Long,
    val name: String,
    val length: Int,
    val sender: String,
    val signals: List<DbcSignal> = emptyList(),
    val comment: String? = null,
    val isExtended: Boolean = false
) {
    /** ID en hexadecimal (sin prefijo '0x'), cero-padded a 3 u 8 dígitos. */
    val hexId: String
        get() = if (isExtended) "%08X".format(id) else "%03X".format(id)

    /** Busca una señal por nombre (sin distinción de mayúsculas). */
    fun findSignal(signalName: String): DbcSignal? =
        signals.firstOrNull { it.name.equals(signalName, ignoreCase = true) }

    override fun toString(): String =
        "DbcMessage(id=0x$hexId \"$name\" dlc=$length sender=\"$sender\" signals=${signals.size})"
}

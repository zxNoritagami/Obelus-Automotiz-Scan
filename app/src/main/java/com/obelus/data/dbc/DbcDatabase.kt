package com.obelus.data.dbc

// ─────────────────────────────────────────────────────────────────────────────
// DbcDatabase.kt
// Contenedor completo del resultado de parsear un archivo .dbc.
// Pure Kotlin – sin dependencias de Android ni de Room.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Resultado completo del parseo de un archivo .dbc.
 *
 * Agrupa todas las secciones relevantes del formato Vector DBC:
 *  - `VERSION "..."` → [version]
 *  - `NS_ ...`       → [newSymbols]
 *  - `BU_ ...`       → [nodes]
 *  - `BO_ / SG_`     → [messages] (cada uno lleva sus señales embebidas)
 *  - `CM_`           → ya integrado en cada [DbcMessage] y [DbcSignal] como `.comment`
 *  - `BA_`           → [attributes]
 *
 * @param version     Cadena de versión declarada en el .dbc (puede ser vacía).
 * @param newSymbols  Lista de símbolos declarados en la sección NS_.
 * @param nodes       Nodos de red (ECUs) declarados en la sección BU_.
 * @param messages    Todos los mensajes CAN parseados, indexados por ID CAN.
 * @param attributes  Pares clave-valor de la sección BA_ (atributos extendidos).
 * @param sourceFile  Nombre del archivo origen (sin ruta), para trazabilidad.
 * @param parseErrors Lista de errores no fatales encontrados durante el parseo.
 */
data class DbcDatabase(
    val version: String?              = null,
    val newSymbols: List<String>      = emptyList(),
    val nodes: List<String>           = emptyList(),
    val messages: List<DbcMessage>    = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    val sourceFile: String?           = null,
    val parseErrors: List<String>     = emptyList()
) {
    // ── Propiedades de conveniencia ───────────────────────────────────────────

    /** Lista plana de todas las señales en todos los mensajes. */
    val signals: List<DbcSignal>
        get() = messages.flatMap { it.signals }

    /** Total de señales en la base de datos. */
    val signalCount: Int
        get() = signals.size

    /** Busca un mensaje por ID CAN canónico. */
    fun messageById(id: Long): DbcMessage? =
        messages.firstOrNull { it.id == id }

    /** Busca un mensaje por nombre (sin distinción de mayúsculas). */
    fun messageByName(name: String): DbcMessage? =
        messages.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Indica si el parseo fue limpio (sin errores). */
    fun isClean(): Boolean = parseErrors.isEmpty()

    override fun toString(): String =
        "DbcDatabase(version=$version nodes=${nodes.size} msgs=${messages.size} " +
        "signals=$signalCount errors=${parseErrors.size} src=$sourceFile)"
}

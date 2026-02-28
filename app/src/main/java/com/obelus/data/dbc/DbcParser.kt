package com.obelus.data.dbc

import com.obelus.data.local.model.Endian

// ─────────────────────────────────────────────────────────────────────────────
// DbcParser.kt
// Parser robusto para archivos .dbc (formato Vector/PEAK) basado en regex.
// Pure Kotlin – sin dependencias de Android ni librerías externas.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parsea el contenido completo de un archivo .dbc y produce un [DbcDatabase].
 *
 * Secciones soportadas:
 * - `VERSION`  – versión del archivo.
 * - `NS_`      – nuevos símbolos (ignorados funcionalmente, almacenados).
 * - `BU_`      – nodos de red (ECUs).
 * - `BO_`      – mensajes CAN con ID, nombre y DLC.
 * - `SG_`      – señales dentro de cada mensaje.
 * - `CM_`      – comentarios asociados a mensajes y señales.
 * - `BA_`      – atributos extendidos.
 *
 * El parser tolera:
 * - Espacios extra o tabulaciones en cualquier posición.
 * - Líneas vacías entre bloques.
 * - IDs de mensajes con el bit de frame extendido (> 0x7FFFFFFF).
 * - Valores de scale/offset en notación científica (1.5e-3).
 */
object DbcParser {

    // ── Expresiones regulares ─────────────────────────────────────────────────

    /** VERSION "1.0" */
    private val RE_VERSION = Regex("""^\s*VERSION\s+"([^"]*)"""")

    /** NS_ simbolo1 simbolo2 ... (puede ser multilínea, terminamos en siguiente sección) */
    private val RE_NS_LINE = Regex("""^\s{1,}(\S+)\s*$""")

    /** BU_: ECU1 ECU2 ECU3 */
    private val RE_BU = Regex("""^\s*BU_\s*:\s*(.*)""")

    /**
     * BO_ 1234 MessageName: 8 SenderNode
     * Groups: 1=rawId, 2=name, 3=dlc, 4=sender
     */
    private val RE_MSG = Regex("""^\s*BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)\s+(\w+)""")

    /**
     * SG_ SignalName : 0|16@1+ (0.1,0) [0|100] "km/h" ReceiverNode
     * Also handles multiplexing: SG_ Name M : ...  or  SG_ Name m3 : ...
     * Groups: 1=name, 2=startBit, 3=length, 4=byteOrder(0/1), 5=signed(+/-),
     *         6=scale, 7=offset, 8=min, 9=max, 10=unit, 11=receivers
     */
    private val RE_SIG = Regex(
        """^\s*SG_\s+(\w+)\s+(?:[Mm]\d*\s+)?:\s*(\d+)\|(\d+)@([01])([+\-])\s*""" +
        """\(([-\d.Ee+]+),([-\d.Ee+]+)\)\s*\[([-\d.Ee+]*)\|([-\d.Ee+]*)\]\s*"([^"]*)"\s*(.*)"""
    )

    /**
     * CM_ SG_ 123 SignalName "comment text";
     * CM_ BO_ 123 "comment text";
     * Support multi-line comments (rare).
     * Groups: 1=type(SG_/BO_/BU_/EV_), 2=id (optional), 3=name(optional), 4=comment
     */
    private val RE_CM_SG = Regex(
        """^\s*CM_\s+(SG_|BO_)\s+(\d+)\s+(?:(\w+)\s+)?"([^"]*)"\s*;"""
    )

    /**
     * BA_ "AttributeName" BO_ msgId value;
     * Groups: 1=attrName, 2=value
     */
    private val RE_BA = Regex("""^\s*BA_\s+"([^"]+)"\s+(?:BO_|SG_|BU_|EV_)?\s*\d*\s*(\w+)\s*;""")

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Parsea el contenido de un archivo .dbc y devuelve un [DbcDatabase].
     *
     * @param fileContent Contenido completo del archivo como String.
     * @param sourceFile  Nombre del archivo (sólo para trazabilidad, puede ser null).
     * @return [DbcDatabase] con todos los datos parseados y errores no fatales.
     */
    fun parse(fileContent: String, sourceFile: String? = null): DbcDatabase {
        println("[DbcParser] Iniciando parseo${sourceFile?.let { " de \"$it\"" } ?: ""}…")

        val lines      = fileContent.lines()
        val errors     = mutableListOf<String>()
        var version    : String? = null
        val newSymbols = mutableListOf<String>()
        val nodes      = mutableListOf<String>()
        // id → (name, dlc, sender, signals mutable)
        val messages   = mutableMapOf<Long, Triple<DbcMessage, MutableList<DbcSignal>, Boolean>>()
        val attributes = mutableMapOf<String, String>()

        // Estado del parser
        var currentMsgId: Long? = null
        var inNsSection = false

        // ── Paso 1: parsear BO_ / SG_ / VERSION / BU_ / NS_ ─────────────────
        for ((lineNo, rawLine) in lines.withIndex()) {
            val line = rawLine.trimEnd()

            when {
                // — Version —
                RE_VERSION.containsMatchIn(line) -> {
                    version = RE_VERSION.find(line)!!.groupValues[1]
                    println("[DbcParser] VERSION = \"$version\"")
                    inNsSection = false
                }

                // — NS_ Start —
                line.trimStart().startsWith("NS_") -> {
                    inNsSection = true
                    // Símbolos en la misma línea: NS_ : SYM1 SYM2
                    val inline = line.substringAfter("NS_").substringAfter(":").trim()
                    inline.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { newSymbols.add(it) }
                }

                // — NS_ continuation (líneas que empiezan con espacios/tab) —
                inNsSection && line.matches(RE_NS_LINE) -> {
                    val sym = RE_NS_LINE.find(line)!!.groupValues[1]
                    if (!sym.endsWith(":")) newSymbols.add(sym)
                }

                // — BU_ —
                line.trimStart().startsWith("BU_") -> {
                    inNsSection = false
                    RE_BU.find(line)?.groupValues?.get(1)?.let { nodeStr ->
                        nodes.addAll(nodeStr.split(Regex("\\s+")).filter { it.isNotBlank() })
                        println("[DbcParser] BU_: ${nodes.size} nodos → $nodes")
                    }
                }

                // — BO_ (mensaje) —
                line.trimStart().startsWith("BO_") -> {
                    inNsSection = false
                    saveCurrentMessage(messages, currentMsgId, errors)
                    currentMsgId = null

                    RE_MSG.find(line)?.let { m ->
                        val rawId    = m.groupValues[1].toLongOrNull() ?: return@let
                        val name     = m.groupValues[2]
                        val dlc      = m.groupValues[3].toIntOrNull() ?: 8
                        val sender   = m.groupValues[4]

                        val isExtended = rawId > 0x7FFFFFFF
                        val canonId    = if (isExtended) rawId and 0x7FFFFFFF else rawId

                        val msg = DbcMessage(
                            id         = canonId,
                            name       = name,
                            length     = dlc,
                            sender     = sender,
                            isExtended = isExtended
                        )
                        messages[canonId] = Triple(msg, mutableListOf(), isExtended)
                        currentMsgId = canonId
                        println("[DbcParser] BO_ 0x${"%X".format(canonId)} \"$name\" dlc=$dlc")
                    } ?: errors.add("L${lineNo+1}: BO_ no pudo parsearse: \"${line.trim()}\"")
                }

                // — SG_ (señal dentro del mensaje actual) —
                line.trimStart().startsWith("SG_") -> {
                    inNsSection = false
                    val msgId = currentMsgId ?: run {
                        errors.add("L${lineNo+1}: SG_ fuera de BO_: \"${line.trim()}\"")
                        return@let
                    }

                    RE_SIG.find(line)?.let { m ->
                        val g = m.groupValues
                        try {
                            val scale = g[6].toDoubleOrNull() ?: 1.0
                            val signal = DbcSignal(
                                name       = g[1],
                                messageId  = msgId,
                                startBit   = g[2].toInt(),
                                bitLength  = g[3].toInt(),
                                endianness = if (g[4] == "0") Endian.BIG else Endian.LITTLE,
                                signed     = g[5] == "-",
                                scale      = scale,
                                offset     = g[7].toDoubleOrNull() ?: 0.0,
                                min        = g[8].toDoubleOrNull(),
                                max        = g[9].toDoubleOrNull(),
                                unit       = g[10].ifBlank { null },
                                receiver   = g[11].trim()
                            )
                            messages[msgId]?.second?.add(signal)
                        } catch (e: NumberFormatException) {
                            errors.add("L${lineNo+1}: SG_ \"${g[1]}\" – número inválido: ${e.message}")
                        }
                    } ?: errors.add("L${lineNo+1}: SG_ no pudo parsearse: \"${line.trim()}\"")
                }

                // — Fin de bloque BO_ (línea vacía o nueva sección macro) —
                line.isBlank() || line.trimStart().let { t ->
                    t.startsWith("CM_") || t.startsWith("BA_") ||
                    t.startsWith("VAL_") || t.startsWith("SIG_GROUP_") ||
                    t.startsWith("EV_") || t.startsWith("ENVVAR_DATA_")
                } -> {
                    if (line.isBlank()) { inNsSection = false }
                    if (currentMsgId != null && line.isBlank()) {
                        // No cerramos aquí, lo haremos al final o al siguiente BO_
                    }
                }

                else -> inNsSection = false
            }
        }
        // Guardar el último mensaje
        saveCurrentMessage(messages, currentMsgId, errors)

        // ── Paso 2: parsear CM_ (comentarios) ────────────────────────────────
        parsedComments(fileContent, messages, errors)

        // ── Paso 3: parsear BA_ (atributos) ──────────────────────────────────
        parseAttributes(fileContent, attributes)

        // ── Ensamblar lista final de mensajes ─────────────────────────────────
        val finalMessages = messages.values.map { (msg, sigs, _) ->
            msg.copy(signals = sigs.toList())
        }.sortedBy { it.id }

        val db = DbcDatabase(
            version     = version,
            newSymbols  = newSymbols.toList(),
            nodes       = nodes.toList(),
            messages    = finalMessages,
            attributes  = attributes.toMap(),
            sourceFile  = sourceFile,
            parseErrors = errors.toList()
        )

        println("[DbcParser] Parseo completado: ${db.messages.size} mensajes, " +
                "${db.signalCount} señales, ${errors.size} error(es).")
        if (errors.isNotEmpty()) errors.forEach { println("[DbcParser][WARN] $it") }

        return db
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    /** Guarda el mensaje acumulado en el mapa (lo "cierra"). */
    private fun saveCurrentMessage(
        messages: MutableMap<Long, Triple<DbcMessage, MutableList<DbcSignal>, Boolean>>,
        msgId: Long?,
        @Suppress("UNUSED_PARAMETER") errors: MutableList<String>
    ) {
        // El cierre es implícito: las señales ya se agregan al mutable list
        // directamente, así que no hay nada explícito que hacer aquí.
        // Se mantiene el punto de extensión para futuros usos.
    }

    /** Parsea la sección CM_ e inyecta los comentarios en mensajes/señales. */
    private fun parsedComments(
        content: String,
        messages: MutableMap<Long, Triple<DbcMessage, MutableList<DbcSignal>, Boolean>>,
        errors: MutableList<String>
    ) {
        // Regex multi-línea para CM_ (comentarios pueden contener \n dentro de "")
        val cmRegex = Regex(
            """CM_\s+(SG_|BO_)\s+(\d+)\s+(?:(\w+)\s+)?"([\s\S]*?)"\s*;""",
            RegexOption.MULTILINE
        )
        for (m in cmRegex.findAll(content)) {
            val type    = m.groupValues[1]
            val msgId   = m.groupValues[2].toLongOrNull() ?: continue
            val sigName = m.groupValues[3].ifBlank { null }
            val comment = m.groupValues[4].trim()

            val entry = messages[msgId] ?: continue
            when (type) {
                "BO_" -> {
                    val updated = entry.first.copy(comment = comment)
                    messages[msgId] = Triple(updated, entry.second, entry.third)
                }
                "SG_" -> {
                    val sigIdx = entry.second.indexOfFirst {
                        it.name.equals(sigName, ignoreCase = true)
                    }
                    if (sigIdx >= 0) {
                        entry.second[sigIdx] = entry.second[sigIdx].copy(comment = comment)
                    }
                }
            }
        }
    }

    /** Parsea atributos BA_ en pares clave-valor simples. */
    private fun parseAttributes(content: String, out: MutableMap<String, String>) {
        RE_BA.findAll(content).forEach { m ->
            out[m.groupValues[1]] = m.groupValues[2]
        }
        if (out.isNotEmpty()) println("[DbcParser] BA_: ${out.size} atributo(s) encontrado(s).")
    }
}

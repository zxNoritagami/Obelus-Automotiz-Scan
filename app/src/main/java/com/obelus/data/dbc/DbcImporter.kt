package com.obelus.data.dbc

import com.obelus.data.local.dao.DbcDefinitionDao
import com.obelus.data.local.entity.CanSignal
import com.obelus.data.local.entity.DbcDefinition
import com.obelus.data.local.model.Endian
import com.obelus.data.local.model.SignalSource
import java.io.File
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// DbcImporter.kt
// Integración con Room: lee un archivo .dbc, lo parsea y persiste en la BD.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Orquesta la importación completa de archivos .dbc hacia la base de datos Room.
 *
 * Flujo:
 *  1. Leer el archivo desde [filePath].
 *  2. Parsear con [DbcParser].
 *  3. Validar con [DbcValidator] (errores críticos abortan la importación).
 *  4. Convertir cada [DbcSignal] → [CanSignal] Room entity.
 *  5. Persistir [DbcDefinition] + señales en una transacción atómica.
 *
 * @param dbcDefinitionDao DAO inyectado por Hilt para acceso a Room.
 */
class DbcImporter @Inject constructor(
    private val dbcDefinitionDao: DbcDefinitionDao
) {

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Importa un archivo .dbc desde el sistema de archivos y lo persiste en Room.
     *
     * @param filePath Ruta absoluta al archivo .dbc a importar.
     * @param profileName Nombre del perfil en Room (default = nombre del archivo sin extensión).
     * @param validate Si `true`, aborta la importación si hay errores de validación.
     * @return [Result.success] con el número de señales insertadas,
     *         o [Result.failure] con la excepción descriptiva.
     */
    suspend fun importFromFile(
        filePath: String,
        profileName: String? = null,
        validate: Boolean = true
    ): Result<Int> {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return Result.failure(IllegalArgumentException("Archivo no accesible: $filePath"))
            }

            println("[DbcImporter] Leyendo \"${file.name}\" (${file.length()} bytes)…")
            val content = file.readText(Charsets.UTF_8)

            importFromContent(
                content     = content,
                fileName    = file.name,
                profileName = profileName ?: file.nameWithoutExtension,
                validate    = validate
            )
        } catch (e: Exception) {
            println("[DbcImporter] Error leyendo archivo: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Importa un .dbc a partir de su contenido en memoria (útil para URIs, streams, tests).
     *
     * @param content     Contenido completo del .dbc como String.
     * @param fileName    Nombre del archivo (para trazabilidad).
     * @param profileName Nombre del perfil que se creará en Room.
     * @param validate    Si `true`, aborta si hay errores críticos de validación.
     * @return [Result.success] con el número de señales insertadas.
     */
    suspend fun importFromContent(
        content: String,
        fileName: String,
        profileName: String = fileName.substringBeforeLast('.'),
        validate: Boolean = true
    ): Result<Int> {
        println("[DbcImporter] Parseando \"$fileName\"…")

        // 1 – Parsear
        val db = DbcParser.parse(content, fileName)
        println("[DbcImporter] Parseado: ${db.messages.size} mensajes, ${db.signalCount} señales.")

        // 2 – Validar (opcional)
        if (validate) {
            val report = DbcValidator.validate(db)
            if (!report.isValid) {
                val msg = "Validación fallida (${report.errors.size} error(es)): " +
                          report.errors.take(3).joinToString("; ")
                println("[DbcImporter] $msg")
                return Result.failure(IllegalStateException(msg))
            }
        }

        // 3 – Persistir en Room
        saveToDatabase(db, fileName, profileName)

        println("[DbcImporter] ✓ Importación completada: ${db.signalCount} señales guardadas.")
        return Result.success(db.signalCount)
    }

    /**
     * Convierte un [DbcSignal] del modelo de parseo en un [CanSignal] de Room.
     *
     * Mapeo de campos:
     * - [DbcSignal.messageId]   → [CanSignal.canId] (hex string)
     * - [DbcSignal.endianness]  → [CanSignal.endianness]
     * - [DbcSignal.scale]       → [CanSignal.scale] (Float)
     * - [DbcSignal.signed]      → [CanSignal.signed]
     * - [DbcSignal.receiver]    → [CanSignal.description] (informativo)
     *
     * @param dbcSignal Señal a convertir.
     * @param sourceFile Nombre del archivo .dbc para trazabilidad.
     * @param definitionId ID del [DbcDefinition] padre (0 si aún no se insertó).
     */
    fun convertToCanSignal(
        dbcSignal: DbcSignal,
        sourceFile: String = "",
        definitionId: Long = 0L
    ): CanSignal {
        // El canId se almacena como hex string "7E0", "12C", etc.
        val canIdHex = "%X".format(dbcSignal.messageId)

        return CanSignal(
            name           = dbcSignal.name,
            description    = dbcSignal.comment
                             ?: dbcSignal.receiver.ifBlank { null },
            canId          = canIdHex,
            isExtended     = dbcSignal.messageId > 0x7FF,
            startByte      = dbcSignal.startBit / 8,
            startBit       = dbcSignal.startBit,
            bitLength      = dbcSignal.bitLength,
            endianness     = dbcSignal.endianness,
            scale          = dbcSignal.scale.toFloat(),
            offset         = dbcSignal.offset.toFloat(),
            signed         = dbcSignal.signed,
            unit           = dbcSignal.unit,
            minValue       = dbcSignal.min?.toFloat(),
            maxValue       = dbcSignal.max?.toFloat(),
            source         = SignalSource.DBC_FILE,
            sourceFile     = sourceFile.ifBlank { null },
            category       = null,              // puede enriquecerse en capa superior
            dbcDefinitionId = definitionId.takeIf { it > 0L }
        )
    }

    /**
     * Persiste toda la [DbcDatabase] en Room dentro de una transacción atómica.
     *
     * Crea un [DbcDefinition] con los metadatos del archivo y luego inserta
     * todas las señales vinculadas mediante [DbcDefinitionDao.insertDefinitionWithSignals].
     *
     * @param database    Base de datos DBC a persistir.
     * @param fileName    Nombre del archivo origen.
     * @param profileName Nombre del perfil Room a crear/actualizar.
     * @return ID del [DbcDefinition] creado o actualizado.
     */
    suspend fun saveToDatabase(
        database: DbcDatabase,
        fileName: String,
        profileName: String = fileName.substringBeforeLast('.')
    ): Long {
        println("[DbcImporter] Guardando en Room como perfil \"$profileName\"…")

        val definition = DbcDefinition(
            name        = profileName,
            description = "Importado desde $fileName" +
                          (database.version?.let { " (v$it)" } ?: ""),
            sourceFile  = fileName,
            isBuiltIn   = false,
            protocol    = "CAN",
            signalCount = database.signalCount
        )

        // Convertir todas las señales (sin definitionId aún → se rellena en el DAO)
        val canSignals = database.signals.map { sig ->
            convertToCanSignal(sig, fileName, definitionId = 0L)
        }

        val defId = dbcDefinitionDao.insertDefinitionWithSignals(definition, canSignals)
        println("[DbcImporter] DbcDefinition insertado con id=$defId, ${canSignals.size} señal(es).")
        return defId
    }
}

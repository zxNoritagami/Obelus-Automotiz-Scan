package com.obelus.data.export

import android.content.Context
import android.net.Uri
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import com.obelus.protocol.OBD2Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// ReportGenerator.kt
// Orquestador de exportación multi-formato.
// Delega en ExportManager (PDF/XLSX), CsvGenerator y JsonExporter.
// Integra StorageManager para gestionar el directorio de salida.
// ─────────────────────────────────────────────────────────────────────────────

/** Formato de exportación disponible. */
enum class ReportFormat { PDF, CSV, JSON, EXCEL }

/** Rango de fechas para la exportación. */
sealed class ExportRange {
    /** Solo la sesión activa. */
    object LastSession : ExportRange()
    /** Todas las lecturas de las últimas N horas. */
    data class LastHours(val hours: Int) : ExportRange()
    /** Rango personalizado. */
    data class Custom(val from: Long, val to: Long) : ExportRange()
}

/** Resultado de una exportación. */
sealed class ExportResult {
    data class Success(val file: File, val format: ReportFormat) : ExportResult()
    data class Failure(val error: String, val format: ReportFormat) : ExportResult()
}

/**
 * Orquestador que coordina los generadores individuales y
 * emite progreso vía [Flow] de [ExportProgress].
 *
 * @param exportManager PDF + Excel (iText + Apache POI).
 * @param csvGenerator  Exportación CSV pura.
 * @param jsonExporter  Exportación JSON estructurado.
 * @param storageManager Gestión de directorio y limpieza.
 */
@Singleton
class ReportGenerator @Inject constructor(
    private val exportManager: ExportManager,
    private val csvGenerator: CsvGenerator,
    private val jsonExporter: JsonExporter,
    private val storageManager: StorageManager
) {

    // ═══════════════════════════════════════════════════════════════════════════
    // API principal
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Genera un reporte en el formato indicado y emite progreso.
     *
     * @param context       Contexto para acceso a FileProvider y almacenamiento.
     * @param format        [ReportFormat] deseado.
     * @param session       Sesión de diagnóstico.
     * @param readings      Lecturas de señales.
     * @param dtcs          Códigos de falla.
     * @param protocol      Protocolo OBD2 detectado.
     * @param dbcFileName   Nombre del DBC cargado.
     * @return [Flow] de [ExportProgress] para manejar desde la UI.
     */
    fun generate(
        context: Context,
        format: ReportFormat,
        session: ScanSession,
        readings: List<SignalReading>,
        dtcs: List<DtcCode>,
        protocol: OBD2Protocol?   = null,
        dbcFileName: String?      = null
    ): Flow<ExportProgress> = flow {
        emit(ExportProgress.InProgress("Preparando exportación…", 0.05f))

        try {
            when (format) {

                ReportFormat.PDF -> {
                    val outFile = storageManager.newPdfFile(context, session.id)
                    val outUri  = Uri.fromFile(outFile)
                    // Delegar al ExportManager existente (iText PDF)
                    exportManager.exportToPdf(context, session, readings, dtcs, outUri)
                        .collect { progress -> emit(progress) }
                }

                ReportFormat.EXCEL -> {
                    val outFile = File(
                        storageManager.getObelusDir(context),
                        "obelus_data_s${session.id}_${System.currentTimeMillis()}.xlsx"
                    )
                    val outUri = Uri.fromFile(outFile)
                    exportManager.exportToExcel(context, session, readings, dtcs, outUri)
                        .collect { progress -> emit(progress) }
                }

                ReportFormat.CSV -> {
                    emit(ExportProgress.InProgress("Generando CSV…", 0.30f))
                    val outFile = storageManager.newCsvFile(context, session.id)
                    withContext(Dispatchers.IO) {
                        csvGenerator.generateReadingsCsv(session, readings, outFile)
                    }
                    emit(ExportProgress.InProgress("CSV generado.", 0.90f))
                    emit(ExportProgress.Done(Uri.fromFile(outFile)))
                }

                ReportFormat.JSON -> {
                    emit(ExportProgress.InProgress("Generando JSON…", 0.30f))
                    val outFile = storageManager.newJsonFile(context, session.id)
                    withContext(Dispatchers.IO) {
                        jsonExporter.exportSession(
                            session     = session,
                            readings    = readings,
                            dtcs        = dtcs,
                            protocol    = protocol,
                            dbcFileName = dbcFileName,
                            outputFile  = outFile
                        )
                    }
                    emit(ExportProgress.InProgress("JSON generado.", 0.90f))
                    emit(ExportProgress.Done(Uri.fromFile(outFile)))
                }
            }
        } catch (e: Exception) {
            emit(ExportProgress.Failed("Error exportando $format: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    // ═══════════════════════════════════════════════════════════════════════════
    // Filtrado por rango
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Filtra las lecturas según el [ExportRange] indicado.
     */
    fun filterReadings(
        readings: List<SignalReading>,
        range: ExportRange
    ): List<SignalReading> = when (range) {
        is ExportRange.LastSession  -> readings
        is ExportRange.LastHours    -> {
            val cutoff = System.currentTimeMillis() - range.hours * 3_600_000L
            readings.filter { it.timestamp >= cutoff }
        }
        is ExportRange.Custom -> readings.filter { it.timestamp in range.from..range.to }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Limpieza periódica
    // ═══════════════════════════════════════════════════════════════════════════

    /** Borra reportes con más de 30 días. */
    suspend fun cleanupOldReports(context: Context): Int =
        storageManager.deleteOldReports(context)
}

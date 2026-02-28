package com.obelus.data.export

import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// CsvGenerator.kt
// Exporta datos de sesión en formato CSV compatible con Excel.
// Headers: timestamp, signal_name, pid, value, unit, status
// ─────────────────────────────────────────────────────────────────────────────

private val SDF = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

@Singleton
class CsvGenerator @Inject constructor() {

    companion object {
        const val READINGS_HEADER = "timestamp,datetime,session_id,pid,signal_name,value,unit"
        const val DTC_HEADER      = "code,description,category,is_active,is_pending,is_permanent,session_id"
        const val STATS_HEADER    = "pid,signal_name,unit,min,avg,max,count"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lecturas brutas
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Genera un archivo CSV con todas las lecturas de la sesión ordenadas por timestamp.
     *
     * @param session   Sesión de diagnóstico.
     * @param readings  Lecturas de señales.
     * @param outputFile Archivo destino.
     * @return El archivo escrito.
     */
    suspend fun generateReadingsCsv(
        session: ScanSession,
        readings: List<SignalReading>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.bufferedWriter(Charsets.UTF_8).use { bw ->
            // BOM para compatibilidad perfecta con Excel
            bw.write("\uFEFF")
            bw.write("# Obelus Scan – Reporte de Sesión ${session.id}")
            bw.newLine()
            bw.write("# Generado: ${SDF.format(Date())}")
            bw.newLine()
            bw.write(READINGS_HEADER)
            bw.newLine()

            readings
                .sortedBy { it.timestamp }
                .forEach { r ->
                    bw.write(buildString {
                        append(r.timestamp);             append(',')
                        append(SDF.format(Date(r.timestamp))); append(',')
                        append(r.sessionId);             append(',')
                        append(escapeCsv(r.pid));        append(',')
                        append(escapeCsv(r.name));       append(',')
                        append("%.4f".format(r.value));  append(',')
                        append(escapeCsv(r.unit))
                    })
                    bw.newLine()
                }
        }
        println("[CsvGenerator] Readings CSV → ${outputFile.name} (${readings.size} filas)")
        outputFile
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTCs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Genera un CSV con los codes de falla de la sesión.
     */
    suspend fun generateDtcCsv(
        dtcs: List<DtcCode>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.bufferedWriter(Charsets.UTF_8).use { bw ->
            bw.write("\uFEFF")
            bw.write(DTC_HEADER)
            bw.newLine()
            dtcs.sortedBy { it.code }.forEach { d ->
                bw.write(buildString {
                    append(escapeCsv(d.code));        append(',')
                    append(escapeCsv(d.description ?: "")); append(',')
                    append(d.category);               append(',')
                    append(if (d.isActive) "1" else "0");    append(',')
                    append(if (d.isPending) "1" else "0");   append(',')
                    append(if (d.isPermanent) "1" else "0"); append(',')
                    append(d.sessionId ?: "")
                })
                bw.newLine()
            }
        }
        println("[CsvGenerator] DTC CSV → ${outputFile.name} (${dtcs.size} filas)")
        outputFile
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Estadísticas por PID
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Genera un CSV de estadísticas agregadas (min/avg/max por señal).
     */
    suspend fun generateStatsCsv(
        readings: List<SignalReading>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val stats = readings
            .groupBy { it.pid }
            .map { (pid, list) ->
                val values = list.map { it.value }
                arrayOf(
                    pid,
                    list.first().name,
                    list.first().unit,
                    "%.2f".format(values.min()),
                    "%.2f".format(values.average()),
                    "%.2f".format(values.max()),
                    values.size.toString()
                )
            }
            .sortedBy { it[0] }

        outputFile.bufferedWriter(Charsets.UTF_8).use { bw ->
            bw.write("\uFEFF")
            bw.write(STATS_HEADER)
            bw.newLine()
            stats.forEach { row ->
                bw.write(row.joinToString(",") { escapeCsv(it) })
                bw.newLine()
            }
        }
        println("[CsvGenerator] Stats CSV → ${outputFile.name} (${stats.size} señales)")
        outputFile
    }

    // ── Escape RFC 4180 ───────────────────────────────────────────────────────
    private fun escapeCsv(v: String): String {
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            return "\"${v.replace("\"", "\"\"")}\""
        }
        return v
    }
}

package com.obelus.data.export

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// StorageManager.kt
// Gestión del almacenamiento de reportes exportados.
// Android 10+ usa Scoped Storage (getExternalFilesDir).
// Android 9-: puede usar Downloads/ público.
// ─────────────────────────────────────────────────────────────────────────────

private const val OBELUS_DIR  = "Obelus"
private const val MAX_AGE_MS  = 30L * 24 * 60 * 60 * 1_000   // 30 días
private val SDF_FILENAME      = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

@Singleton
class StorageManager @Inject constructor() {

    // ═══════════════════════════════════════════════════════════════════════════
    // Obtener directorio base
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retorna el directorio Obelus/ dentro del almacenamiento externo de la app.
     * Android 10+: `getExternalFilesDir(null)/Obelus/`  (no requiere permiso WRITE)
     * Android  9-: `Environment.EXTERNAL_STORAGE/Documents/Obelus/`
     */
    fun getObelusDir(context: Context): File {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage – no necesita WRITE_EXTERNAL_STORAGE
            context.getExternalFilesDir(null)
                ?: context.filesDir
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), "Documents")
        }
        return File(base, OBELUS_DIR).also { it.mkdirs() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Crear archivos con nombre timestamped
    // ═══════════════════════════════════════════════════════════════════════════

    /** `obelus_report_20250228_143022.pdf` */
    fun newPdfFile(context: Context, sessionId: Long? = null): File =
        newFile(context, "obelus_report${sessionId?.let { "_s$it" } ?: ""}", "pdf")

    /** `obelus_data_20250228_143022.csv` */
    fun newCsvFile(context: Context, sessionId: Long? = null): File =
        newFile(context, "obelus_data${sessionId?.let { "_s$it" } ?: ""}", "csv")

    /** `obelus_json_20250228_143022.json` */
    fun newJsonFile(context: Context, sessionId: Long? = null): File =
        newFile(context, "obelus_json${sessionId?.let { "_s$it" } ?: ""}", "json")

    private fun newFile(context: Context, prefix: String, ext: String): File {
        val ts   = SDF_FILENAME.format(Date())
        return File(getObelusDir(context), "${prefix}_$ts.$ext")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Listar reportes existentes
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Retorna todos los reportes en el directorio Obelus/,
     * ordenados de más reciente a más antiguo.
     */
    suspend fun listReports(context: Context): List<ReportEntry> = withContext(Dispatchers.IO) {
        val dir = getObelusDir(context)
        dir.listFiles()
            ?.filter { it.isFile && it.extension in listOf("pdf", "csv", "json") }
            ?.map { f ->
                ReportEntry(
                    file        = f,
                    name        = f.name,
                    extension   = f.extension.uppercase(),
                    sizeBytes   = f.length(),
                    createdAt   = f.lastModified()
                )
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Limpieza automática
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Elimina reportes con más de 30 días de antigüedad.
     *
     * @return Número de archivos eliminados.
     */
    suspend fun deleteOldReports(context: Context): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val dir    = getObelusDir(context)
        var count  = 0
        dir.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach { f ->
                if (f.delete()) {
                    count++
                    println("[StorageManager] Borrado antiguo: ${f.name}")
                }
            }
        println("[StorageManager] Limpieza completada: $count archivo(s) eliminado(s).")
        count
    }

    /**
     * Borra un reporte específico.
     *
     * @return true si fue eliminado.
     */
    fun deleteReport(file: File): Boolean {
        val deleted = file.delete()
        println("[StorageManager] ${if (deleted) "Borrado" else "ERROR al borrar"}: ${file.name}")
        return deleted
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tamaño total
    // ═══════════════════════════════════════════════════════════════════════════

    /** Retorna el espacio total ocupado por reportes Obelus en bytes. */
    suspend fun totalSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        getObelusDir(context).listFiles()?.sumOf { it.length() } ?: 0L
    }
}

/** Modelo de entrada de la lista de reportes. */
data class ReportEntry(
    val file:      File,
    val name:      String,
    val extension: String,   // "PDF", "CSV", "JSON"
    val sizeBytes: Long,
    val createdAt: Long
)

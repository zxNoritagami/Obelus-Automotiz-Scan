package com.obelus.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// ─────────────────────────────────────────────────────────────────────────────
// ShareManager.kt
// Compartir reportes exportados vía Intent o Android Print Framework.
// FileProvider: usa `${applicationId}.provider` (ya registrado en Manifest).
// ─────────────────────────────────────────────────────────────────────────────

@Singleton
class ShareManager @Inject constructor() {

    // ═══════════════════════════════════════════════════════════════════════════
    // Compartir PDF
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lanza un Intent de compartir para un archivo PDF.
     * Compatible con WhatsApp, Gmail, Drive, Telegram, etc.
     *
     * @param context Contexto de la Activity/Fragment.
     * @param file    Archivo PDF a compartir.
     */
    fun sharePdf(context: Context, file: File) {
        shareFile(
            context   = context,
            file      = file,
            mimeType  = "application/pdf",
            chooserTitle = "Compartir reporte PDF"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Compartir CSV
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lanza un Intent de compartir para un archivo CSV.
     *
     * @param context Contexto de la Activity/Fragment.
     * @param file    Archivo CSV a compartir.
     */
    fun shareCsv(context: Context, file: File) {
        shareFile(
            context      = context,
            file         = file,
            mimeType     = "text/csv",
            chooserTitle = "Compartir datos CSV"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Compartir JSON
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Lanza un Intent de compartir para un archivo JSON.
     */
    fun shareJson(context: Context, file: File) {
        shareFile(
            context      = context,
            file         = file,
            mimeType     = "application/json",
            chooserTitle = "Compartir datos JSON"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Imprimir PDF (Android Print Framework)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Abre el diálogo de impresión nativo de Android para un PDF.
     * Requiere Android 5.0+ (API 21).
     *
     * @param context   Contexto de la Activity.
     * @param file      Archivo PDF a imprimir.
     * @param jobName   Nombre del trabajo de impresión.
     */
    fun printPdf(context: Context, file: File, jobName: String = "Obelus Scan Report") {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = ObelusPrintDocumentAdapter(context, file)
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            printManager.print(jobName, printAdapter, printAttributes)
            println("[ShareManager] Impresión iniciada: $jobName")
        } catch (e: Exception) {
            println("[ShareManager] Error al imprimir: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Reporte Obelus Scan – ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            println("[ShareManager] Compartiendo: ${file.name} ($mimeType)")
        } catch (e: Exception) {
            println("[ShareManager] Error al compartir ${file.name}: ${e.message}")
        }
    }

    /**
     * Genera y devuelve la URI del archivo via FileProvider para uso en compose/UI.
     */
    fun getFileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

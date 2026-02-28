package com.obelus.presentation.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.obelus.domain.model.DiagnosticReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generador de reportes técnicos en formato PDF.
 * Utiliza la API nativa de Android PdfDocument para garantizar compatibilidad y ligereza.
 * Genera un informe profesional con los hallazgos del motor experto.
 */
@Singleton
class DiagnosticPdfGenerator @Inject constructor() {

    /**
     * Genera un reporte PDF profesional y lo guarda en el almacenamiento interno.
     */
    fun generateReport(
        context: Context,
        report: DiagnosticReport,
        vin: String?,
        vehicleModel: String?
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.DKGRAY
        }

        var currentY = 50f

        // --- 1. HEADER ---
        canvas.drawText("OBELUS - REPORTE TÉCNICO DE DIAGNÓSTICO", 50f, currentY, titlePaint)
        currentY += 30f
        
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${dateFormat.format(report.generatedAt)}", 50f, currentY, textPaint)
        currentY += 20f
        canvas.drawText("VIN: ${vin ?: "No Detectado"}", 50f, currentY, textPaint)
        currentY += 20f
        canvas.drawText("Vehículo: ${vehicleModel ?: "Genérico"}", 50f, currentY, textPaint)
        currentY += 40f

        // --- 2. RESUMEN GENERAL ---
        canvas.drawText("RESUMEN DE SALUD", 50f, currentY, headerPaint)
        currentY += 25f
        
        val healthColor = when {
            report.findings.isEmpty() -> Color.GREEN
            report.findings.first().posteriorProbability > 0.7 -> Color.RED
            else -> Color.rgb(255, 165, 0) // Orange
        }
        val scorePaint = Paint(textPaint).apply { color = healthColor; typeface = Typeface.DEFAULT_BOLD }
        
        val healthScore = if (report.findings.isNotEmpty()) {
            ((1.0 - report.findings.first().posteriorProbability) * 100).toInt()
        } else 100
        
        canvas.drawText("Puntaje de Salud: $healthScore / 100", 50f, currentY, scorePaint)
        currentY += 20f
        canvas.drawText("Modelo: ${report.modelVersion}", 50f, currentY, textPaint)
        currentY += 40f

        // --- 3. TABLA DE HALLAZGOS ---
        canvas.drawText("HALLAZGOS TÉCNICOS (RANKING BAYESIANO)", 50f, currentY, headerPaint)
        currentY += 25f

        // Header de tabla
        paint.color = Color.LTGRAY
        canvas.drawRect(50f, currentY - 15f, 545f, currentY + 5f, paint)
        canvas.drawText("DTC", 55f, currentY, headerPaint.apply { textSize = 10f })
        canvas.drawText("PROBABILIDAD", 120f, currentY, headerPaint)
        canvas.drawText("SEV.", 230f, currentY, headerPaint)
        canvas.drawText("CAUSA PROBABLE", 280f, currentY, headerPaint)
        currentY += 25f

        report.findings.take(10).forEach { finding ->
            if (currentY > 780f) return@forEach // Evitar desborde simple (v1)
            
            canvas.drawText(finding.dtcCode, 55f, currentY, textPaint.apply { textSize = 10f })
            canvas.drawText(String.format("%.2f%%", finding.posteriorProbability * 100), 120f, currentY, textPaint)
            canvas.drawText("${finding.severityLevel}/5", 230f, currentY, textPaint)
            
            val causeSnippet = if (finding.probableCause.length > 40) finding.probableCause.take(37) + "..." else finding.probableCause
            canvas.drawText(causeSnippet, 280f, currentY, textPaint)
            
            currentY += 20f
        }
        
        currentY += 30f

        // --- 4. EVIDENCIA Y RECOMENDACIÓN ---
        if (report.findings.isNotEmpty()) {
            val top = report.findings.first()
            canvas.drawText("ACCIÓN RECOMENDADA", 50f, currentY, headerPaint.apply { textSize = 14f })
            currentY += 25f
            canvas.drawText("Inspección prioritaria de: ${top.probableCause}", 50f, currentY, textPaint.apply { typeface = Typeface.DEFAULT_BOLD; textSize = 12f })
        }

        pdfDocument.finishPage(page)

        // Guardar archivo
        val fileName = "Obelus_Report_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        
        try {
            pdfDocument.writeTo(FileOutputStream(file))
        } finally {
            pdfDocument.close()
        }

        return file
    }
}

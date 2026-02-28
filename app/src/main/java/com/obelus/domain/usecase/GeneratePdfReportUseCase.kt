package com.obelus.domain.usecase

import android.content.Context
import android.os.Environment
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class GeneratePdfReportUseCase @Inject constructor() {

    fun execute(context: Context, session: ScanSession, readings: List<SignalReading>): File {
        val fileName = "Obelus_Report_${session.id}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        
        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // Header
        val title = Paragraph("OBELUS PRO DIAGNOSTIC REPORT")
            .setFontSize(20f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(DeviceRgb(0, 212, 170)) // Neon Cyan
        document.add(title)

        document.add(Paragraph("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"))
        document.add(Paragraph("Session ID: ${session.id}"))
        document.add(Paragraph("Protocol: ${session.protocol}"))
        document.add(Paragraph("Notes: ${session.notes}"))
        
        document.add(Paragraph("\n"))

        // Stats
        document.add(Paragraph("SUMMARY STATISTICS").setBold().setFontSize(14f))
        val statsTable = Table(floatArrayOf(1f, 1f))
        statsTable.addCell("Average Speed")
        statsTable.addCell("${session.averageSpeed ?: 0f} km/h")
        statsTable.addCell("Max RPM")
        statsTable.addCell("${session.maxRpm ?: 0} RPM")
        document.add(statsTable)

        document.add(Paragraph("\n"))

        // Data Table
        document.add(Paragraph("SENSOR DATA LOG").setBold().setFontSize(14f))
        val table = Table(floatArrayOf(2f, 3f, 2f, 2f))
        table.addHeaderCell("Time")
        table.addHeaderCell("Sensor Name")
        table.addHeaderCell("Value")
        table.addHeaderCell("Unit")

        val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        readings.take(100).forEach { reading -> // Limit to first 100 for brevity in sample
            table.addCell(timeFormatter.format(Date(reading.timestamp)))
            table.addCell(reading.name)
            table.addCell(String.format(Locale.getDefault(), "%.2f", reading.value))
            table.addCell(reading.unit)
        }
        
        document.add(table)
        
        if (readings.size > 100) {
            document.add(Paragraph("... and ${readings.size - 100} more data points."))
        }

        document.close()
        return file
    }
}

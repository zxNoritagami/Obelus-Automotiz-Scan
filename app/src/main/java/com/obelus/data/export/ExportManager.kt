package com.obelus.data.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.obelus.data.local.entity.DtcCode
import com.obelus.data.local.entity.ScanSession
import com.obelus.data.local.entity.SignalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress state for export operations (reported to the UI via Flow).
 */
sealed class ExportProgress {
    data class InProgress(val label: String, val pct: Float) : ExportProgress()
    data class Done(val uri: Uri)                            : ExportProgress()
    data class Failed(val message: String)                   : ExportProgress()
}

/**
 * Produces a simple per-PID statistics summary from a list of readings.
 */
data class PidSummary(
    val pid: String,
    val name: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val avg: Float,
    val count: Int
)

@Singleton
class ExportManager @Inject constructor() {

    companion object {
        private const val TAG = "ExportManager"
        private val SDF_DATETIME = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        private val SDF_DATE     = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Exports the session to a PDF file at [outputUri].
     * Returns a [Flow] of [ExportProgress] to drive the UI progress indicator.
     */
    fun exportToPdf(
        context: Context,
        session: ScanSession,
        readings: List<SignalReading>,
        dtcs: List<DtcCode>,
        outputUri: Uri
    ): Flow<ExportProgress> = flow {
        emit(ExportProgress.InProgress("Preparando PDF...", 0.05f))
        try {
            withContext(Dispatchers.IO) {
                val pdfStats = buildPidSummaries(readings)
                emit(ExportProgress.InProgress("Generando gráfico...", 0.2f))

                // Render chart bitmap → bytes
                val chartBitmap = ChartBitmapRenderer.render(readings)
                val chartBytes  = bitmapToBytes(chartBitmap)
                emit(ExportProgress.InProgress("Componiendo PDF...", 0.5f))

                context.contentResolver.openOutputStream(outputUri)!!.use { outStream ->
                    val writer = PdfWriter(outStream)
                    val pdf    = PdfDocument(writer)
                    val doc    = Document(pdf)
                    doc.setMargins(36f, 36f, 36f, 36f)

                    // ── Header ─────────────────────────────────────────────
                    addHeader(doc, session)
                    emit(ExportProgress.InProgress("Escribiendo tabla PID...", 0.65f))

                    // ── Vehicle info ───────────────────────────────────────
                    addVehicleInfo(doc, session)

                    // ── PID statistics table ───────────────────────────────
                    addPidStatisticsTable(doc, pdfStats)
                    emit(ExportProgress.InProgress("Incrustando gráfico...", 0.78f))

                    // ── Chart ──────────────────────────────────────────────
                    if (chartBytes != null) {
                        addChart(doc, chartBytes)
                    }
                    emit(ExportProgress.InProgress("Escribiendo DTCs...", 0.90f))

                    // ── DTCs ───────────────────────────────────────────────
                    if (dtcs.isNotEmpty()) addDtcSection(doc, dtcs)

                    // ── Footer ─────────────────────────────────────────────
                    addFooter(doc, session)

                    doc.close()
                }
            }
            emit(ExportProgress.Done(outputUri))
            Log.i(TAG, "PDF export done: $outputUri")
        } catch (e: Exception) {
            Log.e(TAG, "PDF export failed", e)
            emit(ExportProgress.Failed(e.message ?: "Error desconocido al generar PDF"))
        }
    }

    /**
     * Exports the session to an Excel .xlsx file at [outputUri].
     * Returns a [Flow] of [ExportProgress].
     */
    fun exportToExcel(
        context: Context,
        session: ScanSession,
        readings: List<SignalReading>,
        dtcs: List<DtcCode>,
        outputUri: Uri
    ): Flow<ExportProgress> = flow {
        emit(ExportProgress.InProgress("Creando libro Excel...", 0.05f))
        try {
            withContext(Dispatchers.IO) {
                val workbook = XSSFWorkbook()
                val styles   = ExcelStyles(workbook)

                emit(ExportProgress.InProgress("Hoja Resumen...", 0.2f))
                buildSummarySheet(workbook, styles, session)

                emit(ExportProgress.InProgress("Hoja Datos crudos...", 0.4f))
                buildRawDataSheet(workbook, styles, readings)

                emit(ExportProgress.InProgress("Hoja Estadísticas...", 0.6f))
                buildStatisticsSheet(workbook, styles, readings)

                emit(ExportProgress.InProgress("Hoja DTCs...", 0.75f))
                buildDtcSheet(workbook, styles, dtcs)

                emit(ExportProgress.InProgress("Guardando archivo...", 0.9f))
                context.contentResolver.openOutputStream(outputUri)!!.use { outStream ->
                    workbook.write(outStream)
                }
                workbook.close()
            }
            emit(ExportProgress.Done(outputUri))
            Log.i(TAG, "Excel export done: $outputUri")
        } catch (e: Exception) {
            Log.e(TAG, "Excel export failed", e)
            emit(ExportProgress.Failed(e.message ?: "Error desconocido al generar Excel"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PDF HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private val colorPrimary = DeviceRgb(88, 166, 255)   // #58A6FF
    private val colorDark    = DeviceRgb(13, 17, 23)      // #0D1117
    private val colorAccent  = DeviceRgb(163, 113, 247)   // #A371F7
    private val colorError   = DeviceRgb(239, 68, 68)     // #EF4444
    private val colorWarning = DeviceRgb(245, 158, 11)    // #F59E0B

    private fun addHeader(doc: Document, session: ScanSession) {
        val bold = PdfFontFactory.createFont()

        // Brand title
        val title = Paragraph("◉ OBELUS SCAN")
            .setFontSize(28f)
            .setBold()
            .setFontColor(colorPrimary)
            .setTextAlignment(TextAlignment.CENTER)
        doc.add(title)

        // Subtitle / report date
        val subtitle = Paragraph(
            "Reporte de Sesión de Diagnóstico — ${SDF_DATETIME.format(Date())}"
        ).setFontSize(10f)
            .setFontColor(ColorConstants.GRAY)
            .setTextAlignment(TextAlignment.CENTER)
        doc.add(subtitle)

        // Divider line (thin table)
        val div = Table(UnitValue.createPercentArray(floatArrayOf(1f))).useAllAvailableWidth()
        val divCell = Cell().setHeight(2f).setBackgroundColor(colorPrimary).setBorder(SolidBorder(colorPrimary, 0f))
        div.addCell(divCell)
        doc.add(div)
        doc.add(Paragraph("\n").setFontSize(4f))  // spacer
    }

    private fun addVehicleInfo(doc: Document, session: ScanSession) {
        doc.add(
            Paragraph("Información del Vehículo")
                .setFontSize(13f).setBold().setFontColor(colorAccent)
        )

        val duration = ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000
        val rows = listOfNotNull(
            "ID Sesión" to session.id.toString(),
            "Fecha inicio" to SDF_DATE.format(Date(session.startTime)),
            "Duración" to "%d min %d seg".format(duration / 60, duration % 60),
            session.notes?.takeIf { it.isNotBlank() }?.let { "Notas" to it },
            session.averageSpeed?.let { "Vel. media" to "${it.toInt()} km/h" }
        )

        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f))).useAllAvailableWidth()
        rows.forEachIndexed { idx, (label, value) ->
            val bg = if (idx % 2 == 0) DeviceRgb(28, 33, 40) else DeviceRgb(22, 27, 34)
            infoTable.addCell(
                Cell().add(Paragraph(label).setBold().setFontSize(10f))
                    .setBackgroundColor(bg).setPadding(6f)
            )
            infoTable.addCell(
                Cell().add(Paragraph(value).setFontSize(10f))
                    .setBackgroundColor(bg).setPadding(6f)
            )
        }
        doc.add(infoTable)
        doc.add(Paragraph("\n").setFontSize(6f))
    }

    private fun addPidStatisticsTable(doc: Document, stats: List<PidSummary>) {
        doc.add(
            Paragraph("Estadísticas por PID")
                .setFontSize(13f).setBold().setFontColor(colorAccent)
        )
        if (stats.isEmpty()) {
            doc.add(Paragraph("Sin lecturas de sensores en esta sesión.").setFontSize(10f).setFontColor(ColorConstants.GRAY))
            doc.add(Paragraph("\n").setFontSize(6f))
            return
        }

        val headers = listOf("PID", "Sensor", "Unidad", "Mín", "Promedio", "Máx", "Lecturas")
        val table   = Table(UnitValue.createPercentArray(floatArrayOf(8f, 28f, 10f, 12f, 15f, 12f, 15f)))
            .useAllAvailableWidth()

        headers.forEach { h ->
            table.addHeaderCell(
                Cell().add(Paragraph(h).setBold().setFontSize(9f).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(colorDark).setPadding(5f)
                    .setTextAlignment(TextAlignment.CENTER)
            )
        }

        stats.forEachIndexed { idx, s ->
            val bg = if (idx % 2 == 0) DeviceRgb(28, 33, 40) else DeviceRgb(22, 27, 34)
            fun dataCell(v: String, center: Boolean = true): Cell =
                Cell().add(Paragraph(v).setFontSize(9f))
                    .setBackgroundColor(bg).setPadding(4f)
                    .setTextAlignment(if (center) TextAlignment.CENTER else TextAlignment.LEFT)

            table.addCell(dataCell(s.pid))
            table.addCell(dataCell(s.name, center = false))
            table.addCell(dataCell(s.unit))
            table.addCell(dataCell("%.2f".format(s.min)))
            table.addCell(dataCell("%.2f".format(s.avg)))
            table.addCell(dataCell("%.2f".format(s.max)))
            table.addCell(dataCell(s.count.toString()))
        }
        doc.add(table)
        doc.add(Paragraph("\n").setFontSize(6f))
    }

    private fun addChart(doc: Document, chartBytes: ByteArray) {
        doc.add(
            Paragraph("Gráfico de RPM y Velocidad")
                .setFontSize(13f).setBold().setFontColor(colorAccent)
        )
        val imgData = ImageDataFactory.create(chartBytes)
        val image   = Image(imgData)
            .setAutoScale(true)
            .setHorizontalAlignment(HorizontalAlignment.CENTER)
        doc.add(image)
        doc.add(Paragraph("\n").setFontSize(6f))
    }

    private fun addDtcSection(doc: Document, dtcs: List<DtcCode>) {
        doc.add(
            Paragraph("Códigos de Error DTC (${dtcs.size})")
                .setFontSize(13f).setBold().setFontColor(colorError)
        )
        val table = Table(UnitValue.createPercentArray(floatArrayOf(15f, 55f, 15f, 15f)))
            .useAllAvailableWidth()

        listOf("Código", "Descripción", "Categoría", "Estado").forEach { h ->
            table.addHeaderCell(
                Cell().add(Paragraph(h).setBold().setFontSize(9f).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(colorDark).setPadding(5f)
            )
        }

        dtcs.forEachIndexed { idx, dtc ->
            val bg = if (idx % 2 == 0) DeviceRgb(35, 18, 18) else DeviceRgb(28, 14, 14)
            fun dtcCell(v: String): Cell =
                Cell().add(Paragraph(v).setFontSize(9f))
                    .setBackgroundColor(bg).setPadding(4f)

            table.addCell(dtcCell(dtc.code).setFontColor(colorError))
            table.addCell(dtcCell(dtc.description ?: "N/A"))
            table.addCell(dtcCell(dtc.category.toString()))
            val status = when {
                dtc.isPermanent -> "Perm."
                dtc.isActive    -> "Activo"
                dtc.isPending   -> "Pend."
                else            -> "Hist."
            }
            table.addCell(dtcCell(status))
        }
        doc.add(table)
    }

    private fun addFooter(doc: Document, session: ScanSession) {
        doc.add(Paragraph("\n").setFontSize(6f))
        doc.add(
            Paragraph("Generado por Obelus Scan — ${SDF_DATETIME.format(Date())}")
                .setFontSize(8f)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.RIGHT)
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // EXCEL HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private inner class ExcelStyles(wb: XSSFWorkbook) {
        val header: CellStyle = wb.createCellStyle().also { cs ->
            cs.fillForegroundColor = IndexedColors.DARK_BLUE.index
            cs.fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = wb.createFont().also { f ->
                f.bold = true; f.color = IndexedColors.WHITE.index; f.fontHeightInPoints = 11
            }
            cs.setFont(font)
            cs.borderBottom = BorderStyle.THIN
        }
        val altRow: CellStyle  = wb.createCellStyle().also { cs ->
            cs.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            cs.fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val numFmt: CellStyle  = wb.createCellStyle().also { cs ->
            val fmt = wb.createDataFormat()
            cs.dataFormat = fmt.getFormat("#,##0.00")
        }
        val dateFmt: CellStyle = wb.createCellStyle().also { cs ->
            val fmt = wb.createDataFormat()
            cs.dataFormat = fmt.getFormat("dd/mm/yyyy hh:mm:ss")
        }
    }

    private fun buildSummarySheet(wb: XSSFWorkbook, s: ExcelStyles, session: ScanSession) {
        val sheet = wb.createSheet("Resumen")
        sheet.setColumnWidth(0, 7000); sheet.setColumnWidth(1, 12000)

        fun hdr(row: Int, label: String, value: String) {
            val r = sheet.createRow(row)
            r.createCell(0).also { it.setCellValue(label); it.cellStyle = s.header }
            r.createCell(1).setCellValue(value)
        }

        val duration = ((session.endTime ?: System.currentTimeMillis()) - session.startTime) / 1000
        var row = 0
        hdr(row++, "OBELUS — Reporte de Sesión", "")
        row++ // blank
        hdr(row++, "ID Sesión",   session.id.toString())
        hdr(row++, "Fecha inicio", SDF_DATE.format(Date(session.startTime)))
        session.endTime?.let { hdr(row++, "Fecha fin", SDF_DATE.format(Date(it))) }
        hdr(row++, "Duración",    "%d min %d seg".format(duration / 60, duration % 60))
        session.notes?.takeIf { it.isNotBlank() }?.let { hdr(row++, "Notas", it) }
        session.averageSpeed?.let { hdr(row++, "Vel. media", "${it.toInt()} km/h") }
        hdr(row++, "Generado", SDF_DATETIME.format(Date()))
    }

    private fun buildRawDataSheet(wb: XSSFWorkbook, s: ExcelStyles, readings: List<SignalReading>) {
        val sheet = wb.createSheet("Datos crudos")
        val headers = listOf("Timestamp", "PID", "Sensor", "Valor", "Unidad")
        val widths  = listOf(7500, 3000, 8000, 4000, 4000)
        headers.forEachIndexed { i, h ->
            sheet.setColumnWidth(i, widths[i])
            sheet.createRow(0).also { r ->
                r.createCell(i).also { c -> c.setCellValue(h); c.cellStyle = s.header }
            }
        }

        readings.forEachIndexed { idx, r ->
            val row = sheet.createRow(idx + 1)
            val cs  = if (idx % 2 != 0) s.altRow else wb.createCellStyle()
            fun cell(col: Int): Cell = row.createCell(col).also { it.cellStyle = cs }

            cell(0).setCellValue(SDF_DATETIME.format(Date(r.timestamp)))
            cell(1).setCellValue(r.pid)
            cell(2).setCellValue(r.name)
            row.createCell(3).also { it.setCellValue(r.value.toDouble()); it.cellStyle = s.numFmt }
            cell(4).setCellValue(r.unit)
        }
    }

    private fun buildStatisticsSheet(wb: XSSFWorkbook, s: ExcelStyles, readings: List<SignalReading>) {
        val sheet   = wb.createSheet("Estadísticas")
        val headers = listOf("PID", "Sensor", "Unidad", "Mín", "Promedio", "Máx", "Lecturas")
        val widths  = listOf(3000, 8000, 3500, 4000, 5000, 4000, 4500)

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            sheet.setColumnWidth(i, widths[i])
            headerRow.createCell(i).also { c -> c.setCellValue(h); c.cellStyle = s.header }
        }

        val stats = buildPidSummaries(readings)
        stats.forEachIndexed { idx, ps ->
            val row = sheet.createRow(idx + 1)
            val cs  = if (idx % 2 != 0) s.altRow else wb.createCellStyle()
            fun cell(col: Int, v: String) = row.createCell(col).also { it.setCellValue(v); it.cellStyle = cs }
            fun numCell(col: Int, v: Double) = row.createCell(col).also { it.setCellValue(v); it.cellStyle = s.numFmt }

            cell(0, ps.pid)
            cell(1, ps.name)
            cell(2, ps.unit)
            numCell(3, ps.min.toDouble())
            numCell(4, ps.avg.toDouble())
            numCell(5, ps.max.toDouble())
            row.createCell(6).also { it.setCellValue(ps.count.toDouble()); it.cellStyle = cs }
        }
    }

    private fun buildDtcSheet(wb: XSSFWorkbook, s: ExcelStyles, dtcs: List<DtcCode>) {
        val sheet   = wb.createSheet("DTCs")
        val headers = listOf("Código", "Descripción", "Categoría", "Activo", "Pendiente", "Permanente")
        val widths  = listOf(4000, 16000, 5000, 3500, 4000, 5000)

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            sheet.setColumnWidth(i, widths[i])
            headerRow.createCell(i).also { c -> c.setCellValue(h); c.cellStyle = s.header }
        }

        if (dtcs.isEmpty()) {
            sheet.createRow(1).createCell(0).setCellValue("Sin DTCs registrados en esta sesión")
            return
        }

        dtcs.forEachIndexed { idx, dtc ->
            val row = sheet.createRow(idx + 1)
            val cs  = if (idx % 2 != 0) s.altRow else wb.createCellStyle()
            fun cell(col: Int, v: String) = row.createCell(col).also { it.setCellValue(v); it.cellStyle = cs }
            fun boolCell(col: Int, v: Boolean) = row.createCell(col).also { it.setCellValue(if (v) "Sí" else "No"); it.cellStyle = cs }

            cell(0, dtc.code)
            cell(1, dtc.description)
            cell(2, dtc.category)
            boolCell(3, dtc.isActive)
            boolCell(4, dtc.isPending)
            boolCell(5, dtc.isPermanent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SHARED HELPERS
    // ─────────────────────────────────────────────────────────────────────

    fun buildPidSummaries(readings: List<SignalReading>): List<PidSummary> {
        return readings
            .groupBy { it.pid }
            .map { (pid, rList) ->
                val values = rList.map { it.value }
                PidSummary(
                    pid   = pid,
                    name  = rList.first().name,
                    unit  = rList.first().unit,
                    min   = values.min(),
                    max   = values.max(),
                    avg   = values.average().toFloat(),
                    count = values.size
                )
            }
            .sortedBy { it.pid }
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, bos)
            bos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress chart bitmap", e)
            null
        }
    }
}


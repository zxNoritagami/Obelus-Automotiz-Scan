package com.obelus.data.export

import android.graphics.*
import com.obelus.data.local.entity.SignalReading

/**
 * Renders a simple line chart as an Android Bitmap (800×300 px).
 * Shows up to two series: RPM (normalized 0–8000) and Speed (0–250 km/h).
 * The resulting Bitmap is embedded into the PDF report.
 */
object ChartBitmapRenderer {

    private const val WIDTH  = 800
    private const val HEIGHT = 300
    private const val PAD_L  = 60f
    private const val PAD_R  = 20f
    private const val PAD_T  = 20f
    private const val PAD_B  = 40f

    private val colorBg      = Color.parseColor("#1C2128")
    private val colorGrid    = Color.parseColor("#30363D")
    private val colorRpm     = Color.parseColor("#A371F7") // Purple
    private val colorSpeed   = Color.parseColor("#3FB950") // Green
    private val colorText    = Color.parseColor("#8B949E")
    private val colorAxisLine = Color.parseColor("#58A6FF")

    fun render(readings: List<SignalReading>): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colorBg)

        val rpmReadings   = readings.filter { it.pid == "0C" }.sortedBy { it.timestamp }
        val speedReadings = readings.filter { it.pid == "0D" }.sortedBy { it.timestamp }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ── Grid lines ─────────────────────────────────────────────────────
        paint.color = colorGrid
        paint.strokeWidth = 1f
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = PAD_T + (HEIGHT - PAD_T - PAD_B) * i / gridLines
            canvas.drawLine(PAD_L, y, WIDTH - PAD_R, y, paint)
        }

        // ── Legend ─────────────────────────────────────────────────────────
        paint.textSize = 22f
        paint.typeface = Typeface.MONOSPACE
        if (rpmReadings.isNotEmpty()) {
            paint.color = colorRpm
            canvas.drawRect(PAD_L, 4f, PAD_L + 18f, 22f, paint)
            paint.color = colorText
            canvas.drawText("RPM", PAD_L + 24f, 20f, paint)
        }
        if (speedReadings.isNotEmpty()) {
            paint.color = colorSpeed
            canvas.drawRect(PAD_L + 100f, 4f, PAD_L + 118f, 22f, paint)
            paint.color = colorText
            canvas.drawText("km/h", PAD_L + 124f, 20f, paint)
        }

        // ── Axis labels (Y) ────────────────────────────────────────────────
        paint.color = colorAxisLine
        paint.strokeWidth = 2f
        canvas.drawLine(PAD_L, PAD_T, PAD_L, HEIGHT - PAD_B, paint)  // Y axis
        canvas.drawLine(PAD_L, HEIGHT - PAD_B, WIDTH - PAD_R, HEIGHT - PAD_B, paint) // X axis

        paint.color = colorText
        paint.textSize = 18f
        listOf("8000", "6000", "4000", "2000", "0").forEachIndexed { i, label ->
            val y = PAD_T + (HEIGHT - PAD_T - PAD_B) * i / 4
            canvas.drawText(label, 4f, y + 6, paint)
        }

        // ── Draw series ────────────────────────────────────────────────────
        drawSeries(canvas, rpmReadings, maxVal = 8000f, color = colorRpm)
        drawSeries(canvas, speedReadings, maxVal = 250f, color = colorSpeed)

        // ── No data label ──────────────────────────────────────────────────
        if (rpmReadings.isEmpty() && speedReadings.isEmpty()) {
            paint.color = colorText
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Sin datos de RPM / velocidad", WIDTH / 2f, HEIGHT / 2f, paint)
        }

        return bitmap
    }

    private fun drawSeries(canvas: Canvas, readings: List<SignalReading>, maxVal: Float, color: Int) {
        if (readings.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val chartW = WIDTH - PAD_L - PAD_R
        val chartH = HEIGHT - PAD_T - PAD_B
        val path   = Path()
        val minTs  = readings.first().timestamp.toFloat()
        val maxTs  = readings.last().timestamp.toFloat()
        val tsRange = (maxTs - minTs).coerceAtLeast(1f)

        readings.forEachIndexed { idx, r ->
            val x = PAD_L + chartW * (r.timestamp - minTs) / tsRange
            val y = PAD_T + chartH * (1f - (r.value.coerceIn(0f, maxVal) / maxVal))
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}

package com.obelus.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.obelus.data.local.entity.SignalReading

@Composable
fun LineChart(
    readings: List<SignalReading>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.height(200.dp)) {
        if (readings.size < 2) return@Canvas
        
        val width = size.width
        val height = size.height
        
        val minTime = readings.first().timestamp
        val maxTime = readings.last().timestamp
        val timeRange = maxTime - minTime
        
        val minValue = readings.minOf { it.value }
        val maxValue = readings.maxOf { it.value }
        val valueRange = maxValue - minValue

        val path = Path()
        
        readings.forEachIndexed { index, reading ->
            val x = if (timeRange > 0) {
                ((reading.timestamp - minTime).toFloat() / timeRange) * width
            } else {
                0f
            }

            val y = if (valueRange > 0) {
                height - (((reading.value - minValue) / valueRange) * height)
            } else {
                height / 2f
            }

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

package com.example.audioanalyser

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.max

@Composable
fun WaterfallVisualizer(
    spectrogram: List<FloatArray>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (spectrogram.isEmpty()) return@Canvas

            val rows = spectrogram.size
            val cols = spectrogram.first().size
            val drawCols = minOf(cols, 256)
            val colStep = cols.toFloat() / drawCols

            // Find global max to normalize
            var globalMax = 1f
            for (r in spectrogram) {
                val m = r.maxOrNull() ?: 0f
                if (m > globalMax) globalMax = m
            }

            val cellW = size.width / drawCols
            val cellH = size.height / rows

            for (row in 0 until rows) {
                val frame = spectrogram[row]
                val y = row * cellH
                for (c in 0 until drawCols) {
                    val src = (c * colStep).toInt().coerceIn(0, cols - 1)
                    val mag = frame[src]
                    val norm = (mag / max(globalMax, 1f)).coerceIn(0f, 1f)
                    // Map normalized magnitude to hue 240 (blue) -> 0 (red)
                    val hue = (1f - norm) * 240f
                    val hsv = floatArrayOf(hue, 1f, norm)
                    val intColor = AndroidColor.HSVToColor(hsv)
                    val color = Color(intColor)
                    drawRect(
                        color = color,
                        topLeft = Offset(c * cellW, y),
                        size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f)
                    )
                }
            }
        }
    }
}

package com.example.audioanalyser

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun WaterfallVisualizer(
    spectrogram: List<FloatArray>,
    modifier: Modifier = Modifier,
    minPercentile: Float = 0.05f,
    maxPercentile: Float = 0.95f,
    gamma: Float = 0.6f,
    colormapName: String = "inferno"
) {
    Box(modifier = modifier) {
        // Keep a mutable bitmap and integer pixel buffer across recompositions
        val bitmapState = remember { mutableStateOf<Bitmap?>(null) }
        val pixelBufferState = remember { mutableStateOf(IntArray(0)) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (spectrogram.isEmpty()) return@Canvas

            val rows = spectrogram.size
            val cols = spectrogram.first().size
            val drawCols = minOf(cols, 256)
            val colStep = cols.toFloat() / drawCols

            val widthPx = size.width.roundToInt().coerceAtLeast(1)
            val heightPx = size.height.roundToInt().coerceAtLeast(1)

            // Ensure bitmap and buffer are sized appropriately
            var bitmap = bitmapState.value
            if (bitmap == null || bitmap.width != widthPx || bitmap.height != heightPx) {
                bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmapState.value = bitmap
                pixelBufferState.value = IntArray(widthPx * heightPx)
            }
            val pixels = pixelBufferState.value
            if (pixels.size < widthPx * heightPx) pixelBufferState.value = IntArray(widthPx * heightPx).also { _ -> }

            // Convert magnitudes to dB and collect a flattened array for percentile stats
            val eps = 1e-12f
            val dbList = FloatArray(rows * drawCols)
            var flatIdx = 0
            for (r in 0 until rows) {
                val frame = spectrogram[r]
                for (c in 0 until drawCols) {
                    val src = (c * colStep).toInt().coerceIn(0, cols - 1)
                    val mag = frame[src]
                    val db = (20.0 * log10((mag + eps).toDouble())).toFloat()
                    dbList[flatIdx++] = db
                }
            }
            if (flatIdx == 0) return@Canvas

            val validCount = flatIdx
            val tmp = dbList.copyOf(validCount)
            tmp.sort()
            val lowIdx = ((validCount - 1) * minPercentile).toInt().coerceIn(0, validCount - 1)
            val highIdx = ((validCount - 1) * maxPercentile).toInt().coerceIn(0, validCount - 1)
            var lowVal = tmp[lowIdx]
            var highVal = tmp[highIdx]
            if (highVal <= lowVal) highVal = lowVal + 1f

            // Fill pixels buffer: map each spectrogram row into image rows
            // We'll map row 0 (oldest) to top pixel row 0 and newest to bottom
            val rowHeight = heightPx.toFloat() / rows
            val w = widthPx
            val h = heightPx
            // ensure pixel array size
            if (pixels.size < w * h) {
                pixelBufferState.value = IntArray(w * h)
            }
            val outPixels = pixelBufferState.value

            // Clear to black initially
            var p = 0
            val total = w * h
            while (p < total) {
                outPixels[p++] = AndroidColor.BLACK
            }

            for (r in 0 until rows) {
                val frame = spectrogram[r]
                val yStart = (r * rowHeight).toInt().coerceIn(0, h - 1)
                val yEnd = (((r + 1) * rowHeight).toInt()).coerceIn(0, h)
                for (c in 0 until drawCols) {
                    val src = (c * colStep).toInt().coerceIn(0, cols - 1)
                    val mag = frame[src]
                    val db = (20.0 * log10((mag + eps).toDouble())).toFloat()
                    var norm = (db - lowVal) / (highVal - lowVal)
                    norm = norm.coerceIn(0f, 1f)
                    norm = norm.pow(gamma)
                    val colorInt = when (colormapName.lowercase()) {
                        "viridis" -> viridisColor(norm)
                        "plasma" -> plasmaColor(norm)
                        "magma" -> magmaColor(norm)
                        else -> infernoColor(norm)
                    }
                    // map column to pixel x range
                    val xStart = (c * (w.toFloat() / drawCols)).toInt().coerceIn(0, w - 1)
                    val xEnd = (((c + 1) * (w.toFloat() / drawCols)).toInt()).coerceIn(0, w)
                    for (yy in yStart until yEnd) {
                        val rowOffset = yy * w
                        var xx = xStart
                        while (xx < xEnd) {
                            outPixels[rowOffset + xx] = colorInt
                            xx++
                        }
                    }
                }
            }

            // push pixels into bitmap and draw using Compose drawImage
            try {
                bitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
                val img = bitmap.asImageBitmap()
                drawImage(img, srcSize = IntSize(w, h), dstSize = IntSize(w, h))
            } catch (t: Throwable) {
                // fallback: draw nothing
            }
        }
    }
}

// Inferno-like colormap: interpolate between color stops
private fun infernoColor(t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val stops = intArrayOf(
        AndroidColor.parseColor("#000004"),
        AndroidColor.parseColor("#2a1a5a"),
        AndroidColor.parseColor("#5c1191"),
        AndroidColor.parseColor("#9a3a6b"),
        AndroidColor.parseColor("#de6a25"),
        AndroidColor.parseColor("#fca50a"),
        AndroidColor.parseColor("#fcffa4")
    )
    val n = stops.size
    val pos = clamped * (n - 1)
    val i = pos.toInt().coerceIn(0, n - 2)
    val local = pos - i
    val c1 = stops[i]
    val c2 = stops[i + 1]
    return lerpColor(c1, c2, local)
}
private fun viridisColor(t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val stops = intArrayOf(
        AndroidColor.parseColor("#440154"),
        AndroidColor.parseColor("#3b528b"),
        AndroidColor.parseColor("#21918c"),
        AndroidColor.parseColor("#5ec962"),
        AndroidColor.parseColor("#fde725")
    )
    val n = stops.size
    val pos = clamped * (n - 1)
    val i = pos.toInt().coerceIn(0, n - 2)
    val local = pos - i
    return lerpColor(stops[i], stops[i + 1], local)
}

private fun plasmaColor(t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val stops = intArrayOf(
        AndroidColor.parseColor("#0d0887"),
        AndroidColor.parseColor("#6a00a8"),
        AndroidColor.parseColor("#b12a90"),
        AndroidColor.parseColor("#e16462"),
        AndroidColor.parseColor("#fca636"),
        AndroidColor.parseColor("#f0f921")
    )
    val n = stops.size
    val pos = clamped * (n - 1)
    val i = pos.toInt().coerceIn(0, n - 2)
    val local = pos - i
    return lerpColor(stops[i], stops[i + 1], local)
}

private fun magmaColor(t: Float): Int {
    val clamped = t.coerceIn(0f, 1f)
    val stops = intArrayOf(
        AndroidColor.parseColor("#000004"),
        AndroidColor.parseColor("#3b0f70"),
        AndroidColor.parseColor("#8c2981"),
        AndroidColor.parseColor("#de4968"),
        AndroidColor.parseColor("#fe9f6d"),
        AndroidColor.parseColor("#fcfdbf")
    )
    val n = stops.size
    val pos = clamped * (n - 1)
    val i = pos.toInt().coerceIn(0, n - 2)
    val local = pos - i
    return lerpColor(stops[i], stops[i + 1], local)
}

private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
    val a1 = AndroidColor.alpha(c1)
    val r1 = AndroidColor.red(c1)
    val g1 = AndroidColor.green(c1)
    val b1 = AndroidColor.blue(c1)
    val a2 = AndroidColor.alpha(c2)
    val r2 = AndroidColor.red(c2)
    val g2 = AndroidColor.green(c2)
    val b2 = AndroidColor.blue(c2)
    val a = (a1 + (a2 - a1) * t).roundToInt().coerceIn(0, 255)
    val r = (r1 + (r2 - r1) * t).roundToInt().coerceIn(0, 255)
    val g = (g1 + (g2 - g1) * t).roundToInt().coerceIn(0, 255)
    val b = (b1 + (b2 - b1) * t).roundToInt().coerceIn(0, 255)
    return AndroidColor.argb(a, r, g, b)
}

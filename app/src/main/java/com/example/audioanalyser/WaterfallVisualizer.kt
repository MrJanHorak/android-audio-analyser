package com.example.audioanalyser

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.log10
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
    // Build the palette only when a setting changes, instead of parsing colors per cell.
    val palette = remember(colormapName, gamma) {
        IntArray(256) { index ->
            val level = (index / 255f).pow(gamma)
            when (colormapName.lowercase()) {
                "viridis" -> viridisColor(level)
                "plasma" -> plasmaColor(level)
                "magma" -> magmaColor(level)
                else -> infernoColor(level)
            }
        }
    }
    val latestSpectrogram by rememberUpdatedState(spectrogram)
    val image by produceState<ImageBitmap?>(
        initialValue = null, minPercentile, maxPercentile, palette
    ) {
        // Finish rendering the current frame and skip stale updates if the phone is busy.
        snapshotFlow { latestSpectrogram }.conflate().collect { history ->
            value = withContext(Dispatchers.Default) {
                renderWaterfall(history, minPercentile, maxPercentile, palette)
            }
        }
    }

    Canvas(modifier = modifier) {
        image?.let { bitmap ->
            drawImage(
                image = bitmap,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
            )
        }
    }
}

/** Render one pixel per displayed frequency bin and history frame, then scale at draw time. */
private suspend fun renderWaterfall(
    spectrogram: List<FloatArray>,
    minPercentile: Float,
    maxPercentile: Float,
    palette: IntArray
): ImageBitmap? {
    if (spectrogram.isEmpty() || spectrogram.first().isEmpty()) return null
    val rows = spectrogram.size
    val sourceCols = spectrogram.first().size
    val cols = minOf(sourceCols, 256)
    val cellCount = rows * cols
    val values = FloatArray(cellCount)
    val context = currentCoroutineContext()
    for (row in 0 until rows) {
        context.ensureActive()
        val frame = spectrogram[row]
        for (col in 0 until cols) {
            val source = (col.toLong() * sourceCols / cols).toInt()
            val magnitude = frame.getOrElse(source) { 0f }
            values[row * cols + col] = (20.0 * log10((magnitude + 1e-12f).toDouble())).toFloat()
        }
    }
    val sorted = values.copyOf()
    sorted.sort()
    context.ensureActive()
    val lowIndex = ((cellCount - 1) * minPercentile).toInt().coerceIn(0, cellCount - 1)
    val highIndex = ((cellCount - 1) * maxPercentile).toInt().coerceIn(0, cellCount - 1)
    val low = sorted[lowIndex]
    val high = maxOf(sorted[highIndex], low + 1f)
    val pixels = IntArray(cellCount) { index ->
        val normalized = ((values[index] - low) / (high - low)).coerceIn(0f, 1f)
        palette[(normalized * 255).roundToInt().coerceIn(0, 255)]
    }
    context.ensureActive()
    return Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, cols, 0, 0, cols, rows)
    }.asImageBitmap()
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

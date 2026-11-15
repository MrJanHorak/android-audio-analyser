package com.example.audioanalyser

/** Only persist offsets for identified built-in inputs; external names and IDs can be reused. */
internal fun calibrationProfileKey(source: Int, builtIn: Boolean, name: String?): String? {
    if (!builtIn || name.isNullOrBlank()) return null
    return "input_offset:$source:${name.length}:$name"
}

internal fun noiseGateProfileKey(calibrationKey: String?): String? =
    calibrationKey?.let { "input_gate:" + it.removePrefix("input_offset:") }

internal const val ANALYZER_CSV_HEADER =
    "Timestamp,dB(Z),dB(A),dB(C),DominantFreq(Hz),dB(Z) status,dB(A) status,dB(C) status"

internal fun analyzerCsvRow(timestamp: Long, z: Float, a: Float, c: Float, frequency: Float): String {
    fun level(value: Float) = if (value > 0f && value.isFinite()) value.toString() else ""
    fun status(value: Float) = if (value > 0f && value.isFinite()) "available" else "below_gate_or_floor"
    return "$timestamp,${level(z)},${level(a)},${level(c)},$frequency,${status(z)},${status(a)},${status(c)}"
}

/** Preserve the loudest bin in each fixed-width frequency group for the waterfall. */
internal fun downsampleSpectrumPeaks(spectrum: FloatArray, targetBins: Int): FloatArray {
    require(targetBins > 0 && spectrum.size >= targetBins)
    if (spectrum.size == targetBins) return spectrum
    return FloatArray(targetBins) { index ->
        val start = (index.toLong() * spectrum.size / targetBins).toInt()
        val end = ((index + 1L) * spectrum.size / targetBins).toInt()
        var peak = 0f
        for (source in start until end) peak = maxOf(peak, spectrum[source])
        peak
    }
}

package com.example.audioanalyser

/** Arithmetic mean of already-smoothed linear-magnitude spectrum updates. */
internal class SpectrumMean(private val size: Int) {
    private val sums = DoubleArray(size)
    var count: Int = 0
        private set
    fun add(frame: FloatArray): Boolean {
        if (frame.size != size || frame.any { !it.isFinite() || it < 0f } || frame.none { it > 0f }) return false
        frame.forEachIndexed { i, value -> sums[i] += value.toDouble() }
        count++
        return true
    }
    fun result(): FloatArray? = if (count < 2) null else FloatArray(size) { (sums[it] / count).toFloat() }
}

package com.example.audioanalyser.shared

/**
 * Pure ring-buffer storage for spectrogram rows.
 *
 * This class is intentionally non-reactive and not thread-safe; platform code
 * is responsible for any publication model or synchronization.
 */
class SpectrogramHistoryBuffer(
    historySize: Int = 128,
    binCount: Int
) {
    private val validatedHistorySize = historySize.also {
        require(it > 0) { "historySize must be greater than zero" }
    }
    private val validatedBinCount = binCount.also {
        require(it >= 0) { "binCount must not be negative" }
    }
    private val buffer: Array<FloatArray> = Array(validatedHistorySize) { FloatArray(validatedBinCount) }
    private var nextIndex = 0
    private var frameCount = 0

    fun addFrame(frame: FloatArray) {
        val copyLen = minOf(frame.size, validatedBinCount)
        val slot = buffer[nextIndex]

        var index = 0
        while (index < copyLen) {
            slot[index] = frame[index]
            index++
        }
        while (index < validatedBinCount) {
            slot[index] = 0f
            index++
        }

        nextIndex = (nextIndex + 1) % validatedHistorySize
        if (frameCount < validatedHistorySize) {
            frameCount += 1
        }
    }

    fun snapshot(): List<FloatArray> {
        if (frameCount == 0) {
            return emptyList()
        }

        val out = ArrayList<FloatArray>(validatedHistorySize)
        var index = nextIndex
        repeat(validatedHistorySize) {
            out.add(buffer[index].copyOf())
            index = (index + 1) % validatedHistorySize
        }
        return out
    }
}

package com.example.audioanalyser

import com.example.audioanalyser.shared.SpectrogramHistoryBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpectrogramBuffer(historySize: Int = 128, binCount: Int) {
    private val historyBuffer = SpectrogramHistoryBuffer(historySize = historySize, binCount = binCount)
    private var lastPublishedAtMs: Long? = null
    private val _spectrogramFlow = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrogramFlow: StateFlow<List<FloatArray>> = _spectrogramFlow

    @Synchronized
    fun addFrame(frame: FloatArray, timestampMs: Long) {
        historyBuffer.addFrame(frame)
        val previous = lastPublishedAtMs
        if (previous == null || timestampMs - previous >= 100L || timestampMs < previous) {
            _spectrogramFlow.value = historyBuffer.snapshot()
            lastPublishedAtMs = timestampMs
        }
    }
}

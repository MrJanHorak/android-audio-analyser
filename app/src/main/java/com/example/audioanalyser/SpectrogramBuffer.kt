package com.example.audioanalyser

import com.example.audioanalyser.shared.SpectrogramHistoryBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpectrogramBuffer(historySize: Int = 128, binCount: Int) {
    private val historyBuffer = SpectrogramHistoryBuffer(historySize = historySize, binCount = binCount)
    private val _spectrogramFlow = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrogramFlow: StateFlow<List<FloatArray>> = _spectrogramFlow

    @Synchronized
    fun addFrame(frame: FloatArray) {
        historyBuffer.addFrame(frame)
        _spectrogramFlow.value = historyBuffer.snapshot()
    }
}

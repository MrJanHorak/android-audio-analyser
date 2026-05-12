package com.example.audioanalyser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpectrogramBuffer(private val historySize: Int = 128, private val binCount: Int) {
    private val buffer: Array<FloatArray> = Array(historySize) { FloatArray(binCount) }
    private var nextIndex = 0
    private val _spectrogramFlow = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrogramFlow: StateFlow<List<FloatArray>> = _spectrogramFlow

    @Synchronized
    fun addFrame(frame: FloatArray) {
        val copyLen = minOf(frame.size, binCount)
        val slot = buffer[nextIndex]
        // copy values, zero the rest
        var i = 0
        while (i < copyLen) {
            slot[i] = frame[i]
            i++
        }
        while (i < binCount) {
            slot[i] = 0f
            i++
        }

        nextIndex = (nextIndex + 1) % historySize

        // create ordered snapshot oldest..newest
        val out = ArrayList<FloatArray>(historySize)
        var idx = nextIndex
        var c = 0
        while (c < historySize) {
            out.add(buffer[idx].copyOf())
            idx = (idx + 1) % historySize
            c++
        }
        _spectrogramFlow.value = out
    }
}

package com.example.audioanalyser

import org.junit.Assert.assertEquals
import org.junit.Test

class SpectrogramBufferTest {
    @Test
    fun retainsEveryAudioFrameWhileThrottlingDisplaySnapshots() {
        val buffer = SpectrogramBuffer(historySize = 4, binCount = 1)
        buffer.addFrame(floatArrayOf(1f), 0)
        val firstSnapshot = buffer.spectrogramFlow.value
        buffer.addFrame(floatArrayOf(2f), 25)
        buffer.addFrame(floatArrayOf(3f), 75)
        assertEquals(1f, buffer.spectrogramFlow.value.last()[0], 0f)

        buffer.addFrame(floatArrayOf(4f), 100)
        val published = buffer.spectrogramFlow.value
        assertEquals(listOf(1f, 2f, 3f, 4f), published.map { it[0] })
        assertEquals(1f, firstSnapshot.last()[0], 0f)
    }

    @Test
    fun clearRemovesOldHistoryBeforeNextResolution() {
        val buffer = SpectrogramBuffer(historySize = 2, binCount = 1)
        buffer.addFrame(floatArrayOf(1f), 0)
        buffer.clear()
        assertEquals(0, buffer.spectrogramFlow.value.size)
        buffer.addFrame(floatArrayOf(2f), 1)
        assertEquals(2f, buffer.spectrogramFlow.value.last()[0], 0f)
    }

    @Test
    fun publishesAgainIfClockMovesBackwards() {
        val buffer = SpectrogramBuffer(historySize = 2, binCount = 1)
        buffer.addFrame(floatArrayOf(1f), 500)
        buffer.addFrame(floatArrayOf(2f), 0)
        assertEquals(2f, buffer.spectrogramFlow.value.last()[0], 0f)
    }
}

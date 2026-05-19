package com.example.audioanalyser.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SpectrogramHistoryBufferTest {
    @Test
    fun rejectsNonPositiveHistorySize() {
        assertFailsWith<IllegalArgumentException> {
            SpectrogramHistoryBuffer(historySize = 0, binCount = 2)
        }
    }

    @Test
    fun rejectsNegativeBinCount() {
        assertFailsWith<IllegalArgumentException> {
            SpectrogramHistoryBuffer(historySize = 2, binCount = -1)
        }
    }

    @Test
    fun snapshotStartsEmpty() {
        val buffer = SpectrogramHistoryBuffer(historySize = 3, binCount = 2)

        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun snapshotPreservesHistoryShapeAfterFirstFrame() {
        val buffer = SpectrogramHistoryBuffer(historySize = 3, binCount = 2)

        buffer.addFrame(floatArrayOf(1f, 2f))

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertContentEquals(floatArrayOf(0f, 0f), snapshot[0])
        assertContentEquals(floatArrayOf(0f, 0f), snapshot[1])
        assertContentEquals(floatArrayOf(1f, 2f), snapshot[2])
    }

    @Test
    fun zeroFillsShortFrames() {
        val buffer = SpectrogramHistoryBuffer(historySize = 2, binCount = 4)

        buffer.addFrame(floatArrayOf(1f, 2f))

        assertContentEquals(floatArrayOf(1f, 2f, 0f, 0f), buffer.snapshot().last())
    }

    @Test
    fun truncatesLongFrames() {
        val buffer = SpectrogramHistoryBuffer(historySize = 2, binCount = 2)

        buffer.addFrame(floatArrayOf(1f, 2f, 3f))

        assertContentEquals(floatArrayOf(1f, 2f), buffer.snapshot().last())
    }

    @Test
    fun wrapsAroundOldestToNewest() {
        val buffer = SpectrogramHistoryBuffer(historySize = 3, binCount = 1)

        buffer.addFrame(floatArrayOf(1f))
        buffer.addFrame(floatArrayOf(2f))
        buffer.addFrame(floatArrayOf(3f))
        buffer.addFrame(floatArrayOf(4f))

        val snapshot = buffer.snapshot()
        assertContentEquals(floatArrayOf(2f), snapshot[0])
        assertContentEquals(floatArrayOf(3f), snapshot[1])
        assertContentEquals(floatArrayOf(4f), snapshot[2])
    }

    @Test
    fun snapshotReturnsCopies() {
        val buffer = SpectrogramHistoryBuffer(historySize = 2, binCount = 2)

        buffer.addFrame(floatArrayOf(1f, 2f))
        val firstSnapshot = buffer.snapshot()
        firstSnapshot.last()[0] = 99f

        val secondSnapshot = buffer.snapshot()
        assertContentEquals(floatArrayOf(1f, 2f), secondSnapshot.last())
    }
}

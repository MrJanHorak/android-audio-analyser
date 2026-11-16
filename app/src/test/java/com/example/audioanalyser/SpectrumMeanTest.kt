package com.example.audioanalyser

import org.junit.Assert.*
import org.junit.Test

class SpectrumMeanTest {
    @Test fun averagesLinearMagnitudesAndRequiresTwoUpdates() {
        val mean = SpectrumMean(2)
        assertTrue(mean.add(floatArrayOf(2f, 4f)))
        assertNull(mean.result())
        assertTrue(mean.add(floatArrayOf(4f, 8f)))
        assertArrayEquals(floatArrayOf(3f, 6f), mean.result(), 0f)
        assertEquals(2, mean.count)
    }
    @Test fun refusesGatedInvalidAndChangedSizeFrames() {
        val mean = SpectrumMean(2)
        assertFalse(mean.add(floatArrayOf(0f, 0f)))
        assertFalse(mean.add(floatArrayOf(Float.NaN, 1f)))
        assertFalse(mean.add(floatArrayOf(1f)))
        assertEquals(0, mean.count)
    }
}

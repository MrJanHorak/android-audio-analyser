package com.example.audioanalyser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementFormatsTest {
    @Test
    fun builtInProfilesSeparateInputModesAndNames() {
        val standard = calibrationProfileKey(1, true, "Phone mic")
        assertNotEquals(standard, calibrationProfileKey(6, true, "Phone mic"))
        assertNotEquals(standard, calibrationProfileKey(1, true, "Other mic"))
        assertNull(calibrationProfileKey(1, false, "USB mic"))
        assertNull(calibrationProfileKey(1, true, null))
    }

    @Test
    fun gateProfilesSeparateModesAndNeverPersistUnknownInputs() {
        val standard = noiseGateProfileKey(calibrationProfileKey(1, true, "Phone mic"))
        val voice = noiseGateProfileKey(calibrationProfileKey(6, true, "Phone mic"))
        assertNotEquals(standard, voice)
        assertNull(noiseGateProfileKey(calibrationProfileKey(1, false, "USB mic")))
        assertEquals("input_gate:1:9:Phone mic", standard)
    }

    @Test
    fun waterfallDownsamplingRetainsTheStrongestBinInEachGroup() {
        val reduced = downsampleSpectrumPeaks(floatArrayOf(1f, 9f, 2f, 3f, 4f, 5f, 8f, 6f), 2)
        assertEquals(listOf(9f, 8f), reduced.toList())
    }

    @Test
    fun csvDistinguishesSuppressedReadingsFromMeasurements() {
        val cells = analyzerCsvRow(123L, 70.5f, 0f, 61.2f, 125f).split(',')
        assertEquals(9, cells.size)
        assertEquals("70.5", cells[1])
        assertEquals("", cells[2])
        assertEquals("available", cells[5])
        assertEquals("below_gate_or_floor", cells[6])
        assertEquals("available", cells[7])
        assertEquals("available", cells[8])
    }

    @Test
    fun suppressedFramesDoNotProduceAnAvailableFrequency() {
        assertEquals(0f, availableDominantFrequency(0f, 440f), 0f)
        assertEquals(440f, availableDominantFrequency(50f, 440f), 0f)
        assertEquals("No peak", formatDominantFrequency(0f))
        val cells = analyzerCsvRow(123L, 0f, 0f, 0f, 440f).split(',')
        assertEquals("", cells[4])
        assertEquals("unavailable", cells[8])
    }
}

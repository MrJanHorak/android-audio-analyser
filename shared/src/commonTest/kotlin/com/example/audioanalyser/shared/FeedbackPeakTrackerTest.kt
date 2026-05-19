package com.example.audioanalyser.shared

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackPeakTrackerTest {
    @Test
    fun emitsPeakForClearCandidate() {
        val tracker = FeedbackPeakTracker()

        val peaks = tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 10, peakValue = 10f),
            currentDb = 50f,
            binSize = 10f
        )

        assertEquals(1, peaks.size)
        assertClose(100f, peaks.first().frequencyHz, 0.001f)
    }

    @Test
    fun smoothsMatchedFrequencyAcrossUpdates() {
        val tracker = FeedbackPeakTracker()

        tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 10, peakValue = 10f),
            currentDb = 50f,
            binSize = 50f
        )

        val peaks = tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 11, peakValue = 10f),
            currentDb = 50f,
            binSize = 50f
        )

        assertEquals(1, peaks.size)
        assertClose(515f, peaks.first().frequencyHz, 0.001f)
        assertEquals(2, peaks.first().holdFrames)
    }

    @Test
    fun keepsSeparatePeaksOutsideMatchTolerance() {
        val tracker = FeedbackPeakTracker()

        tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 10, peakValue = 10f),
            currentDb = 50f,
            binSize = 100f
        )

        val peaks = tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 11, peakValue = 10f),
            currentDb = 50f,
            binSize = 100f
        )

        assertEquals(2, peaks.size)
        assertTrue(peaks.any { abs(it.frequencyHz - 1000f) < 0.001f })
        assertTrue(peaks.any { abs(it.frequencyHz - 1100f) < 0.001f })
    }

    @Test
    fun decaysPeaksAwayAfterRepeatedNoSignalUpdates() {
        val tracker = FeedbackPeakTracker()

        tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 10, peakValue = 10f),
            currentDb = 50f,
            binSize = 10f
        )

        var peaks = emptyList<FeedbackPeak>()
        repeat(12) {
            peaks = tracker.update(
                spectrum = FloatArray(16),
                currentDb = 0f,
                binSize = 10f
            )
        }

        assertTrue(peaks.isEmpty())
    }

    @Test
    fun clearResetsTrackedState() {
        val tracker = FeedbackPeakTracker()

        tracker.update(
            spectrum = spectrumWithPeak(size = 16, peakIndex = 10, peakValue = 10f),
            currentDb = 50f,
            binSize = 10f
        )

        tracker.clear()

        val peaks = tracker.update(
            spectrum = FloatArray(16),
            currentDb = 0f,
            binSize = 10f
        )

        assertTrue(peaks.isEmpty())
    }

    private fun spectrumWithPeak(size: Int, peakIndex: Int, peakValue: Float): FloatArray {
        return FloatArray(size).also { spectrum ->
            spectrum[peakIndex] = peakValue
            if (peakIndex - 1 >= 0) spectrum[peakIndex - 1] = peakValue * 0.1f
            if (peakIndex + 1 < size) spectrum[peakIndex + 1] = peakValue * 0.1f
        }
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected $expected but was $actual with tolerance $tolerance"
        )
    }
}

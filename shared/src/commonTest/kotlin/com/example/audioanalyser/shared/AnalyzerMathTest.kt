package com.example.audioanalyser.shared

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzerMathTest {
    @Test
    fun calculateRmsMatchesCurrentAnalyzerBehavior() {
        assertClose(
            expected = 1000.0,
            actual = AnalyzerMath.calculateRms(shortArrayOf(1000, -1000, 1000, -1000), 4).toDouble(),
            tolerance = 1e-6
        )
        assertClose(
            expected = 3.5355339059327378,
            actual = AnalyzerMath.calculateRms(shortArrayOf(3, 4), 2).toDouble(),
            tolerance = 1e-6
        )
    }

    @Test
    fun dbConversionHelpersMatchCurrentAnalyzerThresholding() {
        assertClose(
            expected = 60.0,
            actual = AnalyzerMath.rawDbFromRms(1000f).toDouble(),
            tolerance = 1e-6
        )
        assertEquals(-120f, AnalyzerMath.rawDbFromRms(0f))
        assertEquals(40f, AnalyzerMath.applyDbOffset(rawDb = 10f, dbOffset = 30f))
        assertEquals(0f, AnalyzerMath.applyDbOffset(rawDb = -80f, dbOffset = 30f))
        assertEquals(40f, AnalyzerMath.applyNoiseGate(calibratedDb = 40f, noiseThreshold = 25f))
        assertEquals(0f, AnalyzerMath.applyNoiseGate(calibratedDb = 25f, noiseThreshold = 25f))
    }

    @Test
    fun weightingCurvesMatchCurrentReferenceValues() {
        assertClose(
            expected = 1.0000396587558529,
            actual = AnalyzerMath.aWeightingLinear(1000.0),
            tolerance = 1e-9
        )
        assertClose(
            expected = 0.11037694664912509,
            actual = AnalyzerMath.aWeightingLinear(100.0),
            tolerance = 1e-9
        )
        assertClose(
            expected = 0.9997810644629856,
            actual = AnalyzerMath.cWeightingLinear(1000.0),
            tolerance = 1e-9
        )
        assertClose(
            expected = 0.9658798703581338,
            actual = AnalyzerMath.cWeightingLinear(100.0),
            tolerance = 1e-9
        )
        assertTrue(AnalyzerMath.aWeightingLinear(100.0) < AnalyzerMath.cWeightingLinear(100.0))
    }

    @Test
    fun suggestCutFrequencyKeepsExistingBucketRules() {
        assertEquals(200f, AnalyzerMath.suggestCutFrequency(198f))
        assertEquals(1000f, AnalyzerMath.suggestCutFrequency(999f))
        assertEquals(3450f, AnalyzerMath.suggestCutFrequency(3456f))
        assertEquals(4550f, AnalyzerMath.suggestCutFrequency(4567f))
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected $expected but was $actual with tolerance $tolerance"
        )
    }
}

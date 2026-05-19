package com.example.audioanalyser.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyzerTextFormatterTest {
    @Test
    fun formatsFrequencyLabels() {
        assertEquals("440 Hz", AnalyzerTextFormatter.formatFrequencyLabel(440f))
        assertEquals("1.5 kHz", AnalyzerTextFormatter.formatFrequencyLabel(1500f))
        assertEquals("1000 Hz", AnalyzerTextFormatter.formatFrequencyLabel(999.95f))
    }

    @Test
    fun formatsFrequencyRanges() {
        assertEquals(
            "100 Hz - 2.5 kHz",
            AnalyzerTextFormatter.formatFrequencyRange(100f, 2500f)
        )
    }

    @Test
    fun formatsSignedAndRelativeDbValues() {
        assertEquals("+2.5 dB", AnalyzerTextFormatter.formatRelativeCurveLevel(2.5f))
        assertEquals("-2.5 dB", AnalyzerTextFormatter.formatRelativeCurveLevel(-2.5f))
        assertEquals("+0.0 dB", AnalyzerTextFormatter.formatSignedDbDelta(0f))
        assertEquals("-0.0 dB", AnalyzerTextFormatter.formatSignedDbDelta(-0.04f))
    }

    @Test
    fun formatsSignedFrequencyDeltas() {
        assertEquals("+1.2 kHz", AnalyzerTextFormatter.formatSignedFrequencyDelta(1200f))
        assertEquals("-45 Hz", AnalyzerTextFormatter.formatSignedFrequencyDelta(-45f))
    }

    @Test
    fun formatsDecibelValuesWithoutUnits() {
        assertEquals("12.3", AnalyzerTextFormatter.formatDecibelValue(12.34f))
        assertEquals("--", AnalyzerTextFormatter.formatDecibelValue(Float.NaN))
    }
}

package com.example.audioanalyser.shared

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzerFrameAnalyzerTest {
    private val sampleRate = 44_100
    private val fftSize = 1_024

    @Test
    fun analyzePcm16DetectsExpectedDominantFrequency() {
        val targetBin = 23
        val expectedFrequencyHz = targetBin * sampleRate.toFloat() / fftSize
        val analyzer = AnalyzerFrameAnalyzer(sampleRate = sampleRate, fftSize = fftSize)
        val buffer = createSineWavePcm16(bin = targetBin, amplitude = 16_000.0)

        val result = analyzer.analyzePcm16(buffer = buffer, read = buffer.size)

        assertEquals(fftSize / 2, result.magnitudes.size)
        assertClose(
            expected = expectedFrequencyHz.toDouble(),
            actual = result.dominantFrequencyHz.toDouble(),
            tolerance = result.binSize.toDouble()
        )
    }

    @Test
    fun analyzeFloatMatchesPcm16SummaryForEquivalentSamples() {
        val targetBin = 17
        val analyzer = AnalyzerFrameAnalyzer(sampleRate = sampleRate, fftSize = fftSize)
        val pcm16Buffer = createSineWavePcm16(bin = targetBin, amplitude = 12_000.0)
        val floatBuffer = FloatArray(pcm16Buffer.size) { index -> pcm16Buffer[index].toFloat() }

        val pcm16Result = analyzer.analyzePcm16(buffer = pcm16Buffer, read = pcm16Buffer.size)
        val floatResult = analyzer.analyzeFloat(buffer = floatBuffer, read = floatBuffer.size)

        assertClose(
            expected = pcm16Result.dominantFrequencyHz.toDouble(),
            actual = floatResult.dominantFrequencyHz.toDouble(),
            tolerance = 1e-6
        )
        assertClose(
            expected = pcm16Result.aWeightingAdjustmentDb.toDouble(),
            actual = floatResult.aWeightingAdjustmentDb.toDouble(),
            tolerance = 1e-6
        )
        assertClose(
            expected = pcm16Result.cWeightingAdjustmentDb.toDouble(),
            actual = floatResult.cWeightingAdjustmentDb.toDouble(),
            tolerance = 1e-6
        )
    }

    @Test
    fun lowFrequencyToneShowsStrongerAWeightingReductionThanC() {
        val lowBin = 2
        val analyzer = AnalyzerFrameAnalyzer(sampleRate = sampleRate, fftSize = fftSize)
        val buffer = FloatArray(fftSize) { index ->
            sin(2.0 * PI * lowBin * index / fftSize).toFloat()
        }

        val result = analyzer.analyzeFloat(buffer = buffer, read = buffer.size)

        assertTrue(result.aWeightingAdjustmentDb < result.cWeightingAdjustmentDb)
        assertTrue(result.cWeightingAdjustmentDb < 0f)
    }

    private fun createSineWavePcm16(bin: Int, amplitude: Double): ShortArray {
        return ShortArray(fftSize) { index ->
            (amplitude * sin(2.0 * PI * bin * index / fftSize)).roundToInt().toShort()
        }
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected $expected but was $actual with tolerance $tolerance"
        )
    }
}

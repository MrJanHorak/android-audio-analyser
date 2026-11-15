package com.example.audioanalyser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSmootherTest {
    @Test
    fun constantSignalAndResetSettleImmediately() {
        val smoother = LevelSmoother()
        assertEquals(70f, smoother.update(70f, 0.02), 0.001f)
        assertEquals(70f, smoother.update(70f, 0.02), 0.001f)
        smoother.reset()
        assertEquals(50f, smoother.update(50f, 0.02), 0.001f)
    }

    @Test
    fun averagesEnergyRatherThanAveragingDecibels() {
        val smoother = LevelSmoother()
        smoother.update(60f, 0.02)
        val afterOneSecond = smoother.update(80f, 1.0)
        assertEquals(78.0f, afterOneSecond, 0.1f)
        assertTrue(afterOneSecond > 70f)
    }

    @Test
    fun handlesVeryQuietFramesAndDecaysAfterLoudSound() {
        val smoother = LevelSmoother()
        assertEquals(80f, smoother.update(80f, 0.02), 0.001f)
        val quieter = smoother.update(-90f, 1.0)
        assertTrue(quieter.isFinite())
        assertTrue(quieter < 80f && quieter > -90f)
        assertTrue(smoother.update(-90f, 1.0) < quieter)
    }

    @Test
    fun responseUsesElapsedAudioTimeRatherThanFrameCount() {
        fun simulate(frameSeconds: Double, frames: Int): Float {
            val smoother = LevelSmoother()
            smoother.update(60f, frameSeconds)
            var result = 0f
            repeat(frames) { result = smoother.update(80f, frameSeconds) }
            return result
        }
        assertEquals(simulate(0.025, 40), simulate(0.1, 10), 0.001f)
    }
}

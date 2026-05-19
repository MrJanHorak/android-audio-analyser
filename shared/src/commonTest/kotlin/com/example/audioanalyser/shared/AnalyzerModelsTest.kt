package com.example.audioanalyser.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzerModelsTest {
    @Test
    fun targetCurveFrequenciesStayInExpectedOrder() {
        assertEquals(
            listOf(31.5f, 63f, 125f, 250f, 1000f, 4000f, 8000f, 16000f),
            customTargetCurveFrequencies
        )
    }

    @Test
    fun venueCategoryOptionsRemainStable() {
        assertEquals("Church", venueCategoryOptions.first())
        assertEquals("Other", venueCategoryOptions.last())
        assertTrue("Touring" in venueCategoryOptions)
    }

    @Test
    fun feedbackPeakStabilityKeysStayAligned() {
        assertEquals(
            "watch",
            FeedbackPeakPresentation.stabilityKey(
                FeedbackPeak(
                    frequencyHz = 400f,
                    suggestedCutHz = 400f,
                    normalizedLevel = 0.4f,
                    stability = 0.2f,
                    holdFrames = 2
                )
            )
        )
        assertEquals(
            "building",
            FeedbackPeakPresentation.stabilityKey(
                FeedbackPeak(
                    frequencyHz = 630f,
                    suggestedCutHz = 630f,
                    normalizedLevel = 0.5f,
                    stability = 0.6f,
                    holdFrames = 7
                )
            )
        )
        assertEquals(
            "stable",
            FeedbackPeakPresentation.stabilityKey(
                FeedbackPeak(
                    frequencyHz = 1000f,
                    suggestedCutHz = 1000f,
                    normalizedLevel = 0.8f,
                    stability = 0.9f,
                    holdFrames = 15
                )
            )
        )
    }
}

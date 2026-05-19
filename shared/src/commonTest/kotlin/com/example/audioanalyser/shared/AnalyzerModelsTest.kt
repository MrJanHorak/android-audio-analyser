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
}

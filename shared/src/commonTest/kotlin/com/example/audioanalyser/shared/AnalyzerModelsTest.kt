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
    fun defaultCustomTargetCurveSettingsRemainStable() {
        val defaults = defaultCustomTargetCurveSettings()

        assertEquals("My Room", defaults.name)
        assertEquals(listOf(3.5f, 3f, 2f, 1f, 0f, -1f, -2.5f, -4f), defaults.points)
    }

    @Test
    fun displayNameIncludesCategoryVenueAndCurveName() {
        val preset = SavedTargetCurvePreset(
            id = "saved_curve_test",
            settings = CustomTargetCurveSettings(name = "Sunday Mix", points = emptyList()),
            venueName = "Main Hall",
            venueCategory = "Church"
        )

        assertEquals("Church • Main Hall - Sunday Mix", preset.displayName())
    }

    @Test
    fun buildSavedTargetCurveDescriptionHandlesBlankVenue() {
        assertEquals(
            "Your saved room or organisation target curve.",
            buildSavedTargetCurveDescription("")
        )
        assertEquals(
            "Saved target curve for Main Hall.",
            buildSavedTargetCurveDescription("Main Hall")
        )
    }

    @Test
    fun normalizeCustomTargetCurveLevelsFillsMissingValuesFromDefaults() {
        val normalized = normalizeCustomTargetCurveLevels(listOf(9f, 8f))

        assertEquals(customTargetCurveFrequencies.size, normalized.size)
        assertEquals(9f, normalized[0])
        assertEquals(8f, normalized[1])
        assertEquals(defaultCustomTargetCurveSettings().points[2], normalized[2])
        assertEquals(defaultCustomTargetCurveSettings().points.last(), normalized.last())
    }

    @Test
    fun buildCustomTargetCurvePointsUsesSharedFrequenciesAndNormalizedLevels() {
        val points = buildCustomTargetCurvePoints(listOf(4f, 3f, 2f))

        assertEquals(customTargetCurveFrequencies.size, points.size)
        assertEquals(customTargetCurveFrequencies.first(), points.first().frequencyHz)
        assertEquals(4f, points.first().relativeDb)
        assertEquals(defaultCustomTargetCurveSettings().points.last(), points.last().relativeDb)
    }

    @Test
    fun markUsedUpdatesPresetTimestamp() {
        val preset = SavedTargetCurvePreset(
            id = "saved_curve_test",
            settings = defaultCustomTargetCurveSettings()
        )

        assertEquals(1234L, preset.markUsed(1234L).lastUsedAtEpochMillis)
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

    @Test
    fun analyzerReferenceCatalogKeepsExpectedCoreProfiles() {
        val overlays = AnalyzerReferenceCatalog.overlayProfiles()
        val targetCurves = AnalyzerReferenceCatalog.targetCurveProfiles()

        assertEquals("off", overlays.first().id)
        assertTrue(overlays.any { it.id == "lead_vocal" })
        assertTrue(overlays.any { it.id == "speech" })

        assertEquals("off", targetCurves.first().id)
        assertTrue(targetCurves.any { it.id == "harman_room" })
        assertTrue(targetCurves.any { it.id == "lead_vocal_guide" })
    }

    @Test
    fun analyzerReferenceCatalogKeepsEqEntriesAvailable() {
        val entries = AnalyzerReferenceCatalog.eqReferenceEntries()

        assertTrue(entries.any { it.title == "Lower-Range Vocal" })
        assertTrue(entries.any { it.title == "General Gain Staging & Tuning" })
    }
}

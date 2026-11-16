package com.example.audioanalyser

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SavedSpectraTest {
    private val original = SavedSpectrum(
        "id-1", "Balcony", "Centre", "Check after doors open", 123456L,
        "USB microphone", "Standard mic", SpectrumSnapshot(FloatArray(512) { it.toFloat() }, 100f, 70f)
    )

    @Test fun roundTripsSpectrumAndContext() {
        val decoded = parseSavedSpectra(savedSpectraToJson(listOf(original)))!!
        assertEquals("Balcony", decoded.single().name)
        assertEquals("Centre", decoded.single().position)
        assertEquals("USB microphone", decoded.single().input)
        assertEquals(123456L, decoded.single().snapshot.capturedAt)
        assertArrayEquals(original.snapshot.frequencies, decoded.single().snapshot.frequencies, 0f)
        assertEquals(1, decoded.single().snapshot.captureFrames)
        val averaged = original.copy(snapshot = original.snapshot.copy(captureFrames = 12, captureDurationMs = 3000))
        val restored = parseSavedSpectra(savedSpectraToJson(listOf(averaged)))!!.single()
        assertEquals(12, restored.snapshot.captureFrames)
        assertEquals(3000L, restored.snapshot.captureDurationMs)
        val legacy = savedSpectraToJson(listOf(original))
        legacy.getJSONObject(0).remove("captureFrames"); legacy.getJSONObject(0).remove("captureDurationMs")
        assertEquals(1, parseSavedSpectra(legacy)!!.single().snapshot.captureFrames)
    }

    @Test fun rejectsInvalidBinsAndDuplicateIds() {
        val bad = savedSpectraToJson(listOf(original))
        bad.getJSONObject(0).put("frequencies", JSONArray().put(1))
        assertNull(parseSavedSpectra(bad))
        assertNull(parseSavedSpectra(savedSpectraToJson(listOf(original, original))))
    }

    @Test fun versionTwoRequiresValidSnapshotsAndVersionOneRemainsValid() {
        val backup = JSONObject().put("formatVersion", 2).put("preferences", JSONObject())
            .put("savedTargetCurves", JSONArray()).put("savedSpectra", savedSpectraToJson(listOf(original)))
        assertTrue(isValidAnalyzerBackup(backup))
        backup.remove("savedSpectra")
        assertFalse(isValidAnalyzerBackup(backup))
        backup.put("formatVersion", 1)
        assertTrue(isValidAnalyzerBackup(backup))
    }
}

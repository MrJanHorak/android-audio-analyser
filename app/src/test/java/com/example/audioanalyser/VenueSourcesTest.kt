package com.example.audioanalyser

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VenueSourcesTest {
    private val source = VenueSource("s1", "venue1", "Guitar", "DI", "7", "Check patch")
    @Test fun roundTripsAndRejectsDuplicates() {
        assertEquals(listOf(source), parseVenueSources(venueSourcesToJson(listOf(source))))
        assertNull(parseVenueSources(venueSourcesToJson(listOf(source, source))))
    }
    @Test fun backupRejectsSourcesWithoutTheirVenue() {
        val root = JSONObject().put("formatVersion", 3).put("preferences", JSONObject())
            .put("savedSpectra", JSONArray()).put("savedTargetCurves", JSONArray())
            .put("venueSources", venueSourcesToJson(listOf(source)))
        assertFalse(isValidAnalyzerBackup(root))
        root.getJSONArray("savedTargetCurves").put(JSONObject().put("id", "venue1")
            .put("points", JSONArray(listOf(0, 0, 0, 0, 0, 0, 0, 0))))
        assertTrue(isValidAnalyzerBackup(root))
    }
}

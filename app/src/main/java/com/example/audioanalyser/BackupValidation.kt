package com.example.audioanalyser

import org.json.JSONArray
import org.json.JSONObject

/** Reject unknown, partial or malformed backups before any current user data is replaced. */
internal fun isValidAnalyzerBackup(root: JSONObject): Boolean {
    val version = root.opt("formatVersion")
    if (version !is Number || version.toDouble() != 1.0) return false
    val preferences = root.opt("preferences") as? JSONObject ?: return false
    for (key in listOf("dbOffset", "noiseThreshold")) {
        if (preferences.has(key)) {
            val value = preferences.opt(key) as? Number ?: return false
            if (!value.toDouble().isFinite() || !value.toFloat().isFinite()) return false
            if (key == "noiseThreshold" && value.toDouble() !in 0.0..120.0) return false
        }
    }
    for (key in listOf("feedbackHuntEnabled", "bigGraphModeEnabled")) {
        if (preferences.has(key) && preferences.opt(key) !is Boolean) return false
    }
    for (key in listOf("selectedOverlayId", "selectedTargetCurveId")) {
        if (preferences.has(key) && preferences.opt(key) !is String) return false
    }
    val curves = when {
        root.has("savedTargetCurves") -> root.opt("savedTargetCurves") as? JSONArray
        root.has("savedCurves") -> root.opt("savedCurves") as? JSONArray
        else -> null
    } ?: return false
    val ids = mutableSetOf<String>()
    for (index in 0 until curves.length()) {
        val item = curves.opt(index) as? JSONObject ?: return false
        val id = (item.opt("id") as? String)?.takeIf { it.isNotBlank() } ?: return false
        if (!ids.add(id)) return false
        val points = item.opt("points") as? JSONArray ?: return false
        if (points.length() != 8) return false
        for (point in 0 until points.length()) {
            val value = points.opt(point) as? Number ?: return false
            if (!value.toDouble().isFinite() || value.toDouble() !in -12.0..6.0) return false
        }
        for (key in listOf("name", "venueName", "venueCategory", "deskName", "paSystem", "roomSize", "problemBands", "generalNotes", "notes")) {
            if (item.has(key) && item.opt(key) !is String) return false
        }
    }
    return true
}

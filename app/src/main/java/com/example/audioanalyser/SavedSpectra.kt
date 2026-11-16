package com.example.audioanalyser

import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_SAVED_SPECTRA = 20
internal const val SAVED_SPECTRA_KEY = "saved_spectra_v1"

internal data class SavedSpectrum(
    val id: String,
    val name: String,
    val position: String,
    val notes: String,
    val capturedAt: Long,
    val input: String,
    val inputMode: String,
    val snapshot: SpectrumSnapshot
)

internal fun savedSpectraToJson(items: List<SavedSpectrum>): JSONArray = JSONArray().apply {
    items.forEach { item ->
        put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("position", item.position); put("notes", item.notes)
            put("capturedAt", item.capturedAt); put("input", item.input); put("inputMode", item.inputMode)
            put("dominantFrequency", item.snapshot.dominantFrequency.toDouble()); put("dbLevel", item.snapshot.dbLevel.toDouble())
            put("captureFrames", item.snapshot.captureFrames); put("captureDurationMs", item.snapshot.captureDurationMs)
            put("frequencies", JSONArray().apply { item.snapshot.frequencies.forEach { put(it.toDouble()) } })
        })
    }
}

/** Returns null instead of partially importing an invalid list. */
internal fun parseSavedSpectra(array: JSONArray): List<SavedSpectrum>? = runCatching {
    require(array.length() <= MAX_SAVED_SPECTRA)
    val ids = mutableSetOf<String>()
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        fun str(key: String, max: Int): String {
            val value = item.get(key) as? String ?: error("Invalid $key")
            require(value.length <= max)
            return value
        }
        fun number(key: String): Float {
            val value = item.get(key) as? Number ?: error("Invalid $key")
            val result = value.toFloat()
            require(result.isFinite() && result >= 0f)
            return result
        }
        val id = str("id", 100).also { require(it.isNotBlank() && ids.add(it)) }
        val name = str("name", 100).also { require(it.isNotBlank()) }
        val position = str("position", 200)
        val notes = str("notes", 1000)
        val input = str("input", 200)
        val mode = str("inputMode", 100)
        val time = item.get("capturedAt") as? Number ?: error("Invalid time")
        require(time.toDouble().isFinite() && time.toLong() > 0)
        val bins = item.getJSONArray("frequencies")
        require(bins.length() == 512 || bins.length() == 2048)
        val magnitudes = FloatArray(bins.length()) { i ->
            val value = bins.get(i) as? Number ?: error("Invalid magnitude")
            value.toFloat().also { require(it.isFinite() && it >= 0f) }
        }
        val frames = if (item.has("captureFrames")) (item.get("captureFrames") as? Number)?.toInt() ?: error("frames") else 1
        val duration = if (item.has("captureDurationMs")) (item.get("captureDurationMs") as? Number)?.toLong() ?: error("duration") else 0L
        require(frames in 1..10000 && duration in 0..60000)
        SavedSpectrum(id, name, position, notes, time.toLong(), input, mode,
            SpectrumSnapshot(magnitudes, number("dominantFrequency"), number("dbLevel"), time.toLong(), input, mode, frames, duration))
    }
}.getOrNull()

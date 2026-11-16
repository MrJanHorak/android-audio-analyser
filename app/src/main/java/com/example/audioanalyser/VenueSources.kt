package com.example.audioanalyser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val VENUE_SOURCES_KEY = "venue_sources_v1"
internal data class VenueSource(val id: String, val venueId: String, val name: String,
    val input: String, val channel: String, val notes: String)

internal fun venueSourcesToJson(items: List<VenueSource>) = JSONArray().apply {
    items.forEach { item -> put(JSONObject().apply {
        put("id", item.id); put("venueId", item.venueId); put("name", item.name)
        put("input", item.input); put("channel", item.channel); put("notes", item.notes)
    }) }
}
internal fun parseVenueSources(array: JSONArray): List<VenueSource>? = runCatching {
    require(array.length() <= 200)
    val ids = mutableSetOf<String>()
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        fun field(key: String, limit: Int): String = (item.get(key) as? String ?: error(key)).also { require(it.length <= limit) }
        VenueSource(field("id", 100), field("venueId", 100), field("name", 100), field("input", 200), field("channel", 200), field("notes", 2000)).also {
            require(it.id.isNotBlank() && ids.add(it.id) && it.venueId.isNotBlank() && it.name.isNotBlank())
        }
    }
}.getOrNull()
internal fun loadVenueSources(context: Context): List<VenueSource> = runCatching {
    parseVenueSources(JSONArray(context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).getString(VENUE_SOURCES_KEY, "[]") ?: "[]")) ?: emptyList()
}.getOrDefault(emptyList())
internal fun saveVenueSources(context: Context, items: List<VenueSource>) {
    context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().putString(VENUE_SOURCES_KEY, venueSourcesToJson(items).toString()).apply()
}

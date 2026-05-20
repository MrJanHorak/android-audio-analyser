package com.example.audioanalyser

import androidx.compose.ui.graphics.Color
import com.example.audioanalyser.shared.AnalyzerGuideColor
import com.example.audioanalyser.shared.AnalyzerGuideRangeBand as SharedAnalyzerGuideRangeBand
import com.example.audioanalyser.shared.AnalyzerOverlayReferenceProfile as SharedAnalyzerOverlayReferenceProfile
import com.example.audioanalyser.shared.AnalyzerReferenceCatalog
import com.example.audioanalyser.shared.AnalyzerTargetCurveReferenceProfile as SharedAnalyzerTargetCurveReferenceProfile
import com.example.audioanalyser.shared.AnalyzerTextFormatter
import com.example.audioanalyser.shared.CUSTOM_TARGET_CURVE_ID_PREFIX
import com.example.audioanalyser.shared.buildCustomTargetCurvePoints
import com.example.audioanalyser.shared.buildSavedTargetCurveDescription
import com.example.audioanalyser.shared.CustomTargetCurveSettings
import com.example.audioanalyser.shared.defaultCustomTargetCurveSettings
import com.example.audioanalyser.shared.EqReferenceEntry
import com.example.audioanalyser.shared.SavedTargetCurvePreset
import com.example.audioanalyser.shared.TargetCurvePoint
import com.example.audioanalyser.shared.customTargetCurveFrequencies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AnalyzerRangeBand(
    val label: String,
    val startHz: Float,
    val endHz: Float,
    val note: String,
    val color: Color
) {
    fun contains(frequencyHz: Float): Boolean = frequencyHz in startHz..endHz
}

data class AnalyzerOverlayProfile(
    val id: String,
    val label: String,
    val description: String,
    val bands: List<AnalyzerRangeBand>
) {
    fun bandFor(frequencyHz: Float): AnalyzerRangeBand? = bands.firstOrNull { it.contains(frequencyHz) }
}

fun SavedTargetCurvePreset.lastUsedLabel(nowMillis: Long = System.currentTimeMillis()): String {
    if (lastUsedAtEpochMillis <= 0L) {
        return "Not used yet"
    }

    val elapsedMillis = (nowMillis - lastUsedAtEpochMillis).coerceAtLeast(0L)
    val elapsedDays = TimeUnit.MILLISECONDS.toDays(elapsedMillis)
    return when {
        elapsedDays == 0L -> "Today"
        elapsedDays == 1L -> "Yesterday"
        elapsedDays < 7L -> "$elapsedDays days ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(lastUsedAtEpochMillis))
    }
}

fun SavedTargetCurvePreset.venueDetailRows(): List<Pair<String, String>> = buildList {
    if (venueCategory.isNotBlank()) add("Category" to venueCategory)
    if (venueName.isNotBlank()) add("Venue" to venueName)
    if (deskName.isNotBlank()) add("Desk" to deskName)
    if (paSystem.isNotBlank()) add("PA" to paSystem)
    if (roomSize.isNotBlank()) add("Room" to roomSize)
    if (problemBands.isNotBlank()) add("Problem bands" to problemBands)
    if (lastUsedAtEpochMillis > 0L) add("Last used" to lastUsedLabel())
    if (notes.isNotBlank()) add("Notes" to notes)
}

data class AnalyzerTargetCurveProfile(
    val id: String,
    val label: String,
    val badge: String,
    val description: String,
    val useCase: String,
    val caution: String,
    val color: Color,
    val points: List<TargetCurvePoint>
) {
    val isEnabled: Boolean = points.isNotEmpty()
}

private fun AnalyzerGuideColor.asComposeColor(): Color = Color(
    red = red,
    green = green,
    blue = blue
)

private fun SharedAnalyzerGuideRangeBand.asAndroidBand(): AnalyzerRangeBand = AnalyzerRangeBand(
    label = label,
    startHz = startHz,
    endHz = endHz,
    note = note,
    color = color.asComposeColor()
)

private fun SharedAnalyzerOverlayReferenceProfile.asAndroidProfile(): AnalyzerOverlayProfile = AnalyzerOverlayProfile(
    id = id,
    label = label,
    description = summary,
    bands = bands.map { it.asAndroidBand() }
)

private fun SharedAnalyzerTargetCurveReferenceProfile.asAndroidProfile(): AnalyzerTargetCurveProfile = AnalyzerTargetCurveProfile(
    id = id,
    label = label,
    badge = badge,
    description = summary,
    useCase = useCase,
    caution = caution,
    color = color.asComposeColor(),
    points = points
)

val analyzerOverlayProfiles = AnalyzerReferenceCatalog.overlayProfiles().map { it.asAndroidProfile() }

val analyzerTargetCurveProfiles = AnalyzerReferenceCatalog.targetCurveProfiles().map { it.asAndroidProfile() }

fun newSavedTargetCurvePreset(
    settings: CustomTargetCurveSettings = defaultCustomTargetCurveSettings(),
    venueName: String = "",
    venueCategory: String = "",
    deskName: String = "",
    paSystem: String = "",
    roomSize: String = "",
    problemBands: String = "",
    lastUsedAtEpochMillis: Long = 0L,
    notes: String = ""
): SavedTargetCurvePreset =
    SavedTargetCurvePreset(
        id = "$CUSTOM_TARGET_CURVE_ID_PREFIX${UUID.randomUUID()}",
        settings = settings,
        venueName = venueName,
        venueCategory = venueCategory,
        deskName = deskName,
        paSystem = paSystem,
        roomSize = roomSize,
        problemBands = problemBands,
        lastUsedAtEpochMillis = lastUsedAtEpochMillis,
        notes = notes
    )

fun buildCustomTargetCurveProfile(preset: SavedTargetCurvePreset): AnalyzerTargetCurveProfile {
    val defaultSettings = defaultCustomTargetCurveSettings()

    return AnalyzerTargetCurveProfile(
        id = preset.id,
        label = preset.settings.name.ifBlank { defaultSettings.name },
        badge = "Saved",
        description = buildSavedTargetCurveDescription(preset.venueName),
        useCase = "Useful when you regularly mix in the same room or for the same organisation and want your own repeatable reference.",
        caution = "Use it as a recall target, then still tune by ear for the audience size, PA deployment, and stage volume that day.",
        color = Color(0xFF8E24AA),
        points = buildCustomTargetCurvePoints(
            points = preset.settings.points,
            fallbackPoints = defaultSettings.points
        )
    )
}

fun buildAnalyzerTargetCurveProfiles(savedCurves: List<SavedTargetCurvePreset>): List<AnalyzerTargetCurveProfile> =
    analyzerTargetCurveProfiles + savedCurves.map(::buildCustomTargetCurveProfile)

val eqReferenceEntries = AnalyzerReferenceCatalog.eqReferenceEntries()

fun formatFrequencyLabel(frequencyHz: Float): String =
    AnalyzerTextFormatter.formatFrequencyLabel(frequencyHz)

fun formatFrequencyRange(startHz: Float, endHz: Float): String =
    AnalyzerTextFormatter.formatFrequencyRange(startHz, endHz)

fun formatRelativeCurveLevel(relativeDb: Float): String =
    AnalyzerTextFormatter.formatRelativeCurveLevel(relativeDb)

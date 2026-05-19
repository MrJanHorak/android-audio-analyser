package com.example.audioanalyser.shared

fun SavedTargetCurvePreset.displayName(): String = buildString {
    if (venueCategory.isNotBlank()) {
        append(venueCategory)
        append(" • ")
    }
    if (venueName.isNotBlank()) {
        append(venueName)
    }
    if (settings.name.isNotBlank()) {
        if (isNotEmpty()) {
            append(" - ")
        }
        append(settings.name)
    }
}.ifBlank { settings.name }

fun SavedTargetCurvePreset.markUsed(nowMillis: Long): SavedTargetCurvePreset =
    copy(lastUsedAtEpochMillis = nowMillis)

fun defaultCustomTargetCurveSettings(): CustomTargetCurveSettings = CustomTargetCurveSettings(
    name = "My Room",
    points = listOf(3.5f, 3f, 2f, 1f, 0f, -1f, -2.5f, -4f)
)

fun buildSavedTargetCurveDescription(venueName: String): String =
    if (venueName.isBlank()) {
        "Your saved room or organisation target curve."
    } else {
        "Saved target curve for $venueName."
    }

fun normalizeCustomTargetCurveLevels(
    points: List<Float>,
    fallbackPoints: List<Float> = defaultCustomTargetCurveSettings().points
): List<Float> = customTargetCurveFrequencies.mapIndexed { index, _ ->
    points.getOrElse(index) { fallbackPoints.getOrElse(index) { 0f } }
}

fun buildCustomTargetCurvePoints(
    points: List<Float>,
    fallbackPoints: List<Float> = defaultCustomTargetCurveSettings().points
): List<TargetCurvePoint> =
    customTargetCurveFrequencies
        .zip(normalizeCustomTargetCurveLevels(points, fallbackPoints))
        .map { (frequencyHz, relativeDb) ->
            TargetCurvePoint(frequencyHz = frequencyHz, relativeDb = relativeDb)
        }

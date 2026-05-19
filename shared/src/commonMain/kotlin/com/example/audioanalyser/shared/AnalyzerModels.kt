package com.example.audioanalyser.shared

data class FeedbackPeak(
    val frequencyHz: Float,
    val suggestedCutHz: Float,
    val normalizedLevel: Float,
    val stability: Float,
    val holdFrames: Int
)

data class TargetCurvePoint(
    val frequencyHz: Float,
    val relativeDb: Float
)

data class CustomTargetCurveSettings(
    val name: String,
    val points: List<Float>
)

data class SavedTargetCurvePreset(
    val id: String,
    val settings: CustomTargetCurveSettings,
    val venueName: String = "",
    val venueCategory: String = "",
    val deskName: String = "",
    val paSystem: String = "",
    val roomSize: String = "",
    val problemBands: String = "",
    val lastUsedAtEpochMillis: Long = 0L,
    val notes: String = ""
)

data class EqReferenceEntry(
    val title: String,
    val role: String,
    val highPass: String,
    val body: String,
    val presence: String,
    val watchOut: String,
    val startingMove: String
)

val venueCategoryOptions = listOf(
    "Church",
    "Club",
    "Theatre",
    "Corporate",
    "School",
    "Outdoor",
    "Touring",
    "Other"
)

const val CUSTOM_TARGET_CURVE_ID_PREFIX = "saved_curve_"

val customTargetCurveFrequencies = listOf(31.5f, 63f, 125f, 250f, 1000f, 4000f, 8000f, 16000f)

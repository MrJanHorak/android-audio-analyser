package com.example.audioanalyser

import androidx.compose.ui.graphics.Color
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

fun SavedTargetCurvePreset.markUsed(nowMillis: Long = System.currentTimeMillis()): SavedTargetCurvePreset =
    copy(lastUsedAtEpochMillis = nowMillis)

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

data class EqReferenceEntry(
    val title: String,
    val role: String,
    val highPass: String,
    val body: String,
    val presence: String,
    val watchOut: String,
    val startingMove: String
)

private val lowBandColor = Color(0xFF6D4C41)
private val bodyBandColor = Color(0xFFD17B0F)
private val cautionBandColor = Color(0xFFC62828)
private val presenceBandColor = Color(0xFF1976D2)
private val airBandColor = Color(0xFF00897B)
private val targetRoomColor = Color(0xFF2E7D32)
private val targetGuideColor = Color(0xFF1565C0)
private val targetCinemaColor = Color(0xFFAD1457)

val analyzerOverlayProfiles = listOf(
    AnalyzerOverlayProfile(
        id = "off",
        label = "Off",
        description = "No guide overlay selected.",
        bands = emptyList()
    ),
    AnalyzerOverlayProfile(
        id = "lead_vocal",
        label = "Lead Vocal",
        description = "Highlights the common warmth, mud, presence, and air zones used to seat a vocal over a band.",
        bands = listOf(
            AnalyzerRangeBand("Warmth", 120f, 250f, "Body and chest resonance.", bodyBandColor),
            AnalyzerRangeBand("Mud", 250f, 400f, "Too much here clouds lyric clarity.", cautionBandColor),
            AnalyzerRangeBand("Presence", 2000f, 4500f, "Helps the lyric cut through.", presenceBandColor),
            AnalyzerRangeBand("Air", 8000f, 12000f, "Adds openness if the mic is dull.", airBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "speech",
        label = "Speech",
        description = "Useful for spoken word, sermons, announcements, and conference-style intelligibility checks.",
        bands = listOf(
            AnalyzerRangeBand("Weight", 120f, 220f, "Too little sounds thin.", lowBandColor),
            AnalyzerRangeBand("Mud", 220f, 350f, "Trim here if speech feels cloudy.", cautionBandColor),
            AnalyzerRangeBand("Intelligibility", 1500f, 3500f, "Consonants and intelligibility live here.", presenceBandColor),
            AnalyzerRangeBand("Sibilance", 5000f, 8000f, "Can get sharp quickly.", airBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "kick",
        label = "Kick",
        description = "Shows the low-end weight, boxiness, and click that usually matter most on kick drum.",
        bands = listOf(
            AnalyzerRangeBand("Thump", 50f, 80f, "Low-end impact.", lowBandColor),
            AnalyzerRangeBand("Boxy", 250f, 400f, "Cut if the kick feels cardboard.", cautionBandColor),
            AnalyzerRangeBand("Click", 2500f, 4000f, "Beater definition.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "bass",
        label = "Bass",
        description = "Helps separate useful bass weight from mud and finger or pick definition.",
        bands = listOf(
            AnalyzerRangeBand("Weight", 40f, 80f, "Low-end foundation.", lowBandColor),
            AnalyzerRangeBand("Mud", 150f, 250f, "Too much can hide the kick.", cautionBandColor),
            AnalyzerRangeBand("Growl", 700f, 1200f, "Adds note definition.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "snare",
        label = "Snare",
        description = "Marks the common body, ring, and crack zones when shaping a snare in a live mix.",
        bands = listOf(
            AnalyzerRangeBand("Body", 120f, 240f, "Adds thickness.", bodyBandColor),
            AnalyzerRangeBand("Ring", 700f, 1200f, "Ringing often shows up here.", cautionBandColor),
            AnalyzerRangeBand("Crack", 2000f, 5000f, "Attack and cut-through.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "acoustic_guitar",
        label = "Ac Gtr",
        description = "Useful for spotting boom, body, and pick articulation in acoustic guitar channels.",
        bands = listOf(
            AnalyzerRangeBand("Boom", 100f, 180f, "Too much feels boomy fast.", cautionBandColor),
            AnalyzerRangeBand("Body", 180f, 300f, "Natural wood tone.", bodyBandColor),
            AnalyzerRangeBand("Pick", 2000f, 5000f, "Attack and articulation.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "electric_guitar",
        label = "Elec Gtr",
        description = "Shows where guitar weight, bite, and fizz usually sit in a typical live rig.",
        bands = listOf(
            AnalyzerRangeBand("Body", 150f, 350f, "Useful thickness.", bodyBandColor),
            AnalyzerRangeBand("Bite", 1500f, 3500f, "Helps the part speak.", presenceBandColor),
            AnalyzerRangeBand("Fizz", 5000f, 7000f, "Cut here if the amp feels brittle.", cautionBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "keys",
        label = "Keys",
        description = "Good for finding low-end buildup, boxiness, and upper-mid definition in piano or keyboard patches.",
        bands = listOf(
            AnalyzerRangeBand("Weight", 80f, 200f, "Can swallow the mix if overdone.", lowBandColor),
            AnalyzerRangeBand("Boxy", 250f, 500f, "Trim here if keys feel crowded.", cautionBandColor),
            AnalyzerRangeBand("Presence", 2000f, 5000f, "Adds articulation.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "horns",
        label = "Horns",
        description = "Trumpet, Sax, and Trombone. Shows the body, honk, and piercing zones.",
        bands = listOf(
            AnalyzerRangeBand("Body", 180f, 350f, "Warmth and weight.", bodyBandColor),
            AnalyzerRangeBand("Honk", 600f, 1000f, "Can feel nasal if over-focused.", cautionBandColor),
            AnalyzerRangeBand("Pierce", 2500f, 5000f, "Bite, but cuts heads off if too loud.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "strings",
        label = "Strings",
        description = "Useful for Cellos, Violas, and Violins to remove rosin-scratch and boost bowing clarity.",
        bands = listOf(
            AnalyzerRangeBand("Warmth", 150f, 300f, "Cello & Viola body foundation.", bodyBandColor),
            AnalyzerRangeBand("Scratch", 1500f, 3000f, "Harsh rosin/bow scraping.", cautionBandColor),
            AnalyzerRangeBand("Sheen", 7000f, 10000f, "Gives strings a smooth lift.", airBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "toms",
        label = "Toms",
        description = "Rack and floor toms. Highlights low rumble, boxy ringing, and stick definition.",
        bands = listOf(
            AnalyzerRangeBand("Thump", 80f, 150f, "Low-end ringing sustain.", lowBandColor),
            AnalyzerRangeBand("Cardboard", 300f, 600f, "Muddy and cloudy ringing.", cautionBandColor),
            AnalyzerRangeBand("Slap", 3500f, 5500f, "Stick impact and snap.", presenceBandColor)
        )
    ),
    AnalyzerOverlayProfile(
        id = "upright_bass",
        label = "Uprht Bass",
        description = "Acoustic Double Bass. Good for managing unruly low frequencies and finding finger pluck.",
        bands = listOf(
            AnalyzerRangeBand("Sub/Rumble", 40f, 100f, "Can feedback easily on stages.", cautionBandColor),
            AnalyzerRangeBand("Wood", 150f, 250f, "The actual woody tone of the body.", bodyBandColor),
            AnalyzerRangeBand("Pluck", 1200f, 2500f, "The string snap and articulation.", presenceBandColor)
        )
    )
)

val analyzerTargetCurveProfiles = listOf(
    AnalyzerTargetCurveProfile(
        id = "off",
        label = "Off",
        badge = "None",
        description = "No target curve selected.",
        useCase = "Use only the live spectrum and source focus bands.",
        caution = "",
        color = targetRoomColor,
        points = emptyList()
    ),
    AnalyzerTargetCurveProfile(
        id = "harman_room",
        label = "Harman",
        badge = "Room",
        description = "Bass-forward room target based on listener preference studies, with a gentle downward tilt into the highs.",
        useCase = "Useful as a room-mix reference when you want a modern, full-range balance instead of a flat analyzer trace.",
        caution = "Treat this as a room target, not a vocal or instrument EQ preset.",
        color = targetRoomColor,
        points = listOf(
            TargetCurvePoint(20f, 5.5f),
            TargetCurvePoint(40f, 5f),
            TargetCurvePoint(80f, 4f),
            TargetCurvePoint(100f, 3.5f),
            TargetCurvePoint(250f, 1.5f),
            TargetCurvePoint(1000f, 0f),
            TargetCurvePoint(4000f, -1.5f),
            TargetCurvePoint(10000f, -3.5f),
            TargetCurvePoint(16000f, -5f)
        )
    ),
    AnalyzerTargetCurveProfile(
        id = "bk_room",
        label = "B&K",
        badge = "Room",
        description = "Classic Bruel and Kjaer style house curve with a smooth downward slope from lows to highs.",
        useCase = "Good for natural-sounding room tuning and conservative live-system balance.",
        caution = "This is broader and gentler than an instrument target; use it on the system, not the channel strip.",
        color = Color(0xFF33691E),
        points = listOf(
            TargetCurvePoint(20f, 3.5f),
            TargetCurvePoint(40f, 3f),
            TargetCurvePoint(80f, 2.5f),
            TargetCurvePoint(160f, 2f),
            TargetCurvePoint(315f, 1.4f),
            TargetCurvePoint(630f, 0.8f),
            TargetCurvePoint(1250f, 0f),
            TargetCurvePoint(2500f, -1f),
            TargetCurvePoint(5000f, -2.3f),
            TargetCurvePoint(10000f, -3.8f),
            TargetCurvePoint(16000f, -5f)
        )
    ),
    AnalyzerTargetCurveProfile(
        id = "cinema_x",
        label = "Cinema X",
        badge = "Cinema",
        description = "ISO 2969 style cinema X-curve with a much stronger treble roll-off above roughly 2 kHz.",
        useCase = "Relevant for large cinema or dubbing-style playback environments.",
        caution = "Usually too dark for small rooms, worship spaces, speech reinforcement, and music mixing on a compact PA.",
        color = targetCinemaColor,
        points = listOf(
            TargetCurvePoint(31.5f, 2f),
            TargetCurvePoint(63f, 1.5f),
            TargetCurvePoint(125f, 1f),
            TargetCurvePoint(250f, 0.5f),
            TargetCurvePoint(500f, 0f),
            TargetCurvePoint(1000f, 0f),
            TargetCurvePoint(2000f, -1.5f),
            TargetCurvePoint(4000f, -4.5f),
            TargetCurvePoint(8000f, -8f),
            TargetCurvePoint(16000f, -12f)
        )
    ),
    AnalyzerTargetCurveProfile(
        id = "atmos_music",
        label = "Atmos Music",
        badge = "Room",
        description = "A gentle immersive-music style room target: slightly supported lows, steady mids, and a smoother top than a flat trace.",
        useCase = "Useful as a modern room playback reference when you want clarity without a hyped top end.",
        caution = "This is a practical live-reference approximation, not a certification tool for Dolby alignment.",
        color = Color(0xFF00695C),
        points = listOf(
            TargetCurvePoint(20f, 4f),
            TargetCurvePoint(40f, 3.5f),
            TargetCurvePoint(80f, 2.8f),
            TargetCurvePoint(160f, 1.6f),
            TargetCurvePoint(315f, 0.8f),
            TargetCurvePoint(1000f, 0f),
            TargetCurvePoint(4000f, -1f),
            TargetCurvePoint(8000f, -2.5f),
            TargetCurvePoint(16000f, -4f)
        )
    ),
    AnalyzerTargetCurveProfile(
        id = "speech_guide",
        label = "Speech",
        badge = "Guide",
        description = "A speech-intelligibility guide curve that de-emphasizes sub energy and biases the upper mids where consonants live.",
        useCase = "Helpful when tuning spoken word channels or a speech-heavy room mix.",
        caution = "This is a live-sound guide shape, not a formal industry standard curve.",
        color = targetGuideColor,
        points = listOf(
            TargetCurvePoint(50f, -10f),
            TargetCurvePoint(80f, -7f),
            TargetCurvePoint(125f, -4f),
            TargetCurvePoint(250f, -1.5f),
            TargetCurvePoint(500f, -0.5f),
            TargetCurvePoint(1500f, 1.5f),
            TargetCurvePoint(2500f, 2f),
            TargetCurvePoint(4000f, 1f),
            TargetCurvePoint(8000f, -1.5f)
        )
    ),
    AnalyzerTargetCurveProfile(
        id = "lead_vocal_guide",
        label = "Lead Vox",
        badge = "Guide",
        description = "A source guide for lead vocal channels with filtered lows, controlled mud, and a presence lift through the intelligibility band.",
        useCase = "Helpful when comparing a live vocal against a common modern worship or pop vocal shape.",
        caution = "This is a vocal shaping guide only; room acoustics, mic choice, and singer tone should override it.",
        color = Color(0xFF283593),
        points = listOf(
            TargetCurvePoint(60f, -12f),
            TargetCurvePoint(100f, -8f),
            TargetCurvePoint(160f, -3f),
            TargetCurvePoint(250f, -1f),
            TargetCurvePoint(400f, -2f),
            TargetCurvePoint(1000f, 0f),
            TargetCurvePoint(2500f, 2.2f),
            TargetCurvePoint(4000f, 1.4f),
            TargetCurvePoint(8000f, 0.4f),
            TargetCurvePoint(12000f, -1.5f)
        )
    )
)

fun defaultCustomTargetCurveSettings(): CustomTargetCurveSettings = CustomTargetCurveSettings(
    name = "My Room",
    points = listOf(3.5f, 3f, 2f, 1f, 0f, -1f, -2.5f, -4f)
)

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
    val safePoints = customTargetCurveFrequencies.mapIndexed { index, _ ->
        preset.settings.points.getOrElse(index) { defaultSettings.points.getOrElse(index) { 0f } }
    }
    val description = preset.venueName.ifBlank {
        "Your saved room or organisation target curve."
    }.let { venueName ->
        if (venueName == "Your saved room or organisation target curve.") {
            venueName
        } else {
            "Saved target curve for $venueName."
        }
    }

    return AnalyzerTargetCurveProfile(
        id = preset.id,
        label = preset.settings.name.ifBlank { defaultSettings.name },
        badge = "Saved",
        description = description,
        useCase = "Useful when you regularly mix in the same room or for the same organisation and want your own repeatable reference.",
        caution = "Use it as a recall target, then still tune by ear for the audience size, PA deployment, and stage volume that day.",
        color = Color(0xFF8E24AA),
        points = customTargetCurveFrequencies.zip(safePoints).map { (frequencyHz, relativeDb) ->
            TargetCurvePoint(frequencyHz = frequencyHz, relativeDb = relativeDb)
        }
    )
}

fun buildAnalyzerTargetCurveProfiles(savedCurves: List<SavedTargetCurvePreset>): List<AnalyzerTargetCurveProfile> =
    analyzerTargetCurveProfiles + savedCurves.map(::buildCustomTargetCurveProfile)

val eqReferenceEntries = listOf(
    EqReferenceEntry(
        title = "Male Vocal",
        role = "Lead or solo singer",
        highPass = "HPF around 80-100 Hz unless the voice is especially light.",
        body = "Warmth and body usually sit around 120-250 Hz.",
        presence = "Clarity around 2-4.5 kHz, air around 10-12 kHz if needed.",
        watchOut = "Watch 250-400 Hz mud and 4.5-6 kHz harshness.",
        startingMove = "HPF first, then a small presence lift only if the lyric is still getting buried."
    ),
    EqReferenceEntry(
        title = "Female Vocal",
        role = "Lead or featured singer",
        highPass = "HPF around 100-120 Hz for most live vocal mics.",
        body = "Body often sits around 180-300 Hz.",
        presence = "Presence around 3-5 kHz and air around 10-14 kHz.",
        watchOut = "Watch 300-500 Hz buildup and 5-7 kHz sibilance.",
        startingMove = "Start with subtraction in the low mids before boosting top end."
    ),
    EqReferenceEntry(
        title = "Speech / Pastor",
        role = "Spoken word, sermons, conferences",
        highPass = "HPF around 100 Hz keeps rumble out of the PA.",
        body = "Keep enough 120-220 Hz so the voice does not feel thin.",
        presence = "Speech intelligibility is usually 1.5-3.5 kHz.",
        watchOut = "Watch 220-350 Hz mud and 5-8 kHz sibilance.",
        startingMove = "Cut mud first, then add clarity only until every word reads clearly."
    ),
    EqReferenceEntry(
        title = "Choir / Ensemble",
        role = "Multiple singers sharing the same space",
        highPass = "HPF around 120 Hz keeps stage rumble from accumulating.",
        body = "Shared low-mid energy often builds around 180-350 Hz.",
        presence = "Articulation tends to come from 2-4 kHz.",
        watchOut = "Too much upper-mid boost gets edgy quickly with many voices.",
        startingMove = "Use wider, gentler moves than you would on a solo vocal."
    ),
    EqReferenceEntry(
        title = "Kick Drum",
        role = "Modern or general live kick sound",
        highPass = "Usually no HPF, or keep it very low if the room blooms.",
        body = "Weight and punch are often 50-80 Hz, with body around 80-120 Hz.",
        presence = "Click and beater definition often sit around 2.5-4 kHz.",
        watchOut = "Watch 250-400 Hz cardboard box tone.",
        startingMove = "Find the low-end note first, then only add click if the kick disappears in the mix."
    ),
    EqReferenceEntry(
        title = "Bass Guitar",
        role = "Electric bass in a live mix",
        highPass = "Use a gentle HPF only if sub buildup is hurting clarity.",
        body = "Foundation usually sits around 40-80 Hz with note body around 80-150 Hz.",
        presence = "Definition often lives around 700 Hz to 1.2 kHz.",
        watchOut = "Watch 150-250 Hz mud if bass and kick fight each other.",
        startingMove = "Choose whether the kick or the bass owns the lowest octave, then shape around that choice."
    ),
    EqReferenceEntry(
        title = "Snare Drum",
        role = "Live snare top mic",
        highPass = "HPF around 80-100 Hz if stage wash is heavy.",
        body = "Body is usually around 120-240 Hz.",
        presence = "Crack and stick attack often sit around 2-5 kHz.",
        watchOut = "Watch 700 Hz to 1.2 kHz ring and 5-8 kHz harshness.",
        startingMove = "Cut the ring before boosting top end."
    ),
    EqReferenceEntry(
        title = "Acoustic Guitar",
        role = "Strummed or picked acoustic in a live room",
        highPass = "HPF around 80-120 Hz depending on the instrument and arrangement.",
        body = "Natural body often sits around 180-300 Hz.",
        presence = "Pick articulation usually lands around 2-5 kHz.",
        watchOut = "Watch 100-180 Hz boom and brittle upper mids around 3-6 kHz.",
        startingMove = "Get rid of boom first, then add just enough attack for definition."
    ),
    EqReferenceEntry(
        title = "Electric Guitar",
        role = "Mic'd guitar amp or modeler",
        highPass = "HPF around 80-100 Hz keeps it out of the kick and bass lane.",
        body = "Weight and body often sit around 150-350 Hz.",
        presence = "Bite usually sits around 1.5-3.5 kHz.",
        watchOut = "Watch 5-7 kHz fizz and 2-4 kHz harshness.",
        startingMove = "Shape with cuts first so the guitar keeps attitude without taking over the vocal range."
    ),
    EqReferenceEntry(
        title = "Piano / Keys",
        role = "Stage piano, synth pad, or general keyboard patch",
        highPass = "HPF depends on the patch, but many parts can lose low rumble below 80-100 Hz.",
        body = "Body often collects around 100-250 Hz.",
        presence = "Articulation and attack often sit around 2-5 kHz.",
        watchOut = "Watch 250-500 Hz boxiness when keys stack with guitars and vocals.",
        startingMove = "Decide whether the patch is supporting or featured, then trim low mids aggressively enough to leave room."
    ),
    EqReferenceEntry(
        title = "Orchestral Strings (Violin/Cello)",
        role = "Solo or section strings in a contemporary or church mix",
        highPass = "Violins: HPF 150-200 Hz. Celli: HPF 60-80 Hz depending on the mix.",
        body = "Resonance and warmth sit around 200-400 Hz for violins, 100-250 Hz for celli.",
        presence = "Bow texture and rosin noise often sit around 2-5 kHz.",
        watchOut = "Watch 1-3 kHz harshness heavily on close-mic'd violins. They can sound very scratchy un-EQ'd.",
        startingMove = "Cut harshness around 2 kHz before boosting highs to keep the natural acoustic tone."
    ),
    EqReferenceEntry(
        title = "Brass (Trumpet/Trombone)",
        role = "Live horn section or solo brass",
        highPass = "HPF around 150-200 Hz for trumpets; 80-100 Hz for trombones.",
        body = "Fundamentals often sit around: Trumpet 200-400 Hz, Trombone 100-250 Hz.",
        presence = "Bite and projection naturally push around 2-5 kHz.",
        watchOut = "Horns can easily pierce the mix around 2.5-4 kHz. Keep an eye on harsh buildups.",
        startingMove = "Tame the piercing upper mids first, let the natural acoustic body shine through."
    ),
    EqReferenceEntry(
        title = "Woodwinds (Flute/Clarinet)",
        role = "Live woodwind section",
        highPass = "HPF around 200-250 Hz for flute to avoid stage rumble.",
        body = "Warmth sits around 300-600 Hz.",
        presence = "Breath and air sit highly around 4-6 kHz and above.",
        watchOut = "Watch 4-6 kHz whistle or sibilance, and avoid boosting too much air unless it sounds dull.",
        startingMove = "A gentle cut in the harsh upper frequencies helps it blend smoothly with strings or vocals."
    ),
    EqReferenceEntry(
        title = "Upright Bass",
        role = "Acoustic pizzicato or bowed bass",
        highPass = "HPF around 40 Hz to remove subsonic bowing noise.",
        body = "Fundamentals sit around 40-100 Hz. Pluck/Finger sound around 700-900 Hz.",
        presence = "Fingerboard snap and slap sit around 2-4 kHz.",
        watchOut = "Watch 150-300 Hz boominess which can easily run away into feedback on stage.",
        startingMove = "Find the resonant feedback frequencies and notch them out, then shape the low-end weight."
    ),
    EqReferenceEntry(
        title = "General Gain Staging & Tuning",
        role = "Overall best practices for Live Sound",
        highPass = "Use high-pass filters on almost everything except Kick, Bass, and Synth subs to clean up stage rumble.",
        body = "Mud builds up across an entire mix at 200-400 Hz. If the whole mix feels cloudy, check this range.",
        presence = "Clash and pain usually live in 2-4 kHz. If it hurts, start looking there.",
        watchOut = "When using an RTA or Analyzer, trust your ears first. A perfectly flat line often sounds harsh or unnatural.",
        startingMove = "Cut frequencies to fix problems. Boost frequencies to change character."
    )
)

fun formatFrequencyLabel(frequencyHz: Float): String = when {
    frequencyHz >= 1000f -> String.format(Locale.getDefault(), "%.1f kHz", frequencyHz / 1000f)
    else -> String.format(Locale.getDefault(), "%.0f Hz", frequencyHz)
}

fun formatFrequencyRange(startHz: Float, endHz: Float): String =
    "${formatFrequencyLabel(startHz)} - ${formatFrequencyLabel(endHz)}"

fun formatRelativeCurveLevel(relativeDb: Float): String =
    String.format(Locale.getDefault(), "%+.1f dB", relativeDb)
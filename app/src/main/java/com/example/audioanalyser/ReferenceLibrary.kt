package com.example.audioanalyser

import androidx.compose.ui.graphics.Color
import java.util.Locale

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
    )
)

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
    )
)

fun formatFrequencyLabel(frequencyHz: Float): String = when {
    frequencyHz >= 1000f -> String.format(Locale.getDefault(), "%.1f kHz", frequencyHz / 1000f)
    else -> String.format(Locale.getDefault(), "%.0f Hz", frequencyHz)
}

fun formatFrequencyRange(startHz: Float, endHz: Float): String =
    "${formatFrequencyLabel(startHz)} - ${formatFrequencyLabel(endHz)}"
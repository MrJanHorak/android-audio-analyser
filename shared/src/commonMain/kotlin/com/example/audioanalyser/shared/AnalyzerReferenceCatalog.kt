package com.example.audioanalyser.shared

data class AnalyzerGuideColor(
    val red: Float,
    val green: Float,
    val blue: Float
)

data class AnalyzerGuideRangeBand(
    val label: String,
    val startHz: Float,
    val endHz: Float,
    val note: String,
    val color: AnalyzerGuideColor
) {
    fun contains(frequencyHz: Float): Boolean = frequencyHz in startHz..endHz
}

data class AnalyzerOverlayReferenceProfile(
    val id: String,
    val label: String,
    val summary: String,
    val bands: List<AnalyzerGuideRangeBand>
) {
    fun bandFor(frequencyHz: Float): AnalyzerGuideRangeBand? = bands.firstOrNull { it.contains(frequencyHz) }
}

data class AnalyzerTargetCurveReferenceProfile(
    val id: String,
    val label: String,
    val badge: String,
    val summary: String,
    val useCase: String,
    val caution: String,
    val color: AnalyzerGuideColor,
    val points: List<TargetCurvePoint>
) {
    val isEnabled: Boolean = points.isNotEmpty()
}

object AnalyzerReferenceCatalog {
    fun overlayProfiles(): List<AnalyzerOverlayReferenceProfile> = overlayProfilesCatalog

    fun targetCurveProfiles(): List<AnalyzerTargetCurveReferenceProfile> = targetCurveProfilesCatalog

    fun eqReferenceEntries(): List<EqReferenceEntry> = eqReferenceEntriesCatalog
}

private fun rgb(red: Int, green: Int, blue: Int): AnalyzerGuideColor = AnalyzerGuideColor(
    red = red / 255f,
    green = green / 255f,
    blue = blue / 255f
)

private val lowBandColor = rgb(0x6D, 0x4C, 0x41)
private val bodyBandColor = rgb(0xD1, 0x7B, 0x0F)
private val cautionBandColor = rgb(0xC6, 0x28, 0x28)
private val presenceBandColor = rgb(0x19, 0x76, 0xD2)
private val airBandColor = rgb(0x00, 0x89, 0x7B)
private val targetRoomColor = rgb(0x2E, 0x7D, 0x32)
private val targetGuideColor = rgb(0x15, 0x65, 0xC0)
private val targetCinemaColor = rgb(0xAD, 0x14, 0x57)

private val overlayProfilesCatalog = listOf(
    AnalyzerOverlayReferenceProfile(
        id = "off",
        label = "Off",
        summary = "No guide overlay selected.",
        bands = emptyList()
    ),
    AnalyzerOverlayReferenceProfile(
        id = "lead_vocal",
        label = "Lead Vocal",
        summary = "Broad vocal overlay for HPF, body, mud, presence, sibilance, and air. Use Low Vox or High Vox when the singer range is known.",
        bands = listOf(
            AnalyzerGuideRangeBand("Rumble", 70f, 120f, "Set HPF above stage rumble without thinning the singer.", lowBandColor),
            AnalyzerGuideRangeBand("Body", 120f, 260f, "Fundamental weight and chest tone.", bodyBandColor),
            AnalyzerGuideRangeBand("Mud", 260f, 500f, "Too much here clouds lyric clarity.", cautionBandColor),
            AnalyzerGuideRangeBand("Presence", 2000f, 5000f, "Helps the lyric cut through.", presenceBandColor),
            AnalyzerGuideRangeBand("Sibilance", 5000f, 8000f, "Sharp S and T sounds often gather here.", cautionBandColor),
            AnalyzerGuideRangeBand("Air", 10000f, 14000f, "Adds openness if the mic is dull.", airBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "low_vocal",
        label = "Low Vox",
        summary = "Starting ranges for a lower voice. Match the filter and EQ to the actual singer and microphone.",
        bands = listOf(
            AnalyzerGuideRangeBand("Rumble", 50f, 90f, "HPF often starts around 70-100 Hz unless the voice thins.", lowBandColor),
            AnalyzerGuideRangeBand("Body", 90f, 180f, "Fundamental and chest weight for many lower voices.", bodyBandColor),
            AnalyzerGuideRangeBand("Warmth", 180f, 250f, "Useful fullness before it turns cloudy.", bodyBandColor),
            AnalyzerGuideRangeBand("Mud", 250f, 400f, "Proximity buildup can hide consonants.", cautionBandColor),
            AnalyzerGuideRangeBand("Presence", 2000f, 4500f, "Lyric intelligibility and forwardness.", presenceBandColor),
            AnalyzerGuideRangeBand("Sibilance", 4500f, 7000f, "Watch bite, esses, and mic harshness.", cautionBandColor),
            AnalyzerGuideRangeBand("Air", 10000f, 14000f, "Lift only if the vocal sounds dull.", airBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "high_vocal",
        label = "High Vox",
        summary = "Starting ranges for a higher voice. Match the filter and EQ to the actual singer and microphone.",
        bands = listOf(
            AnalyzerGuideRangeBand("Rumble", 80f, 140f, "HPF often starts around 100-140 Hz, lower if the tone gets thin.", lowBandColor),
            AnalyzerGuideRangeBand("Body", 165f, 260f, "Fundamental weight for many higher voices.", bodyBandColor),
            AnalyzerGuideRangeBand("Mud", 260f, 500f, "Low-mid buildup can make the vocal cloudy.", cautionBandColor),
            AnalyzerGuideRangeBand("Presence", 2500f, 5000f, "Consonants and lead-vocal focus.", presenceBandColor),
            AnalyzerGuideRangeBand("Sibilance", 5500f, 9000f, "Watch sharp esses before adding air.", cautionBandColor),
            AnalyzerGuideRangeBand("Air", 10000f, 16000f, "Adds openness after sibilance is controlled.", airBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "speech",
        label = "Speech",
        summary = "Useful for spoken word, sermons, announcements, and conference-style intelligibility checks.",
        bands = listOf(
            AnalyzerGuideRangeBand("Weight", 120f, 220f, "Too little sounds thin.", lowBandColor),
            AnalyzerGuideRangeBand("Mud", 220f, 350f, "Trim here if speech feels cloudy.", cautionBandColor),
            AnalyzerGuideRangeBand("Intelligibility", 1500f, 3500f, "Consonants and intelligibility live here.", presenceBandColor),
            AnalyzerGuideRangeBand("Sibilance", 5000f, 8000f, "Can get sharp quickly.", airBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "kick",
        label = "Kick",
        summary = "Shows the low-end weight, boxiness, and click that usually matter most on kick drum.",
        bands = listOf(
            AnalyzerGuideRangeBand("Thump", 50f, 80f, "Low-end impact.", lowBandColor),
            AnalyzerGuideRangeBand("Boxy", 250f, 400f, "Cut if the kick feels cardboard.", cautionBandColor),
            AnalyzerGuideRangeBand("Click", 2500f, 4000f, "Beater definition.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "bass",
        label = "Bass",
        summary = "Helps separate useful bass weight from mud and finger or pick definition.",
        bands = listOf(
            AnalyzerGuideRangeBand("Weight", 40f, 80f, "Low-end foundation.", lowBandColor),
            AnalyzerGuideRangeBand("Mud", 150f, 250f, "Too much can hide the kick.", cautionBandColor),
            AnalyzerGuideRangeBand("Growl", 700f, 1200f, "Adds note definition.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "snare",
        label = "Snare",
        summary = "Marks the common body, ring, and crack zones when shaping a snare in a live mix.",
        bands = listOf(
            AnalyzerGuideRangeBand("Body", 120f, 240f, "Adds thickness.", bodyBandColor),
            AnalyzerGuideRangeBand("Ring", 700f, 1200f, "Ringing often shows up here.", cautionBandColor),
            AnalyzerGuideRangeBand("Crack", 2000f, 5000f, "Attack and cut-through.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "acoustic_guitar",
        label = "Ac Gtr",
        summary = "Useful for spotting boom, body, and pick articulation in acoustic guitar channels.",
        bands = listOf(
            AnalyzerGuideRangeBand("Boom", 100f, 180f, "Too much feels boomy fast.", cautionBandColor),
            AnalyzerGuideRangeBand("Body", 180f, 300f, "Natural wood tone.", bodyBandColor),
            AnalyzerGuideRangeBand("Pick", 2000f, 5000f, "Attack and articulation.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "electric_guitar",
        label = "Elec Gtr",
        summary = "Shows where guitar weight, bite, and fizz usually sit in a typical live rig.",
        bands = listOf(
            AnalyzerGuideRangeBand("Body", 150f, 350f, "Useful thickness.", bodyBandColor),
            AnalyzerGuideRangeBand("Bite", 1500f, 3500f, "Helps the part speak.", presenceBandColor),
            AnalyzerGuideRangeBand("Fizz", 5000f, 7000f, "Cut here if the amp feels brittle.", cautionBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "keys",
        label = "Keys",
        summary = "Good for finding low-end buildup, boxiness, and upper-mid definition in piano or keyboard patches.",
        bands = listOf(
            AnalyzerGuideRangeBand("Weight", 80f, 200f, "Can swallow the mix if overdone.", lowBandColor),
            AnalyzerGuideRangeBand("Boxy", 250f, 500f, "Trim here if keys feel crowded.", cautionBandColor),
            AnalyzerGuideRangeBand("Presence", 2000f, 5000f, "Adds articulation.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "horns",
        label = "Horns",
        summary = "Trumpet, Sax, and Trombone. Shows the body, honk, and piercing zones.",
        bands = listOf(
            AnalyzerGuideRangeBand("Body", 180f, 350f, "Warmth and weight.", bodyBandColor),
            AnalyzerGuideRangeBand("Honk", 600f, 1000f, "Can feel nasal if over-focused.", cautionBandColor),
            AnalyzerGuideRangeBand("Pierce", 2500f, 5000f, "Bite, but cuts heads off if too loud.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "strings",
        label = "Strings",
        summary = "Useful for Cellos, Violas, and Violins to remove rosin-scratch and boost bowing clarity.",
        bands = listOf(
            AnalyzerGuideRangeBand("Warmth", 150f, 300f, "Cello & Viola body foundation.", bodyBandColor),
            AnalyzerGuideRangeBand("Scratch", 1500f, 3000f, "Harsh rosin/bow scraping.", cautionBandColor),
            AnalyzerGuideRangeBand("Sheen", 7000f, 10000f, "Gives strings a smooth lift.", airBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "toms",
        label = "Toms",
        summary = "Rack and floor toms. Highlights low rumble, boxy ringing, and stick definition.",
        bands = listOf(
            AnalyzerGuideRangeBand("Thump", 80f, 150f, "Low-end ringing sustain.", lowBandColor),
            AnalyzerGuideRangeBand("Cardboard", 300f, 600f, "Muddy and cloudy ringing.", cautionBandColor),
            AnalyzerGuideRangeBand("Slap", 3500f, 5500f, "Stick impact and snap.", presenceBandColor)
        )
    ),
    AnalyzerOverlayReferenceProfile(
        id = "upright_bass",
        label = "Uprht Bass",
        summary = "Acoustic Double Bass. Good for managing unruly low frequencies and finding finger pluck.",
        bands = listOf(
            AnalyzerGuideRangeBand("Sub/Rumble", 40f, 100f, "Can feedback easily on stages.", cautionBandColor),
            AnalyzerGuideRangeBand("Wood", 150f, 250f, "The actual woody tone of the body.", bodyBandColor),
            AnalyzerGuideRangeBand("Pluck", 1200f, 2500f, "The string snap and articulation.", presenceBandColor)
        )
    )
)

private val targetCurveProfilesCatalog = listOf(
    AnalyzerTargetCurveReferenceProfile(
        id = "off",
        label = "Off",
        badge = "None",
        summary = "No target curve selected.",
        useCase = "Use only the live spectrum and source focus bands.",
        caution = "",
        color = targetRoomColor,
        points = emptyList()
    ),
    AnalyzerTargetCurveReferenceProfile(
        id = "harman_room",
        label = "Harman-inspired",
        badge = "Room",
        summary = "Illustrative bass-forward curve inspired by listener-preference research; not a specified Harman target.",
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
    AnalyzerTargetCurveReferenceProfile(
        id = "bk_room",
        label = "B&K-inspired",
        badge = "Room",
        summary = "Illustrative B&K-inspired house-curve shape; not a verified standard or published reference.",
        useCase = "Good for natural-sounding room tuning and conservative live-system balance.",
        caution = "This is broader and gentler than an instrument target; use it on the system, not the channel strip.",
        color = rgb(0x33, 0x69, 0x1E),
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
    AnalyzerTargetCurveReferenceProfile(
        id = "cinema_x",
        label = "Cinema X-style",
        badge = "Cinema",
        summary = "Illustrative cinema X-style shape; not a verified implementation of ISO 2969.",
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
    AnalyzerTargetCurveReferenceProfile(
        id = "atmos_music",
        label = "Immersive-style",
        badge = "Room",
        summary = "Illustrative immersive-music style shape, not a Dolby or other alignment specification.",
        useCase = "Useful as a modern room playback reference when you want clarity without a hyped top end.",
        caution = "This is a practical live-reference approximation, not a certification tool for Dolby alignment.",
        color = rgb(0x00, 0x69, 0x5C),
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
    AnalyzerTargetCurveReferenceProfile(
        id = "speech_guide",
        label = "Speech",
        badge = "Guide",
        summary = "A speech-intelligibility guide curve that de-emphasizes sub energy and biases the upper mids where consonants live.",
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
    AnalyzerTargetCurveReferenceProfile(
        id = "lead_vocal_guide",
        label = "Lead Vox",
        badge = "Guide",
        summary = "A broad source guide for lead vocal channels with filtered lows, controlled mud, and a presence lift through the intelligibility band.",
        useCase = "Illustrative vocal-shaping reference for different live music styles. Pair it with a voice-range overlay as a starting point, not a required target.",
        caution = "This is a vocal shaping guide only; choose the range-specific overlay by the singer's actual voice, mic, and room.",
        color = rgb(0x28, 0x35, 0x93),
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

private val eqReferenceEntriesCatalog = listOf(
    EqReferenceEntry(
        title = "Lower-Range Vocal",
        role = "Lower-range lead or solo singer",
        highPass = "Start HPF around 70-100 Hz, then raise it until rumble clears without thinning the chest tone.",
        body = "Fundamental weight is often 85-180 Hz, with warmth extending through about 250 Hz.",
        presence = "Lyric clarity usually sits around 2-4.5 kHz, with air around 10-14 kHz if needed.",
        watchOut = "Watch 180-400 Hz proximity mud and 4.5-7 kHz bite or sibilance.",
        startingMove = "Check for rumble and low-mid buildup first; make small changes only if needed, then reassess in the mix."
    ),
    EqReferenceEntry(
        title = "Higher-Range Vocal",
        role = "Higher-range lead or featured singer",
        highPass = "Start HPF around 100-140 Hz, backing it down if the singer loses body.",
        body = "Fundamental weight is often 165-260 Hz, with low-mid warmth reaching toward 350 Hz.",
        presence = "Presence usually sits around 2.5-5 kHz, with air around 10-16 kHz after sibilance is controlled.",
        watchOut = "Watch 250-500 Hz buildup and 5.5-9 kHz sibilance or mic harshness.",
        startingMove = "Set HPF to the actual voice. If it sounds cloudy or sibilant, investigate those ranges before boosting air."
    ),
    EqReferenceEntry(
        title = "Speech / Presenter",
        role = "Spoken word, sermons, conferences",
        highPass = "HPF around 100 Hz keeps rumble out of the PA.",
        body = "Keep enough 120-220 Hz so the voice does not feel thin.",
        presence = "Speech intelligibility is usually 1.5-3.5 kHz.",
        watchOut = "Watch 220-350 Hz mud and 5-8 kHz sibilance.",
        startingMove = "If speech sounds cloudy, investigate the low mids; add clarity only if needed and listen to the words in the room."
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
        startingMove = "If an unwanted ring is audible, identify it and try a small cut before adding top end."
    ),
    EqReferenceEntry(
        title = "Acoustic Guitar",
        role = "Strummed or picked acoustic in a live room",
        highPass = "HPF around 80-120 Hz depending on the instrument and arrangement.",
        body = "Natural body often sits around 180-300 Hz.",
        presence = "Pick articulation usually lands around 2-5 kHz.",
        watchOut = "Watch 100-180 Hz boom and brittle upper mids around 3-6 kHz.",
        startingMove = "If the guitar sounds boomy, investigate the low end; add attack only if needed."
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
        startingMove = "Consider the patch's role; if the mix sounds crowded, try a small low-mid adjustment and compare."
    ),
    EqReferenceEntry(
        title = "Orchestral Strings (Violin/Cello)",
        role = "Solo or section strings in a live mix",
        highPass = "Choose the HPF by ear and the lowest notes being played; steep or high filters can thin a violin or cello.",
        body = "Resonance and warmth sit around 200-400 Hz for violins, 100-250 Hz for celli.",
        presence = "Bow texture and rosin noise often sit around 2-5 kHz.",
        watchOut = "Watch 1-3 kHz harshness heavily on close-mic'd violins. They can sound very scratchy un-EQ'd.",
        startingMove = "If the sound is harsh, investigate the upper mids and try a small adjustment before boosting highs."
    ),
    EqReferenceEntry(
        title = "Brass (Trumpet/Trombone)",
        role = "Live horn section or solo brass",
        highPass = "HPF around 150-200 Hz for trumpets; 80-100 Hz for trombones.",
        body = "Fundamentals often sit around: Trumpet 200-400 Hz, Trombone 100-250 Hz.",
        presence = "Bite and projection naturally push around 2-5 kHz.",
        watchOut = "Horns can easily pierce the mix around 2.5-4 kHz. Keep an eye on harsh buildups.",
        startingMove = "If the horns sound piercing, investigate the upper mids while preserving their natural body."
    ),
    EqReferenceEntry(
        title = "Woodwinds (Flute/Clarinet)",
        role = "Live woodwind section",
        highPass = "HPF around 200-250 Hz for flute to avoid stage rumble.",
        body = "Warmth sits around 300-600 Hz.",
        presence = "Breath and air sit highly around 4-6 kHz and above.",
        watchOut = "Watch 4-6 kHz whistle or sibilance, and avoid boosting too much air unless it sounds dull.",
        startingMove = "If the tone sounds harsh, try a gentle high-frequency adjustment and listen in context."
    ),
    EqReferenceEntry(
        title = "Upright Bass",
        role = "Acoustic pizzicato or bowed bass",
        highPass = "HPF around 40 Hz to remove subsonic bowing noise.",
        body = "Fundamentals sit around 40-100 Hz. Pluck/Finger sound around 700-900 Hz.",
        presence = "Fingerboard snap and slap sit around 2-4 kHz.",
        watchOut = "Watch 150-300 Hz boominess which can easily run away into feedback on stage.",
        startingMove = "If feedback occurs, reduce level and check placement first; only notch frequencies confirmed by ear."
    ),
    EqReferenceEntry(
        title = "General Gain Staging & Tuning",
        role = "Overall best practices for Live Sound",
        highPass = "Use a high-pass filter when unwanted low-frequency energy is present; set it for the source and listen for lost body.",
        body = "Mud builds up across an entire mix at 200-400 Hz. If the whole mix feels cloudy, check this range.",
        presence = "Clash and pain usually live in 2-4 kHz. If it hurts, start looking there.",
        watchOut = "When using an RTA or Analyzer, trust your ears first. A perfectly flat line often sounds harsh or unnatural.",
        startingMove = "Cut frequencies to fix problems. Boost frequencies to change character."
    )
)

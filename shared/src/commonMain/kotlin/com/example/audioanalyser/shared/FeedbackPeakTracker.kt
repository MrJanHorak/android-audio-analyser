package com.example.audioanalyser.shared

import kotlin.math.abs

data class FeedbackPeakTrackerConfig(
    val candidateLimit: Int = 6,
    val peakLimit: Int = 3,
    val minFrequencyHz: Float = 80f,
    val maxFrequencyHz: Float = 12000f,
    val decayFrames: Int = 10,
    val floorRatio: Float = 0.18f,
    val updateDecayMultiplier: Float = 0.92f,
    val idleDecayMultiplier: Float = 0.88f,
    val minNormalizedLevel: Float = 0.08f,
    val existingFrequencyWeight: Float = 0.7f,
    val candidateFrequencyWeight: Float = 0.3f,
    val minMatchToleranceHz: Float = 60f,
    val matchToleranceRatio: Float = 0.05f,
    val stabilityFrameCap: Int = 18,
    val rankingLevelWeight: Float = 0.55f,
    val rankingStabilityWeight: Float = 0.45f,
    val edgeBinExclusion: Int = 2
)

private data class FeedbackCandidate(
    val frequencyHz: Float,
    val normalizedLevel: Float
)

private data class TrackedFeedbackPeak(
    var frequencyHz: Float,
    var normalizedLevel: Float,
    var holdFrames: Int,
    var staleFrames: Int
)

class FeedbackPeakTracker(
    private val config: FeedbackPeakTrackerConfig = FeedbackPeakTrackerConfig()
) {
    private val trackedFeedbackPeaks = mutableListOf<TrackedFeedbackPeak>()

    fun update(spectrum: FloatArray, currentDb: Float, binSize: Float): List<FeedbackPeak> {
        val globalMax = spectrum.maxOrNull() ?: 0f
        if (currentDb <= 0f || globalMax <= 0f || spectrum.size <= config.edgeBinExclusion * 2) {
            return decay()
        }

        val candidates = mutableListOf<FeedbackCandidate>()
        for (index in config.edgeBinExclusion until spectrum.size - config.edgeBinExclusion) {
            val magnitude = spectrum[index]
            if (magnitude < globalMax * config.floorRatio) continue
            if (magnitude < spectrum[index - 1] || magnitude < spectrum[index + 1]) continue

            val frequencyHz = index * binSize
            if (frequencyHz < config.minFrequencyHz || frequencyHz > config.maxFrequencyHz) continue

            candidates += FeedbackCandidate(
                frequencyHz = frequencyHz,
                normalizedLevel = (magnitude / globalMax).coerceIn(0f, 1f)
            )
        }

        if (candidates.isEmpty()) {
            return decay()
        }

        trackedFeedbackPeaks.forEach { peak ->
            peak.staleFrames += 1
            peak.normalizedLevel *= config.updateDecayMultiplier
        }

        val matchedPeaks = mutableSetOf<TrackedFeedbackPeak>()
        candidates
            .sortedByDescending { it.normalizedLevel }
            .take(config.candidateLimit)
            .forEach { candidate ->
                val matched = trackedFeedbackPeaks
                    .filterNot { it in matchedPeaks }
                    .minByOrNull { abs(it.frequencyHz - candidate.frequencyHz) }
                    ?.takeIf {
                        abs(it.frequencyHz - candidate.frequencyHz) <= maxOf(
                            config.minMatchToleranceHz,
                            candidate.frequencyHz * config.matchToleranceRatio
                        )
                    }

                if (matched != null) {
                    matched.frequencyHz =
                        matched.frequencyHz * config.existingFrequencyWeight +
                            candidate.frequencyHz * config.candidateFrequencyWeight
                    matched.normalizedLevel = maxOf(matched.normalizedLevel, candidate.normalizedLevel)
                    matched.holdFrames += 1
                    matched.staleFrames = 0
                    matchedPeaks += matched
                } else {
                    val newPeak = TrackedFeedbackPeak(
                        frequencyHz = candidate.frequencyHz,
                        normalizedLevel = candidate.normalizedLevel,
                        holdFrames = 1,
                        staleFrames = 0
                    )
                    trackedFeedbackPeaks += newPeak
                    matchedPeaks += newPeak
                }
            }

        trackedFeedbackPeaks.removeAll { peak ->
            if (peak.staleFrames == 0) return@removeAll false
            peak.holdFrames = maxOf(1, peak.holdFrames - 1)
            peak.staleFrames > config.decayFrames || peak.normalizedLevel < config.minNormalizedLevel
        }

        return publish()
    }

    fun clear() {
        trackedFeedbackPeaks.clear()
    }

    private fun decay(): List<FeedbackPeak> {
        if (trackedFeedbackPeaks.isEmpty()) {
            return emptyList()
        }

        trackedFeedbackPeaks.removeAll { peak ->
            peak.staleFrames += 1
            peak.normalizedLevel *= config.idleDecayMultiplier
            peak.holdFrames = maxOf(1, peak.holdFrames - 1)
            peak.staleFrames > config.decayFrames || peak.normalizedLevel < config.minNormalizedLevel
        }

        return publish()
    }

    private fun publish(): List<FeedbackPeak> {
        return trackedFeedbackPeaks
            .sortedByDescending { peak ->
                val stability = peak.holdFrames.coerceAtMost(config.stabilityFrameCap) /
                    config.stabilityFrameCap.toFloat()
                peak.normalizedLevel * config.rankingLevelWeight +
                    stability * config.rankingStabilityWeight
            }
            .take(config.peakLimit)
            .map { peak ->
                FeedbackPeak(
                    frequencyHz = peak.frequencyHz,
                    suggestedCutHz = AnalyzerMath.suggestCutFrequency(peak.frequencyHz),
                    normalizedLevel = peak.normalizedLevel.coerceIn(0f, 1f),
                    stability = (
                        peak.holdFrames.coerceAtMost(config.stabilityFrameCap) /
                            config.stabilityFrameCap.toFloat()
                        ).coerceIn(0f, 1f),
                    holdFrames = peak.holdFrames
                )
            }
    }
}

package com.example.audioanalyser.shared

object FeedbackPeakPresentation {
    fun stabilityKey(peak: FeedbackPeak): String = when {
        peak.stability >= 0.8f -> "stable"
        peak.stability >= 0.45f -> "building"
        else -> "watch"
    }
}

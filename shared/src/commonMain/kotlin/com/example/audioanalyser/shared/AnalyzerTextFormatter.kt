package com.example.audioanalyser.shared

import kotlin.math.abs
import kotlin.math.floor

object AnalyzerTextFormatter {
    fun formatFrequencyLabel(frequencyHz: Float): String =
        if (frequencyHz >= 1000f) {
            "${formatNumber(frequencyHz / 1000f, fractionDigits = 1)} kHz"
        } else {
            "${formatNumber(frequencyHz, fractionDigits = 0)} Hz"
        }

    fun formatFrequencyRange(startHz: Float, endHz: Float): String =
        "${formatFrequencyLabel(startHz)} - ${formatFrequencyLabel(endHz)}"

    fun formatRelativeCurveLevel(relativeDb: Float): String =
        "${formatSignedNumber(relativeDb, fractionDigits = 1)} dB"

    fun formatSignedFrequencyDelta(deltaHz: Float): String =
        if (abs(deltaHz) >= 1000f) {
            "${formatSignedNumber(deltaHz / 1000f, fractionDigits = 1)} kHz"
        } else {
            "${formatSignedNumber(deltaHz, fractionDigits = 0)} Hz"
        }

    fun formatSignedDbDelta(deltaDb: Float): String =
        "${formatSignedNumber(deltaDb, fractionDigits = 1)} dB"

    fun formatDecibelValue(valueDb: Float): String =
        formatNumber(valueDb, fractionDigits = 1)

    private fun formatNumber(value: Float, fractionDigits: Int): String =
        formatScaledNumber(value = value.toDouble(), fractionDigits = fractionDigits, alwaysSigned = false)

    private fun formatSignedNumber(value: Float, fractionDigits: Int): String =
        formatScaledNumber(value = value.toDouble(), fractionDigits = fractionDigits, alwaysSigned = true)

    private fun formatScaledNumber(value: Double, fractionDigits: Int, alwaysSigned: Boolean): String {
        if (!value.isFinite()) {
            return "--"
        }

        val scale = scaleFactor(fractionDigits)
        val roundedAbsScaled = floor(abs(value) * scale + 0.5).toLong()
        val wholePart = roundedAbsScaled / scale
        val fractionPart = roundedAbsScaled % scale
        val sign = when {
            value < 0.0 -> "-"
            alwaysSigned -> "+"
            else -> ""
        }

        return if (fractionDigits == 0) {
            "$sign$wholePart"
        } else {
            "$sign$wholePart.${fractionPart.toString().padStart(fractionDigits, '0')}"
        }
    }

    private fun scaleFactor(fractionDigits: Int): Long {
        var scale = 1L
        repeat(fractionDigits) {
            scale *= 10L
        }
        return scale
    }
}

package com.example.audioanalyser.shared

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AnalyzerMath {
    fun calculateRms(buffer: ShortArray, read: Int): Float {
        require(read > 0) { "read must be greater than zero" }
        require(read <= buffer.size) { "read must not exceed the buffer size" }

        var sum = 0.0
        for (index in 0 until read) {
            val value = buffer[index].toDouble()
            sum += value * value
        }
        return sqrt(sum / read).toFloat()
    }

    fun rawDbFromRms(rms: Float, floorDb: Float = -120f): Float {
        require(rms >= 0f) { "rms must not be negative" }

        return if (rms > 1e-9f) {
            (20.0 * log10(rms.toDouble())).toFloat()
        } else {
            floorDb
        }
    }

    fun applyDbOffset(rawDb: Float, dbOffset: Float): Float {
        return (rawDb + dbOffset).coerceAtLeast(0f)
    }

    fun applyNoiseGate(calibratedDb: Float, noiseThreshold: Float): Float {
        return if (calibratedDb > noiseThreshold) calibratedDb else 0f
    }

    fun suggestCutFrequency(frequencyHz: Float): Float {
        val step = when {
            frequencyHz < 200f -> 5f
            frequencyHz < 1000f -> 10f
            frequencyHz < 4000f -> 25f
            else -> 50f
        }
        return (frequencyHz / step).roundToInt() * step
    }

    fun aWeightingLinear(freqHz: Double): Double {
        val f = freqHz.coerceAtLeast(1.0)
        val f2 = f * f
        val term1 = f2 + 20.598997 * 20.598997
        val term2 = f2 + 107.65265 * 107.65265
        val term3 = f2 + 737.86223 * 737.86223
        val term4 = f2 + 12194.217 * 12194.217

        val ra = (12194.217 * 12194.217 * f2 * f2) / (term1 * sqrt(term2 * term3) * term4)
        val aDb = 20.0 * log10(ra) + 2.0
        return 10.0.pow(aDb / 20.0)
    }

    fun cWeightingLinear(freqHz: Double): Double {
        val f = freqHz.coerceAtLeast(1.0)
        val f2 = f * f
        val term1 = f2 + 20.598997 * 20.598997
        val term2 = f2 + 12194.217 * 12194.217
        val rc = (12194.217 * 12194.217 * f2) / (term1 * term2)
        val cDb = 20.0 * log10(rc) + 0.06
        return 10.0.pow(cDb / 20.0)
    }
}

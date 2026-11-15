package com.example.audioanalyser

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/** Exponential smoothing of level in linear power, with a sample-time-based time constant. */
internal class LevelSmoother(private val timeConstantSeconds: Double = 1.0) {
    init { require(timeConstantSeconds > 0.0 && timeConstantSeconds.isFinite()) }
    private var smoothedPower: Double? = null

    fun reset() { smoothedPower = null }

    fun update(levelDb: Float, durationSeconds: Double): Float {
        require(levelDb.isFinite())
        require(durationSeconds > 0.0 && durationSeconds.isFinite())
        val power = 10.0.pow(levelDb.toDouble() / 10.0)
        val previous = smoothedPower
        val result = if (previous == null) power else {
            val retained = exp(-durationSeconds / timeConstantSeconds)
            previous * retained + power * (1.0 - retained)
        }
        smoothedPower = result
        return (10.0 * log10(result)).toFloat()
    }
}

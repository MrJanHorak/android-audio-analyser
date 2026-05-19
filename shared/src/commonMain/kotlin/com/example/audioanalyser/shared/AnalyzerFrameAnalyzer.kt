package com.example.audioanalyser.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.sin

interface AnalyzerFftProcessor {
    fun transform(real: DoubleArray, imag: DoubleArray, size: Int): Boolean
}

data class AnalyzerFrameAnalysisResult(
    val magnitudes: FloatArray,
    val dominantFrequencyHz: Float,
    val aWeightingAdjustmentDb: Float,
    val cWeightingAdjustmentDb: Float,
    val binSize: Float
)

class AnalyzerFrameAnalyzer private constructor(
    private val sampleRate: Int,
    private val fftSize: Int,
    private val fftProcessor: AnalyzerFftProcessor?
) {
    private val binSize = sampleRate.toFloat() / fftSize
    private val window = DoubleArray(fftSize) { index ->
        0.5 * (1.0 - cos(2.0 * PI * index / (fftSize - 1)))
    }
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val magnitudes = FloatArray(fftSize / 2)

    constructor(sampleRate: Int, fftSize: Int) : this(
        sampleRate = sampleRate,
        fftSize = fftSize,
        fftProcessor = null
    )

    init {
        require(sampleRate > 0) { "sampleRate must be greater than zero" }
        require(fftSize > 1) { "fftSize must be greater than one" }
        require(fftSize and (fftSize - 1) == 0) { "fftSize must be a power of two" }
    }

    fun analyzePcm16(buffer: ShortArray, read: Int): AnalyzerFrameAnalysisResult {
        validateRead(read = read, bufferSize = buffer.size)
        prepareInput(read) { index -> buffer[index].toDouble() }
        runFft()
        return summarizeSpectrum()
    }

    fun analyzeFloat(buffer: FloatArray, read: Int): AnalyzerFrameAnalysisResult {
        validateRead(read = read, bufferSize = buffer.size)
        prepareInput(read) { index -> buffer[index].toDouble() }
        runFft()
        return summarizeSpectrum()
    }

    private fun validateRead(read: Int, bufferSize: Int) {
        require(read > 0) { "read must be greater than zero" }
        require(read <= bufferSize) { "read must not exceed the buffer size" }
        require(read <= fftSize) { "read must not exceed fftSize" }
    }

    private fun prepareInput(read: Int, sampleAt: (Int) -> Double) {
        for (index in 0 until fftSize) {
            val sample = if (index < read) sampleAt(index) else 0.0
            real[index] = sample * window[index]
            imag[index] = 0.0
        }
    }

    private fun runFft() {
        val transformed = try {
            fftProcessor?.transform(real, imag, fftSize) ?: false
        } catch (_: Throwable) {
            false
        }

        if (!transformed) {
            managedFftInPlace(real, imag)
        }
    }

    private fun summarizeSpectrum(): AnalyzerFrameAnalysisResult {
        var maxMagnitude = 1e-12f
        var maxIndex = 0
        var spectralPower = 0.0
        var weightedPowerA = 0.0
        var weightedPowerC = 0.0

        for (index in magnitudes.indices) {
            val magnitude = sqrt(real[index] * real[index] + imag[index] * imag[index]).toFloat()
            magnitudes[index] = magnitude
            if (magnitude > maxMagnitude) {
                maxMagnitude = magnitude
                maxIndex = index
            }
        }

        for (index in 1 until magnitudes.size) {
            val magnitude = magnitudes[index].toDouble()
            val frequencyHz = index * binSize.toDouble()
            val weightA = AnalyzerMath.aWeightingLinear(frequencyHz)
            val weightC = AnalyzerMath.cWeightingLinear(frequencyHz)
            val power = magnitude * magnitude
            spectralPower += power
            weightedPowerA += power * (weightA * weightA)
            weightedPowerC += power * (weightC * weightC)
        }

        val aWeightingAdjustmentDb = if (spectralPower > 0.0) {
            (10.0 * log10(weightedPowerA / spectralPower)).toFloat()
        } else {
            0f
        }
        val cWeightingAdjustmentDb = if (spectralPower > 0.0) {
            (10.0 * log10(weightedPowerC / spectralPower)).toFloat()
        } else {
            0f
        }

        return AnalyzerFrameAnalysisResult(
            magnitudes = magnitudes.copyOf(),
            dominantFrequencyHz = maxIndex * binSize,
            aWeightingAdjustmentDb = aWeightingAdjustmentDb,
            cWeightingAdjustmentDb = cWeightingAdjustmentDb,
            binSize = binSize
        )
    }

    private fun managedFftInPlace(real: DoubleArray, imag: DoubleArray) {
        val size = real.size
        var bitReversedIndex = 0
        for (index in 1 until size) {
            var bit = size shr 1
            while (bitReversedIndex >= bit) {
                bitReversedIndex -= bit
                bit = bit shr 1
            }
            bitReversedIndex += bit
            if (index < bitReversedIndex) {
                val temporaryReal = real[index]
                real[index] = real[bitReversedIndex]
                real[bitReversedIndex] = temporaryReal
                val temporaryImag = imag[index]
                imag[index] = imag[bitReversedIndex]
                imag[bitReversedIndex] = temporaryImag
            }
        }

        var length = 2
        while (length <= size) {
            val halfLength = length / 2
            val theta = -2.0 * PI / length
            val twiddleReal = cos(theta)
            val twiddleImag = sin(theta)
            var offset = 0
            while (offset < size) {
                var currentTwiddleReal = 1.0
                var currentTwiddleImag = 0.0
                var index = 0
                while (index < halfLength) {
                    val evenIndex = offset + index
                    val oddIndex = evenIndex + halfLength
                    val evenReal = real[evenIndex]
                    val evenImag = imag[evenIndex]
                    val oddReal = real[oddIndex] * currentTwiddleReal - imag[oddIndex] * currentTwiddleImag
                    val oddImag = real[oddIndex] * currentTwiddleImag + imag[oddIndex] * currentTwiddleReal
                    real[evenIndex] = evenReal + oddReal
                    imag[evenIndex] = evenImag + oddImag
                    real[oddIndex] = evenReal - oddReal
                    imag[oddIndex] = evenImag - oddImag
                    val nextTwiddleReal = currentTwiddleReal * twiddleReal - currentTwiddleImag * twiddleImag
                    val nextTwiddleImag = currentTwiddleReal * twiddleImag + currentTwiddleImag * twiddleReal
                    currentTwiddleReal = nextTwiddleReal
                    currentTwiddleImag = nextTwiddleImag
                    index++
                }
                offset += length
            }
            length = length shl 1
        }
    }

    companion object {
        fun createWithFftProcessor(
            sampleRate: Int,
            fftSize: Int,
            fftProcessor: AnalyzerFftProcessor
        ): AnalyzerFrameAnalyzer {
            return AnalyzerFrameAnalyzer(
                sampleRate = sampleRate,
                fftSize = fftSize,
                fftProcessor = fftProcessor
            )
        }
    }
}

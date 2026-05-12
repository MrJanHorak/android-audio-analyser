package com.example.audioanalyser

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.PI

data class FeedbackPeak(
    val frequencyHz: Float,
    val suggestedCutHz: Float,
    val normalizedLevel: Float,
    val stability: Float,
    val holdFrames: Int
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

class AudioAnalyzer {
    private val sampleRate = 44100
    private val fftSize = 1024
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(fftSize * 2)

    // Reusable buffers to reduce GC pressure
    private val audioBuffer = ShortArray(fftSize)
    private val window = DoubleArray(fftSize).apply {
        for (i in indices) this[i] = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
    }
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val magnitudes = FloatArray(fftSize / 2)

    private val _dbLevel = MutableStateFlow(0f)
    val dbLevel: StateFlow<Float> = _dbLevel

    // Weighted SPL readings
    private val _dbLevelA = MutableStateFlow(0f)
    val dbLevelA: StateFlow<Float> = _dbLevelA

    private val _dbLevelC = MutableStateFlow(0f)
    val dbLevelC: StateFlow<Float> = _dbLevelC

    private val _dbLevelZ = MutableStateFlow(0f)
    val dbLevelZ: StateFlow<Float> = _dbLevelZ

    private val _dbOffset = MutableStateFlow(30f)
    val dbOffset: StateFlow<Float> = _dbOffset

    private val _noiseThreshold = MutableStateFlow(25f)
    val noiseThreshold: StateFlow<Float> = _noiseThreshold

    private val _minDb = MutableStateFlow(Float.MAX_VALUE)
    val minDb: StateFlow<Float> = _minDb

    private val _maxDb = MutableStateFlow(0f)
    val maxDb: StateFlow<Float> = _maxDb

    private val _avgDb = MutableStateFlow(0f)
    val avgDb: StateFlow<Float> = _avgDb

    private val _dbHistory = MutableStateFlow(emptyList<Float>())
    val dbHistory: StateFlow<List<Float>> = _dbHistory

    private var dbSum = 0.0
    private var dbCount = 0
    private val historyLimit = 100

    // Expose a smoothed frequency array for the visualizer
    private val smoothedFrequencies = FloatArray(fftSize / 2)
    private val smoothingFactor = 0.2f
    private val _frequencies = MutableStateFlow(FloatArray(fftSize / 2))
    val frequencies: StateFlow<FloatArray> = _frequencies

    // Spectrogram (waterfall) buffer
    private val spectrogramBuffer = SpectrogramBuffer(128, fftSize / 2)
    val spectrogram: StateFlow<List<FloatArray>> = spectrogramBuffer.spectrogramFlow

    // Dominant frequency (Hz) for accessibility and quick-read
    private val _dominantFrequency = MutableStateFlow(0f)
    val dominantFrequency: StateFlow<Float> = _dominantFrequency

    private val _feedbackPeaks = MutableStateFlow(emptyList<FeedbackPeak>())
    val feedbackPeaks: StateFlow<List<FeedbackPeak>> = _feedbackPeaks

    // Error reporting
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var isRecording = false
    private val trackedFeedbackPeaks = mutableListOf<TrackedFeedbackPeak>()

    private val binSize = sampleRate.toFloat() / fftSize
    private val feedbackCandidateLimit = 6
    private val feedbackPeakLimit = 3
    private val feedbackPeakMinFrequency = 80f
    private val feedbackPeakMaxFrequency = 12000f
    private val feedbackPeakDecayFrames = 10
    private val feedbackPeakFloorRatio = 0.18f

    fun setDbOffset(value: Float) {
        _dbOffset.value = value
    }

    fun setNoiseThreshold(value: Float) {
        _noiseThreshold.value = value
    }

    fun resetStats() {
        _minDb.value = Float.MAX_VALUE
        _maxDb.value = 0f
        _avgDb.value = 0f
        dbSum = 0.0
        dbCount = 0
        _dbHistory.value = emptyList()
        clearFeedbackPeaks()
    }

    @SuppressLint("MissingPermission")
    suspend fun startAnalyzing() = withContext(Dispatchers.IO) {
        if (isRecording) return@withContext

        _error.value = null

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            _error.value = "AudioRecord initialization failed"
            return@withContext
        }

        try {
            audioRecord.startRecording()
            isRecording = true

            while (isRecording) {
                val read = audioRecord.read(audioBuffer, 0, fftSize)
                if (read <= 0) continue

                // dB calculation (RMS)
                val rms = calculateRMS(audioBuffer, read)
                val rawDb = if (rms > 1e-9) 20 * log10(rms.toDouble()).toFloat() else -120f
                val calibratedDb = (rawDb + _dbOffset.value).coerceAtLeast(0f)
                val currentDb = if (calibratedDb > _noiseThreshold.value) calibratedDb else 0f
                _dbLevel.value = currentDb

                // Update stats
                if (currentDb > 0) {
                    _maxDb.value = maxOf(_maxDb.value, currentDb)
                    _minDb.value = minOf(_minDb.value, currentDb)

                    dbSum += currentDb
                    dbCount++
                    _avgDb.value = (dbSum / dbCount).toFloat()

                    val currentHistory = _dbHistory.value.toMutableList()
                    currentHistory.add(currentDb)
                    if (currentHistory.size > historyLimit) currentHistory.removeAt(0)
                    _dbHistory.value = currentHistory
                } else if (_dbLevel.value == 0f) {
                    val currentHistory = _dbHistory.value.toMutableList()
                    currentHistory.add(0f)
                    if (currentHistory.size > historyLimit) currentHistory.removeAt(0)
                    _dbHistory.value = currentHistory
                }

                // Apply window and populate real/imag arrays
                for (i in 0 until fftSize) {
                    val sample = if (i < read) audioBuffer[i].toDouble() else 0.0
                    real[i] = sample * window[i]
                    imag[i] = 0.0
                }

                // In-place iterative FFT
                fftInPlace(real, imag)

                // Magnitudes and smoothing
                var maxMag = 1e-12f
                var maxIndex = 0
                for (i in magnitudes.indices) {
                    val mag = sqrt(real[i] * real[i] + imag[i] * imag[i]).toFloat()
                    magnitudes[i] = mag
                    if (mag > maxMag) {
                        maxMag = mag
                        maxIndex = i
                    }
                }

                // Compute weighted SPLs (A, C, Z) by applying frequency weighting to the spectrum
                var spectralPower = 0.0
                var weightedPowerA = 0.0
                var weightedPowerC = 0.0
                for (i in 1 until magnitudes.size) {
                    val mag = magnitudes[i].toDouble()
                    val freq = i * binSize.toDouble()
                    val wA = aWeightingLinear(freq)
                    val wC = cWeightingLinear(freq)
                    val power = mag * mag
                    spectralPower += power
                    weightedPowerA += power * (wA * wA)
                    weightedPowerC += power * (wC * wC)
                }

                val weightAdjA = if (spectralPower > 0.0) 10.0 * kotlin.math.log10(weightedPowerA / spectralPower) else 0.0
                val weightAdjC = if (spectralPower > 0.0) 10.0 * kotlin.math.log10(weightedPowerC / spectralPower) else 0.0


                for (i in magnitudes.indices) {
                    val mag = if (currentDb > 0) magnitudes[i] else 0f
                    smoothedFrequencies[i] = smoothedFrequencies[i] * (1 - smoothingFactor) + mag * smoothingFactor
                }
                _frequencies.value = smoothedFrequencies.copyOf()
                // push the current smoothed spectrum into the spectrogram buffer
                try {
                    spectrogramBuffer.addFrame(smoothedFrequencies.copyOf())
                } catch (_: Exception) {
                }

                // Dominant frequency in Hz
                _dominantFrequency.value = maxIndex * binSize
                // Publish weighted SPLs (apply dbOffset and noise threshold similar to unweighted meter)
                val dbAvalue = ((calibratedDb + weightAdjA).toFloat()).let { if (it > _noiseThreshold.value) it.coerceAtLeast(0f) else 0f }
                val dbCvalue = ((calibratedDb + weightAdjC).toFloat()).let { if (it > _noiseThreshold.value) it.coerceAtLeast(0f) else 0f }
                val dbZvalue = ((calibratedDb).toFloat()).let { if (it > _noiseThreshold.value) it.coerceAtLeast(0f) else 0f }

                _dbLevelA.value = dbAvalue
                _dbLevelC.value = dbCvalue
                _dbLevelZ.value = dbZvalue
                updateFeedbackPeaks(currentDb)
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Audio processing error"
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
            } catch (ignored: Exception) {
            }
            audioRecord.release()
            isRecording = false
            clearFeedbackPeaks()
        }
    }

    fun stop() {
        isRecording = false
        clearFeedbackPeaks()
    }

    private fun updateFeedbackPeaks(currentDb: Float) {
        val globalMax = smoothedFrequencies.maxOrNull() ?: 0f
        if (currentDb <= 0f || globalMax <= 0f) {
            decayFeedbackPeaks()
            return
        }

        val candidates = mutableListOf<FeedbackCandidate>()
        for (index in 2 until smoothedFrequencies.size - 2) {
            val magnitude = smoothedFrequencies[index]
            if (magnitude < globalMax * feedbackPeakFloorRatio) continue
            if (magnitude < smoothedFrequencies[index - 1] || magnitude < smoothedFrequencies[index + 1]) continue

            val frequencyHz = index * binSize
            if (frequencyHz < feedbackPeakMinFrequency || frequencyHz > feedbackPeakMaxFrequency) continue

            candidates += FeedbackCandidate(
                frequencyHz = frequencyHz,
                normalizedLevel = (magnitude / globalMax).coerceIn(0f, 1f)
            )
        }

        if (candidates.isEmpty()) {
            decayFeedbackPeaks()
            return
        }

        trackedFeedbackPeaks.forEach { peak ->
            peak.staleFrames += 1
            peak.normalizedLevel *= 0.92f
        }

        val matchedPeaks = mutableSetOf<TrackedFeedbackPeak>()
        candidates
            .sortedByDescending { it.normalizedLevel }
            .take(feedbackCandidateLimit)
            .forEach { candidate ->
                val matched = trackedFeedbackPeaks
                    .filterNot { it in matchedPeaks }
                    .minByOrNull { abs(it.frequencyHz - candidate.frequencyHz) }
                    ?.takeIf {
                        abs(it.frequencyHz - candidate.frequencyHz) <= maxOf(60f, candidate.frequencyHz * 0.05f)
                    }

                if (matched != null) {
                    matched.frequencyHz = matched.frequencyHz * 0.7f + candidate.frequencyHz * 0.3f
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
            peak.staleFrames > feedbackPeakDecayFrames || peak.normalizedLevel < 0.08f
        }

        publishFeedbackPeaks()
    }

    private fun decayFeedbackPeaks() {
        if (trackedFeedbackPeaks.isEmpty()) {
            _feedbackPeaks.value = emptyList()
            return
        }

        trackedFeedbackPeaks.removeAll { peak ->
            peak.staleFrames += 1
            peak.normalizedLevel *= 0.88f
            peak.holdFrames = maxOf(1, peak.holdFrames - 1)
            peak.staleFrames > feedbackPeakDecayFrames || peak.normalizedLevel < 0.08f
        }

        publishFeedbackPeaks()
    }

    private fun publishFeedbackPeaks() {
        _feedbackPeaks.value = trackedFeedbackPeaks
            .sortedByDescending { peak ->
                val stability = peak.holdFrames.coerceAtMost(18) / 18f
                peak.normalizedLevel * 0.55f + stability * 0.45f
            }
            .take(feedbackPeakLimit)
            .map { peak ->
                FeedbackPeak(
                    frequencyHz = peak.frequencyHz,
                    suggestedCutHz = suggestCutFrequency(peak.frequencyHz),
                    normalizedLevel = peak.normalizedLevel.coerceIn(0f, 1f),
                    stability = (peak.holdFrames.coerceAtMost(18) / 18f).coerceIn(0f, 1f),
                    holdFrames = peak.holdFrames
                )
            }
    }

    private fun suggestCutFrequency(frequencyHz: Float): Float {
        val step = when {
            frequencyHz < 200f -> 5f
            frequencyHz < 1000f -> 10f
            frequencyHz < 4000f -> 25f
            else -> 50f
        }
        return (frequencyHz / step).roundToInt() * step
    }

    private fun clearFeedbackPeaks() {
        trackedFeedbackPeaks.clear()
        _feedbackPeaks.value = emptyList()
    }

    private fun calculateRMS(buffer: ShortArray, read: Int): Float {
        var sum = 0.0
        for (i in 0 until read) {
            val v = buffer[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / read).toFloat()
    }

    // Iterative in-place radix-2 FFT
    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size

        // If a native FFT is available, prefer it for performance.
        if (NativeFFT.available) {
            try {
                NativeFFT.nativeFft(real, imag, n)
                return
            } catch (t: Throwable) {
                // If native call fails for any reason, fall back to managed implementation.
            }
        }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j >= bit) {
                j -= bit
                bit = bit shr 1
            }
            j += bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val half = len / 2
            val theta = -2.0 * PI / len
            val wlenR = kotlin.math.cos(theta)
            val wlenI = kotlin.math.sin(theta)
            var i = 0
            while (i < n) {
                var wr = 1.0
                var wi = 0.0
                var k = 0
                while (k < half) {
                    val idx = i + k
                    val idy = idx + half
                    val ur = real[idx]
                    val ui = imag[idx]
                    val vr = real[idy] * wr - imag[idy] * wi
                    val vi = real[idy] * wi + imag[idy] * wr
                    real[idx] = ur + vr
                    imag[idx] = ui + vi
                    real[idy] = ur - vr
                    imag[idy] = ui - vi
                    val nextWr = wr * wlenR - wi * wlenI
                    val nextWi = wr * wlenI + wi * wlenR
                    wr = nextWr
                    wi = nextWi
                    k++
                }
                i += len
            }
            len = len shl 1
        }
    }

    // A-weighting (approx) linear scale factor for a given frequency (Hz)
    private fun aWeightingLinear(freqHz: Double): Double {
        val f = freqHz.coerceAtLeast(1.0)
        val f2 = f * f
        val raNum = 12194.217 * 12194.217
        val raNumPow = raNum * raNum * f2 * f2 / (raNum * raNum) // kept for clarity

        // Use the standard analog A-weighting approximation in dB, normalized at 1 kHz
        val term1 = (f2 + 20.598997 * 20.598997)
        val term2 = (f2 + 107.65265 * 107.65265)
        val term3 = (f2 + 737.86223 * 737.86223)
        val term4 = (f2 + 12194.217 * 12194.217)

        val ra = (12194.217 * 12194.217 * f2 * f2) / (term1 * kotlin.math.sqrt(term2 * term3) * term4)
        val aDb = 20.0 * kotlin.math.log10(ra) + 2.0
        return 10.0.pow(aDb / 20.0)
    }

    // C-weighting (approx) linear scale factor for a given frequency (Hz)
    private fun cWeightingLinear(freqHz: Double): Double {
        val f = freqHz.coerceAtLeast(1.0)
        val f2 = f * f
        val term1 = (f2 + 20.598997 * 20.598997)
        val term2 = (f2 + 12194.217 * 12194.217)
        val rc = (12194.217 * 12194.217 * f2) / (term1 * term2)
        val cDb = 20.0 * kotlin.math.log10(rc) + 0.06
        return 10.0.pow(cDb / 20.0)
    }
}

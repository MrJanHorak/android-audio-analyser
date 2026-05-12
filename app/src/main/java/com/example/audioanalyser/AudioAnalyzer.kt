package com.example.audioanalyser

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.PI

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

    // Dominant frequency (Hz) for accessibility and quick-read
    private val _dominantFrequency = MutableStateFlow(0f)
    val dominantFrequency: StateFlow<Float> = _dominantFrequency

    // Error reporting
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var isRecording = false

    private val binSize = sampleRate.toFloat() / fftSize

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

                for (i in magnitudes.indices) {
                    val mag = if (currentDb > 0) magnitudes[i] else 0f
                    smoothedFrequencies[i] = smoothedFrequencies[i] * (1 - smoothingFactor) + mag * smoothingFactor
                }
                _frequencies.value = smoothedFrequencies.copyOf()

                // Dominant frequency in Hz
                _dominantFrequency.value = maxIndex * binSize
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
        }
    }

    fun stop() {
        isRecording = false
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
}

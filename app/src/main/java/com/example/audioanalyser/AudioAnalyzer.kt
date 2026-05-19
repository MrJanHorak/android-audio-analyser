package com.example.audioanalyser

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.audioanalyser.shared.AnalyzerFftProcessor
import com.example.audioanalyser.shared.AnalyzerFrameAnalyzer
import com.example.audioanalyser.shared.AnalyzerMath
import com.example.audioanalyser.shared.FeedbackPeak
import com.example.audioanalyser.shared.FeedbackPeakTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

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
    private val frameAnalyzer = AnalyzerFrameAnalyzer.createWithFftProcessor(
        sampleRate = sampleRate,
        fftSize = fftSize,
        fftProcessor = object : AnalyzerFftProcessor {
            override fun transform(real: DoubleArray, imag: DoubleArray, size: Int): Boolean {
                if (!NativeFFT.available) {
                    return false
                }

                return try {
                    NativeFFT.nativeFft(real, imag, size)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }
    )

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
    private val feedbackPeakTracker = FeedbackPeakTracker()

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging
    private val loggedData = mutableListOf<String>()

    private var transientCallback: ((Long, Boolean) -> Unit)? = null

    fun awaitNextTransient(onDetected: (Long, Boolean) -> Unit) {
        transientCallback = onDetected
    }

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
        clearFeedbackPeaks()
    }

    fun startLogging() {
        loggedData.clear()
        loggedData.add("Timestamp,dB(Z),dB(A),dB(C),DominantFreq(Hz)")
        _isLogging.value = true
    }

    fun stopLogging(): String {
        _isLogging.value = false
        val csvContent = loggedData.joinToString("\n")
        loggedData.clear()
        return csvContent
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
                val rms = AnalyzerMath.calculateRms(audioBuffer, read)
                val rawDb = AnalyzerMath.rawDbFromRms(rms)
                val calibratedDb = AnalyzerMath.applyDbOffset(rawDb, _dbOffset.value)
                val currentDb = AnalyzerMath.applyNoiseGate(calibratedDb, _noiseThreshold.value)
                _dbLevel.value = currentDb
                
                // Transient detection
                if (transientCallback != null && currentDb > _noiseThreshold.value + 15f) {
                    var maxAbs = 0
                    var isPositive = true
                    for (i in 0 until read) {
                        val v = audioBuffer[i].toInt()
                        val absV = kotlin.math.abs(v)
                        if (absV > maxAbs) {
                            maxAbs = absV
                            isPositive = v > 0
                        }
                    }
                    transientCallback?.invoke(System.nanoTime(), isPositive)
                    transientCallback = null // only fire once
                }

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

                val frameAnalysis = frameAnalyzer.analyzePcm16(audioBuffer, read)

                for (i in frameAnalysis.magnitudes.indices) {
                    val mag = if (currentDb > 0) frameAnalysis.magnitudes[i] else 0f
                    smoothedFrequencies[i] = smoothedFrequencies[i] * (1 - smoothingFactor) + mag * smoothingFactor
                }
                _frequencies.value = smoothedFrequencies.copyOf()
                // push the current smoothed spectrum into the spectrogram buffer
                try {
                    spectrogramBuffer.addFrame(smoothedFrequencies.copyOf())
                } catch (_: Exception) {
                }

                // Dominant frequency in Hz
                _dominantFrequency.value = frameAnalysis.dominantFrequencyHz
                // Publish weighted SPLs (apply dbOffset and noise threshold similar to unweighted meter)
                val dbAvalue = AnalyzerMath.applyNoiseGate(
                    calibratedDb = (calibratedDb + frameAnalysis.aWeightingAdjustmentDb).coerceAtLeast(0f),
                    noiseThreshold = _noiseThreshold.value
                )
                val dbCvalue = AnalyzerMath.applyNoiseGate(
                    calibratedDb = (calibratedDb + frameAnalysis.cWeightingAdjustmentDb).coerceAtLeast(0f),
                    noiseThreshold = _noiseThreshold.value
                )
                val dbZvalue = currentDb

                _dbLevelA.value = dbAvalue
                _dbLevelC.value = dbCvalue
                _dbLevelZ.value = dbZvalue
                updateFeedbackPeaks(currentDb)

                if (_isLogging.value) {
                    val timestamp = System.currentTimeMillis()
                    loggedData.add("$timestamp,$dbZvalue,$dbAvalue,$dbCvalue,${_dominantFrequency.value}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        _feedbackPeaks.value = feedbackPeakTracker.update(
            spectrum = smoothedFrequencies,
            currentDb = currentDb,
            binSize = binSize
        )
    }

    private fun clearFeedbackPeaks() {
        feedbackPeakTracker.clear()
        _feedbackPeaks.value = emptyList()
    }
}

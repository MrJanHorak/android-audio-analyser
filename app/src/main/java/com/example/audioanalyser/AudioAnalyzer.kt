package com.example.audioanalyser

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.example.audioanalyser.shared.AnalyzerFftProcessor
import com.example.audioanalyser.shared.AnalyzerFrameAnalyzer
import com.example.audioanalyser.shared.AnalyzerMath
import com.example.audioanalyser.shared.FeedbackPeak
import com.example.audioanalyser.shared.FeedbackPeakTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class AudioAnalyzer(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
    private var activeProfileKey: String? = null
    private val sampleRate = 44100
    private var fftSize = 1024
    private var audioBuffer = ShortArray(fftSize)
    private val fftProcessor = object : AnalyzerFftProcessor {
        override fun transform(real: DoubleArray, imag: DoubleArray, size: Int): Boolean {
            if (!NativeFFT.available) return false
            return try {
                NativeFFT.nativeFft(real, imag, size)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
    private fun newFrameAnalyzer(size: Int) = AnalyzerFrameAnalyzer.createWithFftProcessor(
        sampleRate = sampleRate, fftSize = size, fftProcessor = fftProcessor
    )
    private var frameAnalyzer = newFrameAnalyzer(fftSize)

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
    private var smoothedFrequencies = FloatArray(fftSize / 2)
    private val smoothingFactor = 0.2f
    private val _frequencies = MutableStateFlow(FloatArray(fftSize / 2))
    val frequencies: StateFlow<FloatArray> = _frequencies

    // Spectrogram (waterfall) buffer
    private val spectrogramBuffer = SpectrogramBuffer(128, 512)
    val spectrogram: StateFlow<List<FloatArray>> = spectrogramBuffer.spectrogramFlow

    // Dominant frequency (Hz) for accessibility and quick-read
    private val _dominantFrequency = MutableStateFlow(0f)
    val dominantFrequency: StateFlow<Float> = _dominantFrequency

    private val _feedbackPeaks = MutableStateFlow(emptyList<FeedbackPeak>())
    val feedbackPeaks: StateFlow<List<FeedbackPeak>> = _feedbackPeaks

    // Error reporting
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val recordingLock = Any()
    @Volatile private var isRecording = false
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    private val _inputName = MutableStateFlow<String?>(null)
    val inputName: StateFlow<String?> = _inputName
    private val _inputIdentity = MutableStateFlow<Int?>(null)
    val inputIdentity: StateFlow<Int?> = _inputIdentity
    private val _inputRevision = MutableStateFlow(0L)
    val inputRevision: StateFlow<Long> = _inputRevision
    private val _calibrationStatus = MutableStateFlow("Input not identified")
    val calibrationStatus: StateFlow<String> = _calibrationStatus
    private val _inputClipping = MutableStateFlow(false)
    val inputClipping: StateFlow<Boolean> = _inputClipping
    private val feedbackPeakTracker = FeedbackPeakTracker()

    private val loggingLock = Any()
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging
    private val loggedData = mutableListOf<String>()

    private var transientCallback: ((Long, Boolean) -> Unit)? = null

    fun awaitNextTransient(onDetected: (Long, Boolean) -> Unit) {
        transientCallback = onDetected
    }

    private val binSize: Float get() = sampleRate.toFloat() / fftSize

    private fun configureFft(size: Int) {
        if (size == fftSize) return
        fftSize = size
        audioBuffer = ShortArray(size)
        frameAnalyzer = newFrameAnalyzer(size)
        smoothedFrequencies = FloatArray(size / 2)
        _frequencies.value = smoothedFrequencies.copyOf()
        _dominantFrequency.value = 0f
        clearFeedbackPeaks()
        spectrogramBuffer.clear()
    }

    fun setAndSaveDbOffset(value: Float) {
        synchronized(recordingLock) {
            _dbOffset.value = value
            val key = activeProfileKey
            if (key != null) {
                preferences.edit().putFloat(key, value).apply()
                _calibrationStatus.value = "Saved input offset • verify with reference meter"
            } else {
                _calibrationStatus.value = "Temporary offset • verify with reference meter"
            }
        }
    }

    fun setAndSaveNoiseThreshold(value: Float) {
        synchronized(recordingLock) {
            _noiseThreshold.value = value
            noiseGateProfileKey(activeProfileKey)?.let { key ->
                preferences.edit().putFloat(key, value).apply()
            }
        }
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

    fun startLogging() = synchronized(loggingLock) {
        loggedData.clear()
        loggedData.add(ANALYZER_CSV_HEADER)
        _isLogging.value = true
    }

    fun stopLogging(): String = synchronized(loggingLock) {
        _isLogging.value = false
        val csvContent = loggedData.joinToString("\n")
        loggedData.clear()
        csvContent
    }

    @SuppressLint("MissingPermission")
    suspend fun startAnalyzing(audioSource: Int = MediaRecorder.AudioSource.MIC, detailedSpectrum: Boolean = false) = withContext(Dispatchers.IO) {
        synchronized(recordingLock) {
            if (isRecording) return@withContext
            isRecording = true
            _error.value = null
            activeProfileKey = null
            _dbOffset.value = 30f
            _noiseThreshold.value = preferences.getFloat("noise_threshold", 25f)
            _calibrationStatus.value = "Waiting for input"
        }

        val audioRecord = try {
            configureFft(if (detailedSpectrum) 4096 else 1024)
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(fftSize * 2)
            AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            synchronized(recordingLock) {
                if (isRecording) _error.value = e.message ?: "Could not open microphone"
                isRecording = false
            }
            return@withContext
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            synchronized(recordingLock) {
                if (isRecording) _error.value = "AudioRecord initialization failed"
                isRecording = false
            }
            audioRecord.release()
            return@withContext
        }

        try {
            audioRecord.startRecording()
            synchronized(recordingLock) {
                if (!isRecording) return@withContext
                _isListening.value = true
            }
            var lastRailTimeMs = Long.MIN_VALUE
            var lastRoute: Triple<Int, Int, String>? = null
            var routeObserved = false

            while (isRecording) {
                var read = 0
                var readError = 0
                while (read < fftSize && isRecording) {
                    val count = audioRecord.read(audioBuffer, read, fftSize - read)
                    if (count < 0) { readError = count; break }
                    if (count > 0) read += count
                }
                if (!isRecording) break
                if (readError < 0) {
                    _error.value = "Microphone read failed ($readError). Try resuming capture."
                    break
                }
                if (read < fftSize) continue

                val nowMs = SystemClock.elapsedRealtime()
                val route = audioRecord.routedDevice
                val routeName = route?.productName?.toString()?.takeIf { it.isNotBlank() }
                val routeKey = route?.let { Triple(it.id, it.type, routeName.orEmpty()) }
                if (!routeObserved || routeKey != lastRoute) {
                    synchronized(recordingLock) {
                        if (!isRecording) return@withContext
                        val profileKey = calibrationProfileKey(audioSource, route?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC, routeName)
                        activeProfileKey = profileKey
                        _dbOffset.value = if (profileKey != null) preferences.getFloat(profileKey, 30f) else 30f
                        val defaultGate = preferences.getFloat("noise_threshold", 25f)
                        val gateKey = noiseGateProfileKey(profileKey)
                        _noiseThreshold.value = if (gateKey != null) preferences.getFloat(gateKey, defaultGate) else defaultGate
                        _calibrationStatus.value = when {
                            route == null -> "Input not identified • calibration unverified"
                            profileKey == null -> "External or unidentified input • temporary offset only"
                            preferences.contains(profileKey) -> "Saved input offset • verify with reference meter"
                            else -> "Uncalibrated input"
                        }
                        _inputIdentity.value = route?.id
                        _inputRevision.value += 1L
                        _inputName.value = routeName
                    }
                    routeObserved = true
                    lastRoute = routeKey
                }
                if (AnalyzerMath.hasPcmRailSamples(audioBuffer, read)) lastRailTimeMs = nowMs
                _inputClipping.value = lastRailTimeMs != Long.MIN_VALUE && nowMs - lastRailTimeMs < 2000L

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

                if (currentDb > 0f) {
                    for (i in frameAnalysis.magnitudes.indices) {
                        val mag = frameAnalysis.magnitudes[i]
                        smoothedFrequencies[i] = smoothedFrequencies[i] * (1 - smoothingFactor) + mag * smoothingFactor
                    }
                } else {
                    smoothedFrequencies.fill(0f)
                }
                _frequencies.value = smoothedFrequencies.copyOf()
                // push the current smoothed spectrum into the spectrogram buffer
                try {
                    spectrogramBuffer.addFrame(downsampleSpectrumPeaks(smoothedFrequencies, 512), nowMs)
                } catch (_: Exception) {
                }

                // Dominant frequency in Hz
                _dominantFrequency.value = availableDominantFrequency(currentDb, frameAnalysis.dominantFrequencyHz)
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
                if (currentDb > 0f) updateFeedbackPeaks(currentDb) else clearFeedbackPeaks()

                synchronized(loggingLock) {
                    if (_isLogging.value) {
                        val timestamp = System.currentTimeMillis()
                        loggedData.add(analyzerCsvRow(timestamp, dbZvalue, dbAvalue, dbCvalue, _dominantFrequency.value))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isRecording) _error.value = e.message ?: "Audio processing error"
        } finally {
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
            } catch (ignored: Exception) {
            }
            audioRecord.release()
            synchronized(recordingLock) {
                isRecording = false
                _isListening.value = false
                _inputName.value = null
                _inputIdentity.value = null
                activeProfileKey = null
                _calibrationStatus.value = "Input not identified"
                _inputClipping.value = false
            }
            clearFeedbackPeaks()
        }
    }

    fun stop() {
        synchronized(recordingLock) {
            isRecording = false
            _isListening.value = false
            _inputName.value = null
            _inputIdentity.value = null
            activeProfileKey = null
            _calibrationStatus.value = "Input not identified"
            _inputClipping.value = false
        }
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

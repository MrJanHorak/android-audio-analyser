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

class AudioAnalyzer {
    private val sampleRate = 44100
    private val fftSize = 1024
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(fftSize * 2)

    private val _dbLevel = MutableStateFlow(0f)
    val dbLevel: StateFlow<Float> = _dbLevel

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

    private val _frequencies = MutableStateFlow(FloatArray(fftSize / 2))
    val frequencies: StateFlow<FloatArray> = _frequencies

    private var isRecording = false
    
    // Calibration offset - phone mics vary, 40-50 is a common offset to reach real-world dB
    private val dbOffset = 50f 
    private val noiseFloor = 10f // Ignore anything below 10dB after offset
    
    // For smoothing the visualizer
    private var smoothedFrequencies = FloatArray(fftSize / 2)
    private val smoothingFactor = 0.2f 

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
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) return@withContext

        audioRecord.startRecording()
        isRecording = true

        val buffer = ShortArray(fftSize)
        while (isRecording) {
            val read = audioRecord.read(buffer, 0, fftSize)
            if (read == fftSize) {
                // 1. Calculate dB with calibration
                val rms = calculateRMS(buffer, read)
                val rawDb = if (rms > 1e-9) 20 * log10(rms.toDouble()).toFloat() else 0f
                val calibratedDb = (rawDb + dbOffset).coerceAtLeast(0f)
                val currentDb = if (calibratedDb > noiseFloor) calibratedDb else 0f
                _dbLevel.value = currentDb

                // Update Stats
                if (currentDb > 0) {
                    _maxDb.value = maxOf(_maxDb.value, currentDb)
                    _minDb.value = minOf(_minDb.value, currentDb)
                    
                    dbSum += currentDb
                    dbCount++
                    _avgDb.value = (dbSum / dbCount).toFloat()

                    // Update History
                    val currentHistory = _dbHistory.value.toMutableList()
                    currentHistory.add(currentDb)
                    if (currentHistory.size > historyLimit) {
                        currentHistory.removeAt(0)
                    }
                    _dbHistory.value = currentHistory
                }

                // 2. Apply Hanning Window to reduce "static" leakage
                val windowedBuffer = applyHanningWindow(buffer)

                // 3. Calculate FFT
                val currentFft = calculateFFT(windowedBuffer)
                
                // 4. Smooth the frequencies for a cleaner look
                for (i in currentFft.indices) {
                    smoothedFrequencies[i] = smoothedFrequencies[i] * (1 - smoothingFactor) + currentFft[i] * smoothingFactor
                }
                _frequencies.value = smoothedFrequencies.copyOf()
            }
        }

        audioRecord.stop()
        audioRecord.release()
    }

    fun stop() {
        isRecording = false
    }

    private fun calculateRMS(buffer: ShortArray, read: Int): Float {
        var sum = 0.0
        for (i in 0 until read) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / read).toFloat()
    }

    private fun applyHanningWindow(buffer: ShortArray): DoubleArray {
        val n = buffer.size
        val windowed = DoubleArray(n)
        for (i in 0 until n) {
            val multiplier = 0.5 * (1.0 - cos(2.0 * Math.PI * i / (n - 1)))
            windowed[i] = buffer[i] * multiplier
        }
        return windowed
    }

    private fun calculateFFT(windowedBuffer: DoubleArray): FloatArray {
        val n = windowedBuffer.size
        val real = windowedBuffer.copyOf()
        val imag = DoubleArray(n) { 0.0 }
        
        fft(real, imag)
        
        val magnitudes = FloatArray(n / 2)
        for (i in 0 until n / 2) {
            // Convert to a pseudo-dB scale for better visualization of harmonics
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            magnitudes[i] = mag.toFloat()
        }
        return magnitudes
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return

        val realEven = DoubleArray(n / 2)
        val imagEven = DoubleArray(n / 2)
        val realOdd = DoubleArray(n / 2)
        val imagOdd = DoubleArray(n / 2)

        for (i in 0 until n / 2) {
            realEven[i] = real[2 * i]
            imagEven[i] = imag[2 * i]
            realOdd[i] = real[2 * i + 1]
            imagOdd[i] = imag[2 * i + 1]
        }

        fft(realEven, imagEven)
        fft(realOdd, imagOdd)

        for (k in 0 until n / 2) {
            val angle = -2.0 * Math.PI * k / n
            val cosVal = cos(angle)
            val sinVal = kotlin.math.sin(angle)
            val tReal = cosVal * realOdd[k] - sinVal * imagOdd[k]
            val tImag = sinVal * realOdd[k] + cosVal * imagOdd[k]
            real[k] = realEven[k] + tReal
            imag[k] = imagEven[k] + tImag
            real[k + n / 2] = realEven[k] - tReal
            imag[k + n / 2] = imagEven[k] - tImag
        }
    }
}

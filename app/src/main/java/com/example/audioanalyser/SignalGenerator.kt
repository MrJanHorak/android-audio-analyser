package com.example.audioanalyser

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SignalGenerator(private val sampleRate: Int = 44100) {
    enum class Mode { SINE, WHITE, PINK, PULSE, SWEEP }

    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun start(mode: Mode, freqHz: Float = 1000f, level: Float = 0.25f) {
        stop()

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .build()

        audioTrack?.play()
        _isRunning.value = true

        playJob = scope.launch {
            when (mode) {
                Mode.SINE -> playSine(freqHz, level, minBuf)
                Mode.WHITE -> playWhite(level, minBuf)
                Mode.PINK -> playPink(level, minBuf)
                Mode.PULSE -> playPulse(freqHz, level, minBuf)
                Mode.SWEEP -> playSweep(level, minBuf)
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        _isRunning.value = false
    }

    private suspend fun playSine(freqHz: Float, level: Float, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        var phase = 0.0
        val inc = 2.0 * PI * freqHz / sampleRate
        val amp = (level.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt()

        while (isActive()) {
            for (i in buffer.indices) {
                val v = (sin(phase) * amp).toInt()
                buffer[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                phase += inc
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            audioTrack?.write(buffer, 0, buffer.size)
        }
    }

    private suspend fun playWhite(level: Float, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val amp = (level.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt()
        val rnd = Random.Default

        while (isActive()) {
            for (i in buffer.indices) {
                val v = (rnd.nextDouble(-1.0, 1.0) * amp).toInt()
                buffer[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            audioTrack?.write(buffer, 0, buffer.size)
        }
    }

    private suspend fun playPink(level: Float, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val amp = (level.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt()
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0
        val rnd = Random.Default

        while (isActive()) {
            for (i in buffer.indices) {
                val white = rnd.nextDouble(-1.0, 1.0)
                b0 = 0.99886 * b0 + white * 0.0555179
                b1 = 0.99332 * b1 + white * 0.0750759
                b2 = 0.96900 * b2 + white * 0.1538520
                b3 = 0.86650 * b3 + white * 0.3104856
                b4 = 0.55000 * b4 + white * 0.5329522
                b5 = -0.7616 * b5 - white * 0.0168980
                val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
                b6 = white * 0.115926
                val v = (pink * amp * 0.11).toInt()
                buffer[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            audioTrack?.write(buffer, 0, buffer.size)
        }
    }

    private suspend fun playPulse(freqHz: Float, level: Float, bufferSize: Int) {
        // Short burst then pause — suitable for delay measurements
        val burstMs = 8
        val burstLen = (sampleRate * (burstMs / 1000.0)).toInt().coerceAtLeast(32)
        val burst = ShortArray(burstLen)
        val amp = (level.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt()
        var phase = 0.0
        val inc = 2.0 * PI * freqHz / sampleRate

        while (isActive()) {
            for (i in 0 until burstLen) {
                val env = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / burstLen)) // smooth window
                val v = (sin(phase) * amp * env).toInt()
                burst[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                phase += inc
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            audioTrack?.write(burst, 0, burst.size)
            // small pause to allow reflections to be picked up
            delay(300)
        }
    }

    private suspend fun playSweep(level: Float, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val amp = (level.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt()
        val durationSec = 3.0 // 3 seconds sweep
        val startFreq = 20.0
        val endFreq = 20000.0
        
        // Logarithmic sweep parameters
        val l1 = kotlin.math.ln(startFreq)
        val l2 = kotlin.math.ln(endFreq)
        
        var totalSamples = 0.0
        var phase = 0.0
        
        while (isActive()) {
            for (i in buffer.indices) {
                val time = (totalSamples / sampleRate) % durationSec
                
                // Exponential frequency sweep
                val currentFreq = kotlin.math.exp(l1 + (l2 - l1) * (time / durationSec))
                phase += 2.0 * PI * currentFreq / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                
                val v = (sin(phase) * amp).toInt()
                buffer[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                totalSamples++
            }
            audioTrack?.write(buffer, 0, buffer.size)
        }
    }

    private fun isActive(): Boolean = playJob?.isActive ?: false

    fun emitSinglePing(): Long {
        stop()

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            
        // 5ms burst of high amplitude click
        val pulseLength = (sampleRate * 0.005).toInt()
        val buffer = ShortArray(pulseLength)
        for (i in buffer.indices) {
            buffer[i] = (Short.MAX_VALUE * (1.0 - i.toDouble() / buffer.size)).toInt().toShort()
        }
        
        audioTrack?.write(buffer, 0, buffer.size)
        
        val txTime = System.nanoTime()
        audioTrack?.play()
        
        // Cleanup after play
        scope.launch {
            delay(1000)
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
        }
        
        return txTime
    }
}

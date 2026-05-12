package com.example.audioanalyser

/**
 * Kotlin wrapper for an optional native FFT implementation.
 *
 * To enable native FFT:
 * - Provide a native library named `fftnative` with a JNI function matching:
 *   Java_com_example_audioanalyser_NativeFFT_nativeFft(JNIEnv*, jclass, jdoubleArray real, jdoubleArray imag, jint n)
 * - Build and include the library in your APK for each target ABI.
 *
 * If the native library is not present, `available` will be false and callers
 * should fall back to the managed FFT implementation.
 */
object NativeFFT {
    var available: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("fftnative")
            available = true
        } catch (t: Throwable) {
            available = false
        }
    }

    external fun nativeFft(real: DoubleArray, imag: DoubleArray, n: Int)
}

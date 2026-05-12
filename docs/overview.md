# Overview

This project is a small Android app that reads microphone audio, computes sound level (dB) and a frequency spectrum, and visualizes them in real time using Jetpack Compose.

Architecture (high level)

- Audio capture: `AudioAnalyzer` uses `AudioRecord` to read PCM 16-bit mono samples.
- Preprocessing: RMS -> dB conversion, Hanning window applied to the frame.
- Analysis: An in-JVM FFT converts the windowed samples to frequency magnitudes.
- Smoothing: a simple exponential smoothing is applied to reduce flicker.
- UI: Jetpack Compose consumes `StateFlow` values exposed by `AudioAnalyzer` and renders a db meter and frequency visualizer.

Key components

- `AudioAnalyzer.kt` — core audio capture and analysis (sampleRate=44100, fftSize=1024, smoothingFactor=0.2, dbOffset=50f, noiseFloor=10f).
- `MainActivity.kt` — Compose UI, permission flow and lifecycle hooks (starts/stops analyzer).
- Compose components: `DbMeterCard`, `VisualizerCard`, `FrequencyVisualizer`, `HistorySparkline`, and a Mixing Info dialog.

Data flow

1. Read `fftSize` samples from `AudioRecord` into a `ShortArray`.
2. Compute RMS and convert to dB (apply calibration offset).
3. Apply Hanning window and compute FFT magnitudes for the frame.
4. Smooth magnitudes and emit via `MutableStateFlow`.
5. UI collects flows and redraws Compose canvases.

Lifecycle notes

- The app requests `RECORD_AUDIO` permission on startup; without permission the UI shows a grant button.
- `startAnalyzing()` is launched from a Compose `LaunchedEffect`, and `stop()` is called from `DisposableEffect.onDispose`.

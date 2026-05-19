# Overview

This project is a small Android app that reads microphone audio, computes sound level (dB) and a frequency spectrum, and visualizes them in real time using Jetpack Compose.

Architecture (high level)

- Audio capture: `AudioAnalyzer` uses `AudioRecord` to read PCM 16-bit mono samples.
- Preprocessing: RMS -> dB conversion, Hanning window applied to the frame.
- Analysis: An in-JVM FFT converts the windowed samples to frequency magnitudes.
- Smoothing: a simple exponential smoothing is applied to reduce flicker.
- UI: Jetpack Compose consumes `StateFlow` values exposed by `AudioAnalyzer` and renders a db meter and frequency visualizer.
- Shared core: low-risk platform-neutral analyzer helpers now live in the `:shared` Kotlin Multiplatform module so future iOS work can build on the same math.
- Shared models: feedback peaks, target-curve models, EQ references, and target-curve constants can now be reused from `:shared` across Android and future iOS code.
- Shared DSP state machine: feedback-peak detection/tracking now lives in `:shared` so Android and iOS can reuse the same ranking and decay behavior.

Key components

- `AudioAnalyzer.kt` — core audio capture and analysis (sampleRate=44100, fftSize=1024, smoothingFactor=0.2, dbOffset=50f, noiseFloor=10f).
- `MainActivity.kt` — Compose UI, permission flow and lifecycle hooks (starts/stops analyzer).
- `shared/src/commonMain/.../AnalyzerMath.kt` — shared RMS and weighting helpers extracted as the first multiplatform migration slice.
- `shared/src/commonMain/.../AnalyzerModels.kt` — shared feedback and target-curve data models extracted for future iOS reuse.
- `shared/src/commonMain/.../FeedbackPeakTracker.kt` — shared feedback-peak tracking algorithm extracted from `AudioAnalyzer.kt`.
- Compose components: `DbMeterCard`, `VisualizerCard`, `FrequencyVisualizer`, `HistorySparkline`, and a Mixing Info dialog.

New features

- Built-in Signal Generator: `SignalGenerator.kt` provides SINE, WHITE, PINK, and PULSE playback modes via `AudioTrack`. This is accessible from Tools → Signal generator.
- Weighted SPL readings: `AudioAnalyzer.kt` now computes A-, C-, and Z-weighted values and exposes them as `dbLevelA`, `dbLevelC`, `dbLevelZ` `StateFlow`s consumed by the UI.
- Waterfall (Spectrogram): recent FFT frames are stored in a shared `SpectrogramHistoryBuffer`, surfaced on Android through the `SpectrogramBuffer` `StateFlow` wrapper, and rendered by `WaterfallVisualizer.kt`. The waterfall uses dB scaling with percentile-based autoscaling and an inferno-like colormap for better visibility.
- Waterfall settings (min/max percentile, gamma) are adjustable from the Tools sheet and persisted in `SharedPreferences`.

Data flow additions

1. Smoothed spectrum frames are copied into the shared `SpectrogramHistoryBuffer` through `SpectrogramBuffer`, then emitted as a `StateFlow<List<FloatArray>>` for the UI.
2. The waterfall visualizer converts magnitudes to dB, performs percentile autoscaling, applies gamma correction, and maps values to a perceptual colormap before blitting into a bitmap for efficient drawing.

Data flow

1. Read `fftSize` samples from `AudioRecord` into a `ShortArray`.
2. Compute RMS and convert to dB (apply calibration offset).
3. Apply Hanning window and compute FFT magnitudes for the frame.
4. Smooth magnitudes and emit via `MutableStateFlow`.
5. UI collects flows and redraws Compose canvases.

Lifecycle notes

- The app requests `RECORD_AUDIO` permission on startup; without permission the UI shows a grant button.
- `startAnalyzing()` is launched from a Compose `LaunchedEffect`, and `stop()` is called from `DisposableEffect.onDispose`.

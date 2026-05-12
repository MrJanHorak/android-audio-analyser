# Development

Where to change analysis parameters

- `AudioAnalyzer.kt` contains constants at the top you can tune:
  - `sampleRate` (currently 44100)
  - `fftSize` (currently 1024)
  - `dbOffset` (calibration offset, currently 50f)
  - `noiseFloor` (values below this are ignored, currently 10f)
  - `smoothingFactor` (visual smoothing, currently 0.2f)

New development notes

- `SignalGenerator.kt` implements audio playback via `AudioTrack`. Modes and playback code live here — adjust buffer sizes or generator algorithms as needed.
- `SpectrogramBuffer.kt` stores a ring of recent FFT magnitude frames and exposes them as a `StateFlow<List<FloatArray>>` consumed by the UI.
- `WaterfallVisualizer.kt` converts magnitudes to dB, computes percentile-based autoscaling, applies gamma correction, and maps values through an inferno-like colormap. For performance it writes pixels into an Android `Bitmap` and blits that via Compose's `drawImage` to the canvas.
- Preferences: waterfall settings are persisted in `SharedPreferences` with keys `waterfall_min_percentile`, `waterfall_max_percentile`, and `waterfall_gamma`.

Performance tips

- The waterfall renderer uses a bitmap-backed approach which is faster than per-rect drawing for larger histories. If you need extra performance on low-end devices:
  - Reduce `SpectrogramBuffer` history size (default 128).
  - Lower `drawCols` (in `WaterfallVisualizer`) to reduce horizontal resolution.
  - Consider using a native FFT implementation (`NativeFFT`) or JTransforms for larger `fftSize` values.

Testing

- For waterfall tuning, capture sample frames and log percentile ranges to validate defaults across devices (device microphone characteristics vary widely).
- Signal generator verification: use a second device or mic to confirm output frequency and level. The generator includes sine/pink/white options for common verification tasks.

Performance and optimizations

- The current FFT implementation is recursive and written in Kotlin. For lower CPU/battery use or larger FFT sizes, consider:
  - Using an iterative FFT implementation to reduce recursion overhead.
  - Using a native FFT library (NDK) or a tested Java FFT implementation (e.g., JTransforms) for performance.
  - Offloading heavy processing to a dedicated worker thread or native code.

Testing

- There are no unit tests in the repository yet. Suggested unit tests:
  - `calculateRMS` with synthetic sample arrays.
  - `applyHanningWindow` produces expected windowed outputs.
  - `calculateFFT` on known waveforms (sine at 440Hz) to assert dominant bin.

Code style and formatting

- Use Kotlin formatting tools and linting as per your standard workflow. Run `./gradlew ktlintCheck` or your preferred formatters if configured.

Lifecycle notes

- Analyzer lifecycle is controlled by Compose effects in `MainActivity.kt` (`LaunchedEffect` + `DisposableEffect`) — keep that pattern when moving analyzer usage into other components.

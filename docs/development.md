# Development

Where to change analysis parameters

- `AudioAnalyzer.kt` contains constants at the top you can tune:
  - `sampleRate` (currently 44100)
  - `fftSize` (currently 1024)
  - `dbOffset` (calibration offset, currently 50f)
  - `noiseFloor` (values below this are ignored, currently 10f)
  - `smoothingFactor` (visual smoothing, currently 0.2f)

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

# AudioAnalyser

Android app that captures microphone audio, computes dB and frequency spectrum, and visualizes levels and a simple frequency analyzer using Jetpack Compose.

## Features

- Real-time sound level (dB) meter with min/avg/max and history sparkline.
- Frequency spectrum visualizer (log-scaled bands for bass/mids/highs).
- Hanning window + custom FFT implementation and smoothing for stable visuals.
- Orientation-aware UI (portrait/landscape) built with Jetpack Compose.

## Quickstart

Prerequisites

- JDK 11 or later
- Android SDK (matching the project's `compileSdk`)
- Android device or emulator (min SDK 24)

Open in Android Studio (recommended)

- File → Open... → select the project root and let Android Studio sync Gradle.

Command line (Windows)

```powershell
# Build debug APK
.\gradlew.bat assembleDebug

# Install (device or running emulator required)
.\gradlew.bat installDebug
```

Command line (macOS / Linux)

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Permissions

- The app requires microphone access. The permission is declared in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml#L1): `android.permission.RECORD_AUDIO`.

## How it works (short)

- Audio is captured via `AudioRecord` (PCM 16-bit, mono).
- RMS is converted to dB and calibrated by a `dbOffset` (see `AudioAnalyzer`).
- A Hanning window is applied, then an in-JVM FFT is computed to produce magnitudes.
- Magnitudes are smoothed and exposed as `StateFlow` for Compose UI to render.

## Files of interest

- [MainActivity.kt](app/src/main/java/com/example/audioanalyser/MainActivity.kt#L1) — Compose UI, permission flow, lifecycle hooks.
- [AudioAnalyzer.kt](app/src/main/java/com/example/audioanalyser/AudioAnalyzer.kt#L1) — audio capture, FFT and smoothing logic.
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml#L1) — declares microphone permission and app metadata.
- [app/build.gradle.kts](app/build.gradle.kts#L1) — module Gradle config (minSdk, targetSdk, Compose enabled).
- [gradle/libs.versions.toml](gradle/libs.versions.toml#L1) — dependency versions and plugin refs.

## Development notes

- `AudioAnalyzer.startAnalyzing()` is a `suspend` function that runs on `Dispatchers.IO`. The UI calls it from a `LaunchedEffect` and calls `stop()` in `DisposableEffect.onDispose`.
- The FFT implementation is a straightforward recursive Cooley–Tukey algorithm in Kotlin. For high performance or lower battery use, consider using an optimized FFT library or native code (NDK).
- Calibration offset (`dbOffset`) and `noiseFloor` are configurable constants in `AudioAnalyzer`.

## Dependencies

The project uses AndroidX and Jetpack Compose. See [gradle/libs.versions.toml](gradle/libs.versions.toml#L1) for versions (AGP, Kotlin, Compose BOM).

## Contributing

- Open an issue or PR. Keep changes focused and run `./gradlew build` locally.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

© 2026 MrJanHorak

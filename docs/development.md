# Development

## Android analysis and capture

- `app/src/main/java/com/example/audioanalyser/AudioAnalyzer.kt` owns Android `AudioRecord` capture, input routing, per-input settings, level publication, feedback tracking, and CSV logging.
- Capture uses 44,100 Hz mono PCM. Fast mode uses 1,024-sample frames; Detailed mode uses 4,096-sample frames. Frames are collected before analysis, so Detailed updates less often.
- The default uncalibrated offset is 30 dB and the default noise-gate threshold is 25 dB. Built-in input offsets and gates can override those defaults; external and unidentified inputs use temporary values. See [usage](usage.md#android-measurement-guidance) for the limitations.
- `shared/src/commonMain/.../AnalyzerFrameAnalyzer.kt` applies a Hann window and calculates spectrum magnitudes, frequency, and weighting adjustments. Android requests the iterative native FFT in `app/src/main/cpp/fftnative.cpp`, with the shared managed implementation as a fallback.
- The Android meter offers Live and Steady display responses. `LevelSmoother.kt` implements a time-based, one-second exponential average in linear power for each A/C/Z display value; raw frame values remain in the logger, statistics, calibration, and feedback logic.
- Spectrum magnitudes use a smoothing factor of 0.2. Feedback candidates are tracked by `shared/.../FeedbackPeakTracker.kt`; they are suggestions, not confirmed feedback.

## Waterfall and display

- `SpectrogramBuffer.kt` wraps the shared ring buffer in a `StateFlow`. Android stores every analysed frame and publishes history snapshots at most every 100 ms. Detailed spectra are reduced to 512 bins for waterfall history.
- `WaterfallVisualizer.kt` converts magnitudes to dB, computes percentile-based scaling, applies gamma and a colour palette, and renders a compact bitmap on a background dispatcher. Compose scales the bitmap for display.
- Waterfall percentile, gamma, and colour settings are stored in `SharedPreferences`. Input mode, frequency detail, and portrait/landscape compact-meter preferences are also persisted locally. Device-specific input profiles are not transferred in backups.
- Android capture and generator playback stop when the activity pauses. Capture restarts when the activity resumes unless the user paused it. Verify lifecycle and routing behaviour on physical devices.

## Venue and reference safeguards

- Android `BackupValidation.kt` validates the supported version, preference types and saved curve data before the import confirmation can replace anything. The file reader limits input size. Unit tests cover malformed files and duplicate or incomplete records; exercise the confirmation and file picker on a device as well.
- The Android room reference preview uses relative dB and is intentionally separate from the auto-scaled live spectrum; the iOS spectrum also does not overlay a target. Reintroducing an overlay requires a common calibrated scale and clearly documented reference alignment. Existing venue records still include a reference shape even when the user only enters notes.
- Android stores up to 20 named single-frame spectrum snapshots with capture time, input label, requested input mode, frequency resolution, position and notes. `SavedSpectra.kt` validates serialized snapshots; version-2 backups include them, while importing version 1 leaves saved snapshots intact. The active overlay clears on capture restart; matching frequency detail is required to restore it. Input names are not stable microphone identities. Time-averaged capture and structured per-source venue records remain future work. Validate the storage, dialogs and migration on a device.

## Testing

Run the shared JVM tests, Android local unit tests, and Android debug build:

```bash
bash ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug
```

Shared tests cover analyser math, frequency analysis, feedback tracking, formatting, and history storage. Android local tests include meter smoothing and formatting, input profile keys, CSV formatting, and waterfall publication. These do not replace testing microphone routing, capture, UI, and playback on an Android device. See [IDE testing](ide-testing.md) and the [Android device checks](usage.md#android-device-checks).

Use known test signals and reference equipment to evaluate frequency and level measurements. Phone microphones and audio processing vary; a single offset does not make a phone a certified sound-level meter.

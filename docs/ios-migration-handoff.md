# iOS Migration Handoff

This is a historical migration record. For current features, measurement limitations, and toolchain requirements, see [usage](usage.md) and [dependencies](dependencies.md).

**Status:** phase 1 has started. The repository now has a first `:shared` Kotlin Multiplatform module and a native SwiftUI iOS shell in `ios/`. Android Gradle validation and the iOS simulator build both complete successfully in the local toolchains, the SwiftUI shell imports shared Kotlin code, live microphone capture runs through an `AVAudioEngine` input tap, both platforms now share the same per-frame FFT/weighting/dominant-frequency analysis, shared feedback presentation, shared target-curve helpers, shared formatting rules, shared spectrogram-history ring-buffer logic, and iOS now renders a richer live analyzer UI with denser Android-like spectrum guides, saved venue curve recall/editing, spectrum snapshot compare, adjustable waterfall palette/percentile/gamma controls, optional big-graph mode, CSV logging/export, versioned JSON analyzer sample export, dedicated waterfall-session export, full signal-generator mode parity, a native delay calculator with ping auto-measure, and a Markdown session report.

## What landed in this phase

- Added `:shared`, a multiplatform module with:
  - Android target via `com.android.kotlin.multiplatform.library`
  - `jvm()`
  - `iosX64()`
  - `iosArm64()`
  - `iosSimulatorArm64()`
- Extracted the first platform-neutral analyzer helpers into `shared/src/commonMain`:
  - RMS calculation
  - A-weighting
  - C-weighting
  - feedback cut-frequency suggestion
- Extracted shared domain/data models into `shared/src/commonMain`:
  - `FeedbackPeak`
  - `TargetCurvePoint`
  - `CustomTargetCurveSettings`
  - `SavedTargetCurvePreset`
  - `EqReferenceEntry`
  - shared target-curve constants/options
- Extracted the feedback-peak tracking state machine into `shared/src/commonMain/FeedbackPeakTracker.kt` so Android and iOS code can reuse the same peak-matching and decay behavior.
- Added shared tests in `shared/src/commonTest` to lock the current helper behavior before larger extractions.
- Added `ios/` with an XcodeGen-backed SwiftUI shell that can be generated locally into `SoundRunner.xcodeproj`.
- Added XCFramework generation for `Shared` and scripts to prepare/link it into the iOS shell:
  - `scripts/prepare-ios-framework.sh`
  - `scripts/build-ios-shell.sh`
- Wired the SwiftUI shell to import `Shared` and call `AnalyzerMath.shared.suggestCutFrequency(...)`.
- Added the first iOS-only platform seam scaffolding in Swift:
  - microphone permission state via `AVAudioApplication`
  - measurement-mode audio-session configuration via `AVAudioSession`
  - explicit denied/open-settings handling in the SwiftUI shell
- Added the first live iOS audio path in Swift:
  - `AVAudioEngine` microphone capture with an input tap
  - audio-session activation only while capture is running
  - throttled live level / sample-rate / frame-length reporting in SwiftUI
  - shared Kotlin dB conversion helpers now reused by both Android and iOS
- Added shared per-frame analyzer DSP in `shared/src/commonMain/AnalyzerFrameAnalyzer.kt`:
  - windowing + managed radix-2 FFT
  - magnitude generation
  - dominant-frequency detection
  - A/C weighting adjustments from spectral power
- Rewired Android to use the shared analyzer while preserving the optional JNI `NativeFFT` fast path through a shared FFT processor hook.
- Rewired iOS live capture to run the same shared frame analyzer on throttled snapshots so the shell now shows live Z/A/C levels and dominant frequency.
- Added the first real iOS analyzer UI in SwiftUI:
  - metric cards for live Z/A/C, dominant frequency, suggested cut, sample rate, frame size, and channel count
  - a compact Canvas-based live spectrum driven by shared analyzer magnitudes
  - a cleaner separation between operational status rows and actual analyzer output
- Wired shared `FeedbackPeakTracker` into the iOS analyzer loop:
  - added Swift/Kotlin interop-friendly no-arg construction for `FeedbackPeakTracker`
  - updated iOS feedback tracking on each analyzed frame while keeping UI publication throttled
  - surfaced the top live feedback-cut suggestions in the SwiftUI analyzer screen
- Expanded the iOS analyzer UI toward Android parity with:
  - live min/avg/max dB(Z) stats that ignore gated zero readings
  - a recent dB(Z) history sparkline
  - a compact spectrum-history waterfall built from normalized log-binned spectrum bars
- Added the next analyzer display-parity slice on iOS with:
  - adjustable waterfall min/max percentile controls
  - adjustable waterfall gamma control
  - Android-matched inferno / viridis / plasma / magma waterfall palettes
  - an optional FOH-style big graph mode that keeps the graph view more prominent
- Added native iOS saved venue-curve parity with Android by:
  - persisting saved target-curve presets in the same JSON field shape Android uses
  - showing saved curve names/venue recall details directly in the iOS room-curve selector
  - adding a native SwiftUI saved-curve manager for create/edit/delete workflows
- Added iOS analyzer-tool parity with Android for:
  - saved spectrum snapshots that can overlay a matching live spectrum as a cyan reference line
  - in-memory decibel logging with CSV export/share using the same `Timestamp,dB(Z),dB(A),dB(C),DominantFreq(Hz)` column set
  - automatic log finalization when live capture stops so the current session can still be exported
- Added the next iOS analyzer-tool slice with:
  - a shared `FeedbackPeakPresentation.stabilityKey(...)` helper in `:shared` so Android and iOS use the same feedback-tier thresholds
  - configurable live spectrum feedback markers (top 1 / top 3, hide/show) driven by shared feedback peaks
  - a versioned JSON analyzer sample export that captures current live analyzer state, history, feedback display state, and optional saved snapshot metadata
- Added the next saved-session + helper-extraction slice with:
  - a dedicated waterfall-session JSON export on iOS with per-row timestamps and full-precision normalized waterfall rows
  - a shared `TargetCurveHelpers.kt` file for default target-curve settings, saved-target-curve display/description helpers, and normalized saved target-curve point generation
  - Android app call sites updated to consume the shared target-curve helpers instead of keeping that pure logic app-side
- Added the final non-blocked helper/export slice with:
  - a shared `AnalyzerTextFormatter` in `:shared` for deterministic frequency, dB, and delta formatting across Android and iOS
  - Android wrappers updated to delegate formatting through the shared formatter
  - iOS label rendering updated to use the shared formatter (with iOS-specific `--` handling for missing frequencies)
  - a Markdown session report export on iOS for human-readable sharing of live metrics, feedback suggestions, snapshot state, and waterfall-session availability
- Tightened the iOS spectrum presentation toward Android parity by:
  - switching the graph overlays, target curve, and tick marks to a shared log-frequency display axis
  - adding denser in-graph frequency labels/grid lines
  - extending target-curve rendering cleanly across the graph width with visible control-point markers
- Added a native iOS signal-generator tool in Swift with:
  - sine, white-noise, pink-noise, pulse, and sweep playback modes
  - a playback-only AVAudioEngine path so it does not require microphone permission by itself
  - capture coordination so starting live analysis stops playback first instead of fighting the mic session
- Added a native iOS delay/alignment tool in Swift with:
  - manual distance/temperature delay calculation controls
  - a separate short-lived `playAndRecord` ping-measurement path so it does not destabilize the normal analyzer/generator flows
  - polarity readout and measured-delay backfill into the distance control
  - capture-engine rebuild after measurement so the next live analyzer start negotiates a fresh input format
- Split spectrogram history storage from Android publication by:
  - adding `shared/src/commonMain/.../SpectrogramHistoryBuffer.kt` as a pure ring buffer with oldest-to-newest snapshots
  - keeping Android `SpectrogramBuffer.kt` as the `StateFlow`-owning wrapper so current UI behavior and initial `emptyList()` semantics stay intact
  - preserving platform-side synchronization/publication while leaving shared storage non-reactive and non-thread-safe by design
- Validated the current checkpoints with:
  - `bash ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug`
  - `./scripts/build-ios-shell.sh`

## Migration decisions

- Keep **one repository** for Android, shared code, and iOS code.
- Keep Android shippable while shared code is extracted in small slices.
- Target a **native SwiftUI iOS shell** backed by the shared Kotlin core.
- Do **not** move these Android-only surfaces yet:
  - `MainActivity.kt`
  - `AudioAnalyzer` capture loop and `AudioRecord`
  - Android `SignalGenerator` / `AudioTrack` internals (iOS now has a separate native playback-only generator)
  - `WaterfallVisualizer.kt`
  - JNI / NDK FFT integration

## Current module boundaries

### `:app`

Still owns:

- Android UI
- permissions
- lifecycle
- preferences
- import/export/share flows
- Android audio capture and playback

### `:shared`

Owns the first pure helpers and storage primitives that can compile for Android, JVM, and iOS.

## Recommended next extraction order

1. Add more analyzer characterization tests before moving more logic.
2. Extract additional pure DSP helpers from `AudioAnalyzer.kt`.
3. Replace Compose `Color` in shareable model files with a neutral type.
4. Revisit larger shared analyzer-state publication only after an explicit coroutines/locking model is agreed.
5. If needed later, design shared-state abstractions/tests before moving more reactive buffer wrappers or other mutable analyzer state.

## Guardrails for future sessions

- Prefer **test first, then move**.
- Avoid touching `MainActivity.kt` unless the change is required to consume already-extracted shared code.
- Keep platform seams explicit:
  - audio capture/playback
  - preferences
  - file/document APIs
  - sharing/export
- Treat the native FFT path as an optimization, not as the first shared abstraction.

## Validation commands

```bash
bash ./gradlew :shared:jvmTest
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleDebug
```

If Gradle reports `Unable to locate a Java Runtime`, install JDK 21 or configure the JDK selected by the Gradle daemon toolchain.

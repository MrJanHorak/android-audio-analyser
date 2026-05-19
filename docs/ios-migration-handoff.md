# iOS Migration Handoff

**Status:** phase 1 has started. The repository now has a first `:shared` Kotlin Multiplatform module and a minimal native SwiftUI iOS shell in `ios/`. Android Gradle validation and the iOS simulator build both complete successfully in the local toolchains, and the SwiftUI shell now imports shared Kotlin code.

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
- Extracted the feedback-peak tracking state machine into `shared/src/commonMain/FeedbackPeakTracker.kt` so Android and future iOS code can reuse the same peak-matching and decay behavior.
- Added shared tests in `shared/src/commonTest` to lock the current helper behavior before larger extractions.
- Added `ios/` with an XcodeGen-backed SwiftUI shell that can be generated locally into `AudioAnalyser.xcodeproj`.
- Added XCFramework generation for `Shared` and scripts to prepare/link it into the iOS shell:
  - `scripts/prepare-ios-framework.sh`
  - `scripts/build-ios-shell.sh`
- Wired the SwiftUI shell to import `Shared` and call `AnalyzerMath.shared.suggestCutFrequency(...)`.
- Added the first iOS-only platform seam scaffolding in Swift:
  - microphone permission state via `AVAudioApplication`
  - measurement-mode audio-session configuration via `AVAudioSession`
  - explicit denied/open-settings handling in the SwiftUI shell
- Validated the current checkpoints with:
  - `bash ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug`
  - `./scripts/build-ios-shell.sh`

## Migration decisions

- Keep **one repository** for Android, shared code, and future iOS code.
- Keep Android shippable while shared code is extracted in small slices.
- Target a **native SwiftUI iOS shell** later, backed by the shared Kotlin core.
- Do **not** move these Android-only surfaces yet:
  - `MainActivity.kt`
  - `AudioAnalyzer` capture loop and `AudioRecord`
  - `SignalGenerator` / `AudioTrack`
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

Owns the first pure helpers that can compile for Android, JVM, and iOS.

## Recommended next extraction order

1. Add more analyzer characterization tests before moving more logic.
2. Revisit `SpectrogramBuffer` only after shared coroutines dependencies and multiplatform-safe synchronization are in place.
3. Extract additional pure DSP helpers from `AudioAnalyzer.kt`.
4. Replace Compose `Color` in shareable model files with a neutral type.
5. Start live microphone capture with `AVAudioEngine` and confirm buffer format/sample-rate behavior.
6. Begin the first iOS analyzer screen backed by shared logic.

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

If Gradle reports `Unable to locate a Java Runtime`, install or point `JAVA_HOME` at a JDK 17+ first.

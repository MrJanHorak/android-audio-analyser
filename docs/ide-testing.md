# IDE Testing Workflow

This document covers the current Android workflow and the current Xcode workflow for the in-repo iOS shell.

## Current status

- **Android Studio workflow:** runnable once a JDK 17+ and Android SDK are available.
- **Xcode workflow:** runnable now. The repo has an XcodeGen-backed SwiftUI shell, a linked shared Kotlin XCFramework, native microphone/audio-session scaffolding, a live `AVAudioEngine` capture path, shared frame analysis wired into the shell, a real live analyzer UI, and shared feedback-cut suggestions.

## Android Studio

### Prerequisites

- Android Studio
- JDK 17 or newer available to Gradle
- Android SDK matching the project's `compileSdk`
- An emulator or Android device for manual app testing

### Open and sync

1. Open the repository root in Android Studio.
2. Let Gradle sync the project.
3. Confirm both `:app` and `:shared` appear in the Gradle tool window.

### Useful Gradle tasks

```bash
bash ./gradlew :shared:jvmTest
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleDebug
```

### IDE test targets

- `shared/src/commonTest/.../AnalyzerMathTest.kt`
  - Verifies the first extracted shared analyzer helpers.
- `shared/src/commonTest/.../AnalyzerModelsTest.kt`
  - Verifies shared target-curve constants and model invariants used by both Android and future iOS code.
- `shared/src/commonTest/.../FeedbackPeakTrackerTest.kt`
  - Verifies shared feedback-peak tracking behavior, including matching, decay, and reset semantics.
- `app/src/test/...`
  - Android/JVM unit tests for app-only logic as more characterization coverage is added.
- `app/src/androidTest/...`
  - Device or emulator instrumentation tests.

### Manual Android smoke test

1. Run the app on a device or emulator with microphone permission available.
2. Confirm the dB meter still updates.
3. Confirm the dominant frequency and spectrum still respond to a known tone.
4. Confirm weighted readings still change when a tone source changes frequency.
5. Confirm signal generator, waterfall, snapshots, and export flows still open.

## Xcode

The repository now includes an XcodeGen-backed SwiftUI shell in `ios/`.

### Generate the project

```bash
./scripts/prepare-ios-framework.sh
cd ios
xcodegen generate
open AudioAnalyser.xcodeproj
```

### Current iOS shell validation

```bash
./scripts/build-ios-shell.sh
```

### Shared framework preparation

The `:shared` module now produces a `Shared.xcframework` and the helper script copies it into `ios/Frameworks/` before XcodeGen generation.

Example simulator framework task:

```bash
bash ./gradlew :shared:assembleSharedDebugXCFramework
```

### Xcode manual validation checklist

1. Run `./scripts/prepare-ios-framework.sh`.
2. Generate the project with `xcodegen generate`.
3. Open `ios/AudioAnalyser.xcodeproj` in Xcode.
4. Run on an iPhone simulator or device.
5. The current shell screen should open and show:
    - the shared Kotlin analyzer math status row
    - the microphone permission state
    - the configured measurement-mode audio-session summary
    - the live capture status row
    - a live analyzer section with metric cards and a compact spectrum area
    - a feedback suggestion area that can populate once stable peaks are present
6. Tap the microphone action buttons:
    - on first run, confirm the system permission prompt appears
    - after granting access, confirm the audio-session row updates
    - after denying access, confirm the button switches to the Settings path
    - after starting live capture, confirm the analyzer cards and spectrum begin updating with live data
7. Verify parity in this order:
    - live SPL meter
    - live spectrum
    - dominant frequency
   - snapshots/logging
   - signal generator
   - waterfall

### Current iOS smoke test

1. Run `./scripts/build-ios-shell.sh`.
2. Launch the iOS shell and confirm the shared Kotlin status row renders.
3. Confirm microphone permission prompt behavior.
4. Check that the audio session is configured for measurement-oriented capture.
5. Start live capture and confirm the UI reports:
    - a running live capture state
    - negotiated sample rate
    - changing frame size / live Z/A/C level text
    - dominant frequency updates
    - a changing live spectrum visualization
    - feedback suggestions appearing when a stable resonant peak is sustained
6. Feed a known test tone and compare:
    - dominant frequency
    - SPL trend
    - A/C/Z relative behavior
7. Confirm app behavior after route changes:
   - speakers
   - headphones
   - Bluetooth if supported later

## Troubleshooting

### `Unable to locate a Java Runtime`

Gradle needs a working JDK. Install JDK 17+ or set `JAVA_HOME` explicitly before running Gradle tasks.

### Android Studio sync issues after adding `:shared`

- Re-sync Gradle.
- Confirm the Kotlin Multiplatform plugin resolves successfully.
- If needed, run the shared tests first: `bash ./gradlew :shared:jvmTest`.

### Xcode project missing

- Run `./scripts/prepare-ios-framework.sh` first.
- Then run `cd ios && xcodegen generate`.
- Confirm `xcodegen` is installed: `xcodegen --version`.
- Then open `ios/AudioAnalyser.xcodeproj`.

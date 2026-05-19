# IDE Testing Workflow

This document covers the current Android workflow and the intended Xcode workflow for the future iOS shell.

## Current status

- **Android Studio workflow:** runnable once a JDK 17+ and Android SDK are available.
- **Xcode workflow:** preparatory only right now. The repo has shared iOS-compatible Kotlin code, but there is **no iOS app project yet**.

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
cd ios
xcodegen generate
open AudioAnalyser.xcodeproj
```

### Current iOS shell validation

```bash
cd ios
xcodegen generate
xcodebuild -project AudioAnalyser.xcodeproj -scheme AudioAnalyser -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

### Shared framework preparation

The `:shared` module already declares iOS targets and can generate frameworks for future Xcode integration.

Example simulator framework task:

```bash
bash ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

### Planned Xcode workflow

1. Generate the project with `xcodegen generate`.
2. Open `ios/AudioAnalyser.xcodeproj` in Xcode.
3. Run on an iPhone simulator or device.
4. The current shell screen should open and show the iOS port status.
5. After the next integration step, add or refresh the `Shared.framework` built from `:shared`.
6. Verify parity in this order:
   - live SPL meter
   - live spectrum
   - dominant frequency
   - snapshots/logging
   - signal generator
   - waterfall

### Planned iOS manual smoke test

1. Generate and open the project.
2. Launch the iOS shell and confirm the placeholder screen renders.
3. After audio wiring lands, confirm microphone permission prompt behavior.
4. Check that the audio session is configured for measurement-oriented capture.
5. Feed a known test tone and compare:
   - dominant frequency
   - SPL trend
   - A/C/Z relative behavior
6. Confirm app behavior after route changes:
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

- Run `cd ios && xcodegen generate`.
- Confirm `xcodegen` is installed: `xcodegen --version`.
- Then open `ios/AudioAnalyser.xcodeproj`.

# IDE Testing Workflow

This document covers the current Android Studio and Xcode workflows for the repo.

## Current status

- **Android:** full Android app workflow is runnable now.
- **iOS:** the SwiftUI shell is runnable now and uses the shared Kotlin analyzer core.

## Android Studio

### Prerequisites

- Android Studio
- JDK 17 or newer available to Gradle
- Android SDK matching `compileSdk = 36`
- Android emulator or device for manual testing

### Open and sync

1. Open the repository root in Android Studio.
2. Let Gradle sync.
3. Confirm both `:app` and `:shared` appear in the Gradle tool window.

### Useful Gradle tasks

```bash
bash ./gradlew :shared:jvmTest
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleDebug
```

### Useful test targets

- `shared/src/commonTest/.../AnalyzerMathTest.kt`
- `shared/src/commonTest/.../AnalyzerModelsTest.kt`
- `shared/src/commonTest/.../FeedbackPeakTrackerTest.kt`
- `shared/src/commonTest/.../AnalyzerTextFormatterTest.kt`
- `shared/src/commonTest/.../SpectrogramHistoryBufferTest.kt`
- `app/src/test/...`
- `app/src/androidTest/...`

### Manual Android smoke test

1. Run the app on a device or emulator.
2. Grant microphone permission.
3. Confirm the dB meter, weighted readings, dominant frequency, and spectrum update.
4. Confirm the waterfall fills in over time.
5. Confirm Android-only tools such as the signal generator still open and behave normally.

## Xcode

### Prerequisites

- macOS
- Xcode with an iOS 16 or newer simulator runtime, or a physical iPhone
- XcodeGen
- JDK 17+ or Android Studio so Gradle can build `Shared.xcframework`

### Generate and open the project

```bash
./scripts/prepare-ios-framework.sh
cd ios
xcodegen generate
open SoundRunner.xcodeproj
```

### Fast iOS shell build

```bash
./scripts/build-ios-shell.sh
```

That script prepares the shared XCFramework, regenerates the project, and performs an unsigned simulator build.

### Xcode manual validation checklist

1. Select the **SoundRunner** scheme.
2. Choose an iPhone simulator or a signed physical device.
3. If using a physical device, verify **Automatically manage signing** is enabled, a **Team** is selected, the bundle identifier is unique, **Developer Mode** is enabled on the device, and the developer certificate is trusted under **Settings > General > VPN & Device Management**.
4. Run the app.
5. If using the simulator, choose **Simulator > I/O > Audio Input** and select a Mac microphone before testing live capture.
6. Confirm the analyzer screen shows:
   - live metric cards
   - dominant frequency
   - recent dB(Z) history
   - spectrum display
   - compact spectrum-history waterfall
   - feedback suggestion area
   - analyzer tool controls
6. On first launch, verify the microphone permission prompt appears.
7. Start live capture and confirm:
   - the capture status changes to running
   - live Z/A/C readings update
   - dominant frequency changes with input
   - the spectrum and history views animate
   - feedback suggestions appear after a sustained peak
8. Exercise the iOS analyzer tools:
   - feedback marker mode
   - snapshot capture / clear / compare
   - CSV log export
   - analyzer-sample JSON export
   - waterfall-session JSON export
   - Markdown session report export

For the most realistic microphone testing, prefer a physical iPhone. The simulator is still appropriate for build and UI validation.

## Troubleshooting

### `Unable to locate a Java Runtime`

Install JDK 17+ or set `JAVA_HOME` explicitly before running Gradle tasks.

### Android Studio sync issues

- Re-sync Gradle.
- Confirm the Kotlin Multiplatform plugin resolves successfully.
- If needed, run `bash ./gradlew :shared:jvmTest`.

### Xcode project missing

- Run `./scripts/prepare-ios-framework.sh`.
- Run `cd ios && xcodegen generate`.
- Confirm `xcodegen --version` works.

### iOS framework appears out of date

Re-run:

```bash
./scripts/prepare-ios-framework.sh
cd ios
xcodegen generate
```

### Live capture fails in the simulator

- In Simulator, choose **I/O > Audio Input** and select a Mac microphone.
- Confirm macOS microphone privacy permissions allow **Simulator** to access input.
- Prefer a physical iPhone for reliable live-audio validation.

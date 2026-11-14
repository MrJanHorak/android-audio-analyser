# SoundRunner

Cross-platform audio-analyzer app in one repository:

- **Android app** in `app/` using Jetpack Compose
- **iOS app shell** in `ios/` using SwiftUI
- **Shared Kotlin Multiplatform core** in `shared/` for analyzer math, models, feedback logic, formatting, and spectrogram-history storage

Android remains the broader product surface today, while iOS now covers the live analyzer flow, overlay/curve guidance, saved venue curve recall, feedback hunt, adjustable waterfall and big-graph display controls, full generator-mode parity, delay/ping measurement, and the snapshot/export tooling from the same shared core.

## What you can run today

| Area | Android | iOS |
| --- | --- | --- |
| Live microphone capture | Yes | Yes |
| Shared FFT / weighted levels / dominant frequency | Yes | Yes |
| Shared feedback suggestions / formatting | Yes | Yes |
| Source overlays / room curves / saved venue recall / analyzer help | Yes | Yes |
| Spectrum history / waterfall | Yes | Yes |
| Snapshot compare | Yes | Yes |
| Session export tools | Android app flows | CSV, analyzer-sample JSON, waterfall-session JSON, Markdown report |
| Signal generator | Yes | Sine / white / pink / pulse / sweep |
| Delay calculator / ping auto-measure | Yes | Yes |

## Prerequisites

### Common

- Git
- JDK 21 for the configured Gradle daemon toolchain

### Android

- Android Studio
- Android SDK for API 36 (minor API 1), Android build tools, NDK, and CMake; see [toolchain requirements](docs/dependencies.md)
- Android emulator or physical device (min SDK 24)

### iOS

- macOS
- Xcode with an iOS 16 or newer simulator runtime, or a physical iPhone
- XcodeGen (`brew install xcodegen`)

`./scripts/prepare-ios-framework.sh` auto-detects Android Studio's bundled JDK on macOS if `JAVA_HOME` is not already set.

## Quick start

### Launch Android

1. Open the repository root in Android Studio.
2. Let Gradle sync.
3. Select the `app` run configuration.
4. Run on an emulator or device.
5. Grant microphone permission when prompted. Check the listening status and the routed input name before measuring. The meter is indicative and should be checked against a reference meter; see [Android measurement guidance](docs/usage.md#android-measurement-guidance).

Command line:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

### Launch iOS in Xcode

1. From the repository root, prepare the shared framework:

   ```bash
   ./scripts/prepare-ios-framework.sh
   ```

2. Generate the Xcode project:

   ```bash
   cd ios
   xcodegen generate
   ```

3. Open the generated project:

   ```bash
   open SoundRunner.xcodeproj
   ```

4. In Xcode:
    - choose the **SoundRunner** scheme
    - choose an iPhone simulator or a signed physical device
    - press **Run**
5. On a fresh install, tap the **play** button in the SoundRunner header once so iOS can show the microphone permission prompt, then grant microphone permission.

If you want to run on a physical iPhone or iPad, finish signing setup in Xcode first:

1. Open **Signing & Capabilities** for the **SoundRunner** target.
2. Turn on **Automatically manage signing**.
3. Choose your **Team**.
4. Replace the example bundle identifier `com.example.audioanalyser.ios` with your own unique identifier.
5. On the device, enable **Settings > Privacy & Security > Developer Mode** if it is not already enabled.
6. If iOS says the developer app is not trusted, open **Settings > General > VPN & Device Management**, select your developer certificate, and trust it.
7. If a prior install still fails to launch, delete the app from the device, choose **Product > Clean Build Folder** in Xcode, and run again.

If **SoundRunner** is missing from **Settings > Privacy & Security > Microphone**, the current install has not completed a microphone permission prompt yet. Launch the app first and tap the **play** button in the header once. If access was denied earlier, reopen it from **Settings > Privacy & Security > Microphone** and enable **SoundRunner** there. If it is still missing after a denied/failed install, delete the app from the device, reinstall it from Xcode, and request microphone access again.

If you test in the **iOS Simulator**, also choose **Simulator > I/O > Audio Input** and select a Mac microphone. Without a valid host audio input route, live capture can start but fail to negotiate a usable input format.

On a physical iPhone, the iOS shell now attempts to recover live capture automatically after microphone interruptions, input-route changes, and media-services resets. If iOS backgrounds the app, bring it back to the foreground before expecting capture to resume.

If you change shared Kotlin code, rerun `./scripts/prepare-ios-framework.sh` before rebuilding in Xcode so `ios/Frameworks/Shared.xcframework` stays in sync.

## One-command validation

### Android + shared module

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
bash ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug
```

### iOS shell

```bash
./scripts/build-ios-shell.sh
```

That script prepares the shared XCFramework, regenerates the Xcode project, and performs a simulator build without code signing.

## What to test manually

### Shared analyzer flow on both platforms

1. Grant microphone permission.
2. Start live capture from the **play** button in the header.
3. Confirm the live Z/A/C readings, dominant frequency, and spectrum respond to sound.
4. Confirm the recent-history views fill in over time.
5. Confirm feedback suggestions appear after a stable resonant peak.

### iOS-specific tools

1. Open **Tools** and switch source overlays, room curves, saved venue curves, feedback hunt, waterfall mode, waterfall palette/settings, and big graph mode.
2. Use the **Info** button to review the overlay, curve, and EQ guidance sheets.
3. Stop live capture, then start the sine / white / pink / pulse / sweep signal generator modes and confirm playback starts and stops from **Tools**.
4. Stop live capture, then use **Delay calculator** → **Auto-Measure with Ping** and confirm the delay and polarity update.
5. On a physical iPhone, interrupt or change the microphone route and confirm capture pauses cleanly and resumes after the route stabilizes.
6. Capture a spectrum snapshot and toggle compare mode.
7. Start and stop logging, then share the CSV.
8. Prepare an analyzer-sample JSON export.
9. Prepare a waterfall-session JSON export.
10. Prepare a Markdown session report.

### Android-specific tools

1. Open the signal generator.
2. Check the existing Android-only workflows that are not yet mirrored in iOS.

## Repository map

| Path | Purpose |
| --- | --- |
| `app/` | Android application, Compose UI, Android audio capture/playback, platform-specific tools |
| `shared/` | Kotlin Multiplatform analyzer core shared by Android and iOS |
| `ios/` | SwiftUI iOS shell and XcodeGen project spec |
| `docs/` | Architecture, usage, migration, testing, and dependency notes |
| `scripts/` | Helper scripts for preparing and building the iOS shell |

## Documentation map

- [ios/README.md](ios/README.md) — exact Xcode launch and smoke-test workflow
- [docs/ide-testing.md](docs/ide-testing.md) — Android Studio and Xcode test workflow
- [docs/usage.md](docs/usage.md) — current analyzer features and how to exercise them
- [docs/overview.md](docs/overview.md) — cross-platform architecture and module boundaries
- [docs/dependencies.md](docs/dependencies.md) — Gradle and native toolchain requirements
- [docs/ios-migration-handoff.md](docs/ios-migration-handoff.md) — migration history and remaining architectural follow-ups

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE).

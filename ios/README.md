# iOS App Shell

This directory contains the native SwiftUI iOS shell for the shared audio-analyzer core.

## Prerequisites

- macOS
- Xcode with an iOS 16 or newer simulator runtime, or a physical iPhone
- XcodeGen (`brew install xcodegen`)
- JDK 17+ or Android Studio installed so Gradle can build the shared XCFramework

## Launch in Xcode

From the repository root:

1. Prepare the shared framework:

   ```bash
   ./scripts/prepare-ios-framework.sh
   ```

2. Generate the Xcode project:

   ```bash
   cd ios
   xcodegen generate
   ```

3. Open the project:

   ```bash
   open AudioAnalyser.xcodeproj
   ```

4. In Xcode:
   - select the **AudioAnalyser** scheme
   - select an iPhone simulator or a signed physical device
   - press **Run**

5. On first launch:
    - tap **Start live capture** once so iOS can present the microphone prompt
    - grant microphone permission
    - start live capture from the analyzer screen

For a physical device, finish signing setup first. The generated project uses the example bundle identifier `com.example.audioanalyser.ios`.

1. Open **Signing & Capabilities** for the **AudioAnalyser** target.
2. Turn on **Automatically manage signing**.
3. Choose your Apple developer or personal team.
4. Replace `com.example.audioanalyser.ios` with your own unique bundle identifier.
5. On the device, enable **Developer Mode** under **Settings > Privacy & Security** if needed.
6. If iOS reports the developer app is not trusted, open **Settings > General > VPN & Device Management**, select your developer certificate, and trust it.
7. If the app installs but still fails to launch, delete it from the device, clean the build folder in Xcode, and run again.

If **AudioAnalyser** is not listed under **Settings > Privacy & Security > Microphone**, the current install has not completed a microphone permission prompt yet. Launch the app and tap **Start live capture** once. If access was denied earlier, re-enable it from **Settings > Privacy & Security > Microphone** and turn on **AudioAnalyser** there. If the app is still not listed, delete it from the device, reinstall it from Xcode, and request access again.

If you run in the **iOS Simulator**, also set **Simulator > I/O > Audio Input** to a Mac microphone or other host input device. If Simulator has no usable audio input route, the app can launch normally but live capture will fail.

On a physical iPhone, the app now tries to recover live capture automatically after microphone interruptions, input-route changes, and iOS media-services resets. If iOS suspends the app in the background, return it to the foreground before expecting capture to resume.

## Fast command-line build

```bash
./scripts/build-ios-shell.sh
```

This performs the same prep flow automatically:

1. builds `Shared.xcframework` from `:shared`
2. copies it into `ios/Frameworks/`
3. regenerates `AudioAnalyser.xcodeproj`
4. runs an unsigned simulator build with `xcodebuild`

## What is implemented in the iOS shell

- live microphone capture via `AVAudioEngine`
- automatic live-capture recovery after route changes, interruptions, and media-services reset events
- shared Kotlin frame analysis for Z/A/C levels and dominant frequency
- live metric cards and spectrum display
- source overlay selection with on-graph guide bands
- room-curve selection with on-graph target-curve overlay
- feedback-hunt controls and analyzer info/help sheet
- recent dB(Z) sparkline and compact spectrum-history waterfall
- shared feedback suggestion tracking and configurable feedback markers
- spectrum snapshot capture / compare
- CSV decibel log export
- analyzer-sample JSON export
- waterfall-session JSON export
- Markdown session report export

## Manual smoke test

1. Launch the app in Xcode.
2. Confirm the main analyzer screen opens.
3. Grant microphone permission.
4. Start live capture and verify:
    - live Z/A/C readings change
    - dominant frequency changes with a test tone or voice
    - the spectrum and history views update
    - source overlay bands and room-curve guide lines appear when selected in **Tools**
    - feedback suggestions appear after a stable tone when feedback hunt is enabled
5. Exercise the analyzer tools:
    - switch source overlays and room curves
    - toggle feedback hunt and waterfall mode
    - stop live capture, then start / stop the sine, white-noise, pink-noise, pulse, and sweep generator modes
    - stop live capture, then run **Delay calculator** → **Auto-Measure with Ping** and confirm the delay / polarity fields update
    - capture / clear a snapshot
    - toggle compare mode
    - start / stop logging and share the CSV
    - prepare each export type and confirm the share sheet appears
6. On a physical iPhone, lock/unlock the device or change the microphone route and confirm the analyzer pauses cleanly and resumes once the route is usable again.
7. Open the **Info** button and confirm the help sheet lists overlays, room curves, EQ starting points, and capture notes.

For microphone behavior, a physical iPhone is the most reliable test target. The simulator is still useful for launch/build validation and non-audio UI testing.

## When to regenerate

Regenerate the framework and Xcode project whenever one of these changes:

- Kotlin code in `shared/`
- `ios/project.yml`
- framework linkage or build settings

Typical refresh flow:

```bash
./scripts/prepare-ios-framework.sh
cd ios
xcodegen generate
```

## Troubleshooting

### `xcodegen: command not found`

Install it with Homebrew:

```bash
brew install xcodegen
```

### `JAVA_HOME is not set to a usable JDK`

Install JDK 17+ or Android Studio, then rerun `./scripts/prepare-ios-framework.sh`.

### Xcode opens but `Shared` symbols are stale or missing

Rerun:

```bash
./scripts/prepare-ios-framework.sh
cd ios
xcodegen generate
```

Then reopen `AudioAnalyser.xcodeproj`.

### Live capture fails in the simulator

1. In Simulator, choose **I/O > Audio Input** and select your Mac microphone.
2. In macOS **System Settings > Privacy & Security > Microphone**, allow access for **Simulator** if prompted.
3. Relaunch the app and try **Start live capture** again.

For reliable microphone testing, prefer a physical iPhone.

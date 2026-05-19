# iOS Shell

This directory contains the first native SwiftUI shell for the iOS port.

## Generate the Xcode project

```bash
./scripts/prepare-ios-framework.sh
cd ios && xcodegen generate
open ios/AudioAnalyser.xcodeproj
```

## Build from the command line

```bash
./scripts/build-ios-shell.sh
```

## Current scope

- A real Xcode-openable SwiftUI app shell.
- Shared Kotlin framework is linked through a generated XCFramework copy in `ios/Frameworks/`.
- Microphone permission and measurement-mode audio-session setup are scaffolded in native Swift.
- Live microphone capture now runs through an `AVAudioEngine` input tap.
- Shared frame analysis now drives live dominant-frequency and weighted-level status from iOS input.

## Next step

Render live spectrum / analyzer UI from the shared frame-analysis results and feed those results into iOS feedback tracking.

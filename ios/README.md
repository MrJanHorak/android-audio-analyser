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
- The first real iOS analyzer UI now renders live metric cards and a compact spectrum view from shared analysis results.
- Shared feedback tracking now surfaces live cut suggestions in the iOS analyzer UI.

## Next step

Expand the analyzer view with richer history/spectrum presentation and continue extracting remaining non-UI shared helpers.

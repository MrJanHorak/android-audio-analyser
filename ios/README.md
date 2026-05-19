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
- The iOS analyzer UI now renders live metric cards, min/avg/max level stats, a recent dB(Z) history sparkline, a compact spectrum view, and a spectrum-history waterfall from shared analysis results.
- Shared feedback tracking now surfaces live cut suggestions in the iOS analyzer UI.
- iOS can now capture a live spectrum snapshot, compare it against matching live input, and prepare/share CSV decibel logs from the analyzer session.
- iOS now adds richer analyzer tools: shared feedback-stability tiers, configurable spectrum feedback markers, and versioned JSON analyzer sample export.

## Next step

Expand saved-session tooling further and continue extracting remaining non-UI analyzer helpers into shared code.

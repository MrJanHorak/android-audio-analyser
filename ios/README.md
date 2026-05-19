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
- No audio capture yet.

## Next step

Start live microphone capture with `AVAudioEngine`, then feed those buffers into the shared analyzer pipeline.

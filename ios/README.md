# iOS Shell

This directory contains the first native SwiftUI shell for the iOS port.

## Generate the Xcode project

```bash
cd ios
xcodegen generate
open AudioAnalyser.xcodeproj
```

## Build from the command line

```bash
cd ios
xcodegen generate
xcodebuild -project AudioAnalyser.xcodeproj -scheme AudioAnalyser -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build
```

## Current scope

- A real Xcode-openable SwiftUI app shell.
- No audio capture yet.
- Shared Kotlin framework integration is intentionally left as the next checkpoint. When that work starts, extend `project.yml` with the framework search paths and Gradle/Xcode embed step.

## Next step

Wire the generated `Shared.framework` into the Xcode project and replace the placeholder build script with the Gradle/Xcode framework embed flow.

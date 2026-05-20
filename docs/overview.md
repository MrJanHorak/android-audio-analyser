# Overview

SoundRunner is now a cross-platform project with:

- an Android app in `app/`
- a SwiftUI iOS shell in `ios/`
- a Kotlin Multiplatform shared core in `shared/`

## Module boundaries

| Module | Responsibility |
| --- | --- |
| `app` | Android UI, permissions, lifecycle, Android audio capture/playback, Android-only tools |
| `ios` | SwiftUI shell, iOS permissions, `AVAudioEngine` capture, iOS-specific export/share flows |
| `shared` | Platform-neutral analyzer math, models, feedback logic, formatting, and spectrogram-history storage |

## Shared core

The shared module currently owns:

- analyzer math and weighting helpers
- per-frame analyzer DSP
- shared domain models
- feedback peak tracking
- feedback presentation thresholds
- target-curve helper logic
- deterministic analyzer text formatting
- `SpectrogramHistoryBuffer` ring-buffer storage

## Runtime flow

1. Platform audio capture records PCM samples:
   - Android uses `AudioRecord`
   - iOS uses `AVAudioEngine`
2. Each platform feeds audio frames into the shared analyzer core.
3. Shared code computes:
   - live spectrum magnitudes
   - dominant frequency
   - weighted levels
   - feedback candidates
4. Platform UI layers render the results:
   - Jetpack Compose on Android
   - SwiftUI on iOS
5. Platform-specific tools then handle sharing, permissions, lifecycle, and other OS integration work.

## Current feature split

### Shared across Android and iOS

- live microphone analysis
- dominant-frequency detection
- Z/A/C weighting
- live spectrum rendering inputs
- recent history / waterfall data source
- shared feedback suggestions and formatting

### Android still owns

- the broader mature tool surface
- signal generator playback
- deeper Android-only UI flows and settings

### iOS currently adds

- snapshot capture / compare
- CSV analyzer logging
- analyzer-sample JSON export
- waterfall-session JSON export
- Markdown session report export

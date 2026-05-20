# Usage

This guide describes the current analyzer workflow across Android and iOS.

## Shared analyzer workflow

Both platforms now share the same core analyzer logic for:

- FFT-based frame analysis
- dominant-frequency detection
- Z/A/C-weighted level calculations
- feedback peak tracking and stability tiers
- analyzer text formatting
- spectrogram-history ring-buffer storage

Typical flow:

1. Launch the app and grant microphone permission.
2. Start live capture.
3. Watch the live metric cards update:
   - dB(Z), dB(A), dB(C)
   - dominant frequency
   - feedback guidance
4. Use the recent-history views:
   - dB(Z) sparkline
   - live spectrum
   - compact spectrum-history waterfall

## iOS analyzer tools

The iOS shell currently adds these analyzer-session tools:

- **Spectrum snapshot** — capture the current spectrum and compare it against live input.
- **CSV log export** — export `Timestamp,dB(Z),dB(A),dB(C),DominantFreq(Hz)`.
- **Analyzer-sample JSON** — export a structured snapshot of the current analyzer state.
- **Waterfall-session JSON** — export the waterfall history with timestamps.
- **Markdown session report** — export a human-readable summary of the live session.

## Android-focused tools

Android still has the broader app surface, including Android-specific tool flows such as:

- signal generator playback
- richer waterfall tuning controls
- the rest of the established Android UI flows that have not yet been ported to iOS

## Measurement notes

- Phone microphones vary. For precise SPL work, compare against a reference meter or use a calibrated external microphone.
- Weighted readings are still useful for relative analysis even when absolute SPL calibration varies by device.
- For iOS microphone testing, a physical iPhone is the most reliable target.

## Interpreting the analyzer

- **Dominant frequency** helps identify the strongest detected tone or resonant peak.
- **Feedback suggestions** are more meaningful after a stable tone persists for a moment.
- **Min / Avg / Max** accumulate after non-zero readings so idle silence does not pollute the stats.
- **Waterfall history** makes changing resonance or sustained tones easier to see over time.

# Usage

Run the app

- Launch from Android Studio or install the debug APK onto a device/emulator.
- Grant microphone permission when prompted.

Interpreting the UI

- Sound Level: large dB readout shows the current calibrated decibel reading. Colors:
  - Green: safe
  - Amber (~80–90 dB): caution
  - Red (>90 dB): too loud
- Min / Avg / Max: historical statistics since the last reset.
- History Sparkline: recent dB values (historyLimit ≈ 100 samples).
- Frequency Spectrum: log-scaled bands split into bass / mids / highs. Labels on the visualizer show representative frequencies (60, 250, 1k, 4k, 16k).

Mixing & Feedback Guide

- The app includes a short "Mixing & Feedback" dialog accessed from the top-right info icon. It contains tips such as:
  - "Eliminating Feedback": look for sharp spikes in the Highs or Upper Mids and attenuate that band.
  - "Fixing 'Muddy' Sound": check Bass/Low-Mid (60–250Hz) and apply HPF if needed.
  - "Vocal Presence": vocals often sit in 1–4kHz; adjust mids accordingly.
  - "Safe Volume Levels": aim for ~75–85dB for general listening contexts; avoid sustained >90dB.

Calibration

- `dbOffset` in `AudioAnalyzer.kt` attempts to bring phone microphone readings closer to SPL values. Phone mics vary; for accurate SPL measurements use an external calibrated microphone or compare against a reference SPL meter.

Limitations

- The FFT is implemented in Kotlin and may be CPU-heavy on very old devices. Consider using a native or optimized FFT library for production.

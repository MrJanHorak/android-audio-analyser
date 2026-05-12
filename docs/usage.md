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

Signal generator

- Open Tools → Signal generator to play test tones and noise. Modes available:
  - `SINE`: selectable frequency (20 Hz–20 kHz).
  - `WHITE`: white noise useful for broadband testing.
  - `PINK`: pink noise for perceived-equal-energy testing.
  - `PULSE`: short pulses for delay/impulse tests (experimental).
- Always stop playback before moving microphones or making system changes.

Waterfall (spectrogram)

- Enable the waterfall from Tools → Waterfall (switch). The waterfall shows recent spectrum history top→bottom (old→new).
- Use the Waterfall settings sliders to adjust `Min percentile`, `Max percentile`, and `Gamma` to change the dynamic scaling:
  - `Min/Max percentile`: control which percentile of recent dB values map to the colormap range (helps ignore outliers).
  - `Gamma`: alters mid-range contrast (values <1 boost mid-tones).
- If the waterfall is too dark, increase `Max percentile` or reduce `Gamma`.

Weighted SPLs and calibration

- The meter shows calibrated dB and also displays dB(A), dB(C), and dB(Z) derived from the spectrum. Use `Settings` → `Open calibration` to set `dbOffset` and `noiseThreshold` to align readings against a reference SPL meter.


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

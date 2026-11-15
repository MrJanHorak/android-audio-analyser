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

On phones in portrait, tap **Compact** on the sound-level card to show a smaller meter and leave more space for the graph. Tap **Show details** to restore the statistics and history. The meter preference is remembered separately from the existing big-graph setting. On landscape phones, the meter starts compact and the A/C/Z buttons select which value appears in the main reading; **Show details** expands the meter. Scroll its details area to see the statistics and history while the current reading and A/C/Z buttons remain visible. Portrait and landscape compact preferences are stored separately; tablet layout remains unchanged.

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
- **Feedback suggestions** are more meaningful after a stable tone persists for a moment. Enable Feedback hunt from Tools, then tap **Guide** beside the graph for a step-by-step workflow. Scroll inside the guide for all four steps; its candidate is captured when the guide opens. A stable peak can still be music or ambient sound; confirm by ear.
- **Min / Avg / Max** accumulate after non-zero readings so idle silence does not pollute the stats.
- **Waterfall history** makes changing resonance or sustained tones easier to see over time. On Android, the display refreshes up to 10 times per second to reduce rendering load; the analyzer continues processing audio frames and storing history between display updates.

## Android measurement guidance

- The meter provides indicative readings, not a hearing-safety assessment. Its default offset is not a device calibration; recheck against a reference meter whenever the microphone or audio route changes. A single offset does not correct frequency response, processing, or clipping.
- Offsets are stored separately for each identified built-in microphone name and requested input mode. The status says whether an offset was saved; a saved offset is not a certification of accuracy. External or unidentified microphones use temporary offsets that reset when capture restarts or the route changes, because Android may reuse device names and IDs. Recalibrate those inputs after reconnecting or resuming.
- Earlier single global offsets are retained in preferences but are not automatically assigned to an input; recalibrate each built-in input mode. Backup files do not transfer device-specific profiles, and imported legacy offsets are kept unassigned.
- Min, max, and arithmetic average are Z-weighted frame readings. The average is not LAeq and does not represent an exposure dose. Paused readings remain frozen.
- Feedback candidates can also be musical notes or room resonances. Lower the level, confirm by ear, and use small EQ adjustments.
- The delay calculator uses the difference in distances to the listening position; apply its estimate to the nearer speaker. Automatic ping and polarity results have been removed from the Android interface pending a validated measurement method.
- Before using the generator, lower the mixer channel, check the output route, and increase the level gradually. Generator playback stops when the app leaves the foreground.
- Android capture stops when the app loses foreground focus; bring it back to resume. The status shows the routed input name when Android provides one, or “Input unknown” otherwise. A connected microphone is not necessarily the selected input; check the status after connecting or disconnecting it. Check the offset and recalibrate when changing input or reconnecting an external microphone.
- Calibration samples are discarded if capture stops, the reported input identity changes, or PCM clipping is detected during measurement. Android may not report every route change, so verify the input and repeat calibration after reconnecting a microphone.
- A clipping warning appears for two seconds after PCM samples approach full scale. Reduce the level at the microphone and recheck. Some microphones or Android processing can distort before reaching full scale, so the absence of a warning does not prove the signal is clean.
- If capture fails, use the play button to retry. The app keeps the last readings while paused or unavailable; those values are not live.

### Android device checks

Before using a new phone or microphone for an event:

1. Start capture, make a sound, and confirm the status says **Listening** and the readings change. Pause and resume, then background and reopen the app; check that capture restarts only when expected.
2. Connect and disconnect an external microphone, checking the input name and recalibrating against a reference meter after changes. A device may report a generic name or no name.
3. Check the clipping warning using a controlled input where available. Do not create unsafe sound levels just to trigger it. Check for distortion by ear as well.
4. Test microphone permission denial and recovery, and confirm the retry button works after an input error.
5. Verify the generator output route and start with the mixer channel down before raising the level.

### Microphone processing and quiet rooms (Android)

Some phones apply automatic gain or other microphone processing, which can raise the apparent level of quiet background noise. In **Settings → Microphone input**, Standard requests the normal Android microphone source. Measurement requests the unprocessed source when the phone reports support; otherwise it requests the voice-recognition source, which may apply less processing. These are requests: Android devices and routes vary, and neither setting guarantees that automatic gain is absent. The active status displays the requested mode. If Measurement cannot start capture, switch back to Standard.

Android does not expose a universal hardware microphone sensitivity or gain slider. The **Noise Gate** setting is stored separately for each identified built-in microphone and requested input mode. A mode without its own saved gate starts with the existing global threshold. External or unidentified inputs use a temporary gate that returns to that starting threshold after capture restarts or the route changes. Backups include the current gate as a starting threshold, but do not transfer individual input profiles. A gate hides entire frames below the chosen displayed level; it does not reduce microphone gain or remove noise from a louder signal. On the meter, an em dash or “Below range” means the reading is gated or below the display floor; it does not mean the room is silent. CSV logs use empty level cells and a `below_gate_or_floor` status for suppressed values; `available` means only that a reading was not suppressed, not that it is calibrated. Set it low enough that quiet speech and feedback are not hidden. The waterfall also auto-adjusts its colour scale, so background noise can look bright even when it is quiet. Changing input modes or microphones may change the level and frequency response: compare against a reference meter again.

### Frequency detail (Android)

In **Settings → Frequency detail**, Fast uses 1,024 samples at 44.1 kHz (about 43 Hz between FFT bins), while Detailed uses 4,096 samples (about 11 Hz). Detailed can separate low frequencies more clearly but updates roughly four times less often and may require more processing and battery. Bin spacing is not a guarantee of measurement accuracy, especially with phone microphones or nearby overlapping tones.

Changing this setting restarts capture and clears spectrum snapshots, feedback history, and the waterfall when capture restarts; it is disabled while CSV logging is active. In Detailed mode the waterfall combines groups of bins into its existing 512-column history to keep its rendering load manageable. External microphone offsets and gates are temporary and reset when capture restarts.

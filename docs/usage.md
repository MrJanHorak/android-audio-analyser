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

### Steadier sound-level display

The Android meter starts in **Steady** mode so that speech and other changing sounds are easier to read. To change it, tap **Settings** (the gear), find **Meter response**, and select **Steady** or **Live**. The meter shows the selected response beneath the main number.

- **Steady** smooths each A-, C-, and Z-weighted display using an approximately one-second energy response. It is not an exact average of the last second, and it can make brief changes less visible.
- **Live** displays each audio-frame reading and responds more quickly.
- Min, Avg, Max, the history line, calibration, and CSV logs continue to use frame readings. Min, Avg, Max, and history use Z weighting even when the large display is set to A or C. Max is not a true-peak measurement.
- The display remains an estimate and is not an exposure assessment or a validated standards-compliant sound-level meter. A saved offset does not confirm calibration.

The gate is applied to the smoothed meter independently of the live graph, so a Steady reading may fade briefly after the graph shows “No peak.” More detail is in [measurement guidance](#android-measurement-guidance) and [meter response](#meter-response-and-comparison-with-a-mixer).

Android still has the broader app surface, including Android-specific tool flows such as:

- signal generator playback
- richer waterfall tuning controls
- the rest of the established Android UI flows that have not yet been ported to iOS

## Measurement notes

- Phone microphones vary. Compare against a reference meter; for decisions requiring precise or certified SPL measurements, use suitable validated equipment.
- Weighted readings are still useful for relative analysis even when absolute SPL calibration varies by device.
- For iOS microphone testing, a physical iPhone is the most reliable target.

## Interpreting the analyzer

- **Dominant frequency** helps identify the strongest detected tone or resonant peak.
- **Feedback suggestions** are more meaningful after a stable tone persists for a moment. Enable Feedback hunt from Tools, then tap **Guide** beside the graph for a step-by-step workflow. Scroll inside the guide for all four steps; its candidate is captured when the guide opens. A stable peak can still be music or ambient sound; confirm by ear.
- **Min / Avg / Max** accumulate after non-zero readings so idle silence does not pollute the stats.
- **Waterfall history** makes changing resonance or sustained tones easier to see over time. On Android, the display refreshes up to 10 times per second to reduce rendering load; the analyzer continues processing audio frames and storing history between display updates.

## Venue notes, reference shapes, and snapshots

In Android **Tools → Room → Manage saved venue curves**, save a venue or event name and any useful details: console, speakers, room, problem frequencies, and free-form notes about sources, microphones, channels, and patching. These fields work with any mixer or sound system. All are notes for a person to recall; they do not save, import, or change settings on a mixer. Use **Add source notes template** to append optional source, microphone/input, channel/patch and settings prompts to General notes; repeat it for additional sources and edit the text as needed. You can save notes without editing the optional reference shape, although the record is still stored with a default shape. These are free-form notes, not separate structured channel records.

Room reference shapes are illustrative, not verified manufacturer or industry specifications. Android shows the selected shape separately in the Room tool; it is not drawn on the live spectrum because the two displays use different scales. It is not a measured room response or a mixer EQ curve. The iOS live graph also no longer superimposes the reference; its Room tool retains the description and saved venue notes. Neither platform currently provides a calibrated target comparison. Use an appropriate measurement system and microphone for system tuning.

Android **Settings → backup** exports saved venue curves, their notes, named snapshots, and selected app preferences. Import validates the backup and asks before replacing the current saved curves and included preferences; export the current data first if needed. Deleting a venue curve also asks for confirmation. Per-device microphone calibration profiles and the currently active temporary snapshot are not transferred. Version-2 backups replace named snapshots after confirmation; older version-1 backups leave them unchanged. The importer rejects unsupported or malformed files without replacing current data. Older app versions cannot import version-2 backups.

Android **Tools → Snapshot** captures the current smoothed spectrum frame for a visual comparison. Choose **Save named snapshot** to keep it on this device, with the capture time, reported microphone, requested input mode, frequency detail, and your optional position and notes. Up to 20 snapshots can be saved, exported in a backup, restored, compared, or deleted. Saving records the existing captured frame, not a new reading. The active comparison clears when capture restarts or frequency detail changes, while named snapshots remain saved.

These snapshots are single smoothed frames, not timed averages or calibrated room measurements. The graph shares a relative scale between the live frame and snapshot. For useful comparisons keep the same microphone, input mode, frequency detail, position, sound source and measurement conditions. The app requires matching frequency detail and warns when a reported input or input mode differs, but matching names do not prove the microphone, calibration, or room conditions are the same. Clear and recapture after an input route change. The iOS snapshot remains a session tool; use exports when you need a record.

Source focus bands and instrument tips are general listening starting points. The microphone hears the whole acoustic environment, so selecting a source does not isolate that instrument or mixer channel. Adjust only after identifying the source by ear; appropriate settings depend on the source, microphone, room and system. Feedback candidates can also be music or ambient sounds.

## Android measurement guidance

- The meter provides indicative readings, not a hearing-safety assessment. Its default offset is not a device calibration; recheck against a reference meter whenever the microphone or audio route changes. A single offset does not correct frequency response, processing, or clipping.
- Offsets are stored separately for each identified built-in microphone name and requested input mode. The status says whether an offset was saved; a saved offset is not a certification of accuracy. External or unidentified microphones use temporary offsets that reset when capture restarts or the route changes, because Android may reuse device names and IDs. Recalibrate those inputs after reconnecting or resuming.
- Earlier single global offsets are retained in preferences but are not automatically assigned to an input; recalibrate each built-in input mode. Backup files do not transfer device-specific profiles, and imported legacy offsets are kept unassigned.
- The meter defaults to **Steady**, an approximately one-second exponential response calculated in linear sound power, separately for A, C, and Z. Choose **Live** under Settings → Meter response to see each frame instead. This is a display aid, not LAeq, an exposure dose, or a validated standards-compliant sound-level meter. Short peaks can be less visible in Steady mode. Live and the raw-frame maximum respond faster, but the maximum is a frame RMS level, not a true-peak measurement, and may miss brief transients.
- Min, max, history and the arithmetic average are Z-weighted frame readings; CSV and calibration also use unsmoothed readings. The arithmetic average is not LAeq. Changing meter response does not change the spectrum or the feedback detector. Paused readings remain frozen.
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

Android does not expose a universal hardware microphone sensitivity or gain slider. The **Noise Gate** setting is stored separately for each identified built-in microphone and requested input mode. A mode without its own saved gate starts with the existing global threshold. External or unidentified inputs use a temporary gate that returns to that starting threshold after capture restarts or the route changes. Backups include the current gate as a starting threshold, but do not transfer individual input profiles. The gate hides frames or smoothed values below the chosen level; it does not reduce microphone gain or remove noise from a louder signal. On the meter, an em dash or “Below range” means the reading is gated or below the display floor; it does not mean the room is silent. CSV logs use empty level cells and a `below_gate_or_floor` status for suppressed values; `available` means only that a reading was not suppressed, not that it is calibrated. The Steady meter is smoothed before its gate, while the graph and feedback detector use frame-by-frame gating, so a fading steady value may remain briefly after the graph shows “No peak.” If the Z-weighted frame is gated or no nonzero peak is detected, the graph shows “No peak,” feedback candidates clear, and the CSV frequency cell is empty with an `unavailable` status. Set it low enough that quiet speech and feedback are not hidden. The waterfall also auto-adjusts its colour scale, so background noise can look bright even when it is quiet. Changing input modes or microphones may change the level and frequency response: compare against a reference meter again.

### Frequency detail (Android)

In **Settings → Frequency detail**, Fast uses 1,024 samples at 44.1 kHz (about 43 Hz between FFT bins), while Detailed uses 4,096 samples (about 11 Hz). Detailed can separate low frequencies more clearly but updates roughly four times less often and may require more processing and battery. Bin spacing is not a guarantee of measurement accuracy, especially with phone microphones or nearby overlapping tones.

Changing this setting restarts capture and clears spectrum snapshots, feedback history, and the waterfall when capture restarts; it is disabled while CSV logging is active. In Detailed mode the waterfall combines groups of bins into its existing 512-column history to keep its rendering load manageable. External microphone offsets and gates are temporary and reset when capture restarts.

### Meter response and comparison with a mixer

Steady mode uses an exponential average of linear power with a one-second time constant based on the duration of captured samples. It is not a five-frame arithmetic mean: the number of frames per second changes between Fast and Detailed frequency modes. The smoother starts from the first available frame and resets on capture restart, input-route changes, or offset changes. The setting is stored locally and is not included in backup files.

A mixing console's channel or bus meters show electrical signal levels at a particular point in the signal chain; an EQ screen shows the selected channel or bus filters and may also overlay an electrical signal spectrum. A phone microphone measures sound in the room. Their levels and curves cannot be used as interchangeable SPL measurements. For a meaningful acoustic level comparison, use a validated reference sound-level meter near the phone at the same time, using the same frequency weighting and comparable time response.

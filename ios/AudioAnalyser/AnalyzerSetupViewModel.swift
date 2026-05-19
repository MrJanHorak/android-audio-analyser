import Combine
import Foundation
import Shared

struct LiveAnalyzerSnapshot {
    let zLevelDb: Float
    let aLevelDb: Float
    let cLevelDb: Float
    let minZLevelDb: Float
    let avgZLevelDb: Float
    let maxZLevelDb: Float
    let dominantFrequencyHz: Float
    let suggestedCutFrequencyHz: Float
    let sampleRateHz: Double
    let frameLength: Int
    let channelCount: Int
    let zLevelHistory: [Float]
    let spectrumBars: [Float]
    let spectrumHistoryRows: [[Float]]
    let feedbackSuggestions: [LiveFeedbackSuggestion]
}

struct SavedSpectrumSnapshot {
    let capturedAt: Date
    let dominantFrequencyHz: Float
    let zLevelDb: Float
    let sampleRateHz: Double
    let frameLength: Int
    let spectrumBars: [Float]
}

struct ExportedAnalyzerLog {
    let fileURL: URL
    let rowCount: Int
    let exportedAt: Date
}

struct LiveFeedbackSuggestion: Identifiable {
    let frequencyHz: Float
    let suggestedCutHz: Float
    let normalizedLevel: Float
    let stability: Float
    let holdFrames: Int

    var id: String {
        "\(Int(frequencyHz.rounded()))-\(Int(suggestedCutHz.rounded()))"
    }
}

@MainActor
final class AnalyzerSetupViewModel: ObservableObject {
    @Published private(set) var microphonePermissionState: MicrophonePermissionState
    @Published private(set) var audioSessionSummary: String
    @Published private(set) var audioSessionDetail: String
    @Published private(set) var liveCaptureSummary: String
    @Published private(set) var liveCaptureDetail: String
    @Published private(set) var liveAnalyzerSnapshot: LiveAnalyzerSnapshot?
    @Published private(set) var savedSpectrumSnapshot: SavedSpectrumSnapshot?
    @Published private(set) var isSnapshotCompareRequested = false
    @Published private(set) var isLogging = false
    @Published private(set) var exportedAnalyzerLog: ExportedAnalyzerLog?
    @Published private(set) var logStatusMessage: String?
    @Published private(set) var isCapturing = false
    @Published private(set) var isWorking = false

    private let audioSessionController: AudioSessionController
    private let liveAudioCaptureController: LiveAudioCaptureController
    private let dbOffset: Float = 30
    private let noiseThreshold: Float = 25
    private let spectrumBarCount = 48
    private let zLevelHistoryLimit = 90
    private let spectrumHistoryLimit = 72
    private let fullSpectrumSmoothingFactor: Float = 0.2
    private let uiPublishIntervalNanoseconds: UInt64 = 100_000_000
    private var liveFrameAnalyzer: AnalyzerFrameAnalyzer?
    private var liveFeedbackTracker: FeedbackPeakTracker?
    private var reusableAnalysisInputBuffer: KotlinFloatArray?
    private var reusableTrackerSpectrumBuffer: KotlinFloatArray?
    private var smoothedSpectrum: [Float] = []
    private var zLevelHistory: [Float] = []
    private var spectrumHistoryRows: [[Float]] = []
    private var minObservedZLevelDb: Float = Float.greatestFiniteMagnitude
    private var maxObservedZLevelDb: Float = 0
    private var zLevelSum = 0.0
    private var zLevelSampleCount = 0
    private var loggedCsvRows: [String] = []
    private var loggedRowCount = 0
    private var lastUiPublishTimeNanoseconds: UInt64 = 0

    init(
        audioSessionController: AudioSessionController = .shared,
        liveAudioCaptureController: LiveAudioCaptureController? = nil
    ) {
        self.audioSessionController = audioSessionController
        self.liveAudioCaptureController = liveAudioCaptureController ?? LiveAudioCaptureController(
            audioSessionController: audioSessionController
        )
        let permissionState = audioSessionController.permissionState()
        microphonePermissionState = permissionState
        audioSessionSummary = ""
        audioSessionDetail = ""
        liveCaptureSummary = "Live capture is idle"
        liveCaptureDetail = "Start live capture to validate the negotiated AVAudioEngine input format on iOS."
        liveAnalyzerSnapshot = nil
        savedSpectrumSnapshot = nil
        exportedAnalyzerLog = nil
        logStatusMessage = nil
        bindCaptureCallbacks()
        refreshSetup()
    }

    var primaryActionTitle: String {
        switch microphonePermissionState {
        case .undetermined:
            return "Allow microphone access"
        case .denied:
            return "Open Settings"
        case .granted:
            return "Refresh audio setup"
        }
    }

    var captureActionTitle: String {
        isCapturing ? "Stop live capture" : "Start live capture"
    }

    var isCaptureActionDisabled: Bool {
        if isCapturing {
            return false
        }

        return microphonePermissionState != .granted || isWorking
    }

    var snapshotActionTitle: String {
        savedSpectrumSnapshot == nil ? "Capture snapshot" : "Refresh snapshot"
    }

    var isSnapshotCaptureDisabled: Bool {
        liveAnalyzerSnapshot == nil
    }

    var canToggleSnapshotCompare: Bool {
        guard savedSpectrumSnapshot != nil else {
            return false
        }

        return isSavedSpectrumSnapshotComparable
    }

    var canClearSnapshot: Bool {
        savedSpectrumSnapshot != nil
    }

    var isSnapshotCompareEnabled: Bool {
        isSnapshotCompareRequested && isSavedSpectrumSnapshotComparable
    }

    var snapshotOverlayBars: [Float]? {
        guard isSnapshotCompareEnabled else {
            return nil
        }

        return savedSpectrumSnapshot?.spectrumBars
    }

    var snapshotStatusText: String {
        guard let savedSpectrumSnapshot else {
            return "No snapshot saved yet. Capture one to overlay a cyan reference line on the live bars."
        }

        let summary = "Saved snapshot at \(frequencyString(savedSpectrumSnapshot.dominantFrequencyHz)) and \(decibelString(savedSpectrumSnapshot.zLevelDb)) on \(captureDateString(savedSpectrumSnapshot.capturedAt))."

        guard liveAnalyzerSnapshot != nil else {
            return "\(summary) Resume live capture to compare it against the current spectrum."
        }

        guard isSavedSpectrumSnapshotComparable else {
            return "\(summary) Compare is temporarily unavailable because the current live axis does not match the saved snapshot."
        }

        if isSnapshotCompareEnabled {
            return "\(summary) Snapshot compare is active. The cyan line shows the saved reference over the live bars."
        }

        return "\(summary) Compare is ready."
    }

    var loggingActionTitle: String {
        isLogging ? "Stop logging" : "Start logging"
    }

    var isLoggingActionDisabled: Bool {
        !isCapturing && !isLogging
    }

    var loggingStatusText: String {
        if isLogging {
            return "Logging timestamp, dB(Z), dB(A), dB(C), and dominant frequency at live analyzer cadence. Stop logging to prepare a CSV."
        }

        if let logStatusMessage {
            return logStatusMessage
        }

        return "Start live capture, then log a session to prepare a CSV you can share from iOS."
    }

    func captureSpectrumSnapshot() {
        guard let liveAnalyzerSnapshot else {
            return
        }

        savedSpectrumSnapshot = SavedSpectrumSnapshot(
            capturedAt: Date(),
            dominantFrequencyHz: liveAnalyzerSnapshot.dominantFrequencyHz,
            zLevelDb: liveAnalyzerSnapshot.zLevelDb,
            sampleRateHz: liveAnalyzerSnapshot.sampleRateHz,
            frameLength: liveAnalyzerSnapshot.frameLength,
            spectrumBars: liveAnalyzerSnapshot.spectrumBars
        )
        isSnapshotCompareRequested = true
    }

    func toggleSnapshotCompare() {
        guard savedSpectrumSnapshot != nil else {
            return
        }

        isSnapshotCompareRequested.toggle()
    }

    func clearSpectrumSnapshot() {
        savedSpectrumSnapshot = nil
        isSnapshotCompareRequested = false
    }

    func toggleLogging() {
        if isLogging {
            stopLoggingAndPrepareExport()
        } else {
            startLogging()
        }
    }

    func refreshSetup() {
        microphonePermissionState = audioSessionController.permissionState()

        if microphonePermissionState != .granted && isCapturing {
            stopLiveCaptureWithoutRefreshing()
        }

        switch microphonePermissionState {
        case .undetermined:
            audioSessionSummary = "Waiting for microphone authorization"
            audioSessionDetail = "The iOS analyzer session will stay inactive until permission is granted."
        case .denied:
            audioSessionSummary = "Microphone access is blocked"
            audioSessionDetail = "iOS will not prompt again after a denial. Use Settings to re-enable microphone access."
        case .granted:
            if !isCapturing {
                configureAudioSession()
            }
        }
    }

    func performPrimaryAction() {
        switch microphonePermissionState {
        case .undetermined:
            requestMicrophoneAccess()
        case .denied:
            audioSessionController.openAppSettings()
        case .granted:
            refreshSetup()
        }
    }

    func toggleLiveCapture() {
        if isCapturing {
            stopLiveCaptureWithoutRefreshing()
            refreshSetup()
        } else {
            startLiveCapture()
        }
    }

    private func requestMicrophoneAccess() {
        isWorking = true

        audioSessionController.requestMicrophoneAccess { [weak self] permissionState in
            DispatchQueue.main.async {
                guard let self else {
                    return
                }

                self.isWorking = false
                self.microphonePermissionState = permissionState

                switch permissionState {
                case .granted:
                    self.configureAudioSession()
                case .undetermined:
                    self.audioSessionSummary = "Waiting for microphone authorization"
                    self.audioSessionDetail = "Grant microphone access before starting live analysis."
                case .denied:
                    self.audioSessionSummary = "Microphone access is blocked"
                    self.audioSessionDetail = "Open Settings to allow microphone capture for the iOS analyzer."
                }
            }
        }
    }

    private func bindCaptureCallbacks() {
        liveAudioCaptureController.onSnapshot = { [weak self] snapshot in
            DispatchQueue.main.async {
                self?.applyCaptureSnapshot(snapshot)
            }
        }

        liveAudioCaptureController.onFailure = { [weak self] message in
            DispatchQueue.main.async {
                self?.handleCaptureFailure(message)
            }
        }
    }

    private func startLiveCapture() {
        guard microphonePermissionState == .granted else {
            liveCaptureSummary = "Microphone access is required"
            liveCaptureDetail = "Grant microphone permission before starting live input capture."
            return
        }

        do {
            let startInfo = try liveAudioCaptureController.startCapture()
            liveFrameAnalyzer = AnalyzerFrameAnalyzer(
                sampleRate: Int32(startInfo.inputFormatSampleRate.rounded()),
                fftSize: Int32(startInfo.analysisFrameSize)
            )
            liveFeedbackTracker = FeedbackPeakTracker()
            reusableAnalysisInputBuffer = KotlinFloatArray(size: Int32(startInfo.analysisFrameSize))
            reusableTrackerSpectrumBuffer = KotlinFloatArray(size: Int32(startInfo.analysisFrameSize / 2))
            smoothedSpectrum = Array(repeating: 0, count: startInfo.analysisFrameSize / 2)
            resetLiveHistory()
            lastUiPublishTimeNanoseconds = 0
            isCapturing = true
            audioSessionSummary = "Active measurement capture"
            audioSessionDetail = String(
                format: "Category %@, mode %@, session %.0f Hz, %.1f ms buffer. The negotiated input format is %.0f Hz with %d analysis samples and %d channel%@.",
                startInfo.categoryDescription,
                startInfo.modeDescription,
                startInfo.sessionSampleRate,
                startInfo.sessionBufferDuration * 1_000,
                startInfo.inputFormatSampleRate,
                startInfo.analysisFrameSize,
                startInfo.channelCount,
                startInfo.channelCount == 1 ? "" : "s"
            )
            liveCaptureSummary = "Capturing live microphone input"
            liveCaptureDetail = "Waiting for the first shared frame-analysis update."
        } catch {
            handleCaptureFailure(error.localizedDescription)
        }
    }

    private func stopLiveCaptureWithoutRefreshing() {
        liveAudioCaptureController.stopCapture()
        finalizeLoggingIfNeeded()
        liveFeedbackTracker?.clear()
        liveFrameAnalyzer = nil
        liveFeedbackTracker = nil
        reusableAnalysisInputBuffer = nil
        reusableTrackerSpectrumBuffer = nil
        smoothedSpectrum = []
        resetLiveHistory()
        lastUiPublishTimeNanoseconds = 0
        liveAnalyzerSnapshot = nil
        isCapturing = false
        liveCaptureSummary = "Live capture is idle"
        liveCaptureDetail = "Start live capture to validate the negotiated AVAudioEngine input format on iOS."
    }

    private func applyCaptureSnapshot(_ snapshot: LiveAudioCaptureSnapshot) {
        let rawDb = AnalyzerMath.shared.rawDbFromRms(
            rms: snapshot.pcm16EquivalentRms,
            floorDb: -120
        )
        let calibratedDb = AnalyzerMath.shared.applyDbOffset(rawDb: rawDb, dbOffset: dbOffset)
        let currentDb = AnalyzerMath.shared.applyNoiseGate(
            calibratedDb: calibratedDb,
            noiseThreshold: noiseThreshold
        )

        guard let liveFrameAnalyzer else {
            liveCaptureSummary = "Capturing live microphone input"
            liveCaptureDetail = String(
                format: "Live level %.1f dB, %.0f Hz, %d frames, %d channel%@.",
                currentDb,
                snapshot.sampleRate,
                snapshot.frameLength,
                snapshot.channelCount,
                snapshot.channelCount == 1 ? "" : "s"
            )
            return
        }

        let kotlinSamples = reusableAnalysisInputBuffer ?? KotlinFloatArray(size: Int32(snapshot.analysisSamples.count))
        reusableAnalysisInputBuffer = kotlinSamples
        copy(snapshot.analysisSamples, into: kotlinSamples)
        let frameAnalysis = liveFrameAnalyzer.analyzeFloat(
            buffer: kotlinSamples,
            read: Int32(snapshot.analysisSamples.count)
        )
        smoothedSpectrum = makeSmoothedSpectrum(
            from: frameAnalysis.magnitudes,
            currentDb: currentDb,
            previousSpectrum: smoothedSpectrum
        )
        let weightedDbA = AnalyzerMath.shared.applyNoiseGate(
            calibratedDb: (calibratedDb + frameAnalysis.aWeightingAdjustmentDb).clampedToZero(),
            noiseThreshold: noiseThreshold
        )
        let weightedDbC = AnalyzerMath.shared.applyNoiseGate(
            calibratedDb: (calibratedDb + frameAnalysis.cWeightingAdjustmentDb).clampedToZero(),
            noiseThreshold: noiseThreshold
        )
        let feedbackSuggestions = updateFeedbackSuggestions(
            spectrum: smoothedSpectrum,
            currentDb: currentDb,
            binSizeHz: frameAnalysis.binSize
        )
        updateLevelStats(with: currentDb)
        appendLoggingRowIfNeeded(
            timestampMilliseconds: Int64(Date().timeIntervalSince1970 * 1_000),
            zLevelDb: currentDb,
            aLevelDb: weightedDbA,
            cLevelDb: weightedDbC,
            dominantFrequencyHz: frameAnalysis.dominantFrequencyHz
        )

        if liveCaptureDetail == "Waiting for the first shared frame-analysis update." {
            liveCaptureDetail = "Shared frame analysis is active. The live analyzer panel below updates at a throttled cadence."
        }

        let now = DispatchTime.now().uptimeNanoseconds
        let shouldPublishSnapshot = liveAnalyzerSnapshot == nil ||
            now - lastUiPublishTimeNanoseconds >= uiPublishIntervalNanoseconds
        guard shouldPublishSnapshot else {
            return
        }
        lastUiPublishTimeNanoseconds = now

        let spectrumBars = makeSpectrumBars(
            from: smoothedSpectrum,
            binSizeHz: frameAnalysis.binSize
        )
        appendHistorySample(levelDb: currentDb, spectrumBars: spectrumBars)
        let levelStats = currentLevelStats()

        liveAnalyzerSnapshot = LiveAnalyzerSnapshot(
            zLevelDb: currentDb,
            aLevelDb: weightedDbA,
            cLevelDb: weightedDbC,
            minZLevelDb: levelStats.min,
            avgZLevelDb: levelStats.avg,
            maxZLevelDb: levelStats.max,
            dominantFrequencyHz: frameAnalysis.dominantFrequencyHz,
            suggestedCutFrequencyHz: AnalyzerMath.shared.suggestCutFrequency(
                frequencyHz: frameAnalysis.dominantFrequencyHz
            ),
            sampleRateHz: snapshot.sampleRate,
            frameLength: snapshot.frameLength,
            channelCount: snapshot.channelCount,
            zLevelHistory: zLevelHistory,
            spectrumBars: spectrumBars,
            spectrumHistoryRows: spectrumHistoryRows,
            feedbackSuggestions: feedbackSuggestions
        )
    }

    private func handleCaptureFailure(_ message: String) {
        stopLiveCaptureWithoutRefreshing()
        liveCaptureSummary = "Live capture failed"
        liveCaptureDetail = message

        if microphonePermissionState == .granted {
            configureAudioSession()
        }
    }

    private func configureAudioSession() {
        do {
            let setup = try audioSessionController.configureForAnalysis()
            let bufferMilliseconds = setup.preferredBufferDuration * 1_000

            audioSessionSummary = "Configured for measurement capture"
            audioSessionDetail = String(
                format: "Category %@, mode %@, preferred %.0f Hz, %.1f ms buffer. The session remains inactive until live capture starts.",
                setup.categoryDescription,
                setup.modeDescription,
                setup.preferredSampleRate,
                bufferMilliseconds
            )
        } catch {
            audioSessionSummary = "Audio session configuration failed"
            audioSessionDetail = error.localizedDescription
        }
    }

    private func copy(_ samples: [Float], into kotlinArray: KotlinFloatArray) {
        for (index, sample) in samples.enumerated() {
            kotlinArray.set(index: Int32(index), value: sample)
        }
    }

    private func makeSmoothedSpectrum(
        from magnitudes: KotlinFloatArray,
        currentDb: Float,
        previousSpectrum: [Float]
    ) -> [Float] {
        let magnitudeCount = Int(magnitudes.size)
        guard magnitudeCount > 0 else {
            return []
        }

        var nextSpectrum = previousSpectrum.count == magnitudeCount
            ? previousSpectrum
            : Array(repeating: 0, count: magnitudeCount)

        for index in 0..<magnitudeCount {
            let rawMagnitude = currentDb > 0 ? magnitudes.get(index: Int32(index)) : 0
            let previousValue = nextSpectrum[index]
            nextSpectrum[index] = previousValue * (1 - fullSpectrumSmoothingFactor) + rawMagnitude * fullSpectrumSmoothingFactor
        }

        return nextSpectrum
    }

    private func updateFeedbackSuggestions(
        spectrum: [Float],
        currentDb: Float,
        binSizeHz: Float
    ) -> [LiveFeedbackSuggestion] {
        guard let liveFeedbackTracker else {
            return []
        }

        let kotlinSpectrum = reusableTrackerSpectrumBuffer ?? KotlinFloatArray(size: Int32(spectrum.count))
        reusableTrackerSpectrumBuffer = kotlinSpectrum
        copy(spectrum, into: kotlinSpectrum)

        let feedbackPeaks = liveFeedbackTracker.update(
            spectrum: kotlinSpectrum,
            currentDb: currentDb,
            binSize: binSizeHz
        )

        return feedbackPeaks.map { peak in
            LiveFeedbackSuggestion(
                frequencyHz: peak.frequencyHz,
                suggestedCutHz: peak.suggestedCutHz,
                normalizedLevel: peak.normalizedLevel,
                stability: peak.stability,
                holdFrames: Int(peak.holdFrames)
            )
        }
    }

    private func makeSpectrumBars(
        from smoothedMagnitudes: [Float],
        binSizeHz: Float
    ) -> [Float] {
        let magnitudeCount = smoothedMagnitudes.count
        guard magnitudeCount > 1, binSizeHz > 0 else {
            return Array(repeating: 0, count: spectrumBarCount)
        }

        let maxBinIndex = magnitudeCount - 1
        let nyquistHz = Double(binSizeHz) * Double(maxBinIndex)
        let minFrequencyHz = min(30.0, nyquistHz)
        let maxFrequencyHz = max(minFrequencyHz * 2.0, nyquistHz)
        let minLogFrequency = log10(minFrequencyHz)
        let maxLogFrequency = log10(maxFrequencyHz)
        let logRange = maxLogFrequency - minLogFrequency
        var bars = Array(repeating: Float.zero, count: spectrumBarCount)

        for barIndex in 0..<spectrumBarCount {
            let startRatio = Double(barIndex) / Double(spectrumBarCount)
            let endRatio = Double(barIndex + 1) / Double(spectrumBarCount)
            let startFrequencyHz = pow(10.0, minLogFrequency + logRange * startRatio)
            let endFrequencyHz = pow(10.0, minLogFrequency + logRange * endRatio)
            let startBin = min(max(1, Int(floor(startFrequencyHz / Double(binSizeHz)))), maxBinIndex)
            let endBin = min(max(startBin, Int(ceil(endFrequencyHz / Double(binSizeHz)))), maxBinIndex)

            var peakMagnitude: Float = 0
            for binIndex in startBin...endBin {
                peakMagnitude = max(peakMagnitude, smoothedMagnitudes[binIndex])
            }

            bars[barIndex] = normalizeSpectrumMagnitude(peakMagnitude)
        }

        return bars
    }

    private var isSavedSpectrumSnapshotComparable: Bool {
        guard let savedSpectrumSnapshot, let liveAnalyzerSnapshot else {
            return false
        }

        return savedSpectrumSnapshot.spectrumBars.count == liveAnalyzerSnapshot.spectrumBars.count &&
            Int(savedSpectrumSnapshot.sampleRateHz.rounded()) == Int(liveAnalyzerSnapshot.sampleRateHz.rounded()) &&
            savedSpectrumSnapshot.frameLength == liveAnalyzerSnapshot.frameLength
    }

    private func resetLiveHistory() {
        zLevelHistory = []
        spectrumHistoryRows = []
        minObservedZLevelDb = Float.greatestFiniteMagnitude
        maxObservedZLevelDb = 0
        zLevelSum = 0
        zLevelSampleCount = 0
    }

    private func updateLevelStats(with levelDb: Float) {
        guard levelDb > 0 else {
            return
        }

        minObservedZLevelDb = min(minObservedZLevelDb, levelDb)
        maxObservedZLevelDb = max(maxObservedZLevelDb, levelDb)
        zLevelSum += Double(levelDb)
        zLevelSampleCount += 1
    }

    private func currentLevelStats() -> (min: Float, avg: Float, max: Float) {
        guard zLevelSampleCount > 0 else {
            return (0, 0, 0)
        }

        return (
            minObservedZLevelDb,
            Float(zLevelSum / Double(zLevelSampleCount)),
            maxObservedZLevelDb
        )
    }

    private func appendHistorySample(levelDb: Float, spectrumBars: [Float]) {
        zLevelHistory.append(levelDb)
        if zLevelHistory.count > zLevelHistoryLimit {
            zLevelHistory.removeFirst(zLevelHistory.count - zLevelHistoryLimit)
        }

        spectrumHistoryRows.append(spectrumBars)
        if spectrumHistoryRows.count > spectrumHistoryLimit {
            spectrumHistoryRows.removeFirst(spectrumHistoryRows.count - spectrumHistoryLimit)
        }
    }

    private func startLogging() {
        guard isCapturing else {
            logStatusMessage = "Start live capture before logging a session."
            return
        }

        loggedCsvRows = ["Timestamp,dB(Z),dB(A),dB(C),DominantFreq(Hz)"]
        loggedRowCount = 0
        exportedAnalyzerLog = nil
        logStatusMessage = nil
        isLogging = true
    }

    private func stopLoggingAndPrepareExport() {
        finalizeLoggingIfNeeded()
    }

    private func appendLoggingRowIfNeeded(
        timestampMilliseconds: Int64,
        zLevelDb: Float,
        aLevelDb: Float,
        cLevelDb: Float,
        dominantFrequencyHz: Float
    ) {
        guard isLogging else {
            return
        }

        loggedCsvRows.append(
            "\(timestampMilliseconds),\(zLevelDb),\(aLevelDb),\(cLevelDb),\(dominantFrequencyHz)"
        )
        loggedRowCount += 1
    }

    private func finalizeLoggingIfNeeded() {
        guard isLogging else {
            return
        }

        isLogging = false

        let csvContents = loggedCsvRows.joined(separator: "\n")
        guard !csvContents.isEmpty else {
            logStatusMessage = "No iOS log data was captured."
            return
        }

        let exportUrl = makeAnalyzerLogExportUrl()

        do {
            try csvContents.write(to: exportUrl, atomically: true, encoding: .utf8)
            exportedAnalyzerLog = ExportedAnalyzerLog(
                fileURL: exportUrl,
                rowCount: loggedRowCount,
                exportedAt: Date()
            )
            logStatusMessage = "CSV log ready with \(loggedRowCount) samples: \(exportUrl.lastPathComponent)"
        } catch {
            exportedAnalyzerLog = nil
            logStatusMessage = "Couldn't prepare the CSV export: \(error.localizedDescription)"
        }
    }

    private func makeAnalyzerLogExportUrl() -> URL {
        let timestamp = Int(Date().timeIntervalSince1970 * 1_000)
        let filename = "audio-analyser-log-\(timestamp).csv"
        return FileManager.default.temporaryDirectory.appendingPathComponent(filename)
    }

    private func normalizeSpectrumMagnitude(_ magnitude: Float) -> Float {
        let magnitudeDb = 20.0 * log10(max(Double(magnitude), 1e-9))
        let normalized = Float((magnitudeDb + 80.0) / 80.0)
        return normalized.clampedToUnit()
    }

    private func frequencyString(_ value: Float) -> String {
        guard value > 0 else {
            return "--"
        }

        return String(format: "%.0f Hz", value)
    }

    private func decibelString(_ value: Float) -> String {
        String(format: "%.1f dB(Z)", value)
    }

    private func captureDateString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

private extension Float {
    func clampedToZero() -> Float {
        max(self, 0)
    }

    func clampedToUnit() -> Float {
        min(max(self, 0), 1)
    }
}

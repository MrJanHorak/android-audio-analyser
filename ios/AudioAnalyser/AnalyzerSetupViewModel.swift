import Combine
import Foundation
import Shared
import UIKit

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
    let feedbackMarkers: [LiveFeedbackMarker]
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

struct ExportedAnalyzerSample {
    let fileURL: URL
    let exportedAt: Date
}

struct SavedWaterfallSession {
    let capturedAt: Date
    let sampleRateHz: Double
    let frameLength: Int
    let channelCount: Int
    let dominantFrequencyHz: Float
    let zLevelDb: Float
    let rowTimestamps: [Date]
    let rows: [[Float]]
}

struct ExportedWaterfallSample {
    let fileURL: URL
    let exportedAt: Date
    let rowCount: Int
}

struct ExportedAnalyzerReport {
    let fileURL: URL
    let exportedAt: Date
}

struct LiveFeedbackSuggestion: Identifiable, Equatable {
    let frequencyHz: Float
    let suggestedCutHz: Float
    let normalizedLevel: Float
    let stability: Float
    let holdFrames: Int
    let stabilityKey: String

    var id: String {
        "\(Int(frequencyHz.rounded()))-\(Int(suggestedCutHz.rounded()))"
    }
}

struct LiveFeedbackMarker: Identifiable, Equatable {
    let frequencyHz: Float
    let suggestedCutHz: Float
    let normalizedX: Float
    let normalizedLevel: Float
    let holdFrames: Int
    let stabilityKey: String

    var id: String {
        "\(Int(frequencyHz.rounded()))-\(Int(suggestedCutHz.rounded()))-marker"
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
    @Published private(set) var exportedAnalyzerSample: ExportedAnalyzerSample?
    @Published private(set) var exportedWaterfallSample: ExportedWaterfallSample?
    @Published private(set) var exportedAnalyzerReport: ExportedAnalyzerReport?
    @Published private(set) var logStatusMessage: String?
    @Published private(set) var analyzerSampleStatusMessage: String?
    @Published private(set) var waterfallExportStatusMessage: String?
    @Published private(set) var analyzerReportStatusMessage: String?
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
    private var spectrumHistoryTimestamps: [Date] = []
    private var minObservedZLevelDb: Float = Float.greatestFiniteMagnitude
    private var maxObservedZLevelDb: Float = 0
    private var zLevelSum = 0.0
    private var zLevelSampleCount = 0
    private var loggedCsvRows: [String] = []
    private var loggedRowCount = 0
    private var savedWaterfallSession: SavedWaterfallSession?
    private var lastUiPublishTimeNanoseconds: UInt64 = 0
    private var captureIntentIsActive = false
    private var isCaptureTransitionInFlight = false
    private var shouldResumeWhenAppBecomesActive = false
    private var pendingAutomaticResumeWorkItem: DispatchWorkItem?
    private var ignoreEngineConfigurationChangesUntil = Date.distantPast
    private var startCaptureAfterPermissionGrant = false
    private var cancellables: Set<AnyCancellable> = []

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
        exportedAnalyzerSample = nil
        exportedWaterfallSample = nil
        exportedAnalyzerReport = nil
        logStatusMessage = nil
        analyzerSampleStatusMessage = nil
        waterfallExportStatusMessage = nil
        analyzerReportStatusMessage = nil
        bindCaptureCallbacks()
        observeApplicationLifecycle()
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
        isCaptureSessionActiveOrPending ? "Stop live capture" : "Start live capture"
    }

    var isCaptureActionDisabled: Bool {
        if isCaptureSessionActiveOrPending {
            return false
        }

        return microphonePermissionState == .denied || isWorking
    }

    var isCaptureSessionActiveOrPending: Bool {
        isCapturing || captureIntentIsActive
    }

    func rebuildCaptureEngineAfterExternalAudioTool() {
        guard !isCaptureSessionActiveOrPending else {
            return
        }

        liveAudioCaptureController.rebuildAudioEngine()
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

    var isAnalyzerSampleExportDisabled: Bool {
        liveAnalyzerSnapshot == nil && savedSpectrumSnapshot == nil
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

    var analyzerSampleStatusText: String {
        if let analyzerSampleStatusMessage {
            return analyzerSampleStatusMessage
        }

        return "Prepare a versioned JSON analyzer sample that captures the current live state, history, feedback guidance, and any saved snapshot metadata."
    }

    var isWaterfallExportDisabled: Bool {
        savedWaterfallSession == nil
    }

    var waterfallExportStatusText: String {
        if let waterfallExportStatusMessage {
            return waterfallExportStatusMessage
        }

        if let savedWaterfallSession {
            if isCapturing {
                return "Prepare a dedicated waterfall-session JSON from the current live history rows, including per-row timestamps and higher-fidelity normalized spectrum data."
            }

            return "The most recent waterfall session from \(captureDateString(savedWaterfallSession.capturedAt)) is ready to export even though live capture is idle."
        }

        return "Start live capture to collect a waterfall session you can export for external analysis."
    }

    var isAnalyzerReportDisabled: Bool {
        liveAnalyzerSnapshot == nil && savedSpectrumSnapshot == nil && savedWaterfallSession == nil
    }

    var analyzerReportStatusText: String {
        if let analyzerReportStatusMessage {
            return analyzerReportStatusMessage
        }

        return "Prepare a Markdown session report with human-readable analyzer metrics, feedback suggestions, snapshot status, and waterfall-session availability."
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

    func prepareAnalyzerSampleExport(
        feedbackMarkersEnabled: Bool,
        feedbackDisplayCount: Int
    ) {
        guard liveAnalyzerSnapshot != nil || savedSpectrumSnapshot != nil else {
            analyzerSampleStatusMessage = "Capture live data or save a spectrum snapshot before exporting an analyzer sample."
            return
        }

        let exportDate = Date()
        let markerLimit = max(1, feedbackDisplayCount)
        let livePayload = liveAnalyzerSnapshot.map { snapshot in
            AnalyzerSampleLivePayload(
                zLevelDb: roundForExport(snapshot.zLevelDb, places: 1),
                aLevelDb: roundForExport(snapshot.aLevelDb, places: 1),
                cLevelDb: roundForExport(snapshot.cLevelDb, places: 1),
                minZLevelDb: roundForExport(snapshot.minZLevelDb, places: 1),
                avgZLevelDb: roundForExport(snapshot.avgZLevelDb, places: 1),
                maxZLevelDb: roundForExport(snapshot.maxZLevelDb, places: 1),
                dominantFrequencyHz: roundForExport(snapshot.dominantFrequencyHz, places: 1),
                suggestedCutFrequencyHz: roundForExport(snapshot.suggestedCutFrequencyHz, places: 1),
                sampleRateHz: roundForExport(snapshot.sampleRateHz, places: 1),
                frameLength: snapshot.frameLength,
                channelCount: snapshot.channelCount,
                zLevelHistory: roundForExport(snapshot.zLevelHistory, places: 1),
                spectrumBars: roundForExport(snapshot.spectrumBars, places: 3),
                spectrumHistoryRows: snapshot.spectrumHistoryRows.map { row in
                    roundForExport(row, places: 3)
                }
            )
        }
        let visibleSuggestions = liveAnalyzerSnapshot.map { snapshot in
            Array(snapshot.feedbackSuggestions.prefix(markerLimit)).map { suggestion in
                AnalyzerSampleFeedbackSuggestionPayload(
                    frequencyHz: roundForExport(suggestion.frequencyHz, places: 1),
                    suggestedCutHz: roundForExport(suggestion.suggestedCutHz, places: 1),
                    normalizedLevel: roundForExport(suggestion.normalizedLevel, places: 3),
                    stability: roundForExport(suggestion.stability, places: 3),
                    holdFrames: suggestion.holdFrames,
                    stabilityKey: suggestion.stabilityKey
                )
            }
        } ?? []
        let visibleMarkers = feedbackMarkersEnabled ? liveAnalyzerSnapshot.map { snapshot in
            Array(snapshot.feedbackMarkers.prefix(markerLimit)).map { marker in
                AnalyzerSampleFeedbackMarkerPayload(
                    frequencyHz: roundForExport(marker.frequencyHz, places: 1),
                    suggestedCutHz: roundForExport(marker.suggestedCutHz, places: 1),
                    normalizedX: roundForExport(marker.normalizedX, places: 3),
                    normalizedLevel: roundForExport(marker.normalizedLevel, places: 3),
                    holdFrames: marker.holdFrames,
                    stabilityKey: marker.stabilityKey
                )
            }
        } ?? [] : []
        let savedSnapshotPayload = savedSpectrumSnapshot.map { snapshot in
            AnalyzerSampleSavedSnapshotPayload(
                capturedAt: snapshot.capturedAt,
                dominantFrequencyHz: roundForExport(snapshot.dominantFrequencyHz, places: 1),
                zLevelDb: roundForExport(snapshot.zLevelDb, places: 1),
                sampleRateHz: roundForExport(snapshot.sampleRateHz, places: 1),
                frameLength: snapshot.frameLength,
                spectrumBars: roundForExport(snapshot.spectrumBars, places: 3)
            )
        }
        let envelope = AnalyzerSampleExportEnvelope(
            schemaVersion: 1,
            exportedAt: exportDate,
            liveAnalyzer: livePayload,
            savedSpectrumSnapshot: savedSnapshotPayload,
            feedbackDisplay: AnalyzerSampleFeedbackDisplayPayload(
                markersEnabled: feedbackMarkersEnabled,
                markerLimit: markerLimit,
                visibleSuggestions: visibleSuggestions,
                visibleMarkers: visibleMarkers
            )
        )
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let exportURL = makeAnalyzerSampleExportUrl()

        do {
            let data = try encoder.encode(envelope)
            try data.write(to: exportURL, options: .atomic)
            exportedAnalyzerSample = ExportedAnalyzerSample(
                fileURL: exportURL,
                exportedAt: exportDate
            )
            analyzerSampleStatusMessage = "JSON analyzer sample ready: \(exportURL.lastPathComponent)"
        } catch {
            exportedAnalyzerSample = nil
            analyzerSampleStatusMessage = "Couldn't prepare the analyzer sample export: \(error.localizedDescription)"
        }
    }

    func prepareWaterfallExport() {
        guard let savedWaterfallSession else {
            waterfallExportStatusMessage = "Start live capture before exporting a waterfall session."
            return
        }

        let exportDate = Date()
        let envelope = WaterfallSampleExportEnvelope(
            schemaVersion: 1,
            exportKind: "waterfall-session",
            exportedAt: exportDate,
            capturedAt: savedWaterfallSession.capturedAt,
            rowFormat: "normalized-log-spectrum-bars",
            sampleRateHz: savedWaterfallSession.sampleRateHz,
            frameLength: savedWaterfallSession.frameLength,
            channelCount: savedWaterfallSession.channelCount,
            dominantFrequencyHz: Double(savedWaterfallSession.dominantFrequencyHz),
            zLevelDb: Double(savedWaterfallSession.zLevelDb),
            rowCount: savedWaterfallSession.rows.count,
            columnCount: savedWaterfallSession.rows.first?.count ?? 0,
            rowTimestamps: savedWaterfallSession.rowTimestamps,
            rows: savedWaterfallSession.rows.map { row in row.map(Double.init) }
        )
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let exportURL = makeWaterfallSampleExportUrl()

        do {
            let data = try encoder.encode(envelope)
            try data.write(to: exportURL, options: .atomic)
            exportedWaterfallSample = ExportedWaterfallSample(
                fileURL: exportURL,
                exportedAt: exportDate,
                rowCount: savedWaterfallSession.rows.count
            )
            waterfallExportStatusMessage = "Waterfall session JSON ready: \(exportURL.lastPathComponent)"
        } catch {
            exportedWaterfallSample = nil
            waterfallExportStatusMessage = "Couldn't prepare the waterfall export: \(error.localizedDescription)"
        }
    }

    func prepareAnalyzerReport(
        feedbackMarkersEnabled: Bool,
        feedbackDisplayCount: Int
    ) {
        guard !isAnalyzerReportDisabled else {
            analyzerReportStatusMessage = "Capture live analyzer data or save a session artifact before preparing a report."
            return
        }

        let exportDate = Date()
        let markerLimit = max(1, feedbackDisplayCount)
        let reportMarkdown = buildAnalyzerReportMarkdown(
            exportDate: exportDate,
            feedbackMarkersEnabled: feedbackMarkersEnabled,
            feedbackDisplayCount: markerLimit
        )
        let exportURL = makeAnalyzerReportExportUrl()

        do {
            try reportMarkdown.write(to: exportURL, atomically: true, encoding: .utf8)
            exportedAnalyzerReport = ExportedAnalyzerReport(
                fileURL: exportURL,
                exportedAt: exportDate
            )
            analyzerReportStatusMessage = "Markdown session report ready: \(exportURL.lastPathComponent)"
        } catch {
            exportedAnalyzerReport = nil
            analyzerReportStatusMessage = "Couldn't prepare the session report: \(error.localizedDescription)"
        }
    }

    func refreshSetup() {
        microphonePermissionState = audioSessionController.permissionState()

        if microphonePermissionState != .granted && (isCapturing || captureIntentIsActive) {
            captureIntentIsActive = false
            cancelAutomaticCaptureResume()
            shouldResumeWhenAppBecomesActive = false
            stopLiveCaptureWithoutRefreshing()
        }

        switch microphonePermissionState {
        case .undetermined:
            audioSessionSummary = "Waiting for microphone authorization"
            audioSessionDetail = "The iOS analyzer session will stay inactive until permission is granted."
        case .denied:
            audioSessionSummary = "Microphone access is blocked"
            audioSessionDetail = "iOS will not prompt again after a denial. Open Settings > Privacy & Security > Microphone and enable SoundRunner. If it is missing there, delete the app, reinstall it from Xcode, and request access again."
        case .granted:
            if !isCapturing {
                configureAudioSession()
            }
        }
    }

    func performPrimaryAction() {
        switch microphonePermissionState {
        case .undetermined:
            requestMicrophoneAccess(startCaptureOnGrant: false)
        case .denied:
            audioSessionController.openAppSettings()
        case .granted:
            refreshSetup()
        }
    }

    func toggleLiveCapture() {
        if isCapturing || captureIntentIsActive {
            captureIntentIsActive = false
            cancelAutomaticCaptureResume()
            shouldResumeWhenAppBecomesActive = false
            stopLiveCaptureWithoutRefreshing()
            refreshSetup()
        } else {
            switch microphonePermissionState {
            case .granted:
                captureIntentIsActive = true
                startLiveCapture()
            case .undetermined:
                requestMicrophoneAccess(startCaptureOnGrant: true)
            case .denied:
                audioSessionSummary = "Microphone access is blocked"
                audioSessionDetail = "Open Settings > Privacy & Security > Microphone and enable SoundRunner. If the app is not listed there, delete it, reinstall from Xcode, and request microphone access again."
                audioSessionController.openAppSettings()
            }
        }
    }

    private func requestMicrophoneAccess(startCaptureOnGrant: Bool) {
        isWorking = true
        startCaptureAfterPermissionGrant = startCaptureOnGrant

        audioSessionController.requestMicrophoneAccess { [weak self] permissionState in
            DispatchQueue.main.async {
                guard let self else {
                    return
                }

                self.isWorking = false
                let shouldStartCapture = self.startCaptureAfterPermissionGrant
                self.startCaptureAfterPermissionGrant = false
                self.microphonePermissionState = permissionState

                switch permissionState {
                case .granted:
                    self.configureAudioSession()
                    if shouldStartCapture {
                        self.captureIntentIsActive = true
                        self.startLiveCapture()
                    }
                case .undetermined:
                    self.captureIntentIsActive = false
                    self.audioSessionSummary = "Waiting for microphone authorization"
                    self.audioSessionDetail = "Grant microphone access before starting live analysis."
                case .denied:
                    self.captureIntentIsActive = false
                    self.audioSessionSummary = "Microphone access is blocked"
                    self.audioSessionDetail = "Open Settings > Privacy & Security > Microphone and enable SoundRunner. If the app is not listed there, delete it, reinstall from Xcode, and request microphone access again."
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

        liveAudioCaptureController.onConfigurationChange = { [weak self] in
            DispatchQueue.main.async {
                self?.handleAudioEngineConfigurationChange()
            }
        }

        audioSessionController.onEvent = { [weak self] event in
            DispatchQueue.main.async {
                self?.handleAudioSessionEvent(event)
            }
        }
    }

    private func observeApplicationLifecycle() {
        NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.handleApplicationDidBecomeActive()
            }
            .store(in: &cancellables)
    }

    private func startLiveCapture(automaticResumeReason: String? = nil, attempt: Int = 0) {
        guard microphonePermissionState == .granted else {
            captureIntentIsActive = false
            liveCaptureSummary = "Microphone access is required"
            liveCaptureDetail = "Grant microphone permission before starting live input capture."
            return
        }

        guard !isCaptureTransitionInFlight else {
            return
        }

        if automaticResumeReason != nil, UIApplication.shared.applicationState != .active {
            shouldResumeWhenAppBecomesActive = true
            liveCaptureSummary = "Live capture paused"
            liveCaptureDetail = "Bring the app back to the foreground to restore microphone capture."
            return
        }

        if automaticResumeReason != nil, !audioSessionController.isInputAvailable() {
            liveCaptureSummary = "Live capture paused"
            liveCaptureDetail = "Waiting for a usable microphone input route before restoring live capture."
            return
        }

        isCaptureTransitionInFlight = true
        cancelAutomaticCaptureResume()
        shouldResumeWhenAppBecomesActive = false

        do {
            let startInfo = try liveAudioCaptureController.startCapture()
            configureCaptureState(using: startInfo)
            liveCaptureSummary = "Capturing live microphone input"
            if let automaticResumeReason {
                liveCaptureDetail = "\(automaticResumeReason) Waiting for the first shared frame-analysis update."
            } else {
                liveCaptureDetail = "Waiting for the first shared frame-analysis update."
            }
        } catch {
            if let automaticResumeReason {
                handleAutomaticResumeFailure(error, reason: automaticResumeReason, attempt: attempt)
            } else {
                captureIntentIsActive = false
                handleCaptureFailure(error.localizedDescription)
            }
        }

        isCaptureTransitionInFlight = false
    }

    private func stopLiveCaptureWithoutRefreshing(
        finalizeLogging: Bool = true,
        resetStatus: Bool = true
    ) {
        liveAudioCaptureController.stopCapture()
        if finalizeLogging {
            finalizeLoggingIfNeeded()
        }
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
        if resetStatus {
            liveCaptureSummary = "Live capture is idle"
            liveCaptureDetail = "Start live capture to validate the negotiated AVAudioEngine input format on iOS."
        }
    }

    private func configureCaptureState(using startInfo: LiveAudioCaptureStartInfo) {
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
        ignoreEngineConfigurationChangesUntil = Date().addingTimeInterval(0.5)
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
    }

    private func pauseLiveCaptureForRecovery(summary: String, detail: String, rebuildEngine: Bool = false) {
        cancelAutomaticCaptureResume()
        stopLiveCaptureWithoutRefreshing(finalizeLogging: false, resetStatus: false)

        if rebuildEngine {
            liveAudioCaptureController.rebuildAudioEngine()
        }

        liveCaptureSummary = summary
        liveCaptureDetail = detail
    }

    private func scheduleAutomaticCaptureResume(
        reason: String,
        attempt: Int = 0,
        delay: TimeInterval = 0.2
    ) {
        guard captureIntentIsActive else {
            return
        }

        cancelAutomaticCaptureResume()

        guard UIApplication.shared.applicationState == .active else {
            shouldResumeWhenAppBecomesActive = true
            liveCaptureSummary = "Live capture paused"
            liveCaptureDetail = "The app must be active before iOS can restore microphone capture."
            return
        }

        guard audioSessionController.isInputAvailable() else {
            liveCaptureSummary = "Live capture paused"
            liveCaptureDetail = "No microphone input is currently available. Reconnect or select an input to resume."
            return
        }

        liveCaptureSummary = "Refreshing live capture"
        liveCaptureDetail = reason

        let workItem = DispatchWorkItem { [weak self] in
            DispatchQueue.main.async {
                self?.startLiveCapture(automaticResumeReason: reason, attempt: attempt)
            }
        }

        pendingAutomaticResumeWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: workItem)
    }

    private func cancelAutomaticCaptureResume() {
        pendingAutomaticResumeWorkItem?.cancel()
        pendingAutomaticResumeWorkItem = nil
    }

    private func handleAutomaticResumeFailure(_ error: Error, reason: String, attempt: Int) {
        let isAppActive = UIApplication.shared.applicationState == .active
        let inputAvailable = audioSessionController.isInputAvailable()

        if attempt < 2, isAppActive, inputAvailable {
            liveCaptureSummary = "Live capture paused"
            liveCaptureDetail = "\(reason) Retrying microphone capture as iOS finishes renegotiating the input route."
            scheduleAutomaticCaptureResume(
                reason: reason,
                attempt: attempt + 1,
                delay: 0.25 * Double(attempt + 1)
            )
            return
        }

        liveCaptureSummary = "Live capture paused"

        if !isAppActive {
            shouldResumeWhenAppBecomesActive = true
            liveCaptureDetail = "The app must be active before iOS can restore microphone capture."
        } else if !inputAvailable {
            liveCaptureDetail = "No microphone input is currently available. Reconnect or select an input to resume."
        } else {
            liveCaptureDetail = "\(reason) \(error.localizedDescription)"
        }
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
        let feedbackPeaks = updateFeedbackPeaks(
            spectrum: smoothedSpectrum,
            currentDb: currentDb,
            binSizeHz: frameAnalysis.binSize
        )
        let feedbackSuggestions = buildFeedbackSuggestions(from: feedbackPeaks)
        let feedbackMarkers = makeFeedbackMarkers(
            from: feedbackPeaks,
            magnitudeCount: smoothedSpectrum.count,
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

        if liveCaptureDetail.contains("Waiting for the first shared frame-analysis update.") {
            liveCaptureDetail = "Shared frame analysis is active. The live analyzer panel below updates at a throttled cadence."
        }

        let historyTimestamp = Date()
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
        appendHistorySample(
            levelDb: currentDb,
            spectrumBars: spectrumBars,
            sampledAt: historyTimestamp
        )
        updateSavedWaterfallSession(
            capturedAt: historyTimestamp,
            sampleRateHz: snapshot.sampleRate,
            frameLength: snapshot.frameLength,
            channelCount: snapshot.channelCount,
            dominantFrequencyHz: frameAnalysis.dominantFrequencyHz,
            zLevelDb: currentDb
        )
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
            feedbackMarkers: feedbackMarkers,
            feedbackSuggestions: feedbackSuggestions
        )
    }

    private func handleCaptureFailure(_ message: String) {
        captureIntentIsActive = false
        cancelAutomaticCaptureResume()
        shouldResumeWhenAppBecomesActive = false
        stopLiveCaptureWithoutRefreshing(resetStatus: false)
        liveCaptureSummary = "Live capture failed"
        liveCaptureDetail = message

        if microphonePermissionState == .granted {
            configureAudioSession()
        }
    }

    private func handleAudioSessionEvent(_ event: AudioSessionEvent) {
        guard captureIntentIsActive || isCapturing else {
            return
        }

        switch event {
        case .interruptionBegan(let wasSuspended):
            pauseLiveCaptureForRecovery(
                summary: "Live capture interrupted",
                detail: wasSuspended
                    ? "iOS suspended the app while microphone capture was active. Bring the app back to the foreground to resume."
                    : "iOS temporarily interrupted microphone capture. The analyzer will resume when audio input is available again."
            )
        case .interruptionEnded(let shouldResume):
            guard shouldResume else {
                captureIntentIsActive = false
                cancelAutomaticCaptureResume()
                liveCaptureSummary = "Live capture paused"
                liveCaptureDetail = "The interruption ended without automatic resume permission. Tap Start live capture when you are ready to resume."
                return
            }

            scheduleAutomaticCaptureResume(
                reason: "Microphone access returned. Restoring live capture."
            )
        case .routeChanged(let reason, let inputAvailable, let inputName):
            if !inputAvailable {
                pauseLiveCaptureForRecovery(
                    summary: "Live capture paused",
                    detail: "\(reason.description) Connect or select a microphone input to resume live analysis."
                )
                return
            }

            guard reason.isCaptureRelevant, !isCapturing else {
                return
            }

            let inputDescription = inputName ?? "the current microphone"
            scheduleAutomaticCaptureResume(
                reason: "\(reason.description) Switching to \(inputDescription) and restoring live capture."
            )
        case .mediaServicesWereLost:
            pauseLiveCaptureForRecovery(
                summary: "Live capture paused",
                detail: "iOS audio services were lost. Waiting for the audio subsystem to return."
            )
        case .mediaServicesWereReset:
            pauseLiveCaptureForRecovery(
                summary: "Refreshing live capture",
                detail: "iOS audio services were reset. Rebuilding the capture engine.",
                rebuildEngine: true
            )
            if microphonePermissionState == .granted {
                configureAudioSession()
            }
            scheduleAutomaticCaptureResume(
                reason: "The iOS audio subsystem was reset. Restoring live capture."
            )
        }
    }

    private func handleAudioEngineConfigurationChange() {
        guard captureIntentIsActive, !isCaptureTransitionInFlight else {
            return
        }

        guard Date() >= ignoreEngineConfigurationChangesUntil else {
            return
        }

        pauseLiveCaptureForRecovery(
            summary: "Refreshing live capture",
            detail: "The microphone hardware configuration changed. Renegotiating the live input format."
        )
        scheduleAutomaticCaptureResume(
            reason: "The microphone hardware changed. Restoring live capture."
        )
    }

    private func handleApplicationDidBecomeActive() {
        refreshSetup()

        guard shouldResumeWhenAppBecomesActive, captureIntentIsActive else {
            return
        }

        shouldResumeWhenAppBecomesActive = false
        scheduleAutomaticCaptureResume(
            reason: "The app is active again. Restoring live capture.",
            delay: 0.1
        )
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

    private func updateFeedbackPeaks(
        spectrum: [Float],
        currentDb: Float,
        binSizeHz: Float
    ) -> [FeedbackPeak] {
        guard let liveFeedbackTracker else {
            return []
        }

        let kotlinSpectrum = reusableTrackerSpectrumBuffer ?? KotlinFloatArray(size: Int32(spectrum.count))
        reusableTrackerSpectrumBuffer = kotlinSpectrum
        copy(spectrum, into: kotlinSpectrum)

        return liveFeedbackTracker.update(
            spectrum: kotlinSpectrum,
            currentDb: currentDb,
            binSize: binSizeHz
        )
    }

    private func buildFeedbackSuggestions(from feedbackPeaks: [FeedbackPeak]) -> [LiveFeedbackSuggestion] {
        feedbackPeaks.map { peak in
            LiveFeedbackSuggestion(
                frequencyHz: peak.frequencyHz,
                suggestedCutHz: peak.suggestedCutHz,
                normalizedLevel: peak.normalizedLevel,
                stability: peak.stability,
                holdFrames: Int(peak.holdFrames),
                stabilityKey: FeedbackPeakPresentation.shared.stabilityKey(peak: peak)
            )
        }
    }

    private func makeFeedbackMarkers(
        from feedbackPeaks: [FeedbackPeak],
        magnitudeCount: Int,
        binSizeHz: Float
    ) -> [LiveFeedbackMarker] {
        guard let spectrumAxis = makeSpectrumAxis(
            magnitudeCount: magnitudeCount,
            binSizeHz: binSizeHz
        ) else {
            return []
        }

        return feedbackPeaks.compactMap { peak in
            let stabilityKey = FeedbackPeakPresentation.shared.stabilityKey(peak: peak)
            return LiveFeedbackMarker(
                frequencyHz: peak.frequencyHz,
                suggestedCutHz: peak.suggestedCutHz,
                normalizedX: normalizedSpectrumPosition(
                    frequencyHz: peak.frequencyHz,
                    spectrumAxis: spectrumAxis
                ),
                normalizedLevel: peak.normalizedLevel,
                holdFrames: Int(peak.holdFrames),
                stabilityKey: stabilityKey
            )
        }
    }

    private func makeSpectrumBars(
        from smoothedMagnitudes: [Float],
        binSizeHz: Float
    ) -> [Float] {
        let magnitudeCount = smoothedMagnitudes.count
        guard let spectrumAxis = makeSpectrumAxis(
            magnitudeCount: magnitudeCount,
            binSizeHz: binSizeHz
        ) else {
            return Array(repeating: 0, count: spectrumBarCount)
        }

        var bars = Array(repeating: Float.zero, count: spectrumBarCount)

        for barIndex in 0..<spectrumBarCount {
            let startRatio = Double(barIndex) / Double(spectrumBarCount)
            let endRatio = Double(barIndex + 1) / Double(spectrumBarCount)
            let startFrequencyHz = pow(10.0, spectrumAxis.minLogFrequency + spectrumAxis.logRange * startRatio)
            let endFrequencyHz = pow(10.0, spectrumAxis.minLogFrequency + spectrumAxis.logRange * endRatio)
            let startBin = min(
                max(1, Int(floor(startFrequencyHz / Double(binSizeHz)))),
                spectrumAxis.maxBinIndex
            )
            let endBin = min(
                max(startBin, Int(ceil(endFrequencyHz / Double(binSizeHz)))),
                spectrumAxis.maxBinIndex
            )

            var peakMagnitude: Float = 0
            for binIndex in startBin...endBin {
                peakMagnitude = max(peakMagnitude, smoothedMagnitudes[binIndex])
            }

            bars[barIndex] = normalizeSpectrumMagnitude(peakMagnitude)
        }

        return bars
    }

    private func makeSpectrumAxis(
        magnitudeCount: Int,
        binSizeHz: Float
    ) -> SpectrumAxis? {
        guard magnitudeCount > 1, binSizeHz > 0 else {
            return nil
        }

        let maxBinIndex = magnitudeCount - 1
        let nyquistHz = Double(binSizeHz) * Double(maxBinIndex)
        let minFrequencyHz = min(30.0, nyquistHz)
        let maxFrequencyHz = max(minFrequencyHz * 2.0, nyquistHz)
        let minLogFrequency = log10(minFrequencyHz)
        let maxLogFrequency = log10(maxFrequencyHz)

        return SpectrumAxis(
            minFrequencyHz: minFrequencyHz,
            maxFrequencyHz: maxFrequencyHz,
            minLogFrequency: minLogFrequency,
            logRange: maxLogFrequency - minLogFrequency,
            maxBinIndex: maxBinIndex
        )
    }

    private func normalizedSpectrumPosition(
        frequencyHz: Float,
        spectrumAxis: SpectrumAxis
    ) -> Float {
        let clampedFrequencyHz = min(
            max(Double(frequencyHz), spectrumAxis.minFrequencyHz),
            spectrumAxis.maxFrequencyHz
        )
        let normalized = (log10(clampedFrequencyHz) - spectrumAxis.minLogFrequency) / spectrumAxis.logRange
        return Float(normalized).clampedToUnit()
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
        spectrumHistoryTimestamps = []
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

    private func appendHistorySample(levelDb: Float, spectrumBars: [Float], sampledAt: Date) {
        zLevelHistory.append(levelDb)
        if zLevelHistory.count > zLevelHistoryLimit {
            zLevelHistory.removeFirst(zLevelHistory.count - zLevelHistoryLimit)
        }

        spectrumHistoryRows.append(spectrumBars)
        if spectrumHistoryRows.count > spectrumHistoryLimit {
            spectrumHistoryRows.removeFirst(spectrumHistoryRows.count - spectrumHistoryLimit)
        }

        spectrumHistoryTimestamps.append(sampledAt)
        if spectrumHistoryTimestamps.count > spectrumHistoryLimit {
            spectrumHistoryTimestamps.removeFirst(spectrumHistoryTimestamps.count - spectrumHistoryLimit)
        }
    }

    private func updateSavedWaterfallSession(
        capturedAt: Date,
        sampleRateHz: Double,
        frameLength: Int,
        channelCount: Int,
        dominantFrequencyHz: Float,
        zLevelDb: Float
    ) {
        guard !spectrumHistoryRows.isEmpty else {
            return
        }

        savedWaterfallSession = SavedWaterfallSession(
            capturedAt: capturedAt,
            sampleRateHz: sampleRateHz,
            frameLength: frameLength,
            channelCount: channelCount,
            dominantFrequencyHz: dominantFrequencyHz,
            zLevelDb: zLevelDb,
            rowTimestamps: spectrumHistoryTimestamps,
            rows: spectrumHistoryRows
        )
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

    private func makeAnalyzerSampleExportUrl() -> URL {
        let timestamp = Int(Date().timeIntervalSince1970 * 1_000)
        let filename = "audio-analyser-sample-\(timestamp).json"
        return FileManager.default.temporaryDirectory.appendingPathComponent(filename)
    }

    private func makeWaterfallSampleExportUrl() -> URL {
        let timestamp = Int(Date().timeIntervalSince1970 * 1_000)
        let filename = "audio-analyser-waterfall-\(timestamp).json"
        return FileManager.default.temporaryDirectory.appendingPathComponent(filename)
    }

    private func makeAnalyzerReportExportUrl() -> URL {
        let timestamp = Int(Date().timeIntervalSince1970 * 1_000)
        let filename = "audio-analyser-session-report-\(timestamp).md"
        return FileManager.default.temporaryDirectory.appendingPathComponent(filename)
    }

    private func normalizeSpectrumMagnitude(_ magnitude: Float) -> Float {
        let magnitudeDb = 20.0 * log10(max(Double(magnitude), 1e-9))
        let normalized = Float((magnitudeDb + 80.0) / 80.0)
        return normalized.clampedToUnit()
    }

    private func buildAnalyzerReportMarkdown(
        exportDate: Date,
        feedbackMarkersEnabled: Bool,
        feedbackDisplayCount: Int
    ) -> String {
        var lines = [
            "# SoundRunner session report",
            "",
            "- Generated: \(captureDateString(exportDate))",
            "- Feedback markers: \(feedbackMarkersEnabled ? "Enabled" : "Hidden")",
            "- Feedback focus: Top \(feedbackDisplayCount)"
        ]

        if let liveAnalyzerSnapshot {
            lines += [
                "",
                "## Live analyzer",
                "- Level: \(decibelString(liveAnalyzerSnapshot.zLevelDb))",
                "- A-weighted: \(decibelString(liveAnalyzerSnapshot.aLevelDb, unitSuffix: "dB"))",
                "- C-weighted: \(decibelString(liveAnalyzerSnapshot.cLevelDb, unitSuffix: "dB"))",
                "- Dominant frequency: \(frequencyString(liveAnalyzerSnapshot.dominantFrequencyHz))",
                "- Suggested cut: \(frequencyString(liveAnalyzerSnapshot.suggestedCutFrequencyHz))",
                "- Sample rate: \(Int(liveAnalyzerSnapshot.sampleRateHz)) Hz",
                "- Frame length: \(liveAnalyzerSnapshot.frameLength) samples",
                "- Channels: \(liveAnalyzerSnapshot.channelCount)"
            ]

            let visibleSuggestions = Array(liveAnalyzerSnapshot.feedbackSuggestions.prefix(feedbackDisplayCount))
            lines += [
                "",
                "## Feedback suggestions"
            ]
            if visibleSuggestions.isEmpty {
                lines += ["- No stable feedback suggestions were visible when the report was generated."]
            } else {
                lines += visibleSuggestions.map { suggestion in
                    "- \(frequencyString(suggestion.frequencyHz)) -> Cut \(frequencyString(suggestion.suggestedCutHz)) (\(feedbackTierTitle(for: suggestion.stabilityKey)), hold \(suggestion.holdFrames), level \(roundForExport(suggestion.normalizedLevel, places: 3)))"
                }
            }
        } else {
            lines += [
                "",
                "## Live analyzer",
                "- No live analyzer snapshot was available when the report was generated."
            ]
        }

        if let savedSpectrumSnapshot {
            lines += [
                "",
                "## Saved spectrum snapshot",
                "- Captured: \(captureDateString(savedSpectrumSnapshot.capturedAt))",
                "- Dominant frequency: \(frequencyString(savedSpectrumSnapshot.dominantFrequencyHz))",
                "- Level: \(decibelString(savedSpectrumSnapshot.zLevelDb))",
                "- Compare ready: \(isSavedSpectrumSnapshotComparable ? "Yes" : "No")"
            ]
        }

        if let savedWaterfallSession {
            lines += [
                "",
                "## Saved waterfall session",
                "- Captured: \(captureDateString(savedWaterfallSession.capturedAt))",
                "- Rows: \(savedWaterfallSession.rows.count)",
                "- Columns: \(savedWaterfallSession.rows.first?.count ?? 0)",
                "- Sample rate: \(Int(savedWaterfallSession.sampleRateHz)) Hz",
                "- Frame length: \(savedWaterfallSession.frameLength) samples"
            ]
        }

        return lines.joined(separator: "\n")
    }

    private func frequencyString(_ value: Float) -> String {
        guard value > 0 else {
            return "--"
        }

        return AnalyzerTextFormatter.shared.formatFrequencyLabel(frequencyHz: value)
    }

    private func decibelString(_ value: Float, unitSuffix: String = "dB(Z)") -> String {
        let formattedValue = AnalyzerTextFormatter.shared.formatDecibelValue(valueDb: value)
        return formattedValue == "--" ? "--" : "\(formattedValue) \(unitSuffix)"
    }

    private func captureDateString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    private func feedbackTierTitle(for stabilityKey: String) -> String {
        switch stabilityKey {
        case "stable":
            return "Stable"
        case "building":
            return "Building"
        default:
            return "Watch"
        }
    }

    private func roundForExport(_ value: Float, places: Int) -> Double {
        roundForExport(Double(value), places: places)
    }

    private func roundForExport(_ value: Double, places: Int) -> Double {
        let scale = pow(10.0, Double(places))
        return (value * scale).rounded() / scale
    }

    private func roundForExport(_ values: [Float], places: Int) -> [Double] {
        values.map { roundForExport($0, places: places) }
    }

    private struct SpectrumAxis {
        let minFrequencyHz: Double
        let maxFrequencyHz: Double
        let minLogFrequency: Double
        let logRange: Double
        let maxBinIndex: Int
    }
}

private struct AnalyzerSampleExportEnvelope: Encodable {
    let schemaVersion: Int
    let exportedAt: Date
    let liveAnalyzer: AnalyzerSampleLivePayload?
    let savedSpectrumSnapshot: AnalyzerSampleSavedSnapshotPayload?
    let feedbackDisplay: AnalyzerSampleFeedbackDisplayPayload
}

private struct AnalyzerSampleLivePayload: Encodable {
    let zLevelDb: Double
    let aLevelDb: Double
    let cLevelDb: Double
    let minZLevelDb: Double
    let avgZLevelDb: Double
    let maxZLevelDb: Double
    let dominantFrequencyHz: Double
    let suggestedCutFrequencyHz: Double
    let sampleRateHz: Double
    let frameLength: Int
    let channelCount: Int
    let zLevelHistory: [Double]
    let spectrumBars: [Double]
    let spectrumHistoryRows: [[Double]]
}

private struct AnalyzerSampleSavedSnapshotPayload: Encodable {
    let capturedAt: Date
    let dominantFrequencyHz: Double
    let zLevelDb: Double
    let sampleRateHz: Double
    let frameLength: Int
    let spectrumBars: [Double]
}

private struct AnalyzerSampleFeedbackDisplayPayload: Encodable {
    let markersEnabled: Bool
    let markerLimit: Int
    let visibleSuggestions: [AnalyzerSampleFeedbackSuggestionPayload]
    let visibleMarkers: [AnalyzerSampleFeedbackMarkerPayload]
}

private struct AnalyzerSampleFeedbackSuggestionPayload: Encodable {
    let frequencyHz: Double
    let suggestedCutHz: Double
    let normalizedLevel: Double
    let stability: Double
    let holdFrames: Int
    let stabilityKey: String
}

private struct AnalyzerSampleFeedbackMarkerPayload: Encodable {
    let frequencyHz: Double
    let suggestedCutHz: Double
    let normalizedX: Double
    let normalizedLevel: Double
    let holdFrames: Int
    let stabilityKey: String
}

private struct WaterfallSampleExportEnvelope: Encodable {
    let schemaVersion: Int
    let exportKind: String
    let exportedAt: Date
    let capturedAt: Date
    let rowFormat: String
    let sampleRateHz: Double
    let frameLength: Int
    let channelCount: Int
    let dominantFrequencyHz: Double
    let zLevelDb: Double
    let rowCount: Int
    let columnCount: Int
    let rowTimestamps: [Date]
    let rows: [[Double]]
}

private extension Float {
    func clampedToZero() -> Float {
        max(self, 0)
    }

    func clampedToUnit() -> Float {
        min(max(self, 0), 1)
    }
}

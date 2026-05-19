import Combine
import Foundation
import Shared

@MainActor
final class AnalyzerSetupViewModel: ObservableObject {
    @Published private(set) var microphonePermissionState: MicrophonePermissionState
    @Published private(set) var audioSessionSummary: String
    @Published private(set) var audioSessionDetail: String
    @Published private(set) var liveCaptureSummary: String
    @Published private(set) var liveCaptureDetail: String
    @Published private(set) var isCapturing = false
    @Published private(set) var isWorking = false

    private let audioSessionController: AudioSessionController
    private let liveAudioCaptureController: LiveAudioCaptureController
    private let dbOffset: Float = 30
    private let noiseThreshold: Float = 25
    private var liveFrameAnalyzer: AnalyzerFrameAnalyzer?

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
        liveFrameAnalyzer = nil
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

        let kotlinSamples = makeKotlinFloatArray(from: snapshot.analysisSamples)
        let frameAnalysis = liveFrameAnalyzer.analyzeFloat(
            buffer: kotlinSamples,
            read: Int32(snapshot.analysisSamples.count)
        )
        let weightedDbA = AnalyzerMath.shared.applyNoiseGate(
            calibratedDb: (calibratedDb + frameAnalysis.aWeightingAdjustmentDb).clampedToZero(),
            noiseThreshold: noiseThreshold
        )
        let weightedDbC = AnalyzerMath.shared.applyNoiseGate(
            calibratedDb: (calibratedDb + frameAnalysis.cWeightingAdjustmentDb).clampedToZero(),
            noiseThreshold: noiseThreshold
        )

        liveCaptureSummary = "Capturing live microphone input"
        liveCaptureDetail = String(
            format: "Z %.1f dB, A %.1f dB, C %.1f dB, %.0f Hz dominant, %.0f Hz sample rate, %d frames, %d channel%@.",
            currentDb,
            weightedDbA,
            weightedDbC,
            frameAnalysis.dominantFrequencyHz,
            snapshot.sampleRate,
            snapshot.frameLength,
            snapshot.channelCount,
            snapshot.channelCount == 1 ? "" : "s"
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

    private func makeKotlinFloatArray(from samples: [Float]) -> KotlinFloatArray {
        let kotlinArray = KotlinFloatArray(size: Int32(samples.count))
        for (index, sample) in samples.enumerated() {
            kotlinArray.set(index: Int32(index), value: sample)
        }
        return kotlinArray
    }
}

private extension Float {
    func clampedToZero() -> Float {
        max(self, 0)
    }
}

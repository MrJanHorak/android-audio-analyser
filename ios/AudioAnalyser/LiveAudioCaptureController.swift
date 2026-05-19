import AVFoundation
import Foundation

struct LiveAudioCaptureStartInfo {
    let categoryDescription: String
    let modeDescription: String
    let sessionSampleRate: Double
    let sessionBufferDuration: TimeInterval
    let inputFormatSampleRate: Double
    let analysisFrameSize: Int
    let channelCount: Int
}

struct LiveAudioCaptureSnapshot {
    let pcm16EquivalentRms: Float
    let sampleRate: Double
    let frameLength: Int
    let channelCount: Int
    let analysisSamples: [Float]
}

enum LiveAudioCaptureError: LocalizedError {
    case microphonePermissionRequired
    case inputUnavailable
    case invalidInputFormat
    case unsupportedInputFormat

    var errorDescription: String? {
        switch self {
        case .microphonePermissionRequired:
            return "Grant microphone access before starting live capture."
        case .inputUnavailable:
            return "No microphone input is currently available on this iOS target."
        case .invalidInputFormat:
            return "AVAudioEngine did not negotiate a valid microphone input format."
        case .unsupportedInputFormat:
            return "This build currently expects float32 microphone input buffers."
        }
    }
}

final class LiveAudioCaptureController {
    var onSnapshot: ((LiveAudioCaptureSnapshot) -> Void)?
    var onFailure: ((String) -> Void)?

    private let audioSessionController: AudioSessionController
    private let audioEngine = AVAudioEngine()
    private let requestedBufferSize: AVAudioFrameCount = 1024
    private let uiUpdateIntervalNanoseconds: UInt64 = 100_000_000

    private var lastUiUpdateTime: UInt64 = 0
    private var didReportTapFailure = false

    init(audioSessionController: AudioSessionController) {
        self.audioSessionController = audioSessionController
    }

    func startCapture() throws -> LiveAudioCaptureStartInfo {
        guard audioSessionController.permissionState() == .granted else {
            throw LiveAudioCaptureError.microphonePermissionRequired
        }

        stopCapture()

        let activeSession = try audioSessionController.activateForCapture()
        guard activeSession.inputAvailable else {
            try? audioSessionController.deactivateAfterCapture()
            throw LiveAudioCaptureError.inputUnavailable
        }

        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)

        guard inputFormat.channelCount > 0, inputFormat.sampleRate > 0 else {
            try? audioSessionController.deactivateAfterCapture()
            throw LiveAudioCaptureError.invalidInputFormat
        }

        guard inputFormat.commonFormat == .pcmFormatFloat32 else {
            try? audioSessionController.deactivateAfterCapture()
            throw LiveAudioCaptureError.unsupportedInputFormat
        }

        inputNode.removeTap(onBus: 0)
        lastUiUpdateTime = 0
        didReportTapFailure = false

        let sampleRate = inputFormat.sampleRate
        let channelCount = Int(inputFormat.channelCount)

        inputNode.installTap(onBus: 0, bufferSize: requestedBufferSize, format: inputFormat) { [weak self] buffer, _ in
            self?.handleInputBuffer(
                buffer,
                sampleRate: sampleRate,
                channelCount: channelCount
            )
        }

        audioEngine.prepare()

        do {
            try audioEngine.start()
        } catch {
            inputNode.removeTap(onBus: 0)
            audioEngine.reset()
            try? audioSessionController.deactivateAfterCapture()
            throw error
        }

        return LiveAudioCaptureStartInfo(
            categoryDescription: activeSession.categoryDescription,
            modeDescription: activeSession.modeDescription,
            sessionSampleRate: activeSession.actualSampleRate,
            sessionBufferDuration: activeSession.actualBufferDuration,
            inputFormatSampleRate: sampleRate,
            analysisFrameSize: Int(requestedBufferSize),
            channelCount: channelCount
        )
    }

    func stopCapture() {
        audioEngine.inputNode.removeTap(onBus: 0)

        if audioEngine.isRunning {
            audioEngine.stop()
        }

        audioEngine.reset()
        try? audioSessionController.deactivateAfterCapture()
    }

    private func handleInputBuffer(
        _ buffer: AVAudioPCMBuffer,
        sampleRate: Double,
        channelCount: Int
    ) {
        let frameLength = Int(buffer.frameLength)
        guard frameLength > 0 else {
            return
        }

        let now = DispatchTime.now().uptimeNanoseconds
        guard now - lastUiUpdateTime >= uiUpdateIntervalNanoseconds else {
            return
        }

        lastUiUpdateTime = now

        guard let channelData = buffer.floatChannelData else {
            reportTapFailureOnce("Live capture is receiving an unsupported microphone buffer.")
            return
        }

        let primaryChannel = channelData[0]
        var sumSquares: Float = 0
        let analysisSampleCount = min(frameLength, Int(requestedBufferSize))
        var analysisSamples = Array(repeating: Float.zero, count: analysisSampleCount)

        for index in 0..<frameLength {
            let sample = primaryChannel[index]
            sumSquares += sample * sample
            if index < analysisSampleCount {
                analysisSamples[index] = sample
            }
        }

        let snapshot = LiveAudioCaptureSnapshot(
            pcm16EquivalentRms: sqrt(sumSquares / Float(frameLength)) * Float(Int16.max),
            sampleRate: sampleRate,
            frameLength: frameLength,
            channelCount: channelCount,
            analysisSamples: analysisSamples
        )

        DispatchQueue.main.async { [weak self] in
            self?.onSnapshot?(snapshot)
        }
    }

    private func reportTapFailureOnce(_ message: String) {
        guard !didReportTapFailure else {
            return
        }

        didReportTapFailure = true
        DispatchQueue.main.async { [weak self] in
            self?.onFailure?(message)
        }
    }
}

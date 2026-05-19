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

    var errorDescription: String? {
        switch self {
        case .microphonePermissionRequired:
            return "Grant microphone access before starting live capture."
        case .inputUnavailable:
#if targetEnvironment(simulator)
            return "No microphone input is currently available in the iOS Simulator. Choose Simulator > I/O > Audio Input and select a Mac microphone, then retry, or use a physical iPhone."
#else
            return "No microphone input is currently available on this iOS target."
#endif
        case .invalidInputFormat:
#if targetEnvironment(simulator)
            return "AVAudioEngine did not negotiate a valid microphone input format in the iOS Simulator. Choose Simulator > I/O > Audio Input and select a Mac microphone, then retry, or use a physical iPhone."
#else
            return "AVAudioEngine did not negotiate a valid microphone input format."
#endif
        }
    }
}

final class LiveAudioCaptureController {
    var onSnapshot: ((LiveAudioCaptureSnapshot) -> Void)?
    var onFailure: ((String) -> Void)?
    var onConfigurationChange: (() -> Void)?

    private let audioSessionController: AudioSessionController
    private var audioEngine = AVAudioEngine()
    private let requestedBufferSize: AVAudioFrameCount = 1024
    private var didReportTapFailure = false
    private var engineConfigurationObserver: NSObjectProtocol?

    init(audioSessionController: AudioSessionController) {
        self.audioSessionController = audioSessionController
        observeAudioEngineConfigurationChanges()
    }

    deinit {
        if let engineConfigurationObserver {
            NotificationCenter.default.removeObserver(engineConfigurationObserver)
        }
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
        guard let inputFormat = resolvedInputFormat(for: inputNode) else {
            try? audioSessionController.deactivateAfterCapture()
            throw LiveAudioCaptureError.invalidInputFormat
        }

        inputNode.removeTap(onBus: 0)
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

    func rebuildAudioEngine() {
        audioEngine.inputNode.removeTap(onBus: 0)

        if audioEngine.isRunning {
            audioEngine.stop()
        }

        audioEngine.reset()

        if let engineConfigurationObserver {
            NotificationCenter.default.removeObserver(engineConfigurationObserver)
        }

        audioEngine = AVAudioEngine()
        didReportTapFailure = false
        observeAudioEngineConfigurationChanges()
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

        let analysisSampleCount = min(frameLength, Int(requestedBufferSize))
        guard let extractedBuffer = extractPrimaryChannelBuffer(
            from: buffer,
            frameLength: frameLength,
            analysisSampleCount: analysisSampleCount
        ) else {
            reportTapFailureOnce("Live capture is receiving an unsupported microphone buffer.")
            return
        }

        let snapshot = LiveAudioCaptureSnapshot(
            pcm16EquivalentRms: extractedBuffer.pcm16EquivalentRms,
            sampleRate: sampleRate,
            frameLength: frameLength,
            channelCount: channelCount,
            analysisSamples: extractedBuffer.analysisSamples
        )

        DispatchQueue.main.async { [weak self] in
            self?.onSnapshot?(snapshot)
        }
    }

    private func resolvedInputFormat(for inputNode: AVAudioInputNode) -> AVAudioFormat? {
        for attempt in 0..<3 {
            let inputFormat = inputNode.outputFormat(forBus: 0)
            if inputFormat.channelCount > 0, inputFormat.sampleRate > 0 {
                return inputFormat
            }

            if attempt < 2 {
                Thread.sleep(forTimeInterval: 0.05)
            }
        }

        return nil
    }

    private func extractPrimaryChannelBuffer(
        from buffer: AVAudioPCMBuffer,
        frameLength: Int,
        analysisSampleCount: Int
    ) -> (pcm16EquivalentRms: Float, analysisSamples: [Float])? {
        switch buffer.format.commonFormat {
        case .pcmFormatFloat32:
            guard let channelData = buffer.floatChannelData else {
                return nil
            }

            return buildPrimaryChannelSnapshot(
                frameLength: frameLength,
                analysisSampleCount: analysisSampleCount
            ) { index in
                channelData[0][index]
            }
        case .pcmFormatInt16:
            guard let channelData = buffer.int16ChannelData else {
                return nil
            }

            return buildPrimaryChannelSnapshot(
                frameLength: frameLength,
                analysisSampleCount: analysisSampleCount
            ) { index in
                Float(channelData[0][index]) / Float(Int16.max)
            }
        case .pcmFormatInt32:
            guard let channelData = buffer.int32ChannelData else {
                return nil
            }

            return buildPrimaryChannelSnapshot(
                frameLength: frameLength,
                analysisSampleCount: analysisSampleCount
            ) { index in
                Float(channelData[0][index]) / Float(Int32.max)
            }
        default:
            return nil
        }
    }

    private func buildPrimaryChannelSnapshot(
        frameLength: Int,
        analysisSampleCount: Int,
        sampleAt indexReader: (Int) -> Float
    ) -> (pcm16EquivalentRms: Float, analysisSamples: [Float]) {
        var sumSquares: Float = 0
        var analysisSamples = Array(repeating: Float.zero, count: analysisSampleCount)

        for index in 0..<frameLength {
            let sample = indexReader(index)
            sumSquares += sample * sample
            if index < analysisSampleCount {
                analysisSamples[index] = sample
            }
        }

        let rms = sqrt(sumSquares / Float(frameLength)) * Float(Int16.max)
        return (pcm16EquivalentRms: rms, analysisSamples: analysisSamples)
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

    private func observeAudioEngineConfigurationChanges() {
        engineConfigurationObserver = NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange,
            object: audioEngine,
            queue: .main
        ) { [weak self] _ in
            self?.onConfigurationChange?()
        }
    }
}

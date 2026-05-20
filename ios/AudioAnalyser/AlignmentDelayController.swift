import AVFoundation
import Foundation
import UIKit

private struct DelayMeasurementResult {
    let delaySeconds: Double
    let isPositive: Bool
}

enum AlignmentDelayError: LocalizedError {
    case invalidInputFormat
    case invalidOutputFormat

    var errorDescription: String? {
        switch self {
        case .invalidInputFormat:
            return "The iOS microphone route did not provide a usable input format for ping measurement."
        case .invalidOutputFormat:
            return "The iOS speaker route did not provide a usable output format for ping measurement."
        }
    }
}

@MainActor
final class AlignmentDelayController: ObservableObject {
    static let measurementDidFinishNotification = Notification.Name("AlignmentDelayControllerMeasurementDidFinish")

    @Published var distanceValue: Double = 10
    @Published var temperatureC: Double = 20
    @Published var usesMeters = true
    @Published private(set) var microphonePermissionState: MicrophonePermissionState
    @Published private(set) var isMeasuring = false
    @Published private(set) var detectedPolarity: Bool?
    @Published private(set) var statusText = "Delay calculator ready."

    private let audioSessionController: AudioSessionController
    private let audioSession = AVAudioSession.sharedInstance()
    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?
    private var timeoutWorkItem: DispatchWorkItem?
    private var observers: [NSObjectProtocol] = []

    private let pingLeadTimeSeconds = 0.12
    private let measurementTimeoutSeconds = 2.5
    private let detectionThresholdFloor: Float = 0.02
    private let detectionThresholdMultiplier: Float = 5.6

    init(audioSessionController: AudioSessionController = .shared) {
        self.audioSessionController = audioSessionController
        microphonePermissionState = audioSessionController.permissionState()
        observeApplicationAndAudioEvents()
    }

    deinit {
        timeoutWorkItem?.cancel()
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    var distanceUnitLabel: String {
        usesMeters ? "m" : "ft"
    }

    var distanceInMeters: Double {
        usesMeters ? distanceValue : distanceValue * 0.3048
    }

    var speedOfSoundMetersPerSecond: Double {
        331.3 + (0.606 * temperatureC)
    }

    var delayMilliseconds: Double {
        (distanceInMeters / speedOfSoundMetersPerSecond) * 1_000
    }

    func startMeasurement() {
        guard !isMeasuring else {
            return
        }

        detectedPolarity = nil
        microphonePermissionState = audioSessionController.permissionState()

        switch microphonePermissionState {
        case .granted:
            beginMeasurement()
        case .undetermined:
            statusText = "Requesting microphone access for ping measurement."
            audioSessionController.requestMicrophoneAccess { [weak self] permissionState in
                Task { @MainActor [weak self] in
                    guard let self else {
                        return
                    }

                    self.microphonePermissionState = permissionState
                    switch permissionState {
                    case .granted:
                        self.beginMeasurement()
                    case .undetermined:
                        self.statusText = "Microphone access is still pending."
                    case .denied:
                        self.statusText = "Grant microphone access to use Auto-Measure with Ping."
                    }
                }
            }
        case .denied:
            statusText = "Open Settings > Privacy & Security > Microphone to use Auto-Measure with Ping."
        }
    }

    func stopMeasurement(status: String = "Delay calculator ready.") {
        stopMeasurementInternal(status: status, deactivateSession: true, notifyCaptureStack: true)
    }

    func openAppSettings() {
        audioSessionController.openAppSettings()
    }

    private func beginMeasurement() {
        do {
            try startMeasurementEngine()
        } catch {
            stopMeasurementInternal(
                status: error.localizedDescription,
                deactivateSession: true,
                notifyCaptureStack: false
            )
        }
    }

    private func startMeasurementEngine() throws {
        stopMeasurementInternal(status: nil, deactivateSession: true, notifyCaptureStack: false)

        try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker])
        try audioSession.setPreferredSampleRate(48_000)
        try audioSession.setPreferredIOBufferDuration(0.005)
        try audioSession.setActive(true)

        let engine = AVAudioEngine()
        let playerNode = AVAudioPlayerNode()
        let inputNode = engine.inputNode

        if inputNode.isVoiceProcessingEnabled {
            try? inputNode.setVoiceProcessingEnabled(false)
        }

        guard let inputFormat = resolvedInputFormat(for: inputNode) else {
            throw AlignmentDelayError.invalidInputFormat
        }

        let outputSampleRate = max(engine.outputNode.outputFormat(forBus: 0).sampleRate, audioSession.sampleRate)
        guard let outputFormat = AVAudioFormat(standardFormatWithSampleRate: outputSampleRate, channels: 1) else {
            throw AlignmentDelayError.invalidOutputFormat
        }

        let pingBuffer = makePingBuffer(format: outputFormat)
        let inputLatency = audioSession.inputLatency
        let outputLatency = audioSession.outputLatency
        let thresholdFloor = detectionThresholdFloor
        let thresholdMultiplier = detectionThresholdMultiplier
        let transmitHostTime = mach_absolute_time() + AVAudioTime.hostTime(forSeconds: pingLeadTimeSeconds)
        var observedNoiseFloorPeak: Float = 0
        var hasDetectedTransient = false

        inputNode.installTap(
            onBus: 0,
            bufferSize: 1_024,
            format: inputFormat
        ) { [weak self] buffer, when in
            guard !hasDetectedTransient else {
                return
            }

            if let result = Self.detectTransient(
                in: buffer,
                when: when,
                transmitHostTime: transmitHostTime,
                noiseFloorPeak: &observedNoiseFloorPeak,
                inputLatency: inputLatency,
                outputLatency: outputLatency,
                thresholdFloor: thresholdFloor,
                thresholdMultiplier: thresholdMultiplier
            ) {
                hasDetectedTransient = true
                DispatchQueue.main.async { [weak self] in
                    self?.completeMeasurement(result)
                }
            }
        }

        engine.attach(playerNode)
        engine.connect(playerNode, to: engine.mainMixerNode, format: outputFormat)
        engine.prepare()
        try engine.start()

        playerNode.scheduleBuffer(pingBuffer)
        playerNode.play(at: AVAudioTime(hostTime: transmitHostTime))

        audioEngine = engine
        self.playerNode = playerNode
        isMeasuring = true
        statusText = "Listening for ping..."
        scheduleMeasurementTimeout(after: pingLeadTimeSeconds + measurementTimeoutSeconds)
    }

    private func completeMeasurement(_ result: DelayMeasurementResult) {
        let measuredDistanceMeters = result.delaySeconds * speedOfSoundMetersPerSecond
        distanceValue = usesMeters ? measuredDistanceMeters : measuredDistanceMeters * 3.28084
        detectedPolarity = result.isPositive
        stopMeasurementInternal(
            status: String(
                format: "Measured %.1f ms • %@",
                result.delaySeconds * 1_000,
                result.isPositive ? "In phase (+)" : "Inverted (-)"
            ),
            deactivateSession: true,
            notifyCaptureStack: true
        )
    }

    private func scheduleMeasurementTimeout(after delaySeconds: TimeInterval) {
        timeoutWorkItem?.cancel()

        let workItem = DispatchWorkItem { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, self.isMeasuring else {
                    return
                }

                self.stopMeasurement(
                    status: "No ping was detected. Move the device closer, raise the playback level, and retry."
                )
            }
        }

        timeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + delaySeconds, execute: workItem)
    }

    private func stopMeasurementInternal(
        status: String?,
        deactivateSession: Bool,
        notifyCaptureStack: Bool
    ) {
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil

        let hadActiveMeasurementPath = audioEngine != nil || playerNode != nil

        audioEngine?.inputNode.removeTap(onBus: 0)
        playerNode?.stop()

        if let audioEngine {
            if audioEngine.isRunning {
                audioEngine.stop()
            }
            audioEngine.reset()
        }

        audioEngine = nil
        playerNode = nil
        isMeasuring = false

        if deactivateSession {
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        }

        if let status {
            statusText = status
        }

        if hadActiveMeasurementPath, notifyCaptureStack {
            NotificationCenter.default.post(name: Self.measurementDidFinishNotification, object: nil)
        }
    }

    private func observeApplicationAndAudioEvents() {
        let center = NotificationCenter.default

        observers.append(
            center.addObserver(
                forName: UIApplication.didBecomeActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor [weak self] in
                    self?.microphonePermissionState = self?.audioSessionController.permissionState() ?? .denied
                }
            }
        )

        observers.append(
            center.addObserver(
                forName: UIApplication.didEnterBackgroundNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.isMeasuring else {
                        return
                    }

                    self.stopMeasurement(status: "Ping measurement stopped when the app moved to the background.")
                }
            }
        )

        observers.append(
            center.addObserver(
                forName: AVAudioSession.interruptionNotification,
                object: audioSession,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.isMeasuring else {
                        return
                    }

                    self.stopMeasurement(status: "Ping measurement was interrupted. Retry when the route is stable.")
                }
            }
        )

        observers.append(
            center.addObserver(
                forName: AVAudioSession.routeChangeNotification,
                object: audioSession,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.isMeasuring else {
                        return
                    }

                    self.stopMeasurement(status: "The audio route changed during ping measurement. Retry with the current route.")
                }
            }
        )
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

    private func makePingBuffer(format: AVAudioFormat) -> AVAudioPCMBuffer {
        let sampleCount = max(Int((format.sampleRate * 0.005).rounded()), 64)
        let buffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: AVAudioFrameCount(sampleCount)
        )!
        buffer.frameLength = AVAudioFrameCount(sampleCount)

        if let channelData = buffer.floatChannelData {
            for channelIndex in 0..<Int(format.channelCount) {
                for sampleIndex in 0..<sampleCount {
                    let decay = Float(1.0 - Double(sampleIndex) / Double(max(sampleCount - 1, 1)))
                    channelData[channelIndex][sampleIndex] = decay * 0.9
                }
            }
        }

        return buffer
    }

    private static func detectTransient(
        in buffer: AVAudioPCMBuffer,
        when: AVAudioTime,
        transmitHostTime: UInt64,
        noiseFloorPeak: inout Float,
        inputLatency: TimeInterval,
        outputLatency: TimeInterval,
        thresholdFloor: Float,
        thresholdMultiplier: Float
    ) -> DelayMeasurementResult? {
        guard when.hostTime > 0, buffer.frameLength > 0 else {
            return nil
        }

        let sampleRate = buffer.format.sampleRate
        guard sampleRate > 0 else {
            return nil
        }

        let bufferStartHostTime = when.hostTime
        let bufferDurationHostTime = AVAudioTime.hostTime(forSeconds: Double(buffer.frameLength) / sampleRate)
        let bufferEndHostTime = bufferStartHostTime + bufferDurationHostTime

        let peakBeforeTransmit = scanPeak(in: buffer, startIndex: 0)

        if bufferEndHostTime < transmitHostTime {
            if let peakBeforeTransmit {
                noiseFloorPeak = max(noiseFloorPeak * 0.98, peakBeforeTransmit.maxAbs)
            }
            return nil
        }

        let sampleOffsetFromTransmit: Int
        if transmitHostTime > bufferStartHostTime {
            let offsetSeconds = AVAudioTime.seconds(forHostTime: transmitHostTime - bufferStartHostTime)
            sampleOffsetFromTransmit = min(
                max(Int(floor(offsetSeconds * sampleRate)), 0),
                Int(buffer.frameLength) - 1
            )
        } else {
            sampleOffsetFromTransmit = 0
        }

        guard let peakAfterTransmit = scanPeak(in: buffer, startIndex: sampleOffsetFromTransmit) else {
            return nil
        }

        let threshold = max(noiseFloorPeak * thresholdMultiplier, thresholdFloor)
        guard peakAfterTransmit.maxAbs >= threshold else {
            return nil
        }

        let peakOffsetHostTime = AVAudioTime.hostTime(forSeconds: Double(peakAfterTransmit.sampleIndex) / sampleRate)
        let receiveHostTime = bufferStartHostTime + peakOffsetHostTime
        guard receiveHostTime >= transmitHostTime else {
            return nil
        }

        let rawDelaySeconds = AVAudioTime.seconds(forHostTime: receiveHostTime - transmitHostTime)
        let adjustedDelaySeconds = max(rawDelaySeconds - inputLatency - outputLatency, 0)

        return DelayMeasurementResult(
            delaySeconds: adjustedDelaySeconds,
            isPositive: peakAfterTransmit.isPositive
        )
    }

    private static func scanPeak(
        in buffer: AVAudioPCMBuffer,
        startIndex: Int
    ) -> (maxAbs: Float, sampleIndex: Int, isPositive: Bool)? {
        let frameLength = Int(buffer.frameLength)
        guard startIndex < frameLength else {
            return nil
        }

        var maxAbs: Float = 0
        var maxIndex = startIndex
        var isPositive = true

        switch buffer.format.commonFormat {
        case .pcmFormatFloat32:
            guard let channelData = buffer.floatChannelData else {
                return nil
            }

            for index in startIndex..<frameLength {
                let sample = channelData[0][index]
                let absSample = abs(sample)
                if absSample > maxAbs {
                    maxAbs = absSample
                    maxIndex = index
                    isPositive = sample >= 0
                }
            }
        case .pcmFormatInt16:
            guard let channelData = buffer.int16ChannelData else {
                return nil
            }

            for index in startIndex..<frameLength {
                let sample = Float(channelData[0][index]) / Float(Int16.max)
                let absSample = abs(sample)
                if absSample > maxAbs {
                    maxAbs = absSample
                    maxIndex = index
                    isPositive = sample >= 0
                }
            }
        case .pcmFormatInt32:
            guard let channelData = buffer.int32ChannelData else {
                return nil
            }

            for index in startIndex..<frameLength {
                let sample = Float(channelData[0][index]) / Float(Int32.max)
                let absSample = abs(sample)
                if absSample > maxAbs {
                    maxAbs = absSample
                    maxIndex = index
                    isPositive = sample >= 0
                }
            }
        default:
            return nil
        }

        return (maxAbs, maxIndex, isPositive)
    }
}

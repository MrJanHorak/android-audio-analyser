import AVFoundation
import Foundation
import Shared
import UIKit

enum SignalGeneratorMode: String, CaseIterable, Identifiable {
    case sine
    case white
    case pink

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sine:
            return "Sine"
        case .white:
            return "White"
        case .pink:
            return "Pink"
        }
    }

    var summary: String {
        switch self {
        case .sine:
            return "Single test tone for checking resonances and dominant frequencies."
        case .white:
            return "Broadband noise with equal energy per FFT bin."
        case .pink:
            return "Broadband noise weighted toward lower octaves for room voicing."
        }
    }

    var supportsFrequencyControl: Bool {
        self == .sine
    }
}

enum SignalGeneratorError: LocalizedError {
    case invalidOutputFormat

    var errorDescription: String? {
        switch self {
        case .invalidOutputFormat:
            return "The iOS audio output route did not provide a usable playback format."
        }
    }
}

@MainActor
final class SignalGeneratorController: ObservableObject {
    @Published var selectedMode: SignalGeneratorMode = .sine
    @Published var frequencyHz: Double = 1_000
    @Published var level: Double = 0.25
    @Published private(set) var isRunning = false
    @Published private(set) var statusText = "Signal generator ready."

    private let audioSession = AVAudioSession.sharedInstance()
    private var audioEngine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private var configurationObserver: NSObjectProtocol?
    private var backgroundObserver: NSObjectProtocol?

    init() {
        backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.stop(status: "Signal generator stopped when the app moved to the background.")
            }
        }
    }

    deinit {
        if let backgroundObserver {
            NotificationCenter.default.removeObserver(backgroundObserver)
        }
        if let configurationObserver {
            NotificationCenter.default.removeObserver(configurationObserver)
        }
    }

    func start() {
        do {
            try startPlayback()
        } catch {
            stopEngine(deactivateSession: true)
            statusText = error.localizedDescription
        }
    }

    func stop(status: String = "Signal generator ready.") {
        stopEngine(deactivateSession: true)
        statusText = status
    }

    private func startPlayback() throws {
        stopEngine(deactivateSession: true)

        let mode = selectedMode
        let frequencyHz = min(max(self.frequencyHz, 20), 20_000)
        let level = Float(min(max(self.level, 0), 1))

        try audioSession.setCategory(.playback, mode: .default, options: [])
        try audioSession.setPreferredSampleRate(48_000)
        try audioSession.setPreferredIOBufferDuration(0.01)
        try audioSession.setActive(true)

        let engine = AVAudioEngine()
        let outputFormat = engine.outputNode.outputFormat(forBus: 0)
        let sampleRate = outputFormat.sampleRate > 0 ? outputFormat.sampleRate : audioSession.sampleRate
        guard
            sampleRate > 0,
            let sourceFormat = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)
        else {
            throw SignalGeneratorError.invalidOutputFormat
        }

        var phase = 0.0
        var randomState = UInt64(Date().timeIntervalSince1970.bitPattern | 1)
        var pinkNoiseState = PinkNoiseState()

        let sourceNode = AVAudioSourceNode(format: sourceFormat) { _, _, frameCount, audioBufferList -> OSStatus in
            let bufferList = UnsafeMutableAudioBufferListPointer(audioBufferList)
            let frameTotal = Int(frameCount)

            for frameIndex in 0..<frameTotal {
                let sample: Float
                switch mode {
                case .sine:
                    sample = Self.nextSineSample(
                        phase: &phase,
                        frequencyHz: frequencyHz,
                        sampleRate: sampleRate,
                        level: level
                    )
                case .white:
                    sample = Self.nextWhiteNoiseSample(randomState: &randomState) * level
                case .pink:
                    sample = Self.nextPinkNoiseSample(
                        randomState: &randomState,
                        pinkNoiseState: &pinkNoiseState
                    ) * level
                }

                for bufferIndex in 0..<bufferList.count {
                    guard let channelPointer = bufferList[bufferIndex].mData?.assumingMemoryBound(to: Float.self) else {
                        continue
                    }
                    channelPointer[frameIndex] = sample
                }
            }

            return noErr
        }

        engine.attach(sourceNode)
        engine.connect(sourceNode, to: engine.mainMixerNode, format: sourceFormat)
        engine.mainMixerNode.outputVolume = 1
        engine.prepare()
        try engine.start()

        configurationObserver = NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange,
            object: engine,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.stop(status: "Playback route changed. Restart the signal generator.")
            }
        }

        self.audioEngine = engine
        self.sourceNode = sourceNode
        isRunning = true
        statusText = makeRunningStatus(mode: mode, frequencyHz: frequencyHz, level: level)
    }

    private func stopEngine(deactivateSession: Bool) {
        if let configurationObserver {
            NotificationCenter.default.removeObserver(configurationObserver)
            self.configurationObserver = nil
        }

        sourceNode = nil

        if let audioEngine {
            if audioEngine.isRunning {
                audioEngine.stop()
            }
            audioEngine.reset()
            self.audioEngine = nil
        }

        isRunning = false

        if deactivateSession {
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    private func makeRunningStatus(
        mode: SignalGeneratorMode,
        frequencyHz: Double,
        level: Float
    ) -> String {
        switch mode {
        case .sine:
            let frequencyLabel = AnalyzerTextFormatter.shared.formatFrequencyLabel(frequencyHz: Float(frequencyHz))
            return "Playing \(mode.title.lowercased()) at \(frequencyLabel) • \(Int(level * 100))%"
        case .white, .pink:
            return "Playing \(mode.title.lowercased()) noise • \(Int(level * 100))%"
        }
    }

    private static func nextSineSample(
        phase: inout Double,
        frequencyHz: Double,
        sampleRate: Double,
        level: Float
    ) -> Float {
        let sample = Float(sin(phase)) * level
        phase += 2.0 * .pi * frequencyHz / sampleRate
        if phase >= 2.0 * .pi {
            phase.formTruncatingRemainder(dividingBy: 2.0 * .pi)
        }
        return sample
    }

    private static func nextWhiteNoiseSample(randomState: inout UInt64) -> Float {
        randomState = randomState &* 6_364_136_223_846_793_005 &+ 1
        let normalized = Float(UInt32(truncatingIfNeeded: randomState >> 32)) / Float(UInt32.max)
        return normalized * 2 - 1
    }

    private static func nextPinkNoiseSample(
        randomState: inout UInt64,
        pinkNoiseState: inout PinkNoiseState
    ) -> Float {
        let white = nextWhiteNoiseSample(randomState: &randomState)
        pinkNoiseState.b0 = 0.99886 * pinkNoiseState.b0 + white * 0.0555179
        pinkNoiseState.b1 = 0.99332 * pinkNoiseState.b1 + white * 0.0750759
        pinkNoiseState.b2 = 0.96900 * pinkNoiseState.b2 + white * 0.1538520
        pinkNoiseState.b3 = 0.86650 * pinkNoiseState.b3 + white * 0.3104856
        pinkNoiseState.b4 = 0.55000 * pinkNoiseState.b4 + white * 0.5329522
        pinkNoiseState.b5 = -0.7616 * pinkNoiseState.b5 - white * 0.0168980
        let pink = pinkNoiseState.b0 +
            pinkNoiseState.b1 +
            pinkNoiseState.b2 +
            pinkNoiseState.b3 +
            pinkNoiseState.b4 +
            pinkNoiseState.b5 +
            pinkNoiseState.b6 +
            white * 0.5362
        pinkNoiseState.b6 = white * 0.115926
        return pink * 0.11
    }
}

private struct PinkNoiseState {
    var b0: Float = 0
    var b1: Float = 0
    var b2: Float = 0
    var b3: Float = 0
    var b4: Float = 0
    var b5: Float = 0
    var b6: Float = 0
}

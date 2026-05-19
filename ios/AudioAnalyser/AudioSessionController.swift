import AVFoundation
import Foundation
import UIKit

enum MicrophonePermissionState {
    case undetermined
    case denied
    case granted

    var title: String {
        switch self {
        case .undetermined:
            return "Not requested"
        case .denied:
            return "Denied"
        case .granted:
            return "Granted"
        }
    }

    var detail: String {
        switch self {
        case .undetermined:
            return "Microphone access is still pending. iOS must authorize capture before live analysis can begin."
        case .denied:
            return "Microphone access is denied. Open Settings to allow analysis on this device."
        case .granted:
            return "Microphone access is available for the future live analyzer."
        }
    }
}

struct AnalysisAudioSessionSetup {
    let categoryDescription: String
    let modeDescription: String
    let preferredSampleRate: Double
    let preferredBufferDuration: TimeInterval
}

struct ActiveAnalysisAudioSession {
    let categoryDescription: String
    let modeDescription: String
    let actualSampleRate: Double
    let actualBufferDuration: TimeInterval
    let inputAvailable: Bool
}

enum AudioSessionRouteChangeReason: Equatable {
    case newDeviceAvailable
    case oldDeviceUnavailable
    case categoryChange
    case override
    case wakeFromSleep
    case noSuitableRouteForCategory
    case routeConfigurationChange
    case unknown

    init(_ reason: AVAudioSession.RouteChangeReason) {
        switch reason {
        case .newDeviceAvailable:
            self = .newDeviceAvailable
        case .oldDeviceUnavailable:
            self = .oldDeviceUnavailable
        case .categoryChange:
            self = .categoryChange
        case .override:
            self = .override
        case .wakeFromSleep:
            self = .wakeFromSleep
        case .noSuitableRouteForCategory:
            self = .noSuitableRouteForCategory
        case .routeConfigurationChange:
            self = .routeConfigurationChange
        default:
            self = .unknown
        }
    }

    var description: String {
        switch self {
        case .newDeviceAvailable:
            return "A new microphone input became available."
        case .oldDeviceUnavailable:
            return "The previous microphone input is no longer available."
        case .categoryChange:
            return "The audio session category changed."
        case .override:
            return "The audio route was overridden."
        case .wakeFromSleep:
            return "The device woke from sleep and refreshed the audio route."
        case .noSuitableRouteForCategory:
            return "No suitable audio route is available for measurement capture."
        case .routeConfigurationChange:
            return "The audio route configuration changed."
        case .unknown:
            return "The audio route changed."
        }
    }

    var isCaptureRelevant: Bool {
        switch self {
        case .newDeviceAvailable, .oldDeviceUnavailable:
            return true
        default:
            return false
        }
    }
}

enum AudioSessionEvent: Equatable {
    case interruptionBegan(wasSuspended: Bool)
    case interruptionEnded(shouldResume: Bool)
    case routeChanged(reason: AudioSessionRouteChangeReason, inputAvailable: Bool, inputName: String?)
    case mediaServicesWereLost
    case mediaServicesWereReset
}

final class AudioSessionController {
    static let shared = AudioSessionController()

    var onEvent: ((AudioSessionEvent) -> Void)?

    private let audioSession = AVAudioSession.sharedInstance()
    private var observers: [NSObjectProtocol] = []

    private init() {
        observeAudioSessionEvents()
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    func permissionState() -> MicrophonePermissionState {
        currentMicrophonePermissionState()
    }

    func requestMicrophoneAccess(completion: @escaping (MicrophonePermissionState) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized:
            completion(.granted)
        case .denied, .restricted:
            completion(.denied)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .audio) { [weak self] granted in
                guard let self else {
                    completion(granted ? .granted : .denied)
                    return
                }

                completion(granted ? .granted : self.permissionState())
            }
        @unknown default:
            completion(.denied)
        }
    }

    func configureForAnalysis() throws -> AnalysisAudioSessionSetup {
        try audioSession.setCategory(.record, mode: .measurement, options: [])
        try audioSession.setPreferredSampleRate(48_000)
        try audioSession.setPreferredIOBufferDuration(0.01)

        return AnalysisAudioSessionSetup(
            categoryDescription: audioSession.category.rawValue,
            modeDescription: audioSession.mode.rawValue,
            preferredSampleRate: audioSession.preferredSampleRate,
            preferredBufferDuration: audioSession.preferredIOBufferDuration
        )
    }

    func activateForCapture() throws -> ActiveAnalysisAudioSession {
        _ = try configureForAnalysis()
        try audioSession.setActive(true)

        return ActiveAnalysisAudioSession(
            categoryDescription: audioSession.category.rawValue,
            modeDescription: audioSession.mode.rawValue,
            actualSampleRate: audioSession.sampleRate,
            actualBufferDuration: audioSession.ioBufferDuration,
            inputAvailable: audioSession.isInputAvailable
        )
    }

    func deactivateAfterCapture() throws {
        try audioSession.setActive(false, options: .notifyOthersOnDeactivation)
    }

    func isInputAvailable() -> Bool {
        audioSession.isInputAvailable
    }

    func activeInputName() -> String? {
        audioSession.currentRoute.inputs.first?.portName
    }

    func openAppSettings() {
        guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else {
            return
        }

        UIApplication.shared.open(settingsUrl)
    }

    private func observeAudioSessionEvents() {
        let center = NotificationCenter.default

        observers.append(
            center.addObserver(
                forName: AVAudioSession.interruptionNotification,
                object: audioSession,
                queue: .main
            ) { notification in
                guard
                    let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                    let interruptionType = AVAudioSession.InterruptionType(rawValue: rawType)
                else {
                    return
                }

                switch interruptionType {
                case .began:
                    let rawReason = notification.userInfo?[AVAudioSessionInterruptionReasonKey] as? UInt ?? 0
                    let wasSuspended = rawReason == 1 || rawReason == 3
                    self.onEvent?(.interruptionBegan(wasSuspended: wasSuspended))
                case .ended:
                    let rawOptions = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
                    let options = AVAudioSession.InterruptionOptions(rawValue: rawOptions)
                    self.onEvent?(.interruptionEnded(shouldResume: options.contains(.shouldResume)))
                @unknown default:
                    break
                }
            }
        )

        observers.append(
            center.addObserver(
                forName: AVAudioSession.routeChangeNotification,
                object: audioSession,
                queue: .main
            ) { notification in
                let rawReason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt ?? 0
                let routeChangeReason = AVAudioSession.RouteChangeReason(rawValue: rawReason) ?? .unknown

                self.onEvent?(
                    .routeChanged(
                        reason: AudioSessionRouteChangeReason(routeChangeReason),
                        inputAvailable: self.audioSession.isInputAvailable,
                        inputName: self.audioSession.currentRoute.inputs.first?.portName
                    )
                )
            }
        )

        observers.append(
            center.addObserver(
                forName: AVAudioSession.mediaServicesWereLostNotification,
                object: audioSession,
                queue: .main
            ) { _ in
                self.onEvent?(.mediaServicesWereLost)
            }
        )

        observers.append(
            center.addObserver(
                forName: AVAudioSession.mediaServicesWereResetNotification,
                object: audioSession,
                queue: .main
            ) { _ in
                self.onEvent?(.mediaServicesWereReset)
            }
        )
    }

    private func currentMicrophonePermissionState() -> MicrophonePermissionState {
        microphonePermissionState(for: AVCaptureDevice.authorizationStatus(for: .audio))
    }

    private func microphonePermissionState(
        for authorizationStatus: AVAuthorizationStatus
    ) -> MicrophonePermissionState {
        switch authorizationStatus {
        case .notDetermined:
            return .undetermined
        case .denied, .restricted:
            return .denied
        case .authorized:
            return .granted
        @unknown default:
            return .denied
        }
    }
}

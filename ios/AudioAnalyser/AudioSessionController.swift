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

final class AudioSessionController {
    static let shared = AudioSessionController()

    private let audioApplication = AVAudioApplication.shared
    private let audioSession = AVAudioSession.sharedInstance()
    private var observers: [NSObjectProtocol] = []

    private init() {
        observeAudioSessionEvents()
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    func permissionState() -> MicrophonePermissionState {
        switch audioApplication.recordPermission {
        case .undetermined:
            return .undetermined
        case .denied:
            return .denied
        case .granted:
            return .granted
        @unknown default:
            return .denied
        }
    }

    func requestMicrophoneAccess(completion: @escaping (MicrophonePermissionState) -> Void) {
        AVAudioApplication.requestRecordPermission { [weak self] granted in
            guard let self else {
                completion(granted ? .granted : .denied)
                return
            }

            completion(granted ? .granted : self.permissionState())
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
                print("Audio session interruption: \(notification.userInfo ?? [:])")
            }
        )

        observers.append(
            center.addObserver(
                forName: AVAudioSession.routeChangeNotification,
                object: audioSession,
                queue: .main
            ) { notification in
                print("Audio route change: \(notification.userInfo ?? [:])")
            }
        )
    }
}

import Combine
import Foundation

@MainActor
final class AnalyzerSetupViewModel: ObservableObject {
    @Published private(set) var microphonePermissionState: MicrophonePermissionState
    @Published private(set) var audioSessionSummary: String
    @Published private(set) var audioSessionDetail: String
    @Published private(set) var isWorking = false

    private let audioSessionController: AudioSessionController

    init(audioSessionController: AudioSessionController = .shared) {
        self.audioSessionController = audioSessionController
        let permissionState = audioSessionController.permissionState()
        microphonePermissionState = permissionState
        audioSessionSummary = ""
        audioSessionDetail = ""
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

    func refreshSetup() {
        microphonePermissionState = audioSessionController.permissionState()

        switch microphonePermissionState {
        case .undetermined:
            audioSessionSummary = "Waiting for microphone authorization"
            audioSessionDetail = "The iOS analyzer session will stay inactive until permission is granted."
        case .denied:
            audioSessionSummary = "Microphone access is blocked"
            audioSessionDetail = "iOS will not prompt again after a denial. Use Settings to re-enable microphone access."
        case .granted:
            configureAudioSession()
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
}

import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var setupViewModel = AnalyzerSetupViewModel()
    private let suggestedCutHz = AnalyzerMath.shared.suggestCutFrequency(frequencyHz: Float(440))

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("iOS port in progress")
                            .font(.largeTitle.weight(.bold))
                        Text("This SwiftUI shell now links the shared Kotlin core and has the first iOS-only microphone setup flow in place.")
                            .foregroundStyle(.secondary)
                    }

                    GroupBox("Current status") {
                        VStack(alignment: .leading, spacing: 10) {
                            StatusRow(
                                title: "Shared analyzer math",
                                detail: "AnalyzerMath is linked from Kotlin. Suggested cut for 440 Hz: \(Int(suggestedCutHz)) Hz"
                            )
                            StatusRow(title: "Shared models", detail: "Feedback and target-curve models moved to shared code")
                            StatusRow(title: "Shared feedback tracker", detail: "Peak tracking logic now lives in shared code")
                            StatusRow(
                                title: "Microphone permission",
                                detail: "\(setupViewModel.microphonePermissionState.title) - \(setupViewModel.microphonePermissionState.detail)"
                            )
                            StatusRow(
                                title: "Audio session",
                                detail: "\(setupViewModel.audioSessionSummary). \(setupViewModel.audioSessionDetail)"
                            )
                            StatusRow(title: "iOS UI shell", detail: "SwiftUI app scaffolded, linked to shared code, and buildable in Xcode")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("iOS setup") {
                        VStack(alignment: .leading, spacing: 12) {
                            Button(setupViewModel.primaryActionTitle) {
                                setupViewModel.performPrimaryAction()
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(setupViewModel.isWorking)

                            if setupViewModel.isWorking {
                                ProgressView("Updating microphone access...")
                            } else {
                                Text("The audio session is configured for measurement capture but intentionally stays inactive until live analysis starts.")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("Next milestones") {
                        VStack(alignment: .leading, spacing: 10) {
                            BulletRow(text: "Start live microphone capture with an AVAudioEngine input tap")
                            BulletRow(text: "Feed live iOS buffers into the shared analyzer pipeline")
                            BulletRow(text: "Start live SPL and spectrum rendering on iOS")
                            BulletRow(text: "Move more pure analyzer helpers and defaults into shared code")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(24)
            }
            .navigationTitle("AudioAnalyser")
        }
    }
}

private struct StatusRow: View {
    let title: String
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.headline)
            Text(detail)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

private struct BulletRow: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("•")
            Text(text)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    ContentView()
}

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
                        Text("This SwiftUI shell now links the shared Kotlin core, handles microphone setup, starts live AVAudioEngine capture, and renders a first real analyzer UI from shared frame analysis.")
                            .foregroundStyle(.secondary)
                    }

                    GroupBox("Live analyzer") {
                        if let liveAnalyzerSnapshot = setupViewModel.liveAnalyzerSnapshot {
                            VStack(alignment: .leading, spacing: 16) {
                                LiveAnalyzerMetricsView(snapshot: liveAnalyzerSnapshot)
                                SpectrumBarsView(bars: liveAnalyzerSnapshot.spectrumBars)
                                SpectrumLegendView(sampleRateHz: liveAnalyzerSnapshot.sampleRateHz)
                            }
                        } else {
                            AnalyzerPlaceholderView(isCapturing: setupViewModel.isCapturing)
                        }
                    }

                    GroupBox("iOS setup and capture") {
                        VStack(alignment: .leading, spacing: 12) {
                            Button(setupViewModel.primaryActionTitle) {
                                setupViewModel.performPrimaryAction()
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(setupViewModel.isWorking)

                            Button(setupViewModel.captureActionTitle) {
                                setupViewModel.toggleLiveCapture()
                            }
                            .buttonStyle(.bordered)
                            .disabled(setupViewModel.isCaptureActionDisabled)

                            if setupViewModel.isWorking {
                                ProgressView("Updating microphone access...")
                            } else {
                                Text(
                                    setupViewModel.isCapturing
                                        ? "Live capture keeps the measurement-mode audio session active only while the AVAudioEngine tap is running."
                                        : "The audio session stays inactive until you explicitly start live capture from this screen."
                                )
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
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
                            StatusRow(
                                title: "Live capture",
                                detail: "\(setupViewModel.liveCaptureSummary). \(setupViewModel.liveCaptureDetail)"
                            )
                            StatusRow(title: "iOS UI shell", detail: "SwiftUI app scaffolded, linked to shared code, and buildable in Xcode")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("Next milestones") {
                        VStack(alignment: .leading, spacing: 10) {
                            BulletRow(text: "Feed shared frame results into the existing feedback tracker on iOS")
                            BulletRow(text: "Expand the iOS analyzer UI with a denser live spectrum and history views")
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

private struct LiveAnalyzerMetricsView: View {
    let snapshot: LiveAnalyzerSnapshot

    private let columns = [
        GridItem(.adaptive(minimum: 110), spacing: 12)
    ]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
            MetricCard(title: "dB(Z)", value: decibelString(snapshot.zLevelDb))
            MetricCard(title: "dB(A)", value: decibelString(snapshot.aLevelDb))
            MetricCard(title: "dB(C)", value: decibelString(snapshot.cLevelDb))
            MetricCard(title: "Dominant", value: frequencyString(snapshot.dominantFrequencyHz))
            MetricCard(title: "Suggest cut", value: frequencyString(snapshot.suggestedCutFrequencyHz))
            MetricCard(title: "Sample rate", value: "\(Int(snapshot.sampleRateHz)) Hz")
            MetricCard(title: "Frame size", value: "\(snapshot.frameLength) samples")
            MetricCard(title: "Channels", value: "\(snapshot.channelCount)")
        }
    }

    private func decibelString(_ value: Float) -> String {
        String(format: "%.1f dB", value)
    }

    private func frequencyString(_ value: Float) -> String {
        guard value > 0 else {
            return "--"
        }

        return String(format: "%.0f Hz", value)
    }
}

private struct MetricCard: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3.weight(.semibold))
                .monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.secondary.opacity(0.08))
        )
    }
}

private struct SpectrumBarsView: View {
    let bars: [Float]

    var body: some View {
        Canvas { context, size in
            guard !bars.isEmpty else {
                return
            }

            let spacing: CGFloat = 3
            let count = CGFloat(bars.count)
            let barWidth = max(2, (size.width - spacing * (count - 1)) / count)

            for (index, bar) in bars.enumerated() {
                let normalizedBar = CGFloat(min(max(bar, 0), 1))
                let height = max(2, size.height * normalizedBar)
                let x = CGFloat(index) * (barWidth + spacing)
                let rect = CGRect(x: x, y: size.height - height, width: barWidth, height: height)
                let path = Path(roundedRect: rect, cornerRadius: min(barWidth / 2, 4))
                let color = Color(
                    hue: 0.58 - Double(normalizedBar) * 0.12,
                    saturation: 0.70,
                    brightness: 0.78 + Double(normalizedBar) * 0.18
                )
                context.fill(path, with: .color(color))
            }
        }
        .frame(height: 170)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.black.opacity(0.08))
        )
        .animation(nil, value: bars)
    }
}

private struct SpectrumLegendView: View {
    let sampleRateHz: Double

    var body: some View {
        HStack {
            Text("30 Hz")
            Spacer()
            Text("1 kHz")
            Spacer()
            Text("\(Int(sampleRateHz / 2)) Hz")
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .monospacedDigit()
    }
}

private struct AnalyzerPlaceholderView: View {
    let isCapturing: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(isCapturing ? "Waiting for live analyzer data..." : "Live analyzer preview is ready.")
                .font(.headline)
            Text(
                isCapturing
                    ? "The first shared frame-analysis snapshot will populate this section as soon as the capture loop produces it."
                    : "Start live capture to see weighted levels, dominant frequency, and the shared-spectrum view update in real time."
            )
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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

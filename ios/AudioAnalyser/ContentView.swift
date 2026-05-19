import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var setupViewModel = AnalyzerSetupViewModel()
    @AppStorage("ios-feedback-markers-enabled") private var feedbackMarkersEnabled = true
    @AppStorage("ios-feedback-display-count") private var feedbackDisplayCount = 3
    private let suggestedCutHz = AnalyzerMath.shared.suggestCutFrequency(frequencyHz: Float(440))

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("iOS port in progress")
                            .font(.largeTitle.weight(.bold))
                        Text("This SwiftUI shell now links the shared Kotlin core, handles microphone setup, starts live AVAudioEngine capture, and renders a richer analyzer UI with shared metrics, history, spectrum, and feedback guidance.")
                            .foregroundStyle(.secondary)
                    }

                    GroupBox("Live analyzer") {
                        if let liveAnalyzerSnapshot = setupViewModel.liveAnalyzerSnapshot {
                            VStack(alignment: .leading, spacing: 16) {
                                LiveAnalyzerMetricsView(snapshot: liveAnalyzerSnapshot)
                                LevelHistorySection(snapshot: liveAnalyzerSnapshot)
                                SpectrumBarsView(
                                    bars: liveAnalyzerSnapshot.spectrumBars,
                                    snapshotOverlayBars: setupViewModel.snapshotOverlayBars,
                                    feedbackMarkers: visibleFeedbackMarkers(from: liveAnalyzerSnapshot)
                                )
                                SpectrumLegendView(sampleRateHz: liveAnalyzerSnapshot.sampleRateHz)
                                SpectrumHistoryWaterfallView(rows: liveAnalyzerSnapshot.spectrumHistoryRows)
                                FeedbackFocusSection(
                                    primarySuggestion: visibleFeedbackSuggestions(from: liveAnalyzerSnapshot).first,
                                    feedbackMarkerCount: max(1, feedbackDisplayCount),
                                    markersEnabled: feedbackMarkersEnabled
                                )
                                FeedbackSuggestionsView(
                                    suggestions: visibleFeedbackSuggestions(from: liveAnalyzerSnapshot)
                                )
                            }
                        } else {
                            AnalyzerPlaceholderView(isCapturing: setupViewModel.isCapturing)
                        }
                    }

                    GroupBox("Analyzer tools") {
                        VStack(alignment: .leading, spacing: 18) {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Feedback guidance")
                                    .font(.headline)

                                Toggle(
                                    "Show feedback markers on the live spectrum",
                                    isOn: $feedbackMarkersEnabled
                                )

                                VStack(alignment: .leading, spacing: 8) {
                                    Text("Feedback focus")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.secondary)

                                    Picker("Feedback focus", selection: $feedbackDisplayCount) {
                                        Text("Top 1").tag(1)
                                        Text("Top 3").tag(3)
                                    }
                                    .pickerStyle(.segmented)
                                }

                                Text(
                                    feedbackMarkersEnabled
                                        ? "Markers and suggestion cards now follow the strongest \(feedbackDisplayCount == 1 ? "shared feedback peak" : "shared feedback peaks")."
                                        : "Feedback guidance stays available in the cards below even while spectrum markers are hidden."
                                )
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            }

                            Divider()

                            VStack(alignment: .leading, spacing: 10) {
                                Text("Spectrum snapshot")
                                    .font(.headline)

                                HStack(spacing: 10) {
                                    Button(setupViewModel.snapshotActionTitle) {
                                        setupViewModel.captureSpectrumSnapshot()
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(setupViewModel.isSnapshotCaptureDisabled)

                                    Button(
                                        setupViewModel.isSnapshotCompareEnabled
                                            ? "Compare on"
                                            : "Compare off"
                                    ) {
                                        setupViewModel.toggleSnapshotCompare()
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(!setupViewModel.canToggleSnapshotCompare)

                                    Button("Clear") {
                                        setupViewModel.clearSpectrumSnapshot()
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(!setupViewModel.canClearSnapshot)
                                }

                                Text(setupViewModel.snapshotStatusText)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }

                            Divider()

                            VStack(alignment: .leading, spacing: 10) {
                                Text("Decibel logger")
                                    .font(.headline)

                                HStack(spacing: 10) {
                                    Button(setupViewModel.loggingActionTitle) {
                                        setupViewModel.toggleLogging()
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(setupViewModel.isLoggingActionDisabled)

                                    if let exportedAnalyzerLog = setupViewModel.exportedAnalyzerLog {
                                        ShareLink(item: exportedAnalyzerLog.fileURL) {
                                            Label("Share CSV", systemImage: "square.and.arrow.up")
                                        }
                                        .buttonStyle(.bordered)
                                    }
                                }

                                Text(setupViewModel.loggingStatusText)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)

                                if let exportedAnalyzerLog = setupViewModel.exportedAnalyzerLog {
                                    Text(
                                        "Prepared \(exportedAnalyzerLog.rowCount) samples on \(exportedAnalyzerLog.exportedAt.formatted(date: .abbreviated, time: .shortened))."
                                    )
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .monospacedDigit()
                                }
                            }

                            Divider()

                            VStack(alignment: .leading, spacing: 10) {
                                Text("Analyzer sample export")
                                    .font(.headline)

                                HStack(spacing: 10) {
                                    Button("Prepare JSON sample") {
                                        setupViewModel.prepareAnalyzerSampleExport(
                                            feedbackMarkersEnabled: feedbackMarkersEnabled,
                                            feedbackDisplayCount: feedbackDisplayCount
                                        )
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(setupViewModel.isAnalyzerSampleExportDisabled)

                                    if let exportedAnalyzerSample = setupViewModel.exportedAnalyzerSample {
                                        ShareLink(item: exportedAnalyzerSample.fileURL) {
                                            Label("Share JSON", systemImage: "square.and.arrow.up")
                                        }
                                        .buttonStyle(.bordered)
                                    }
                                }

                                Text(setupViewModel.analyzerSampleStatusText)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)

                                if let exportedAnalyzerSample = setupViewModel.exportedAnalyzerSample {
                                    Text(
                                        "Prepared analyzer sample on \(exportedAnalyzerSample.exportedAt.formatted(date: .abbreviated, time: .shortened))."
                                    )
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .monospacedDigit()
                                }
                            }

                            Divider()

                            VStack(alignment: .leading, spacing: 10) {
                                Text("Waterfall export")
                                    .font(.headline)

                                HStack(spacing: 10) {
                                    Button("Prepare waterfall JSON") {
                                        setupViewModel.prepareWaterfallExport()
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(setupViewModel.isWaterfallExportDisabled)

                                    if let exportedWaterfallSample = setupViewModel.exportedWaterfallSample {
                                        ShareLink(item: exportedWaterfallSample.fileURL) {
                                            Label("Share waterfall", systemImage: "square.and.arrow.up")
                                        }
                                        .buttonStyle(.bordered)
                                    }
                                }

                                Text(setupViewModel.waterfallExportStatusText)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)

                                if let exportedWaterfallSample = setupViewModel.exportedWaterfallSample {
                                    Text(
                                        "Prepared \(exportedWaterfallSample.rowCount) waterfall rows on \(exportedWaterfallSample.exportedAt.formatted(date: .abbreviated, time: .shortened))."
                                    )
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .monospacedDigit()
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
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
                            StatusRow(
                                title: "Analyzer history",
                                detail: "iOS now shows live min/avg/max stats, a recent dB(Z) sparkline, and a compact spectrum history waterfall."
                            )
                            StatusRow(
                                title: "Analyzer tools",
                                detail: "iOS can save live spectrum snapshots, compare them against matching live input, export CSV logs, prepare JSON analyzer samples, and export dedicated waterfall sessions."
                            )
                            StatusRow(title: "iOS UI shell", detail: "SwiftUI app scaffolded, linked to shared code, and buildable in Xcode")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("Next milestones") {
                        VStack(alignment: .leading, spacing: 10) {
                            BulletRow(text: "Move more pure analyzer helpers, defaults, and comparison logic into shared code")
                            BulletRow(text: "Expand saved-session tooling beyond JSON/CSV with additional export targets if needed")
                            BulletRow(text: "Revisit larger shared analyzer state only after multiplatform-safe concurrency is designed")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(24)
            }
            .navigationTitle("AudioAnalyser")
        }
    }

    private func visibleFeedbackSuggestions(from snapshot: LiveAnalyzerSnapshot) -> [LiveFeedbackSuggestion] {
        Array(snapshot.feedbackSuggestions.prefix(max(1, feedbackDisplayCount)))
    }

    private func visibleFeedbackMarkers(from snapshot: LiveAnalyzerSnapshot) -> [LiveFeedbackMarker] {
        guard feedbackMarkersEnabled else {
            return []
        }

        return Array(snapshot.feedbackMarkers.prefix(max(1, feedbackDisplayCount)))
    }
}

private struct LevelHistorySection: View {
    let snapshot: LiveAnalyzerSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Level history")
                .font(.headline)

            HStack(spacing: 10) {
                LevelStatChip(title: "Min", value: snapshot.minZLevelDb)
                LevelStatChip(title: "Avg", value: snapshot.avgZLevelDb)
                LevelStatChip(title: "Max", value: snapshot.maxZLevelDb)
            }

            HistorySparklineView(history: snapshot.zLevelHistory)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct LevelStatChip: View {
    let title: String
    let value: Float

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(String(format: "%.1f dB", value))
                .font(.headline.weight(.semibold))
                .monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.secondary.opacity(0.08))
        )
    }
}

private struct HistorySparklineView: View {
    let history: [Float]

    var body: some View {
        Canvas { context, size in
            guard history.count > 1 else {
                return
            }

            let maximum = max(history.max() ?? 0, 1)
            let stepX = size.width / CGFloat(history.count - 1)

            var linePath = Path()
            var fillPath = Path()

            for (index, value) in history.enumerated() {
                let x = CGFloat(index) * stepX
                let y = size.height - CGFloat(value / maximum) * size.height
                let point = CGPoint(x: x, y: y)

                if index == 0 {
                    linePath.move(to: point)
                    fillPath.move(to: CGPoint(x: x, y: size.height))
                    fillPath.addLine(to: point)
                } else {
                    linePath.addLine(to: point)
                    fillPath.addLine(to: point)
                }
            }

            fillPath.addLine(to: CGPoint(x: size.width, y: size.height))
            fillPath.closeSubpath()

            context.fill(
                fillPath,
                with: .linearGradient(
                    Gradient(colors: [
                        Color.accentColor.opacity(0.22),
                        Color.accentColor.opacity(0.02)
                    ]),
                    startPoint: CGPoint(x: size.width / 2, y: 0),
                    endPoint: CGPoint(x: size.width / 2, y: size.height)
                )
            )
            context.stroke(
                linePath,
                with: .color(Color.accentColor.opacity(0.85)),
                style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(height: 72)
        .padding(.vertical, 4)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.black.opacity(0.08))
        )
        .animation(nil, value: history)
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
    let snapshotOverlayBars: [Float]?
    let feedbackMarkers: [LiveFeedbackMarker]

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

            if let snapshotOverlayBars, snapshotOverlayBars.count == bars.count {
                var overlayPath = Path()

                for (index, bar) in snapshotOverlayBars.enumerated() {
                    let normalizedBar = CGFloat(min(max(bar, 0), 1))
                    let height = max(2, size.height * normalizedBar)
                    let x = CGFloat(index) * (barWidth + spacing) + (barWidth / 2)
                    let point = CGPoint(x: x, y: size.height - height)

                    if index == 0 {
                        overlayPath.move(to: point)
                    } else {
                        overlayPath.addLine(to: point)
                    }
                }

                context.stroke(
                    overlayPath,
                    with: .color(Color(red: 0, green: 0.67, blue: 0.76)),
                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round)
                )
            }

            for marker in feedbackMarkers {
                let x = min(max(CGFloat(marker.normalizedX), 0), 1) * size.width
                let markerColor = feedbackMarkerColor(for: marker.stabilityKey)

                context.stroke(
                    Path { path in
                        path.move(to: CGPoint(x: x, y: 0))
                        path.addLine(to: CGPoint(x: x, y: size.height))
                    },
                    with: .color(markerColor.opacity(0.92)),
                    style: StrokeStyle(lineWidth: 2, lineCap: .round)
                )

                context.fill(
                    Path(
                        ellipseIn: CGRect(
                            x: x - 4,
                            y: 8,
                            width: 8,
                            height: 8
                        )
                    ),
                    with: .color(markerColor)
                )
            }
        }
        .frame(height: 170)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.black.opacity(0.08))
        )
        .animation(nil, value: bars)
        .animation(nil, value: snapshotOverlayBars ?? [])
        .animation(nil, value: feedbackMarkers)
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

private struct SpectrumHistoryWaterfallView: View {
    let rows: [[Float]]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Spectrum history")
                .font(.headline)

            Canvas { context, size in
                guard let firstRow = rows.first, !firstRow.isEmpty else {
                    return
                }

                let rowHeight = size.height / CGFloat(rows.count)
                let columnWidth = size.width / CGFloat(firstRow.count)

                for (rowIndex, row) in rows.enumerated() {
                    let y = CGFloat(rowIndex) * rowHeight
                    for (columnIndex, value) in row.enumerated() {
                        let x = CGFloat(columnIndex) * columnWidth
                        let rect = CGRect(
                            x: x,
                            y: y,
                            width: columnWidth + 0.5,
                            height: rowHeight + 0.5
                        )
                        context.fill(
                            Path(rect),
                            with: .color(waterfallColor(for: value))
                        )
                    }
                }
            }
            .frame(height: 150)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.black.opacity(0.08))
            )
            .animation(nil, value: rows)

            Text("Older frames at the top, newest frames at the bottom.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FeedbackFocusSection: View {
    let primarySuggestion: LiveFeedbackSuggestion?
    let feedbackMarkerCount: Int
    let markersEnabled: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Feedback focus")
                .font(.headline)

            if let primarySuggestion {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(String(format: "%.0f Hz", primarySuggestion.frequencyHz))
                                .font(.title3.weight(.semibold))
                            Text(String(format: "Cut %.0f Hz", primarySuggestion.suggestedCutHz))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        FeedbackTierBadge(stabilityKey: primarySuggestion.stabilityKey)
                    }

                    Text(
                        feedbackFocusDetail(
                            for: primarySuggestion,
                            feedbackMarkerCount: feedbackMarkerCount,
                            markersEnabled: markersEnabled
                        )
                    )
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.secondary.opacity(0.08))
                )
            } else {
                Text("No stable feedback focus yet. Sustain a ringing source and the strongest shared peak will be summarized here.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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

private struct FeedbackSuggestionsView: View {
    let suggestions: [LiveFeedbackSuggestion]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Feedback suggestions")
                .font(.headline)

            if suggestions.isEmpty {
                Text("No stable feedback peaks detected yet.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                VStack(spacing: 10) {
                    ForEach(suggestions) { suggestion in
                        FeedbackSuggestionRow(suggestion: suggestion)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FeedbackSuggestionRow: View {
    let suggestion: LiveFeedbackSuggestion

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(String(format: "%.0f Hz", suggestion.frequencyHz))
                        .font(.headline)
                    FeedbackTierBadge(stabilityKey: suggestion.stabilityKey)
                }
                Spacer()
                Text(String(format: "Cut %.0f Hz", suggestion.suggestedCutHz))
                    .font(.subheadline.weight(.semibold))
            }

            HStack(spacing: 12) {
                SuggestionMeter(label: "Level", value: suggestion.normalizedLevel)
                SuggestionMeter(label: "Stable", value: suggestion.stability)
                Text("Hold \(suggestion.holdFrames)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color.secondary.opacity(0.08))
        )
    }
}

private struct SuggestionMeter: View {
    let label: String
    let value: Float

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.secondary.opacity(0.18))
                    Capsule()
                        .fill(Color.accentColor)
                        .frame(width: geometry.size.width * CGFloat(min(max(value, 0), 1)))
                }
            }
            .frame(height: 8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FeedbackTierBadge: View {
    let stabilityKey: String

    var body: some View {
        Text(feedbackTierTitle(for: stabilityKey))
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule()
                    .fill(feedbackMarkerColor(for: stabilityKey).opacity(0.16))
            )
            .foregroundStyle(feedbackMarkerColor(for: stabilityKey))
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

private func waterfallColor(for value: Float) -> Color {
    let clamped = Double(min(max(value, 0), 1))
    let stops: [(red: Double, green: Double, blue: Double)] = [
        (0.0, 0.0, 0.02),
        (0.16, 0.10, 0.35),
        (0.36, 0.07, 0.57),
        (0.60, 0.23, 0.42),
        (0.87, 0.42, 0.15),
        (0.99, 0.65, 0.05),
        (0.99, 0.99, 0.64)
    ]
    let scaled = clamped * Double(stops.count - 1)
    let startIndex = min(Int(scaled), stops.count - 2)
    let endIndex = startIndex + 1
    let fraction = scaled - Double(startIndex)
    let start = stops[startIndex]
    let end = stops[endIndex]

    return Color(
        red: start.red + (end.red - start.red) * fraction,
        green: start.green + (end.green - start.green) * fraction,
        blue: start.blue + (end.blue - start.blue) * fraction
    )
}

private func feedbackMarkerColor(for stabilityKey: String) -> Color {
    switch stabilityKey {
    case "stable":
        return Color(red: 0.83, green: 0.19, blue: 0.19)
    case "building":
        return Color(red: 0.96, green: 0.49, blue: 0.08)
    default:
        return Color(red: 0.96, green: 0.76, blue: 0.13)
    }
}

private func feedbackTierTitle(for stabilityKey: String) -> String {
    switch stabilityKey {
    case "stable":
        return "Stable"
    case "building":
        return "Building"
    default:
        return "Watch"
    }
}

private func feedbackFocusDetail(
    for suggestion: LiveFeedbackSuggestion,
    feedbackMarkerCount: Int,
    markersEnabled: Bool
) -> String {
    let markerDetail = markersEnabled
        ? "Spectrum markers track the top \(feedbackMarkerCount == 1 ? "shared peak" : "\(feedbackMarkerCount) shared peaks")."
        : "Spectrum markers are hidden right now."

    switch suggestion.stabilityKey {
    case "stable":
        return "This resonance is holding consistently. Cut near \(Int(suggestion.suggestedCutHz.rounded())) Hz first and verify the live bars settle. \(markerDetail)"
    case "building":
        return "This hotspot is strengthening over consecutive frames. Keep an eye on \(Int(suggestion.frequencyHz.rounded())) Hz and be ready to trim around \(Int(suggestion.suggestedCutHz.rounded())) Hz. \(markerDetail)"
    default:
        return "A possible feedback hotspot is emerging around \(Int(suggestion.frequencyHz.rounded())) Hz. Watch for it to build before making a deeper cut. \(markerDetail)"
    }
}

#Preview {
    ContentView()
}

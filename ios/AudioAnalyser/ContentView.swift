import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var setupViewModel = AnalyzerSetupViewModel()
    @AppStorage("ios-feedback-markers-enabled") private var feedbackMarkersEnabled = true
    @AppStorage("ios-feedback-display-count") private var feedbackDisplayCount = 3
    @AppStorage("ios-show-waterfall") private var showWaterfall = false
    @State private var selectedWeighting = "Z"
    @State private var showAnalyzerTools = false

    private var isCaptureFailure: Bool {
        setupViewModel.liveCaptureSummary.localizedCaseInsensitiveContains("failed")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    CaptureControlCard(
                        setupViewModel: setupViewModel,
                        isCaptureFailure: isCaptureFailure
                    )

                    LiveMeterCard(
                        snapshot: setupViewModel.liveAnalyzerSnapshot,
                        selectedWeighting: $selectedWeighting
                    )

                    AnalyzerVisualizerCard(
                        snapshot: setupViewModel.liveAnalyzerSnapshot,
                        isCapturing: setupViewModel.isCapturing,
                        showWaterfall: $showWaterfall,
                        snapshotOverlayBars: setupViewModel.snapshotOverlayBars,
                        feedbackMarkers: setupViewModel.liveAnalyzerSnapshot.map {
                            visibleFeedbackMarkers(from: $0)
                        } ?? [],
                        feedbackSuggestions: setupViewModel.liveAnalyzerSnapshot.map {
                            visibleFeedbackSuggestions(from: $0)
                        } ?? [],
                        feedbackMarkerCount: max(1, feedbackDisplayCount),
                        feedbackMarkersEnabled: feedbackMarkersEnabled,
                        snapshotActionTitle: setupViewModel.snapshotActionTitle,
                        isSnapshotCaptureDisabled: setupViewModel.isSnapshotCaptureDisabled,
                        isSnapshotCompareEnabled: setupViewModel.isSnapshotCompareEnabled,
                        canToggleSnapshotCompare: setupViewModel.canToggleSnapshotCompare,
                        canClearSnapshot: setupViewModel.canClearSnapshot,
                        onCaptureSnapshot: {
                            setupViewModel.captureSpectrumSnapshot()
                        },
                        onToggleSnapshotCompare: {
                            setupViewModel.toggleSnapshotCompare()
                        },
                        onClearSnapshot: {
                            setupViewModel.clearSpectrumSnapshot()
                        }
                    )
                }
                .padding(16)
            }
            .navigationTitle("AudioAnalyser")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showAnalyzerTools = true
                    } label: {
                        Label("Tools", systemImage: "slider.horizontal.3")
                    }
                }
            }
            .sheet(isPresented: $showAnalyzerTools) {
                NavigationStack {
                    AnalyzerToolsSheet(
                        setupViewModel: setupViewModel,
                        feedbackMarkersEnabled: $feedbackMarkersEnabled,
                        feedbackDisplayCount: $feedbackDisplayCount,
                        showWaterfall: $showWaterfall
                    )
                }
                .presentationDetents([.medium, .large])
            }
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

private struct AnalyzerCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color.secondary.opacity(0.08))
        )
    }
}

private struct StatusPill: View {
    let text: String
    let tint: Color

    init(text: String, tint: Color = .accentColor) {
        self.text = text
        self.tint = tint
    }

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(tint.opacity(0.14))
            )
            .foregroundStyle(tint)
    }
}

private struct CaptureControlCard: View {
    @ObservedObject var setupViewModel: AnalyzerSetupViewModel
    let isCaptureFailure: Bool

    private var stateTint: Color {
        if isCaptureFailure {
            return .orange
        }

        return setupViewModel.isCapturing ? .green : .secondary
    }

    private var secondaryStatusText: String {
        if isCaptureFailure {
            return setupViewModel.liveCaptureDetail
        }

        if setupViewModel.isWorking {
            return "Updating microphone access..."
        }

        if setupViewModel.isCapturing {
            return "Listening to live microphone input."
        }

        switch setupViewModel.microphonePermissionState {
        case .granted:
            return "Tap Start live capture."
        case .undetermined:
            return "Allow microphone access to begin."
        case .denied:
            return "Microphone access is blocked."
        }
    }

    var body: some View {
        AnalyzerCard {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(setupViewModel.isCapturing ? "Live capture" : "Analyzer ready")
                        .font(.headline)

                    HStack(spacing: 8) {
                        StatusPill(
                            text: "Mic \(setupViewModel.microphonePermissionState.title)",
                            tint: setupViewModel.microphonePermissionState == .granted ? .green : .secondary
                        )
                        StatusPill(
                            text: setupViewModel.isCapturing ? "Running" : (isCaptureFailure ? "Failed" : "Idle"),
                            tint: stateTint
                        )
                    }
                }

                Spacer()
            }

            HStack(spacing: 10) {
                if setupViewModel.microphonePermissionState != .granted {
                    Button(setupViewModel.primaryActionTitle) {
                        setupViewModel.performPrimaryAction()
                    }
                    .buttonStyle(.bordered)
                    .disabled(setupViewModel.isWorking)
                }

                Button(setupViewModel.captureActionTitle) {
                    setupViewModel.toggleLiveCapture()
                }
                .buttonStyle(.borderedProminent)
                .disabled(setupViewModel.isCaptureActionDisabled)
            }

            if setupViewModel.isWorking {
                ProgressView("Updating microphone access...")
            } else {
                Text(secondaryStatusText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(isCaptureFailure ? 4 : 2)
            }
        }
    }
}

private struct LiveMeterCard: View {
    let snapshot: LiveAnalyzerSnapshot?
    @Binding var selectedWeighting: String

    private var displayDbValue: Float? {
        guard let snapshot else {
            return nil
        }

        switch selectedWeighting {
        case "A":
            return snapshot.aLevelDb
        case "C":
            return snapshot.cLevelDb
        default:
            return snapshot.zLevelDb
        }
    }

    private var displayDbColor: Color {
        guard let displayDbValue else {
            return .primary
        }

        if displayDbValue > 90 {
            return .red
        }

        if displayDbValue > 80 {
            return Color(red: 0.96, green: 0.66, blue: 0.14)
        }

        return .accentColor
    }

    private var displayDbText: String {
        guard let displayDbValue else {
            return "-- dB(\(selectedWeighting))"
        }

        return sharedDecibelLabel(displayDbValue, unitSuffix: "dB(\(selectedWeighting))")
    }

    var body: some View {
        AnalyzerCard {
            Text("Sound level")
                .font(.headline)
                .foregroundStyle(.secondary)

            Text(displayDbText)
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(displayDbColor)
                .monospacedDigit()

            HStack(spacing: 8) {
                WeightingChip(
                    title: "A",
                    value: snapshot?.aLevelDb,
                    isSelected: selectedWeighting == "A"
                ) {
                    selectedWeighting = "A"
                }
                WeightingChip(
                    title: "C",
                    value: snapshot?.cLevelDb,
                    isSelected: selectedWeighting == "C"
                ) {
                    selectedWeighting = "C"
                }
                WeightingChip(
                    title: "Z",
                    value: snapshot?.zLevelDb,
                    isSelected: selectedWeighting == "Z"
                ) {
                    selectedWeighting = "Z"
                }
            }

            HStack(spacing: 10) {
                LevelStatChip(title: "Min", value: snapshot?.minZLevelDb)
                LevelStatChip(title: "Avg", value: snapshot?.avgZLevelDb)
                LevelStatChip(title: "Max", value: snapshot?.maxZLevelDb)
            }

            HistorySparklineView(history: snapshot?.zLevelHistory ?? [])
        }
    }
}

private struct WeightingChip: View {
    let title: String
    let value: Float?
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text("dB(\(title))")
                    .font(.caption2.weight(.semibold))
                Text(value.map(sharedDecibelNumber) ?? "--")
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? Color.accentColor.opacity(0.18) : Color.black.opacity(0.06))
            )
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
    }
}

private struct AnalyzerVisualizerCard: View {
    let snapshot: LiveAnalyzerSnapshot?
    let isCapturing: Bool
    @Binding var showWaterfall: Bool
    let snapshotOverlayBars: [Float]?
    let feedbackMarkers: [LiveFeedbackMarker]
    let feedbackSuggestions: [LiveFeedbackSuggestion]
    let feedbackMarkerCount: Int
    let feedbackMarkersEnabled: Bool
    let snapshotActionTitle: String
    let isSnapshotCaptureDisabled: Bool
    let isSnapshotCompareEnabled: Bool
    let canToggleSnapshotCompare: Bool
    let canClearSnapshot: Bool
    let onCaptureSnapshot: () -> Void
    let onToggleSnapshotCompare: () -> Void
    let onClearSnapshot: () -> Void

    var body: some View {
        AnalyzerCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Frequency spectrum")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                    Text(snapshot.map { sharedFrequencyLabel($0.dominantFrequencyHz) } ?? "--")
                        .font(.system(size: 30, weight: .bold, design: .rounded))
                        .monospacedDigit()

                    if let snapshot {
                        Text("Cut \(sharedFrequencyLabel(snapshot.suggestedCutFrequencyHz))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button(snapshotActionTitle, action: onCaptureSnapshot)
                        .buttonStyle(.bordered)
                        .disabled(isSnapshotCaptureDisabled)

                    Button(isSnapshotCompareEnabled ? "Compare on" : "Compare off", action: onToggleSnapshotCompare)
                        .buttonStyle(.bordered)
                        .disabled(!canToggleSnapshotCompare)

                    Button("Clear", action: onClearSnapshot)
                        .buttonStyle(.bordered)
                        .disabled(!canClearSnapshot)

                    Button(showWaterfall ? "Waterfall on" : "Waterfall off") {
                        showWaterfall.toggle()
                    }
                    .buttonStyle(.bordered)
                }
            }

            if let snapshot {
                if showWaterfall {
                    SpectrumHistoryWaterfallView(rows: snapshot.spectrumHistoryRows, showsLabels: false)
                } else {
                    SpectrumBarsView(
                        bars: snapshot.spectrumBars,
                        snapshotOverlayBars: snapshotOverlayBars,
                        feedbackMarkers: feedbackMarkers
                    )
                    SpectrumLegendView(sampleRateHz: snapshot.sampleRateHz)
                }

                if feedbackSuggestions.first != nil {
                    FeedbackFocusSection(
                        primarySuggestion: feedbackSuggestions.first,
                        feedbackMarkerCount: feedbackMarkerCount,
                        markersEnabled: feedbackMarkersEnabled
                    )
                    FeedbackSuggestionsView(suggestions: feedbackSuggestions)
                }
            } else {
                AnalyzerChartPlaceholder(
                    isCapturing: isCapturing,
                    showingWaterfall: showWaterfall
                )
            }
        }
    }
}

private struct AnalyzerChartPlaceholder: View {
    let isCapturing: Bool
    let showingWaterfall: Bool

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: showingWaterfall ? "square.grid.3x3.square" : "waveform.path.ecg")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(.secondary)

            Text(isCapturing ? "Waiting for live audio…" : "Start live capture")
                .font(.headline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: showingWaterfall ? 150 : 170)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.black.opacity(0.08))
        )
    }
}

private struct AnalyzerToolsSheet: View {
    @ObservedObject var setupViewModel: AnalyzerSetupViewModel
    @Binding var feedbackMarkersEnabled: Bool
    @Binding var feedbackDisplayCount: Int
    @Binding var showWaterfall: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section("Display") {
                Toggle("Show feedback markers", isOn: $feedbackMarkersEnabled)

                Picker("Feedback focus", selection: $feedbackDisplayCount) {
                    Text("Top 1").tag(1)
                    Text("Top 3").tag(3)
                }
                .pickerStyle(.segmented)

                Toggle("Show waterfall", isOn: $showWaterfall)
            }

            Section("Spectrum snapshot") {
                Button(setupViewModel.snapshotActionTitle) {
                    setupViewModel.captureSpectrumSnapshot()
                }
                .disabled(setupViewModel.isSnapshotCaptureDisabled)

                Button(setupViewModel.isSnapshotCompareEnabled ? "Turn compare off" : "Turn compare on") {
                    setupViewModel.toggleSnapshotCompare()
                }
                .disabled(!setupViewModel.canToggleSnapshotCompare)

                Button("Clear snapshot", role: .destructive) {
                    setupViewModel.clearSpectrumSnapshot()
                }
                .disabled(!setupViewModel.canClearSnapshot)

                if setupViewModel.canClearSnapshot || setupViewModel.canToggleSnapshotCompare {
                    Text(setupViewModel.snapshotStatusText)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Decibel logger") {
                Button(setupViewModel.loggingActionTitle) {
                    setupViewModel.toggleLogging()
                }
                .disabled(setupViewModel.isLoggingActionDisabled)

                if let exportedAnalyzerLog = setupViewModel.exportedAnalyzerLog {
                    ShareLink(item: exportedAnalyzerLog.fileURL) {
                        Label("Share CSV", systemImage: "square.and.arrow.up")
                    }

                    Text("Prepared \(exportedAnalyzerLog.rowCount) samples.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Analyzer sample export") {
                Button("Prepare JSON sample") {
                    setupViewModel.prepareAnalyzerSampleExport(
                        feedbackMarkersEnabled: feedbackMarkersEnabled,
                        feedbackDisplayCount: feedbackDisplayCount
                    )
                }
                .disabled(setupViewModel.isAnalyzerSampleExportDisabled)

                if let exportedAnalyzerSample = setupViewModel.exportedAnalyzerSample {
                    ShareLink(item: exportedAnalyzerSample.fileURL) {
                        Label("Share JSON", systemImage: "square.and.arrow.up")
                    }
                }
            }

            Section("Waterfall export") {
                Button("Prepare waterfall JSON") {
                    setupViewModel.prepareWaterfallExport()
                }
                .disabled(setupViewModel.isWaterfallExportDisabled)

                if let exportedWaterfallSample = setupViewModel.exportedWaterfallSample {
                    ShareLink(item: exportedWaterfallSample.fileURL) {
                        Label("Share waterfall JSON", systemImage: "square.and.arrow.up")
                    }
                }
            }

            Section("Session report") {
                Button("Prepare Markdown report") {
                    setupViewModel.prepareAnalyzerReport(
                        feedbackMarkersEnabled: feedbackMarkersEnabled,
                        feedbackDisplayCount: feedbackDisplayCount
                    )
                }
                .disabled(setupViewModel.isAnalyzerReportDisabled)

                if let exportedAnalyzerReport = setupViewModel.exportedAnalyzerReport {
                    ShareLink(item: exportedAnalyzerReport.fileURL) {
                        Label("Share report", systemImage: "square.and.arrow.up")
                    }
                }
            }

            if setupViewModel.liveCaptureSummary.localizedCaseInsensitiveContains("failed") {
                Section("Capture status") {
                    Text(setupViewModel.liveCaptureDetail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Tools")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Done") {
                    dismiss()
                }
            }
        }
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
    let value: Float?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value.map { sharedDecibelLabel($0) } ?? "--")
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
        sharedDecibelLabel(value)
    }

    private func frequencyString(_ value: Float) -> String {
        sharedFrequencyLabel(value)
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
    var showsLabels = true

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if showsLabels {
                Text("Spectrum history")
                    .font(.headline)
            }

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

            if showsLabels {
                Text("Older frames at the top, newest frames at the bottom.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FeedbackFocusSection: View {
    let primarySuggestion: LiveFeedbackSuggestion?
    let feedbackMarkerCount: Int
    let markersEnabled: Bool

    var body: some View {
        Group {
            if let primarySuggestion {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Feedback focus")
                        .font(.headline)

                    VStack(alignment: .leading, spacing: 10) {
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(sharedFrequencyLabel(primarySuggestion.frequencyHz))
                                    .font(.title3.weight(.semibold))
                                Text("Cut \(sharedFrequencyLabel(primarySuggestion.suggestedCutHz))")
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
                }
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
        Group {
            if !suggestions.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Feedback suggestions")
                        .font(.headline)

                    VStack(spacing: 10) {
                        ForEach(suggestions) { suggestion in
                            FeedbackSuggestionRow(suggestion: suggestion)
                        }
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
                    Text(sharedFrequencyLabel(suggestion.frequencyHz))
                        .font(.headline)
                    FeedbackTierBadge(stabilityKey: suggestion.stabilityKey)
                }
                Spacer()
                Text("Cut \(sharedFrequencyLabel(suggestion.suggestedCutHz))")
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
        ? "Tracking top \(feedbackMarkerCount == 1 ? "peak" : "\(feedbackMarkerCount) peaks")."
        : "Markers hidden."

    switch suggestion.stabilityKey {
    case "stable":
        return "Most stable resonance. Cut near \(sharedFrequencyLabel(suggestion.suggestedCutHz)). \(markerDetail)"
    case "building":
        return "This hotspot is building near \(sharedFrequencyLabel(suggestion.frequencyHz)). Watch \(sharedFrequencyLabel(suggestion.suggestedCutHz)). \(markerDetail)"
    default:
        return "Early hotspot near \(sharedFrequencyLabel(suggestion.frequencyHz)). \(markerDetail)"
    }
}

private func sharedDecibelNumber(_ value: Float) -> String {
    AnalyzerTextFormatter.shared.formatDecibelValue(valueDb: value)
}

private func sharedFrequencyLabel(_ value: Float) -> String {
    guard value > 0 else {
        return "--"
    }

    return AnalyzerTextFormatter.shared.formatFrequencyLabel(frequencyHz: value)
}

private func sharedDecibelLabel(_ value: Float, unitSuffix: String = "dB") -> String {
    let numericValue = sharedDecibelNumber(value)
    guard numericValue != "--" else {
        return "--"
    }

    return "\(numericValue) \(unitSuffix)"
}

#Preview {
    ContentView()
}

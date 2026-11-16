import SwiftUI
import Shared

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var setupViewModel = AnalyzerSetupViewModel()
    @StateObject private var signalGenerator = SignalGeneratorController()
    @StateObject private var alignmentDelayController = AlignmentDelayController()
    @StateObject private var savedTargetCurveStore = SavedTargetCurveStore()
    @AppStorage("ios-feedback-hunt-enabled") private var feedbackHuntEnabled = true
    @AppStorage("ios-feedback-display-count") private var feedbackDisplayCount = 3
    @AppStorage("ios-show-waterfall") private var showWaterfall = false
    @AppStorage("ios-big-graph-mode-enabled") private var bigGraphModeEnabled = false
    @AppStorage("ios-waterfall-min-percentile") private var waterfallMinPercentile = 0.05
    @AppStorage("ios-waterfall-max-percentile") private var waterfallMaxPercentile = 0.95
    @AppStorage("ios-waterfall-gamma") private var waterfallGamma = 0.6
    @AppStorage("ios-waterfall-colormap") private var waterfallColormapId = "inferno"
    @AppStorage("ios-selected-overlay-id") private var selectedOverlayId = "off"
    @AppStorage("ios-selected-target-curve-id") private var selectedTargetCurveId = "off"
    @AppStorage("ios-selected-weighting") private var selectedWeighting = "Z"
    @State private var showAnalyzerTools = false
    @State private var showInfoSheet = false

    private var isCaptureFailure: Bool {
        setupViewModel.liveCaptureSummary.localizedCaseInsensitiveContains("failed")
    }

    private var overlayProfiles: [IOSOverlayProfile] {
        IOSAnalyzerReferenceCatalog.overlayProfiles
    }

    private var selectedOverlay: IOSOverlayProfile {
        overlayProfiles.first(where: { $0.id == selectedOverlayId }) ?? overlayProfiles.first ?? IOSOverlayProfile(
            id: "off",
            label: "Off",
            summary: "No guide overlay selected.",
            bands: []
        )
    }

    private var targetCurveProfiles: [IOSTargetCurveProfile] {
        IOSAnalyzerReferenceCatalog.targetCurveProfiles + savedTargetCurveStore.presets.map { $0.asIOSTargetCurveProfile() }
    }

    private var selectedTargetCurve: IOSTargetCurveProfile {
        targetCurveProfiles.first(where: { $0.id == selectedTargetCurveId }) ?? targetCurveProfiles.first ?? IOSTargetCurveProfile(
            id: "off",
            label: "Off",
            badge: "None",
            summary: "No target curve selected.",
            useCase: "Use only the live spectrum and source focus bands.",
            caution: "",
            color: .green,
            points: []
        )
    }

    private var selectedSavedTargetCurve: SavedTargetCurvePreset? {
        savedTargetCurveStore.presets.first(where: { $0.id == selectedTargetCurveId })
    }

    private var targetCurveProfileIdsSignature: String {
        targetCurveProfiles.map(\.id).joined(separator: "|")
    }

    private var waterfallDisplaySettings: WaterfallDisplaySettings {
        WaterfallDisplaySettings(
            minPercentile: waterfallMinPercentile,
            maxPercentile: waterfallMaxPercentile,
            gamma: waterfallGamma,
            colormap: WaterfallColormap(rawValue: waterfallColormapId) ?? .inferno
        )
    }

    private var captureToolbarSymbolName: String {
        setupViewModel.isCaptureSessionActiveOrPending ? "pause.fill" : "play.fill"
    }

    private var captureToolbarTint: Color {
        if setupViewModel.microphonePermissionState == .denied || isCaptureFailure {
            return .orange
        }

        return setupViewModel.isCaptureSessionActiveOrPending ? .green : .accentColor
    }

    private var captureToolbarAccessibilityLabel: String {
        if setupViewModel.isWorking {
            return "Updating microphone access"
        }

        switch setupViewModel.microphonePermissionState {
        case .denied:
            return "Open microphone settings"
        case .undetermined:
            return "Start live capture"
        case .granted:
            return setupViewModel.isCaptureSessionActiveOrPending ? "Pause live capture" : "Start live capture"
        }
    }

    private var captureStatusBanner: CaptureStatusBannerState? {
        if setupViewModel.microphonePermissionState == .denied {
            return CaptureStatusBannerState(
                title: setupViewModel.audioSessionSummary,
                detail: setupViewModel.audioSessionDetail,
                tint: .orange,
                actionTitle: setupViewModel.primaryActionTitle,
                action: performPrimaryAction
            )
        }

        if setupViewModel.isWorking {
            return CaptureStatusBannerState(
                title: "Updating microphone access",
                detail: "SoundRunner is refreshing microphone access and live capture availability.",
                tint: .accentColor,
                actionTitle: nil,
                action: nil,
                showsProgress: true
            )
        }

        guard
            !setupViewModel.isCapturing,
            setupViewModel.liveCaptureSummary != "Live capture is idle"
        else {
            return nil
        }

        return CaptureStatusBannerState(
            title: setupViewModel.liveCaptureSummary,
            detail: setupViewModel.liveCaptureDetail,
            tint: isCaptureFailure ? .orange : .secondary,
            actionTitle: nil,
            action: nil
        )
    }

    private var liveMeterHint: String? {
        guard !setupViewModel.isCapturing else {
            return nil
        }

        if signalGenerator.isRunning {
            return "The signal generator is running. Tap play in the header to stop playback and switch to the microphone."
        }

        switch setupViewModel.microphonePermissionState {
        case .granted:
            return "Tap play in the header to start live capture."
        case .undetermined:
            return "Tap play in the header to request microphone access."
        case .denied:
            return nil
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let captureStatusBanner {
                        CaptureStatusBanner(state: captureStatusBanner)
                    }

                    LiveMeterCard(
                        snapshot: setupViewModel.liveAnalyzerSnapshot,
                        selectedWeighting: $selectedWeighting,
                        idleHint: liveMeterHint
                    )

                    AnalyzerVisualizerCard(
                        snapshot: setupViewModel.liveAnalyzerSnapshot,
                        isCapturing: setupViewModel.isCapturing,
                        selectedOverlay: selectedOverlay,
                        selectedTargetCurve: selectedTargetCurve,
                        showWaterfall: $showWaterfall,
                        bigGraphModeEnabled: bigGraphModeEnabled,
                        waterfallDisplaySettings: waterfallDisplaySettings,
                        snapshotOverlayBars: setupViewModel.snapshotOverlayBars,
                        feedbackMarkers: setupViewModel.liveAnalyzerSnapshot.map {
                            visibleFeedbackMarkers(from: $0)
                        } ?? [],
                        feedbackSuggestions: setupViewModel.liveAnalyzerSnapshot.map {
                            visibleFeedbackSuggestions(from: $0)
                        } ?? [],
                        feedbackMarkerCount: max(1, feedbackDisplayCount),
                        feedbackHuntEnabled: feedbackHuntEnabled,
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
                        },
                        onOpenTools: openAnalyzerTools
                    )
                }
                .padding(16)
            }
            .navigationTitle("SoundRunner")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HStack(spacing: 10) {
                        SoundRunnerHeaderLogo()
                        CaptureToolbarButton(
                            symbolName: captureToolbarSymbolName,
                            tint: captureToolbarTint,
                            isWorking: setupViewModel.isWorking,
                            accessibilityLabel: captureToolbarAccessibilityLabel,
                            isDisabled: setupViewModel.isCaptureToolbarDisabled,
                            action: toggleLiveCapture
                        )
                    }
                }

                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        openInfoSheet()
                    } label: {
                        Image(systemName: "info.circle")
                    }
                    .accessibilityLabel("Info")

                    Button {
                        openAnalyzerTools()
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
            }
            .sheet(isPresented: $showAnalyzerTools) {
                NavigationStack {
                    AnalyzerToolsSheet(
                        setupViewModel: setupViewModel,
                        signalGenerator: signalGenerator,
                        alignmentDelayController: alignmentDelayController,
                        selectedOverlayId: $selectedOverlayId,
                        selectedTargetCurveId: $selectedTargetCurveId,
                        feedbackHuntEnabled: $feedbackHuntEnabled,
                        feedbackDisplayCount: $feedbackDisplayCount,
                        showWaterfall: $showWaterfall,
                        bigGraphModeEnabled: $bigGraphModeEnabled,
                        waterfallMinPercentile: $waterfallMinPercentile,
                        waterfallMaxPercentile: $waterfallMaxPercentile,
                        waterfallGamma: $waterfallGamma,
                        waterfallColormapId: $waterfallColormapId,
                        savedTargetCurves: savedTargetCurveStore.presets,
                        selectedSavedTargetCurve: selectedSavedTargetCurve,
                        overlayProfiles: overlayProfiles,
                        targetCurveProfiles: targetCurveProfiles,
                        selectedOverlay: selectedOverlay,
                        selectedTargetCurve: selectedTargetCurve,
                        onTargetCurveSelected: selectTargetCurve,
                        onUpsertSavedTargetCurve: { preset in
                            savedTargetCurveStore.upsert(preset)
                            selectedTargetCurveId = preset.id
                        },
                        onDeleteSavedTargetCurve: { presetId in
                            savedTargetCurveStore.delete(id: presetId)
                            if selectedTargetCurveId == presetId {
                                selectedTargetCurveId = IOSAnalyzerReferenceCatalog.targetCurveProfiles.first?.id ?? "off"
                            }
                        }
                    )
                }
                .presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $showInfoSheet) {
                NavigationStack {
                    AnalyzerInfoSheet()
                }
                .presentationDetents([.large])
            }
            .onReceive(
                NotificationCenter.default.publisher(
                    for: AlignmentDelayController.measurementDidFinishNotification
                )
            ) { _ in
                setupViewModel.rebuildCaptureEngineAfterExternalAudioTool()
            }
            .onAppear {
                setupViewModel.restoreCaptureIfNeeded()
            }
            .onChange(of: targetCurveProfileIdsSignature) { _ in
                guard !targetCurveProfiles.contains(where: { $0.id == selectedTargetCurveId }) else {
                    return
                }

                selectedTargetCurveId = targetCurveProfiles.first?.id ?? "off"
            }
            .onChange(of: scenePhase) { newPhase in
                guard newPhase == .active else {
                    return
                }

                setupViewModel.restoreCaptureIfNeeded()
            }
        }
    }

    private func visibleFeedbackSuggestions(from snapshot: LiveAnalyzerSnapshot) -> [LiveFeedbackSuggestion] {
        guard feedbackHuntEnabled else {
            return []
        }

        return Array(snapshot.feedbackSuggestions.prefix(max(1, feedbackDisplayCount)))
    }

    private func visibleFeedbackMarkers(from snapshot: LiveAnalyzerSnapshot) -> [LiveFeedbackMarker] {
        guard feedbackHuntEnabled else {
            return []
        }

        return Array(snapshot.feedbackMarkers.prefix(max(1, feedbackDisplayCount)))
    }

    private func performPrimaryAction() {
        if signalGenerator.isRunning {
            signalGenerator.stop(status: "Signal generator stopped while updating microphone access.")
        }

        setupViewModel.performPrimaryAction()
    }

    private func toggleLiveCapture() {
        if signalGenerator.isRunning {
            signalGenerator.stop(status: "Signal generator stopped for live capture.")
        }

        setupViewModel.toggleLiveCapture()
    }

    private func openAnalyzerTools() {
        guard !showAnalyzerTools else {
            return
        }

        showAnalyzerTools = true
    }

    private func openInfoSheet() {
        guard !showInfoSheet else {
            return
        }

        showInfoSheet = true
    }

    private func selectTargetCurve(_ profile: IOSTargetCurveProfile) {
        selectedTargetCurveId = profile.id

        guard savedTargetCurveStore.contains(id: profile.id) else {
            return
        }

        savedTargetCurveStore.markUsed(id: profile.id)
    }
}

private struct SoundRunnerHeaderLogo: View {
    var body: some View {
        Image("AppHeader")
            .resizable()
            .scaledToFit()
            .frame(maxWidth: 180, maxHeight: 22)
            .accessibilityLabel("SoundRunner")
    }
}

private struct CaptureToolbarButton: View {
    let symbolName: String
    let tint: Color
    let isWorking: Bool
    let accessibilityLabel: String
    let isDisabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(tint.opacity(0.16))

                if isWorking {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Image(systemName: symbolName)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(tint)
                }
            }
            .frame(width: 30, height: 30)
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .accessibilityLabel(accessibilityLabel)
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

private struct CaptureStatusBannerState {
    let title: String
    let detail: String
    let tint: Color
    let actionTitle: String?
    let action: (() -> Void)?
    var showsProgress = false
}

private struct CaptureStatusBanner: View {
    let state: CaptureStatusBannerState

    var body: some View {
        AnalyzerCard {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: state.showsProgress ? "mic.badge.clock" : "mic.fill")
                    .font(.headline)
                    .foregroundStyle(state.tint)

                VStack(alignment: .leading, spacing: 8) {
                    Text(state.title)
                        .font(.headline)

                    if state.showsProgress {
                        ProgressView()
                            .controlSize(.small)
                    }

                    Text(state.detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(4)

                    if let actionTitle = state.actionTitle, let action = state.action {
                        Button(actionTitle, action: action)
                            .buttonStyle(.bordered)
                    }
                }
            }
        }
    }
}

private struct LiveMeterCard: View {
    let snapshot: LiveAnalyzerSnapshot?
    @Binding var selectedWeighting: String
    let idleHint: String?

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
            VStack(spacing: 12) {
                Text("Sound level")
                    .font(.headline)
                    .foregroundStyle(.secondary)

                Text(displayDbText)
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                    .foregroundStyle(displayDbColor)
                    .monospacedDigit()
                    .multilineTextAlignment(.center)

                if let idleHint {
                    Text(idleHint)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }

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
                .frame(maxWidth: .infinity, alignment: .center)
            }
            .frame(maxWidth: .infinity, alignment: .center)

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
            VStack(alignment: .center, spacing: 2) {
                Text("dB(\(title))")
                    .font(.caption2.weight(.semibold))
                Text(value.map(sharedDecibelNumber) ?? "--")
                    .font(.caption.weight(.semibold))
                    .monospacedDigit()
            }
            .frame(minWidth: 72)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? Color.accentColor.opacity(0.18) : Color.secondary.opacity(0.10))
            )
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? Color.accentColor : Color.primary)
    }
}

private struct AnalyzerVisualizerCard: View {
    let snapshot: LiveAnalyzerSnapshot?
    let isCapturing: Bool
    let selectedOverlay: IOSOverlayProfile
    let selectedTargetCurve: IOSTargetCurveProfile
    @Binding var showWaterfall: Bool
    let bigGraphModeEnabled: Bool
    let waterfallDisplaySettings: WaterfallDisplaySettings
    let snapshotOverlayBars: [Float]?
    let feedbackMarkers: [LiveFeedbackMarker]
    let feedbackSuggestions: [LiveFeedbackSuggestion]
    let feedbackMarkerCount: Int
    let feedbackHuntEnabled: Bool
    let snapshotActionTitle: String
    let isSnapshotCaptureDisabled: Bool
    let isSnapshotCompareEnabled: Bool
    let canToggleSnapshotCompare: Bool
    let canClearSnapshot: Bool
    let onCaptureSnapshot: () -> Void
    let onToggleSnapshotCompare: () -> Void
    let onClearSnapshot: () -> Void
    let onOpenTools: () -> Void

    private var chartHeight: CGFloat {
        bigGraphModeEnabled ? 320 : (showWaterfall ? 150 : 194)
    }

    private var primaryFeedbackSuggestion: LiveFeedbackSuggestion? {
        feedbackSuggestions.first
    }

    var body: some View {
        AnalyzerCard {
            if bigGraphModeEnabled {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("FOH graph mode")
                            .font(.headline)
                            .foregroundStyle(.secondary)
                        Text(snapshot.map { sharedFrequencyLabel($0.dominantFrequencyHz) } ?? "--")
                            .font(.system(size: 38, weight: .bold, design: .rounded))
                            .monospacedDigit()

                        if feedbackHuntEnabled, let primaryFeedbackSuggestion {
                            Text("Watch \(sharedFrequencyLabel(primaryFeedbackSuggestion.suggestedCutHz))")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        } else if let snapshot {
                            Text("Cut \(sharedFrequencyLabel(snapshot.suggestedCutFrequencyHz))")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer()

                    if let snapshot {
                        VStack(alignment: .trailing, spacing: 8) {
                            StatusPill(text: "dB(Z) \(sharedDecibelNumber(snapshot.zLevelDb))", tint: .accentColor)

                            if showWaterfall {
                                StatusPill(
                                    text: waterfallDisplaySettings.colormap.title,
                                    tint: waterfallDisplaySettings.colormap.accentColor
                                )
                            }
                        }
                    }

                    Button("Tools", action: onOpenTools)
                        .buttonStyle(.bordered)
                }
            } else {
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

                    Button("Tools", action: onOpenTools)
                        .buttonStyle(.bordered)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    StatusPill(text: "Source \(selectedOverlay.label)", tint: .accentColor)
                    StatusPill(
                        text: selectedTargetCurve.isEnabled ? "Curve \(selectedTargetCurve.label)" : "Curve off",
                        tint: selectedTargetCurve.isEnabled ? selectedTargetCurve.color : .secondary
                    )
                    StatusPill(
                        text: feedbackHuntEnabled ? "Feedback on" : "Feedback off",
                        tint: feedbackHuntEnabled ? .orange : .secondary
                    )
                    StatusPill(
                        text: showWaterfall
                            ? "Waterfall \(waterfallDisplaySettings.colormap.title)"
                            : "Waterfall off",
                        tint: showWaterfall ? waterfallDisplaySettings.colormap.accentColor : .secondary
                    )
                    if isSnapshotCompareEnabled {
                        StatusPill(
                            text: "Compare on",
                            tint: Color(red: 0, green: 0.67, blue: 0.76)
                        )
                    }
                }
            }

            if !bigGraphModeEnabled {
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
            }

            if let snapshot {
                if showWaterfall {
                    SpectrumHistoryWaterfallView(
                        rowsDb: snapshot.waterfallHistoryRowsDb,
                        settings: waterfallDisplaySettings,
                        showsLabels: false,
                        chartHeight: chartHeight
                    )
                } else {
                    SpectrumBarsView(
                        bars: snapshot.spectrumBars,
                        sampleRateHz: snapshot.sampleRateHz,
                        overlayBands: selectedOverlay.bands,
                        targetCurve: selectedTargetCurve,
                        snapshotOverlayBars: snapshotOverlayBars,
                        feedbackMarkers: feedbackMarkers,
                        chartHeight: chartHeight
                    )
                }

                if !bigGraphModeEnabled, !selectedOverlay.bands.isEmpty {
                    OverlayBandStrip(bands: selectedOverlay.bands)
                }

                if !bigGraphModeEnabled, selectedTargetCurve.isEnabled {
                    CurveSummaryView(targetCurve: selectedTargetCurve)
                }

                if !bigGraphModeEnabled, feedbackHuntEnabled, feedbackSuggestions.first != nil {
                    FeedbackFocusSection(
                        primarySuggestion: feedbackSuggestions.first,
                        feedbackMarkerCount: feedbackMarkerCount,
                        markersEnabled: feedbackHuntEnabled
                    )
                    FeedbackSuggestionsView(suggestions: feedbackSuggestions)
                }
            } else {
                AnalyzerChartPlaceholder(
                    isCapturing: isCapturing,
                    showingWaterfall: showWaterfall,
                    bigGraphModeEnabled: bigGraphModeEnabled
                )
            }
        }
    }
}

private struct AnalyzerChartPlaceholder: View {
    let isCapturing: Bool
    let showingWaterfall: Bool
    let bigGraphModeEnabled: Bool

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: showingWaterfall ? "square.grid.3x3.square" : "waveform.path.ecg")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(.secondary)

            Text(isCapturing ? "Waiting for live audio…" : "Tap play in the header")
                .font(.headline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .frame(height: bigGraphModeEnabled ? 320 : (showingWaterfall ? 150 : 170))
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.secondary.opacity(0.10))
        )
    }
}

private struct AnalyzerToolsSheet: View {
    @ObservedObject var setupViewModel: AnalyzerSetupViewModel
    @ObservedObject var signalGenerator: SignalGeneratorController
    @ObservedObject var alignmentDelayController: AlignmentDelayController
    @Binding var selectedOverlayId: String
    @Binding var selectedTargetCurveId: String
    @Binding var feedbackHuntEnabled: Bool
    @Binding var feedbackDisplayCount: Int
    @Binding var showWaterfall: Bool
    @Binding var bigGraphModeEnabled: Bool
    @Binding var waterfallMinPercentile: Double
    @Binding var waterfallMaxPercentile: Double
    @Binding var waterfallGamma: Double
    @Binding var waterfallColormapId: String
    let savedTargetCurves: [SavedTargetCurvePreset]
    let selectedSavedTargetCurve: SavedTargetCurvePreset?
    let overlayProfiles: [IOSOverlayProfile]
    let targetCurveProfiles: [IOSTargetCurveProfile]
    let selectedOverlay: IOSOverlayProfile
    let selectedTargetCurve: IOSTargetCurveProfile
    let onTargetCurveSelected: (IOSTargetCurveProfile) -> Void
    let onUpsertSavedTargetCurve: (SavedTargetCurvePreset) -> Void
    let onDeleteSavedTargetCurve: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var showSavedCurveManager = false
    @State private var calibrationTargetDb = 85.0
    @State private var measuredCalibrationAverageDb: Double?
    @State private var isCalibrationMeasuring = false
    @State private var calibrationMeasurementTask: Task<Void, Never>?

    private var frequencySliderBinding: Binding<Double> {
        Binding {
            let minLog = log10(20.0)
            let maxLog = log10(20_000.0)
            let clampedFrequency = min(max(signalGenerator.frequencyHz, 20), 20_000)
            return (log10(clampedFrequency) - minLog) / (maxLog - minLog)
        } set: { normalizedValue in
            let minLog = log10(20.0)
            let maxLog = log10(20_000.0)
            signalGenerator.frequencyHz = pow(10.0, minLog + (maxLog - minLog) * normalizedValue)
        }
    }

    private var waterfallMinPercentileBinding: Binding<Double> {
        Binding {
            waterfallMinPercentile
        } set: { newValue in
            waterfallMinPercentile = min(max(newValue, 0), max(waterfallMaxPercentile - 0.01, 0))
        }
    }

    private var waterfallMaxPercentileBinding: Binding<Double> {
        Binding {
            waterfallMaxPercentile
        } set: { newValue in
            waterfallMaxPercentile = max(min(newValue, 1), min(waterfallMinPercentile + 0.01, 1))
        }
    }

    private var waterfallGammaBinding: Binding<Double> {
        Binding {
            waterfallGamma
        } set: { newValue in
            waterfallGamma = min(max(newValue, 0.25), 2.0)
        }
    }

    private var selectedWaterfallColormap: WaterfallColormap {
        WaterfallColormap(rawValue: waterfallColormapId) ?? .inferno
    }

    private var calibrationOffsetBinding: Binding<Double> {
        Binding {
            setupViewModel.calibrationOffsetDb
        } set: { newValue in
            setupViewModel.setCalibrationOffset(newValue)
        }
    }

    private var noiseThresholdBinding: Binding<Double> {
        Binding {
            setupViewModel.noiseThresholdDb
        } set: { newValue in
            setupViewModel.setNoiseThreshold(newValue)
        }
    }

    private var currentCalibrationLevelDb: Double? {
        setupViewModel.liveAnalyzerSnapshot.map { Double($0.zLevelDb) }
    }

    var body: some View {
        List {
            Section("Source overlay") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(overlayProfiles) { profile in
                            Button(profile.label) {
                                selectedOverlayId = profile.id
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(profile.id == selectedOverlayId ? .accentColor : .secondary.opacity(0.35))
                        }
                    }
                    .padding(.vertical, 4)
                }

                Text(selectedOverlay.summary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if !selectedOverlay.bands.isEmpty {
                    OverlayBandStrip(bands: selectedOverlay.bands)
                }

                Toggle("Feedback hunt", isOn: $feedbackHuntEnabled)

                Picker("Feedback focus", selection: $feedbackDisplayCount) {
                    Text("Top 1").tag(1)
                    Text("Top 3").tag(3)
                }
                .pickerStyle(.segmented)
            }

            Section("Room curve") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(targetCurveProfiles) { profile in
                            let savedPreset = savedTargetCurves.first(where: { $0.id == profile.id })
                            Button(savedPreset?.displayName() ?? profile.label) {
                                onTargetCurveSelected(profile)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(profile.id == selectedTargetCurveId ? profile.color : .secondary.opacity(0.35))
                        }
                    }
                    .padding(.vertical, 4)
                }

                Text(selectedTargetCurve.summary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if selectedTargetCurve.isEnabled {
                    Text("\(selectedSavedTargetCurve?.displayName() ?? selectedTargetCurve.label) \(selectedTargetCurve.badge)")
                        .font(.subheadline.weight(.semibold))

                    CurveSummaryView(targetCurve: selectedTargetCurve)
                    Text("Best for: \(selectedTargetCurve.useCase)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("Caution: \(selectedTargetCurve.caution)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let selectedSavedTargetCurve {
                    let detailRows = selectedSavedTargetCurve.venueDetailRows()
                    if !detailRows.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Venue recall")
                                .font(.subheadline.weight(.semibold))

                            ForEach(detailRows) { detailRow in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(detailRow.label)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(.secondary)
                                    Text(detailRow.value)
                                        .font(.footnote)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color.secondary.opacity(0.10))
                        )
                    }
                }

                Button("Manage saved venue curves") {
                    showSavedCurveManager = true
                }
            }

            Section("Signal generator") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(SignalGeneratorMode.allCases) { mode in
                            Button(mode.title) {
                                signalGenerator.selectedMode = mode
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(mode == signalGenerator.selectedMode ? .accentColor : .secondary.opacity(0.35))
                            .disabled(signalGenerator.isRunning)
                        }
                    }
                    .padding(.vertical, 4)
                }

                Text(signalGenerator.selectedMode.summary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if signalGenerator.selectedMode.supportsFrequencyControl {
                    Text("Frequency \(sharedFrequencyLabel(Float(signalGenerator.frequencyHz)))")
                        .font(.footnote.weight(.semibold))
                    Slider(value: frequencySliderBinding, in: 0...1)
                        .disabled(signalGenerator.isRunning)
                }

                Text("Level \(Int(min(max(signalGenerator.level, 0), 1) * 100))%")
                    .font(.footnote.weight(.semibold))
                Slider(value: $signalGenerator.level, in: 0...1)
                    .disabled(signalGenerator.isRunning)

                HStack(spacing: 12) {
                    Button("Start") {
                        signalGenerator.start()
                    }
                    .disabled(signalGenerator.isRunning || setupViewModel.isCaptureSessionActiveOrPending || setupViewModel.isWorking)

                    Button("Stop") {
                        signalGenerator.stop()
                    }
                    .disabled(!signalGenerator.isRunning)
                }

                Text(
                    setupViewModel.isCaptureSessionActiveOrPending
                        ? "Stop live capture to use the onboard iOS signal generator."
                        : signalGenerator.statusText
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }

            Section("Delay calculator") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button("Meters") {
                            alignmentDelayController.usesMeters = true
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(alignmentDelayController.usesMeters ? .accentColor : .secondary.opacity(0.35))
                        .disabled(alignmentDelayController.isMeasuring)

                        Button("Feet") {
                            alignmentDelayController.usesMeters = false
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(!alignmentDelayController.usesMeters ? .accentColor : .secondary.opacity(0.35))
                        .disabled(alignmentDelayController.isMeasuring)
                    }
                    .padding(.vertical, 4)
                }

                Text(
                    "Distance \(String(format: "%.1f", alignmentDelayController.distanceValue)) \(alignmentDelayController.distanceUnitLabel)"
                )
                .font(.footnote.weight(.semibold))
                Slider(value: $alignmentDelayController.distanceValue, in: 0.5...100)
                    .disabled(alignmentDelayController.isMeasuring)

                Text("Temperature \(String(format: "%.1f", alignmentDelayController.temperatureC)) °C")
                    .font(.footnote.weight(.semibold))
                Slider(value: $alignmentDelayController.temperatureC, in: -10...45)
                    .disabled(alignmentDelayController.isMeasuring)

                VStack(spacing: 4) {
                    Text("Alignment delay")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(String(format: "%.1f ms", alignmentDelayController.delayMilliseconds))
                        .font(.title2.weight(.bold))
                        .monospacedDigit()
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(Color.secondary.opacity(0.08))
                )

                HStack(spacing: 12) {
                    Button(alignmentDelayController.isMeasuring ? "Listening…" : "Auto-Measure with Ping") {
                        if signalGenerator.isRunning {
                            signalGenerator.stop(status: "Signal generator stopped for ping measurement.")
                        }
                        alignmentDelayController.startMeasurement()
                    }
                    .disabled(
                        alignmentDelayController.isMeasuring ||
                            setupViewModel.isCaptureSessionActiveOrPending ||
                            setupViewModel.isWorking
                    )

                    Button("Stop") {
                        alignmentDelayController.stopMeasurement()
                    }
                    .disabled(!alignmentDelayController.isMeasuring)
                }

                if alignmentDelayController.microphonePermissionState == .denied {
                    Button("Open Settings") {
                        alignmentDelayController.openAppSettings()
                    }
                    .buttonStyle(.bordered)
                }

                if let detectedPolarity = alignmentDelayController.detectedPolarity {
                    Text(detectedPolarity ? "Polarity: In phase (+)" : "Polarity: Inverted (-)")
                        .font(.footnote.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill((detectedPolarity ? Color.green : Color.red).opacity(0.18))
                        )
                }

                Text(
                    setupViewModel.isCaptureSessionActiveOrPending
                        ? "Stop live capture before running the alignment ping tool."
                        : alignmentDelayController.statusText
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }

            Section("Display") {
                Toggle("Show waterfall", isOn: $showWaterfall)
                Toggle("Big graph mode", isOn: $bigGraphModeEnabled)

                Text("Keeps the analyzer focused on the graph, dominant frequency, and marker overlays.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Waterfall settings")
                        .font(.subheadline.weight(.semibold))

                    Text("Min percentile \(Int((waterfallMinPercentile * 100).rounded()))%")
                        .font(.footnote.weight(.semibold))
                    Slider(value: waterfallMinPercentileBinding, in: 0...0.49)

                    Text("Max percentile \(Int((waterfallMaxPercentile * 100).rounded()))%")
                        .font(.footnote.weight(.semibold))
                    Slider(value: waterfallMaxPercentileBinding, in: 0.51...1)

                    Text(String(format: "Gamma %.2f", waterfallGamma))
                        .font(.footnote.weight(.semibold))
                    Slider(value: waterfallGammaBinding, in: 0.25...2.0)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(WaterfallColormap.allCases) { colormap in
                            Button(colormap.title) {
                                waterfallColormapId = colormap.rawValue
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(
                                colormap == selectedWaterfallColormap
                                    ? colormap.accentColor
                                    : .secondary.opacity(0.35)
                            )
                        }
                    }
                    .padding(.vertical, 4)
                }

                Text("Lower gamma brightens quieter detail. Higher max percentile preserves stronger peaks before they saturate.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                if setupViewModel.liveCaptureSummary.localizedCaseInsensitiveContains("failed") {
                    Text(setupViewModel.liveCaptureDetail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Calibration") {
                VStack(spacing: 8) {
                    Text(currentCalibrationLevelDb.map { String(format: "%.1f dB(Z)", $0) } ?? "-- dB(Z)")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .monospacedDigit()
                    Text("Use a reference SPL meter when accuracy matters. The offset shifts the main iOS meter and the noise gate trims very quiet readings.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)

                Text(String(format: "dB offset %.1f", setupViewModel.calibrationOffsetDb))
                    .font(.footnote.weight(.semibold))
                Slider(value: calibrationOffsetBinding, in: 0...100)

                Text(String(format: "Noise gate %.1f dB", setupViewModel.noiseThresholdDb))
                    .font(.footnote.weight(.semibold))
                Slider(value: noiseThresholdBinding, in: 0...60)

                Text(String(format: "Target level %.1f dB", calibrationTargetDb))
                    .font(.footnote.weight(.semibold))
                Slider(value: $calibrationTargetDb, in: 0...120)

                HStack(spacing: 12) {
                    Button(isCalibrationMeasuring ? "Measuring…" : "Measure 3s average") {
                        startCalibrationMeasurement()
                    }
                    .disabled(isCalibrationMeasuring || !setupViewModel.isCapturing)

                    Button("Set offset") {
                        let baseLevel = measuredCalibrationAverageDb ?? currentCalibrationLevelDb
                        guard let baseLevel else {
                            return
                        }

                        setupViewModel.applyCalibrationTarget(
                            targetDb: calibrationTargetDb,
                            measuredDb: baseLevel
                        )
                    }
                    .disabled((measuredCalibrationAverageDb ?? currentCalibrationLevelDb) == nil)
                }

                if let measuredCalibrationAverageDb {
                    Text(String(format: "Measured average %.1f dB(Z)", measuredCalibrationAverageDb))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if !setupViewModel.isCapturing {
                    Text("Start live capture from the header before running a calibration measurement.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Button("Reset calibration defaults") {
                    setupViewModel.resetCalibrationDefaults()
                    measuredCalibrationAverageDb = nil
                }
                .buttonStyle(.bordered)
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
                        feedbackMarkersEnabled: feedbackHuntEnabled,
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
                        feedbackMarkersEnabled: feedbackHuntEnabled,
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
        .onDisappear {
            calibrationMeasurementTask?.cancel()
            calibrationMeasurementTask = nil
            isCalibrationMeasuring = false
        }
        .sheet(isPresented: $showSavedCurveManager) {
            NavigationStack {
                SavedTargetCurveManagerView(
                    savedTargetCurves: savedTargetCurves,
                    initialSelectedCurveId: selectedSavedTargetCurve?.id,
                    onSave: { preset in
                        onUpsertSavedTargetCurve(preset)
                    },
                    onDelete: { presetId in
                        onDeleteSavedTargetCurve(presetId)
                    }
                )
            }
            .presentationDetents([.large])
        }
    }

    private func startCalibrationMeasurement() {
        guard !isCalibrationMeasuring, setupViewModel.isCapturing else {
            return
        }

        calibrationMeasurementTask?.cancel()
        isCalibrationMeasuring = true
        measuredCalibrationAverageDb = nil

        calibrationMeasurementTask = Task {
            var samples: [Double] = []
            let deadline = Date().addingTimeInterval(3)

            while Date() < deadline {
                guard !Task.isCancelled else {
                    await MainActor.run {
                        isCalibrationMeasuring = false
                    }
                    return
                }

                let sample = await MainActor.run { currentCalibrationLevelDb }
                if let sample {
                    samples.append(sample)
                }

                try? await Task.sleep(nanoseconds: 100_000_000)
            }

            await MainActor.run {
                measuredCalibrationAverageDb = samples.isEmpty ? nil : (samples.reduce(0, +) / Double(samples.count))
                calibrationMeasurementTask = nil
                isCalibrationMeasuring = false
            }
        }
    }
}

struct OverlayBandStrip: View {
    let bands: [IOSOverlayBand]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(bands) { band in
                    Text("\(band.label) \(sharedFrequencyRange(band.startHz, band.endHz))")
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(band.color.opacity(0.16))
                        )
                }
            }
            .padding(.vertical, 2)
        }
    }
}

struct CurveSummaryView: View {
    let targetCurve: IOSTargetCurveProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Circle()
                    .fill(targetCurve.color)
                    .frame(width: 10, height: 10)
                Text("\(targetCurve.label) \(targetCurve.badge)")
                    .font(.subheadline.weight(.semibold))
            }

            Text(targetCurve.summary)
                .font(.footnote)
                .foregroundStyle(.secondary)

            if targetCurve.isEnabled {
                Text("Illustrative reference only. The live spectrum is not aligned with this curve and is not a calibrated room response.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(targetCurve.color.opacity(0.10))
        )
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
                .fill(Color.secondary.opacity(0.10))
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
    let sampleRateHz: Double
    let overlayBands: [IOSOverlayBand]
    let targetCurve: IOSTargetCurveProfile
    let snapshotOverlayBars: [Float]?
    let feedbackMarkers: [LiveFeedbackMarker]
    let chartHeight: CGFloat

    var body: some View {
        Canvas { context, size in
            guard !bars.isEmpty else {
                return
            }

            let axis = SpectrumDisplayAxis(sampleRateHz: sampleRateHz)
            let labelAreaHeight: CGFloat = 26
            let drawHeight = max(size.height - labelAreaHeight, 1)
            let spacing: CGFloat = 3
            let count = CGFloat(bars.count)
            let barWidth = max(2, (size.width - spacing * (count - 1)) / count)

            context.fill(
                Path(
                    CGRect(
                        x: 0,
                        y: drawHeight,
                        width: size.width,
                        height: labelAreaHeight
                    )
                ),
                with: .color(Color.secondary.opacity(0.10))
            )

            context.stroke(
                Path { path in
                    path.move(to: CGPoint(x: 0, y: drawHeight))
                    path.addLine(to: CGPoint(x: size.width, y: drawHeight))
                },
                with: .color(Color.secondary.opacity(0.25)),
                style: StrokeStyle(lineWidth: 1)
            )

            for band in overlayBands {
                let startX = xPosition(for: band.startHz, axis: axis, width: size.width)
                let endX = xPosition(for: band.endHz, axis: axis, width: size.width)
                let rect = CGRect(
                    x: min(startX, endX),
                    y: 0,
                    width: max(abs(endX - startX), 2),
                    height: drawHeight
                )

                context.fill(
                    Path(roundedRect: rect, cornerRadius: 8),
                    with: .color(band.color.opacity(0.16))
                )
            }

            for tickFrequencyHz in axis.tickFrequenciesHz {
                let x = xPosition(for: tickFrequencyHz, axis: axis, width: size.width)
                context.stroke(
                    Path { path in
                        path.move(to: CGPoint(x: x, y: 0))
                        path.addLine(to: CGPoint(x: x, y: drawHeight))
                    },
                    with: .color(Color.secondary.opacity(0.18)),
                    style: StrokeStyle(lineWidth: 1)
                )

                let labelX = min(max(x, 14), size.width - 14)
                context.draw(
                    Text(axisLabel(for: tickFrequencyHz))
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(.secondary)
                        .monospacedDigit(),
                    at: CGPoint(x: labelX, y: drawHeight + 6),
                    anchor: .top
                )
            }

            for (index, bar) in bars.enumerated() {
                let normalizedBar = CGFloat(min(max(bar, 0), 1))
                let height = max(2, drawHeight * normalizedBar)
                let x = CGFloat(index) * (barWidth + spacing)
                let rect = CGRect(x: x, y: drawHeight - height, width: barWidth, height: height)
                let path = Path(roundedRect: rect, cornerRadius: min(barWidth / 2, 4))
                let frequencyHz = frequency(for: index, axis: axis)
                let overlayTint = overlayBands.first(where: { $0.contains(frequencyHz) })?.color
                let color = Color(
                    hue: 0.58 - Double(normalizedBar) * 0.12,
                    saturation: 0.70,
                    brightness: 0.78 + Double(normalizedBar) * 0.18
                )
                context.fill(path, with: .color(overlayTint?.opacity(0.88) ?? color))
            }

            if let snapshotOverlayBars, snapshotOverlayBars.count == bars.count {
                var overlayPath = Path()

                for (index, bar) in snapshotOverlayBars.enumerated() {
                    let normalizedBar = CGFloat(min(max(bar, 0), 1))
                    let height = max(2, drawHeight * normalizedBar)
                    let x = CGFloat(index) * (barWidth + spacing) + (barWidth / 2)
                    let point = CGPoint(x: x, y: drawHeight - height)

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
                        path.addLine(to: CGPoint(x: x, y: drawHeight))
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
        .frame(height: chartHeight)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.secondary.opacity(0.10))
        )
        .animation(nil, value: bars)
        .animation(nil, value: overlayBands)
        .animation(nil, value: targetCurve.points)
        .animation(nil, value: snapshotOverlayBars ?? [])
        .animation(nil, value: feedbackMarkers)
    }

    private func frequency(for index: Int, axis: SpectrumDisplayAxis) -> Float {
        let barCount = max(bars.count, 1)
        let startRatio = CGFloat(index) / CGFloat(barCount)
        let endRatio = CGFloat(index + 1) / CGFloat(barCount)
        return axis.frequency(forNormalizedRatio: (startRatio + endRatio) / 2)
    }

    private func xPosition(for frequencyHz: Float, axis: SpectrumDisplayAxis, width: CGFloat) -> CGFloat {
        axis.xPosition(for: frequencyHz, width: width)
    }

    private func yPosition(for relativeDb: Float, height: CGFloat) -> CGFloat {
        let minDb: Float = -12
        let maxDb: Float = 6
        let normalized = CGFloat(min(max((relativeDb - minDb) / (maxDb - minDb), 0), 1))
        return height - (normalized * height)
    }

    private func axisLabel(for frequencyHz: Float) -> String {
        if frequencyHz >= 1_000 {
            return "\(Int((frequencyHz / 1_000).rounded()))k"
        }

        return "\(Int(frequencyHz.rounded()))"
    }
}

private struct WaterfallDisplaySettings: Equatable {
    let minPercentile: Double
    let maxPercentile: Double
    let gamma: Double
    let colormap: WaterfallColormap
}

private enum WaterfallColormap: String, CaseIterable, Identifiable {
    case inferno
    case viridis
    case plasma
    case magma

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .inferno:
            return "Inferno"
        case .viridis:
            return "Viridis"
        case .plasma:
            return "Plasma"
        case .magma:
            return "Magma"
        }
    }

    var accentColor: Color {
        waterfallColor(for: 0.72, colormap: self)
    }

    fileprivate var colorStops: [UInt32] {
        switch self {
        case .inferno:
            return [0x000004, 0x2A1A5A, 0x5C1191, 0x9A3A6B, 0xDE6A25, 0xFCA50A, 0xFCFFA4]
        case .viridis:
            return [0x440154, 0x3B528B, 0x21918C, 0x5EC962, 0xFDE725]
        case .plasma:
            return [0x0D0887, 0x6A00A8, 0xB12A90, 0xE16462, 0xFCA636, 0xF0F921]
        case .magma:
            return [0x000004, 0x3B0F70, 0x8C2981, 0xDE4968, 0xFE9F6D, 0xFCFDBF]
        }
    }
}

private struct SpectrumDisplayAxis {
    let minFrequencyHz: Float
    let maxFrequencyHz: Float
    let minLogFrequency: Float
    let logRange: Float
    let tickFrequenciesHz: [Float]

    init(sampleRateHz: Double) {
        let nyquistHz = max(Float(sampleRateHz / 2), 60)
        let minimumFrequencyHz = min(30, nyquistHz)
        let maximumFrequencyHz = max(minimumFrequencyHz * 2, nyquistHz)
        minFrequencyHz = minimumFrequencyHz
        maxFrequencyHz = maximumFrequencyHz
        minLogFrequency = log10(minimumFrequencyHz)
        let maxLogFrequency = log10(maximumFrequencyHz)
        logRange = max(maxLogFrequency - minLogFrequency, 0.001)

        let preferredTicks: [Float] = [30, 63, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000]
        tickFrequenciesHz = preferredTicks.filter { frequencyHz in
            frequencyHz >= minimumFrequencyHz && frequencyHz <= maximumFrequencyHz
        }
    }

    func xPosition(for frequencyHz: Float, width: CGFloat) -> CGFloat {
        normalizedX(for: frequencyHz) * width
    }

    func frequency(forNormalizedRatio normalizedRatio: CGFloat) -> Float {
        let clampedRatio = min(max(normalizedRatio, 0), 1)
        let frequencyHz = pow(10.0, Double(minLogFrequency) + Double(logRange) * Double(clampedRatio))
        return Float(frequencyHz)
    }

    private func normalizedX(for frequencyHz: Float) -> CGFloat {
        let clampedFrequencyHz = min(max(frequencyHz, minFrequencyHz), maxFrequencyHz)
        let normalized = (log10(clampedFrequencyHz) - minLogFrequency) / logRange
        return CGFloat(normalized)
    }
}

private struct SpectrumHistoryWaterfallView: View {
    let rowsDb: [[Float]]
    let settings: WaterfallDisplaySettings
    var showsLabels = true
    var chartHeight: CGFloat = 150

    private var normalizedRows: [[Double]] {
        waterfallNormalizedRows(rowsDb: rowsDb, settings: settings)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if showsLabels {
                Text("Spectrum history")
                    .font(.headline)
            }

            Canvas { context, size in
                let renderRows = normalizedRows
                guard let firstRow = renderRows.first, !firstRow.isEmpty else {
                    return
                }

                let rowHeight = size.height / CGFloat(renderRows.count)
                let columnWidth = size.width / CGFloat(firstRow.count)

                for (rowIndex, row) in renderRows.enumerated() {
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
                            with: .color(
                                waterfallColor(
                                    for: value,
                                    colormap: settings.colormap
                                )
                            )
                        )
                    }
                }
            }
            .frame(height: chartHeight)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.secondary.opacity(0.10))
            )
            .animation(nil, value: rowsDb)
            .animation(nil, value: settings)

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

private func waterfallNormalizedRows(
    rowsDb: [[Float]],
    settings: WaterfallDisplaySettings
) -> [[Double]] {
    let flattenedValues = rowsDb.flatMap { row in
        row.map(Double.init)
    }
    guard !flattenedValues.isEmpty else {
        return []
    }

    let sortedValues = flattenedValues.sorted()
    let upperIndex = sortedValues.count - 1
    let minPercentile = min(max(settings.minPercentile, 0), 0.99)
    let maxPercentile = max(min(settings.maxPercentile, 1), minPercentile + 0.01)
    let lowIndex = min(max(Int(Double(upperIndex) * minPercentile), 0), upperIndex)
    let highIndex = min(max(Int(Double(upperIndex) * maxPercentile), 0), upperIndex)
    let lowValue = sortedValues[lowIndex]
    var highValue = sortedValues[highIndex]
    if highValue <= lowValue {
        highValue = lowValue + 1
    }

    let gamma = max(settings.gamma, 0.25)
    let range = highValue - lowValue

    return rowsDb.map { row in
        row.map { value in
            let normalized = min(max((Double(value) - lowValue) / range, 0), 1)
            return pow(normalized, gamma)
        }
    }
}

private func waterfallColor(for normalizedValue: Double, colormap: WaterfallColormap) -> Color {
    let clamped = min(max(normalizedValue, 0), 1)
    let scaled = clamped * Double(colormap.colorStops.count - 1)
    let startIndex = min(Int(scaled), colormap.colorStops.count - 2)
    let endIndex = startIndex + 1
    let fraction = scaled - Double(startIndex)
    return interpolatedWaterfallColor(
        startHex: colormap.colorStops[startIndex],
        endHex: colormap.colorStops[endIndex],
        fraction: fraction
    )
}

private func interpolatedWaterfallColor(startHex: UInt32, endHex: UInt32, fraction: Double) -> Color {
    let start = waterfallRgbComponents(from: startHex)
    let end = waterfallRgbComponents(from: endHex)

    return Color(
        red: start.red + (end.red - start.red) * fraction,
        green: start.green + (end.green - start.green) * fraction,
        blue: start.blue + (end.blue - start.blue) * fraction
    )
}

private func waterfallRgbComponents(from hex: UInt32) -> (red: Double, green: Double, blue: Double) {
    let red = Double((hex >> 16) & 0xFF) / 255.0
    let green = Double((hex >> 8) & 0xFF) / 255.0
    let blue = Double(hex & 0xFF) / 255.0
    return (red, green, blue)
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

private func sharedFrequencyRange(_ startHz: Float, _ endHz: Float) -> String {
    AnalyzerTextFormatter.shared.formatFrequencyRange(startHz: startHz, endHz: endHz)
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

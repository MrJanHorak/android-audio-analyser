import SwiftUI

struct AnalyzerInfoSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section("Microphone setup") {
                InfoCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("1. Tap Start live capture")
                            .font(.headline)
                        Text("On a fresh install, the main capture button triggers the iOS microphone prompt.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                InfoCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("2. If access was denied")
                            .font(.headline)
                        Text("Open Settings > Privacy & Security > Microphone and enable AudioAnalyser.")
                            .font(.callout)
                        Text("If the app is missing from that list, delete it, reinstall from Xcode, and tap Start live capture again.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section("How to use it") {
                ForEach(IOSAnalyzerReferenceCatalog.guideTips) { tip in
                    InfoCard {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(tip.title)
                                .font(.headline)
                            Text(tip.action)
                                .font(.callout.weight(.medium))
                            Text(tip.whyItHelps)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section("Source overlays") {
                ForEach(IOSAnalyzerReferenceCatalog.overlayProfiles.filter { $0.id != "off" }) { profile in
                    InfoCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(profile.label)
                                .font(.headline)
                            Text(profile.summary)
                                .font(.footnote)
                                .foregroundStyle(.secondary)

                            if !profile.bands.isEmpty {
                                OverlayBandStrip(bands: profile.bands)
                            }
                        }
                    }
                }
            }

            Section("Room curves") {
                ForEach(IOSAnalyzerReferenceCatalog.targetCurveProfiles.filter(\.isEnabled)) { profile in
                    InfoCard {
                        VStack(alignment: .leading, spacing: 10) {
                            CurveSummaryView(targetCurve: profile)
                            InfoDetailRow(label: "Best for", value: profile.useCase)
                            InfoDetailRow(label: "Caution", value: profile.caution, secondary: true)
                        }
                    }
                }
            }

            Section("EQ starting points") {
                ForEach(IOSAnalyzerReferenceCatalog.eqReferenceEntries) { entry in
                    InfoCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(entry.title)
                                .font(.headline)
                            Text(entry.role)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            InfoDetailRow(label: "HPF", value: entry.highPass)
                            InfoDetailRow(label: "Body", value: entry.body)
                            InfoDetailRow(label: "Presence", value: entry.presence)
                            InfoDetailRow(label: "Watch", value: entry.watchOut, secondary: true)
                            InfoDetailRow(label: "Start", value: entry.startingMove, secondary: true)
                        }
                    }
                }
            }

            Section("Capture notes") {
                InfoCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Device first")
                            .font(.headline)
                        Text("The simulator is useful for launch and UI checks, but live microphone behavior is most reliable on a physical iPhone or iPad.")
                            .font(.callout)
                        InfoDetailRow(
                            label: "Simulator input",
                            value: "Set Simulator > I/O > Audio Input to a Mac microphone and make sure Simulator has macOS microphone permission.",
                            secondary: true
                        )
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Analyzer info")
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

private struct InfoCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.secondary.opacity(0.08))
        )
        .listRowInsets(EdgeInsets(top: 6, leading: 0, bottom: 6, trailing: 0))
    }
}

private struct InfoDetailRow: View {
    let label: String
    let value: String
    var secondary = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.footnote)
                .foregroundStyle(secondary ? .secondary : .primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

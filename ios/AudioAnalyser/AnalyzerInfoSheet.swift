import SwiftUI

struct AnalyzerInfoSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section("How to use it") {
                ForEach(IOSAnalyzerReferenceCatalog.guideTips) { tip in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tip.title)
                            .font(.headline)
                        Text(tip.action)
                            .font(.subheadline)
                        Text(tip.whyItHelps)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }

            Section("Source overlays") {
                ForEach(IOSAnalyzerReferenceCatalog.overlayProfiles.filter { $0.id != "off" }) { profile in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(profile.label)
                            .font(.headline)
                        Text(profile.description)
                            .font(.footnote)
                            .foregroundStyle(.secondary)

                        if !profile.bands.isEmpty {
                            OverlayBandStrip(bands: profile.bands)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }

            Section("Room curves") {
                ForEach(IOSAnalyzerReferenceCatalog.targetCurveProfiles.filter(\.isEnabled)) { profile in
                    VStack(alignment: .leading, spacing: 8) {
                        CurveSummaryView(targetCurve: profile)
                        Text("Best for: \(profile.useCase)")
                            .font(.footnote)
                        Text("Caution: \(profile.caution)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }

            Section("EQ starting points") {
                ForEach(IOSAnalyzerReferenceCatalog.eqReferenceEntries) { entry in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(entry.title)
                            .font(.headline)
                        Text(entry.role)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("HPF: \(entry.highPass)")
                            .font(.footnote)
                        Text("Body: \(entry.body)")
                            .font(.footnote)
                        Text("Presence: \(entry.presence)")
                            .font(.footnote)
                        Text("Watch: \(entry.watchOut)")
                            .font(.footnote)
                        Text("Start: \(entry.startingMove)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }

            Section("Capture notes") {
                Text("The simulator can show the UI, but live microphone input is most reliable on a physical iPhone.")
                Text("For Simulator audio input, set Simulator > I/O > Audio Input to a Mac microphone and make sure macOS microphone permission is enabled for Simulator.")
                    .foregroundStyle(.secondary)
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

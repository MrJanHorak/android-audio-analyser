import Foundation
import Shared
import SwiftUI

private let savedTargetCurvesStorageKey = "saved_target_curves_json"

struct SavedTargetCurveDetailRow: Identifiable, Equatable {
    let label: String
    let value: String

    var id: String {
        label
    }
}

extension SavedTargetCurvePreset {
    func lastUsedLabel(nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1_000)) -> String {
        guard lastUsedAtEpochMillis > 0 else {
            return "Not used yet"
        }

        let elapsedMillis = max(nowMillis - lastUsedAtEpochMillis, 0)
        let elapsedDays = elapsedMillis / 86_400_000
        switch elapsedDays {
        case 0:
            return "Today"
        case 1:
            return "Yesterday"
        case 2...6:
            return "\(elapsedDays) days ago"
        default:
            let date = Date(timeIntervalSince1970: TimeInterval(lastUsedAtEpochMillis) / 1_000)
            let formatter = DateFormatter()
            formatter.locale = .current
            formatter.dateFormat = "d MMM yyyy"
            return formatter.string(from: date)
        }
    }

    func venueDetailRows() -> [SavedTargetCurveDetailRow] {
        var rows: [SavedTargetCurveDetailRow] = []

        if !venueCategory.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "Category", value: venueCategory))
        }
        if !venueName.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "Venue", value: venueName))
        }
        if !deskName.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "Desk", value: deskName))
        }
        if !paSystem.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "PA", value: paSystem))
        }
        if !roomSize.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "Room", value: roomSize))
        }
        if !problemBands.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "Problem bands", value: problemBands))
        }
        if lastUsedAtEpochMillis > 0 {
            rows.append(SavedTargetCurveDetailRow(label: "Last used", value: lastUsedLabel()))
        }
        if !notes.isBlankForSavedTargetCurve {
            rows.append(SavedTargetCurveDetailRow(label: "Notes", value: notes))
        }

        return rows
    }

    func asIOSTargetCurveProfile() -> IOSTargetCurveProfile {
        let defaultSettings = TargetCurveHelpersKt.defaultCustomTargetCurveSettings()
        let points = TargetCurveHelpersKt.buildCustomTargetCurvePoints(
            points: settings.points,
            fallbackPoints: defaultSettings.points
        ).map { point in
            IOSTargetCurvePoint(
                frequencyHz: point.frequencyHz,
                relativeDb: point.relativeDb
            )
        }

        return IOSTargetCurveProfile(
            id: id,
            label: settings.name.isBlankForSavedTargetCurve ? defaultSettings.name : settings.name,
            badge: "Saved",
            summary: TargetCurveHelpersKt.buildSavedTargetCurveDescription(venueName: venueName),
            useCase: "Useful when you regularly mix in the same room or for the same organisation and want your own repeatable reference.",
            caution: "Use it as a recall target, then still tune by ear for the audience size, PA deployment, and stage volume that day.",
            color: Color(red: 142.0 / 255.0, green: 36.0 / 255.0, blue: 170.0 / 255.0),
            points: points
        )
    }
}

@MainActor
final class SavedTargetCurveStore: ObservableObject {
    @Published private(set) var presets: [SavedTargetCurvePreset] = []

    private let userDefaults: UserDefaults

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        reload()
    }

    func reload() {
        presets = Self.loadPresets(from: userDefaults)
    }

    func contains(id: String) -> Bool {
        presets.contains { $0.id == id }
    }

    func upsert(_ preset: SavedTargetCurvePreset) {
        let updatedPresets = Self.sortPresets(
            presets.filter { $0.id != preset.id } + [preset]
        )
        persist(updatedPresets)
    }

    func delete(id: String) {
        persist(Self.sortPresets(presets.filter { $0.id != id }))
    }

    func markUsed(id: String, nowMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1_000)) {
        var found = false
        let updatedPresets = presets.map { preset in
            guard preset.id == id else {
                return preset
            }

            found = true
            return preset.markUsed(nowMillis: nowMillis)
        }

        guard found else {
            return
        }

        persist(Self.sortPresets(updatedPresets))
    }

    private func persist(_ updatedPresets: [SavedTargetCurvePreset]) {
        presets = updatedPresets

        guard let rawJson = Self.jsonString(for: updatedPresets) else {
            userDefaults.removeObject(forKey: savedTargetCurvesStorageKey)
            return
        }

        userDefaults.set(rawJson, forKey: savedTargetCurvesStorageKey)
    }

    private static func loadPresets(from userDefaults: UserDefaults) -> [SavedTargetCurvePreset] {
        guard
            let rawJson = userDefaults.string(forKey: savedTargetCurvesStorageKey),
            let data = rawJson.data(using: .utf8),
            let jsonObject = try? JSONSerialization.jsonObject(with: data),
            let items = jsonObject as? [[String: Any]]
        else {
            return []
        }

        return sortPresets(items.compactMap(makePreset(from:)))
    }

    private static func jsonString(for presets: [SavedTargetCurvePreset]) -> String? {
        let items: [[String: Any]] = presets.map { preset in
            [
                "id": preset.id,
                "name": preset.settings.name,
                "venueName": preset.venueName,
                "venueCategory": preset.venueCategory,
                "deskName": preset.deskName,
                "paSystem": preset.paSystem,
                "roomSize": preset.roomSize,
                "problemBands": preset.problemBands,
                "lastUsedAtEpochMillis": preset.lastUsedAtEpochMillis,
                "lastUsedAt": preset.lastUsedAtEpochMillis,
                "generalNotes": preset.notes,
                "notes": preset.notes,
                "points": preset.settings.points.map(\.floatValue)
            ]
        }

        guard
            let data = try? JSONSerialization.data(withJSONObject: items),
            let rawJson = String(data: data, encoding: .utf8)
        else {
            return nil
        }

        return rawJson
    }

    private static func makePreset(from item: [String: Any]) -> SavedTargetCurvePreset? {
        guard let id = (item["id"] as? String)?.trimmedForSavedTargetCurve, !id.isEmpty else {
            return nil
        }

        let defaultSettings = TargetCurveHelpersKt.defaultCustomTargetCurveSettings()
        let defaultPointValues = defaultSettings.points.map(\.floatValue)
        let pointCount = AnalyzerModelsKt.customTargetCurveFrequencies.count
        let rawPoints = item["points"] as? [Any]
        let pointValues = (0..<pointCount).map { index in
            if let value = rawPoints?[safe: index] as? NSNumber {
                return value.floatValue
            }

            return defaultPointValues[safe: index] ?? 0
        }

        let notes = ((item["generalNotes"] as? String) ?? (item["notes"] as? String) ?? "")
            .trimmedForSavedTargetCurve

        return SavedTargetCurvePreset(
            id: id,
            settings: CustomTargetCurveSettings(
                name: ((item["name"] as? String)?.trimmedForSavedTargetCurve).flatMap { $0.isEmpty ? nil : $0 } ?? defaultSettings.name,
                points: pointValues.map { KotlinFloat(float: $0) }
            ),
            venueName: (item["venueName"] as? String)?.trimmedForSavedTargetCurve ?? "",
            venueCategory: (item["venueCategory"] as? String)?.trimmedForSavedTargetCurve ?? "",
            deskName: (item["deskName"] as? String)?.trimmedForSavedTargetCurve ?? "",
            paSystem: (item["paSystem"] as? String)?.trimmedForSavedTargetCurve ?? "",
            roomSize: (item["roomSize"] as? String)?.trimmedForSavedTargetCurve ?? "",
            problemBands: (item["problemBands"] as? String)?.trimmedForSavedTargetCurve ?? "",
            lastUsedAtEpochMillis: (item["lastUsedAtEpochMillis"] as? NSNumber)?.int64Value
                ?? (item["lastUsedAt"] as? NSNumber)?.int64Value
                ?? 0,
            notes: notes
        )
    }

    private static func sortPresets(_ presets: [SavedTargetCurvePreset]) -> [SavedTargetCurvePreset] {
        presets.sorted { lhs, rhs in
            if lhs.lastUsedAtEpochMillis != rhs.lastUsedAtEpochMillis {
                return lhs.lastUsedAtEpochMillis > rhs.lastUsedAtEpochMillis
            }

            let categoryOrder = lhs.venueCategory.localizedCaseInsensitiveCompare(rhs.venueCategory)
            if categoryOrder != .orderedSame {
                return categoryOrder == .orderedAscending
            }

            return lhs.displayName().localizedCaseInsensitiveCompare(rhs.displayName()) == .orderedAscending
        }
    }
}

struct SavedTargetCurveManagerView: View {
    let savedTargetCurves: [SavedTargetCurvePreset]
    let onSave: (SavedTargetCurvePreset) -> Void
    let onDelete: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var editingCurveId: String?
    @State private var curveName: String
    @State private var venueName = ""
    @State private var venueCategory = ""
    @State private var deskName = ""
    @State private var paSystem = ""
    @State private var roomSize = ""
    @State private var problemBands = ""
    @State private var notes = ""
    @State private var pointValues: [Double]

    init(
        savedTargetCurves: [SavedTargetCurvePreset],
        initialSelectedCurveId: String?,
        onSave: @escaping (SavedTargetCurvePreset) -> Void,
        onDelete: @escaping (String) -> Void
    ) {
        self.savedTargetCurves = savedTargetCurves
        self.onSave = onSave
        self.onDelete = onDelete

        let defaultSettings = TargetCurveHelpersKt.defaultCustomTargetCurveSettings()
        _editingCurveId = State(initialValue: initialSelectedCurveId)
        _curveName = State(initialValue: defaultSettings.name)
        _pointValues = State(initialValue: defaultSettings.points.map { Double($0.floatValue) })
    }

    private var defaultSettings: CustomTargetCurveSettings {
        TargetCurveHelpersKt.defaultCustomTargetCurveSettings()
    }

    private var categoryOptions: [String] {
        AnalyzerModelsKt.venueCategoryOptions
    }

    private var frequenciesHz: [Float] {
        AnalyzerModelsKt.customTargetCurveFrequencies.map(\.floatValue)
    }

    private var editingPreset: SavedTargetCurvePreset? {
        savedTargetCurves.first { $0.id == editingCurveId }
    }

    var body: some View {
        Form {
            Section("Saved venue curves") {
                Text("Saved venues are sorted by recent use, then category.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button("New") {
                            editingCurveId = nil
                            restoreEditorState(for: nil)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(editingCurveId == nil ? .accentColor : .secondary.opacity(0.35))

                        ForEach(savedTargetCurves, id: \.id) { preset in
                            Button(preset.displayName()) {
                                editingCurveId = preset.id
                                restoreEditorState(for: preset)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(editingCurveId == preset.id ? .accentColor : .secondary.opacity(0.35))
                        }
                    }
                    .padding(.vertical, 4)
                }
            }

            Section("Curve") {
                TextField("Curve name", text: $curveName)
                TextField("Venue or organisation", text: $venueName)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Category")
                        .font(.footnote.weight(.semibold))

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Button("None") {
                                venueCategory = ""
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(venueCategory.isEmpty ? .accentColor : .secondary.opacity(0.35))

                            ForEach(categoryOptions, id: \.self) { category in
                                Button(category) {
                                    venueCategory = category
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(venueCategory == category ? .accentColor : .secondary.opacity(0.35))
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                if let editingPreset {
                    Text("Last used \(editingPreset.lastUsedLabel())")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Venue recall") {
                Text("Store the practical details you normally need when you walk back into the room.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                TextField("Desk or console", text: $deskName)
                TextField("PA or speaker system", text: $paSystem)
                TextField("Room size or type", text: $roomSize)
                TextField("Known problem bands", text: $problemBands, axis: .vertical)
                    .lineLimit(2...3)
                TextField("General notes", text: $notes, axis: .vertical)
                    .lineLimit(3...5)
            }

            Section("Curve points") {
                Text("Save a reusable target for a venue, organisation, or recurring event mix.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                ForEach(Array(frequenciesHz.enumerated()), id: \.offset) { index, frequencyHz in
                    VStack(alignment: .leading, spacing: 8) {
                        Text("\(savedTargetCurveFrequencyLabel(frequencyHz)) \(savedTargetCurveLevelLabel(Float(pointValues[index])))")
                            .font(.footnote.weight(.semibold))
                        Slider(value: pointBinding(for: index), in: -12...6)
                    }
                    .padding(.vertical, 4)
                }
            }

            if let editingCurveId {
                Section {
                    Button("Delete curve", role: .destructive) {
                        onDelete(editingCurveId)
                        dismiss()
                    }
                }
            }
        }
        .navigationTitle("Saved venue curves")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") {
                    dismiss()
                }
            }

            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    onSave(makePreset())
                    dismiss()
                }
            }
        }
        .onAppear {
            restoreEditorState(for: editingPreset)
        }
    }

    private func restoreEditorState(for preset: SavedTargetCurvePreset?) {
        curveName = preset?.settings.name ?? defaultSettings.name
        venueName = preset?.venueName ?? ""
        venueCategory = preset?.venueCategory ?? ""
        deskName = preset?.deskName ?? ""
        paSystem = preset?.paSystem ?? ""
        roomSize = preset?.roomSize ?? ""
        problemBands = preset?.problemBands ?? ""
        notes = preset?.notes ?? ""

        let normalizedPoints = TargetCurveHelpersKt.normalizeCustomTargetCurveLevels(
            points: preset?.settings.points ?? defaultSettings.points,
            fallbackPoints: defaultSettings.points
        )
        pointValues = normalizedPoints.map { Double($0.floatValue) }
    }

    private func pointBinding(for index: Int) -> Binding<Double> {
        Binding {
            pointValues[safe: index] ?? 0
        } set: { newValue in
            guard pointValues.indices.contains(index) else {
                return
            }
            pointValues[index] = newValue
        }
    }

    private func makePreset() -> SavedTargetCurvePreset {
        let presetId = editingCurveId ?? "\(AnalyzerModelsKt.CUSTOM_TARGET_CURVE_ID_PREFIX)\(UUID().uuidString.lowercased())"
        let preservedLastUsed = editingPreset?.lastUsedAtEpochMillis ?? 0

        return SavedTargetCurvePreset(
            id: presetId,
            settings: CustomTargetCurveSettings(
                name: curveName.trimmedForSavedTargetCurve.isEmpty ? defaultSettings.name : curveName.trimmedForSavedTargetCurve,
                points: pointValues.map { KotlinFloat(float: Float($0)) }
            ),
            venueName: venueName.trimmedForSavedTargetCurve,
            venueCategory: venueCategory.trimmedForSavedTargetCurve,
            deskName: deskName.trimmedForSavedTargetCurve,
            paSystem: paSystem.trimmedForSavedTargetCurve,
            roomSize: roomSize.trimmedForSavedTargetCurve,
            problemBands: problemBands.trimmedForSavedTargetCurve,
            lastUsedAtEpochMillis: preservedLastUsed,
            notes: notes.trimmedForSavedTargetCurve
        )
    }
}

private func savedTargetCurveFrequencyLabel(_ frequencyHz: Float) -> String {
    AnalyzerTextFormatter.shared.formatFrequencyLabel(frequencyHz: frequencyHz)
}

private func savedTargetCurveLevelLabel(_ value: Float) -> String {
    AnalyzerTextFormatter.shared.formatRelativeCurveLevel(relativeDb: value)
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension String {
    var trimmedForSavedTargetCurve: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var isBlankForSavedTargetCurve: Bool {
        trimmedForSavedTargetCurve.isEmpty
    }
}

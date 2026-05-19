import SwiftUI
import Shared

struct IOSOverlayBand: Identifiable, Equatable {
    let label: String
    let startHz: Float
    let endHz: Float
    let note: String
    let color: Color

    var id: String {
        "\(label)-\(Int(startHz.rounded()))-\(Int(endHz.rounded()))"
    }

    func contains(_ frequencyHz: Float) -> Bool {
        frequencyHz >= startHz && frequencyHz <= endHz
    }
}

struct IOSOverlayProfile: Identifiable, Equatable {
    let id: String
    let label: String
    let description: String
    let bands: [IOSOverlayBand]

    func band(for frequencyHz: Float) -> IOSOverlayBand? {
        bands.first { $0.contains(frequencyHz) }
    }
}

struct IOSTargetCurvePoint: Equatable {
    let frequencyHz: Float
    let relativeDb: Float
}

struct IOSTargetCurveProfile: Identifiable, Equatable {
    let id: String
    let label: String
    let badge: String
    let description: String
    let useCase: String
    let caution: String
    let color: Color
    let points: [IOSTargetCurvePoint]

    var isEnabled: Bool {
        !points.isEmpty
    }
}

struct IOSEqReferenceEntry: Identifiable, Equatable {
    let title: String
    let role: String
    let highPass: String
    let body: String
    let presence: String
    let watchOut: String
    let startingMove: String

    var id: String { title }
}

struct IOSGuideTip: Identifiable, Equatable {
    let title: String
    let action: String
    let whyItHelps: String

    var id: String { title }
}

enum IOSAnalyzerReferenceCatalog {
    static let overlayProfiles: [IOSOverlayProfile] = {
        let rawProfiles = AnalyzerReferenceCatalog.shared.overlayProfiles() as NSArray
        var profiles: [IOSOverlayProfile] = []

        for case let profile as AnalyzerOverlayReferenceProfile in rawProfiles {
            let rawBands = profile.bands as NSArray
            var bands: [IOSOverlayBand] = []

            for case let band as AnalyzerGuideRangeBand in rawBands {
                bands.append(
                    IOSOverlayBand(
                        label: band.label,
                        startHz: band.startHz,
                        endHz: band.endHz,
                        note: band.note,
                        color: Color(
                            red: Double(band.color.red),
                            green: Double(band.color.green),
                            blue: Double(band.color.blue)
                        )
                    )
                )
            }

            profiles.append(
                IOSOverlayProfile(
                    id: profile.id,
                    label: profile.label,
                    description: profile.description,
                    bands: bands
                )
            )
        }

        return profiles
    }()

    static let targetCurveProfiles: [IOSTargetCurveProfile] = {
        let rawProfiles = AnalyzerReferenceCatalog.shared.targetCurveProfiles() as NSArray
        var profiles: [IOSTargetCurveProfile] = []

        for case let profile as AnalyzerTargetCurveReferenceProfile in rawProfiles {
            let rawPoints = profile.points as NSArray
            var points: [IOSTargetCurvePoint] = []

            for case let point as TargetCurvePoint in rawPoints {
                points.append(
                    IOSTargetCurvePoint(
                        frequencyHz: point.frequencyHz,
                        relativeDb: point.relativeDb
                    )
                )
            }

            profiles.append(
                IOSTargetCurveProfile(
                    id: profile.id,
                    label: profile.label,
                    badge: profile.badge,
                    description: profile.description,
                    useCase: profile.useCase,
                    caution: profile.caution,
                    color: Color(
                        red: Double(profile.color.red),
                        green: Double(profile.color.green),
                        blue: Double(profile.color.blue)
                    ),
                    points: points
                )
            )
        }

        return profiles
    }()

    static let eqReferenceEntries: [IOSEqReferenceEntry] = {
        let rawEntries = AnalyzerReferenceCatalog.shared.eqReferenceEntries() as NSArray
        var entries: [IOSEqReferenceEntry] = []

        for case let entry as EqReferenceEntry in rawEntries {
            entries.append(
                IOSEqReferenceEntry(
                    title: entry.title,
                    role: entry.role,
                    highPass: entry.highPass,
                    body: entry.body,
                    presence: entry.presence,
                    watchOut: entry.watchOut,
                    startingMove: entry.startingMove
                )
            )
        }

        return entries
    }()

    static let guideTips: [IOSGuideTip] = [
        IOSGuideTip(
            title: "Ring Out Feedback Faster",
            action: "Raise the source until it starts to ring, watch the tallest peak, then make a small targeted cut.",
            whyItHelps: "Feedback usually shows up as a narrow, stubborn hotspot. Small cuts keep more tone than broad subtraction."
        ),
        IOSGuideTip(
            title: "Use Overlays on Purpose",
            action: "Select the source you are shaping before you start chasing the graph.",
            whyItHelps: "The overlay narrows your attention to the zones that usually matter for that source instead of the whole spectrum."
        ),
        IOSGuideTip(
            title: "Choose Vocal Range First",
            action: "Use Lead Vocal for a broad check, Low Vox for lower-range singers, and High Vox for higher-range singers.",
            whyItHelps: "The overlay is a starting map. Match it to the actual singer, mic, and room."
        ),
        IOSGuideTip(
            title: "Clear Mud Before You Boost",
            action: "If a channel feels cloudy, work 200-400 Hz before reaching for top-end boosts.",
            whyItHelps: "Small low-mid cuts usually create more clarity and more gain before feedback than adding highs."
        ),
        IOSGuideTip(
            title: "Calibrate Expectations",
            action: "Treat the phone as a reference tool and calibrate it against a known meter when accuracy matters.",
            whyItHelps: "That keeps the analyzer useful for trends and recall without mistaking it for a lab-grade SPL meter."
        ),
        IOSGuideTip(
            title: "Watch Listener Fatigue",
            action: "If the room feels tiring, check both overall level and energy in the 2-5 kHz region.",
            whyItHelps: "Listener fatigue usually comes from loudness plus aggressive upper mids, not only one or the other."
        )
    ]
}

import SwiftUI

struct ContentView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("iOS port in progress")
                            .font(.largeTitle.weight(.bold))
                        Text("This SwiftUI shell is the first iOS checkpoint. The shared Kotlin core is ready to be linked next.")
                            .foregroundStyle(.secondary)
                    }

                    GroupBox("Current status") {
                        VStack(alignment: .leading, spacing: 10) {
                            StatusRow(title: "Shared analyzer math", detail: "Available in the shared Kotlin module")
                            StatusRow(title: "Shared models", detail: "Feedback and target-curve models moved to shared code")
                            StatusRow(title: "Shared feedback tracker", detail: "Peak tracking logic now lives in shared code")
                            StatusRow(title: "iOS UI shell", detail: "SwiftUI app scaffolded and buildable in Xcode")
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    GroupBox("Next milestones") {
                        VStack(alignment: .leading, spacing: 10) {
                            BulletRow(text: "Link Shared.framework into the Xcode project")
                            BulletRow(text: "Add the first iOS analyzer screen backed by shared logic")
                            BulletRow(text: "Implement iOS microphone permission and audio session setup")
                            BulletRow(text: "Start live SPL and spectrum rendering on iOS")
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

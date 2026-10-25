import SwiftUI

/// App settings. Starts with appearance, connection and servers; the rest of `SettingsScreen` lands in phase 10.
struct SettingsView: View {
    @Environment(AppModel.self) private var app
    @AppStorage(PreferenceKey.themeMode) private var themeMode: ThemeMode = .system
    @State private var confirmDisconnect = false
    @State private var openStatic = DebugLaunch.form == "static"

    var body: some View {
        Form {
            Section("Appearance") {
                Picker("Theme", selection: $themeMode) {
                    ForEach(ThemeMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
            }

            Section("Servers") {
                ForEach(app.servers) { server in
                    HStack(spacing: Spacing.md) {
                        Image(systemName: server.isHost ? "house" : "server.rack")
                            .foregroundStyle(server.agentId == app.activeAgentId ? Palette.blue : Palette.muted)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(server.label)
                            Text(server.detail)
                                .font(.caption.monospaced())
                                .foregroundStyle(server.reachable ? Palette.muted : Palette.red)
                        }
                        Spacer()
                        if !server.isHost { HealthDot(reachable: server.reachable) }
                        if server.agentId == app.activeAgentId {
                            Image(systemName: "checkmark").foregroundStyle(Palette.blue)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { Task { await app.switchServer(to: server.agentId) } }
                }
            }

            Section("Traefik") {
                NavigationLink {
                    StaticConfigView()
                } label: {
                    Label("Static config", systemImage: "doc.text")
                }
            }

            Section("Connection") {
                LabeledContent("Instance", value: app.isDemo ? String(localized: "Demo") : app.connection?.baseURL.absoluteString ?? "")
                Button("Disconnect", role: .destructive) { confirmDisconnect = true }
            }

            Section {
                LabeledContent("Version", value: Bundle.main.versionLabel)
            }
        }
        .navigationTitle("Settings")
        .navigationDestination(isPresented: $openStatic) { StaticConfigView() }
        .confirmationDialog("Disconnect from this instance?", isPresented: $confirmDisconnect, titleVisibility: .visible) {
            Button("Disconnect", role: .destructive) { app.disconnect() }
        } message: {
            Text("The saved URL and API key are removed from this device.")
        }
    }
}

extension Bundle {
    var versionLabel: String {
        let version = infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }
}

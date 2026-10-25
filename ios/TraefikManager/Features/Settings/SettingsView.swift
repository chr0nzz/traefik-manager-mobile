import SwiftUI

enum SettingsPage: String, CaseIterable, Identifiable, Hashable {
    case connection, servers, auth, notifications, history, staticConfig = "static", appearance, diagnostics, about

    var id: Self { self }

    var title: String {
        switch self {
        case .connection: String(localized: "Traefik connection")
        case .servers: String(localized: "Servers")
        case .auth: String(localized: "Authentication")
        case .notifications: String(localized: "Notifications")
        case .history: String(localized: "Notifications")
        case .staticConfig: String(localized: "Static config")
        case .appearance: String(localized: "Appearance")
        case .diagnostics: String(localized: "Diagnostics")
        case .about: String(localized: "About")
        }
    }

    var subtitle: String {
        switch self {
        case .connection: String(localized: "API URL, credentials and a connection test")
        case .servers: String(localized: "Add, edit and remove agents")
        case .auth: String(localized: "Login status and API keys")
        case .notifications: String(localized: "Channels, route checks and the history")
        case .history: ""
        case .staticConfig: String(localized: "Edit traefik.yml and restart Traefik")
        case .appearance: String(localized: "Theme, language and the dashboard layout")
        case .diagnostics: String(localized: "How this device reaches the server")
        case .about: String(localized: "Versions and open source licences")
        }
    }

    var systemImage: String {
        switch self {
        case .connection: "point.3.connected.trianglepath.dotted"
        case .servers: "server.rack"
        case .auth: "lock.shield"
        case .notifications, .history: "bell"
        case .staticConfig: "doc.text"
        case .appearance: "paintpalette"
        case .diagnostics: "stethoscope"
        case .about: "info.circle"
        }
    }

    @ViewBuilder
    var destination: some View {
        switch self {
        case .connection: ConnectionSettingsView()
        case .servers: ServersSettingsView()
        case .auth: AuthSettingsView()
        case .notifications: NotificationsSettingsView()
        case .history: NotificationHistoryView()
        case .staticConfig: StaticConfigView()
        case .appearance: AppearanceView()
        case .diagnostics: DiagnosticsView()
        case .about: AboutView()
        }
    }
}

/// Settings home: the active server's pages, then the app's own. Port of `SettingsScreen`.
struct SettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var confirmDisconnect = false
    @State private var debugPage: SettingsPage? = DebugLaunch.form.flatMap(SettingsPage.init(rawValue:))

    private var onHost: Bool { app.activeAgentId == nil }

    private var serverPages: [SettingsPage] {
        onHost ? [.connection, .servers, .auth, .notifications, .staticConfig] : [.servers, .staticConfig]
    }

    var body: some View {
        Form {
            Section {
                ForEach(serverPages) { row($0) }
            } header: {
                Text("Server · \(app.activeServer.label)")
            } footer: {
                if !onHost {
                    Text("Authentication, connection and notification settings belong to the host. Switch to the host to change them. Static config is this agent's own file.")
                }
            }

            Section("This app") {
                ForEach([SettingsPage.appearance, .diagnostics, .about]) { row($0) }
            }

            Section {
                Button(role: .destructive) { confirmDisconnect = true } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Label("Disconnect", systemImage: "rectangle.portrait.and.arrow.right")
                        Text("Forget this server and its API key on this device")
                            .font(.caption)
                            .foregroundStyle(Palette.muted)
                    }
                }
            } footer: {
                Text(app.isDemo ? String(localized: "Demo") : app.connection?.baseURL.absoluteString ?? "")
            }
        }
        .navigationTitle("Settings")
        .navigationDestination(for: SettingsPage.self) { $0.destination }
        .navigationDestination(item: $debugPage) { $0.destination }
        .confirmationDialog("Disconnect from this instance?", isPresented: $confirmDisconnect, titleVisibility: .visible) {
            Button("Disconnect", role: .destructive) { app.disconnect() }
        } message: {
            Text("The saved URL and API key are removed from this device.")
        }
    }

    private func row(_ page: SettingsPage) -> some View {
        NavigationLink(value: page) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(page.title)
                    Text(page.subtitle).font(.caption).foregroundStyle(Palette.muted)
                }
            } icon: {
                Image(systemName: page.systemImage).foregroundStyle(Palette.blue)
            }
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

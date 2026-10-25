import SwiftUI

/// Theme, language and the dashboard launcher layout. Port of `AppearanceScreen`.
/// Dynamic colour and the nav bar editor are Android-only; the app language is picked in iOS Settings.
struct AppearanceView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    @AppStorage(PreferenceKey.themeMode) private var themeMode: ThemeMode = .system
    @AppStorage(AppLock.key) private var appLock = false
    @State private var lockError: String?

    private func setLock(_ on: Bool) {
        lockError = nil
        guard on else {
            appLock = false
            return
        }
        Task {
            if let failure = await AppLock.authenticate() {
                lockError = failure
            } else {
                appLock = true
            }
        }
    }

    private var languageName: String {
        let code = Bundle.main.preferredLocalizations.first ?? "en"
        return Locale.current.localizedString(forIdentifier: code)?.capitalized(with: .current) ?? code
    }

    var body: some View {
        Form {
            Section {
                Picker("Theme", selection: $themeMode) {
                    ForEach(ThemeMode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            } header: {
                Text("Theme")
            } footer: {
                Text("The theme lives on this device only. It is not sent to the server.")
            }

            Section {
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                } label: {
                    LabeledContent("App language", value: languageName)
                }
                .foregroundStyle(Palette.text)
            } header: {
                Text("Language")
            } footer: {
                Text("Follows the system language. Pick another one for this app in iOS Settings.")
            }

            Section {
                Picker("Layout", selection: Binding(
                    get: { app.launcher.density },
                    set: { value in
                        guard let service = app.service else { return }
                        Task { await app.launcher.setDensity(value, service: service) }
                    }
                )) {
                    Text("Rows").tag(LauncherDensity.list)
                    Text("Icons").tag(LauncherDensity.icons)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            } header: {
                Text("Dashboard")
            } footer: {
                Text("How the launcher on the Dashboard lists your apps. Shared with the web, so both show the same.")
            }

            Section {
                Toggle(isOn: Binding(get: { appLock && AppLock.available }, set: { setLock($0) })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Require unlock")
                        Text(!AppLock.available ? String(localized: "No Face ID, Touch ID or passcode is set up on this device")
                             : appLock ? String(localized: "Asked for on launch and whenever the app returns from the background")
                             : String(localized: "Ask for \(AppLock.biometryName) before showing anything"))
                            .font(.caption2)
                            .foregroundStyle(Palette.muted)
                    }
                }
                .disabled(!AppLock.available)
                if let lockError {
                    Text(lockError).font(.footnote).foregroundStyle(Palette.red)
                }
            } header: {
                Text("Security")
            } footer: {
                Text("The lock lives on this device only. It is not sent to the server.")
            }
        }
        .navigationTitle("Appearance and security")
        .navigationBarTitleDisplayMode(.inline)
        .task { await app.refreshLauncher() }
    }
}

/// What the manager sees when this device calls it. Port of `DiagnosticsScreen`.
struct DiagnosticsView: View {
    @Environment(AppModel.self) private var app
    @State private var diagnostic: ClientIpDiagnostic?
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error {
                ContentUnavailableView {
                    Label("Diagnostic unavailable", systemImage: "stethoscope")
                } description: { Text(error) } actions: {
                    Button("Retry") { Task { await load() } }.buttonStyle(.borderedProminent)
                }
            } else if let diagnostic {
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text("What the manager sees when this device calls it. This is the address that feeds rate limits, the audit log, ipAllowList and CrowdSec matching.")
                            .font(.footnote)
                            .foregroundStyle(Palette.muted)
                        DetailSection(title: String(localized: "Address"), systemImage: "network", tint: Palette.blue) {
                            DetailRow(label: String(localized: "Effective IP"), value: diagnostic.effectiveIp.ifEmpty("-"), mono: true)
                            DetailRow(label: String(localized: "Class"), value: diagnostic.effectiveClass.ifEmpty("-"))
                            DetailRow(label: String(localized: "Socket peer"), value: diagnostic.socketPeer.ifEmpty("-"), mono: true)
                            DetailRow(label: String(localized: "Peer class"), value: diagnostic.socketPeerClass.ifEmpty("-"), last: true)
                        }
                        DetailSection(title: String(localized: "Proxy chain"), systemImage: "arrow.triangle.swap", tint: Palette.purple) {
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text("\(diagnostic.proxyHops) proxy hops").font(.subheadline)
                                if let trusted = diagnostic.proxyTrusted {
                                    Text(trusted ? "Socket peer is a trusted proxy" : "Socket peer is not a trusted proxy, so forwarding headers are ignored")
                                        .font(.caption)
                                        .foregroundStyle(trusted ? Palette.green : Palette.yellow)
                                }
                                if !diagnostic.trustedProxies.isEmpty {
                                    Text("Trusted: \(diagnostic.trustedProxies.joined(separator: ", "))").font(.caption.monospaced()).foregroundStyle(Palette.muted)
                                }
                                if diagnostic.forwardedForChain.isEmpty {
                                    Text("No X-Forwarded-For chain").font(.caption).foregroundStyle(Palette.muted)
                                } else {
                                    ForEach(diagnostic.forwardedForChain, id: \.self) { hop in
                                        HStack {
                                            Text(hop).font(.caption.monospaced())
                                            Spacer()
                                            Text(diagnostic.classes[hop] ?? "").font(.caption).foregroundStyle(Palette.muted)
                                        }
                                    }
                                }
                            }
                        }
                        DetailSection(title: String(localized: "Headers"), systemImage: "list.bullet.rectangle", tint: Palette.blue) {
                            ForEach(Array(ClientIpDiagnostic.headerOrder.enumerated()), id: \.offset) { index, header in
                                DetailRow(label: header, value: (diagnostic.headers[header] ?? "").ifEmpty("-"), mono: true,
                                          last: index == ClientIpDiagnostic.headerOrder.count - 1)
                            }
                        }
                    }
                    .padding(Spacing.lg)
                }
                .background(Palette.bg)
            }
        }
        .navigationTitle("Diagnostics")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel("Refresh")
            }
        }
        .task { await load() }
    }

    private func load() async {
        guard let service = app.hostService else { return }
        loading = diagnostic == nil
        error = nil
        do {
            diagnostic = try await service.clientIpDiagnostic()
        } catch APIError.http(404, _) {
            error = String(localized: "This server is too old for the client IP diagnostic. Requires Traefik Manager 1.8.0 or newer.")
        } catch {
            self.error = error.localizedDescription.ifEmpty(String(localized: "Could not read the diagnostic"))
        }
        loading = false
    }
}

/// App, manager and Traefik versions plus licences. Port of `AboutScreen`.
struct AboutView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    @State private var managerVersion = "…"
    @State private var traefikVersion = "…"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Card {
                    HStack(spacing: Spacing.md) {
                        Image(systemName: "arrow.triangle.branch")
                            .font(.title2)
                            .foregroundStyle(Palette.blue)
                            .frame(width: 44, height: 44)
                            .background(Palette.blue.opacity(0.12), in: RoundedRectangle(cornerRadius: Radius.md))
                        Text("Traefik Manager").font(.title3.weight(.semibold))
                    }
                    Button("GPL-3.0 licence · xyzlab.dev") {
                        if let url = URL(string: "https://xyzlab.dev") { openURL(url) }
                    }
                    .font(.footnote)
                    Divider().overlay(Palette.border)
                    DetailRow(label: String(localized: "App"), value: Bundle.main.versionLabel, mono: true)
                    DetailRow(label: String(localized: "Manager"), value: managerVersion, mono: true)
                    DetailRow(label: "Traefik", value: traefikVersion, mono: true, last: true)
                }
                SectionLabel(text: String(localized: "Open source licences"))
                Card {
                    Text("The iOS app is built only on Apple's SDKs and bundles no third-party libraries or fonts. Traefik Manager itself is GPL-3.0.")
                        .font(.caption)
                        .foregroundStyle(Palette.muted)
                }
            }
            .padding(Spacing.lg)
        }
        .background(Palette.bg)
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard let service = app.hostService else { return }
            async let manager = try? service.managerVersion()
            async let traefik = try? service.traefikVersion()
            managerVersion = await manager.map { "v\($0.version)" } ?? String(localized: "unknown")
            if let t = await traefik {
                let parts = [t.version.map { "v\($0)" }, t.codename].compactMap { $0 }.filter { !$0.isEmpty }
                traefikVersion = parts.isEmpty ? String(localized: "unknown") : parts.joined(separator: " ")
            } else {
                traefikVersion = String(localized: "unknown")
            }
        }
    }
}

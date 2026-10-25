import SwiftUI

private struct IssuedApiKey: Identifiable {
    var key: String
    var id: String { key }
}

/// Login status (read-only), browser sessions and per-device API keys. Port of `AuthSettingsScreen`.
struct AuthSettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var keys: [ApiKeyEntry] = []
    @State private var raw: JSONObject = [:]
    @State private var loading = true
    @State private var busy = false
    @State private var error: String?
    @State private var newKeyName = ""
    @State private var issued: IssuedApiKey?
    @State private var revokeTarget: ApiKeyEntry?
    @State private var confirmSignOut = false
    @State private var message: String?

    /// Settings flags may arrive as bools or strings like "true"/"1".
    private func flag(_ key: String) -> Bool {
        guard let value = raw[key] else { return false }
        if let bool = value.boolValue { return bool }
        return ["true", "1", "yes", "on"].contains(value.stringValue?.lowercased() ?? "")
    }

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                form
            }
        }
        .navigationTitle("Authentication")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Sign out other sessions?", isPresented: $confirmSignOut) {
            Button("Sign out", role: .destructive) { revokeSessions() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Every browser signed in to the web UI is signed out. API keys, including this app's, keep working.")
        }
        .alert("Revoke this API key?", isPresented: Binding(get: { revokeTarget != nil }, set: { if !$0 { revokeTarget = nil } }), presenting: revokeTarget) { key in
            Button("Revoke", role: .destructive) { revoke(key) }
            Button("Cancel", role: .cancel) {}
        } message: { key in
            Text(key.name.isEmpty
                 ? String(localized: "Anything signed in with this key stops working immediately. If it is the key this app uses, you will have to reconnect.")
                 : String(localized: "Anything signed in with \(key.name) stops working immediately. If it is the key this app uses, you will have to reconnect."))
        }
        .sheet(item: $issued) { key in
            NavigationStack { KeySheet(title: String(localized: "New API key"), body: String(localized: "Save this key - it will not be shown again."), key: key.key) }
                .interactiveDismissDisabled()
        }
        .task { await load() }
        .refreshable { await load() }
        .toast($message)
    }

    private var form: some View {
        Form {
            Section {
                fact(String(localized: "Authentication"),
                     value: flag("no_auth") ? String(localized: "Off") : flag("auth_enabled") ? String(localized: "On") : String(localized: "Disabled"),
                     tone: flag("no_auth") ? Palette.red : flag("auth_enabled") ? Palette.green : Palette.yellow,
                     note: flag("auth_env_forced") ? String(localized: "Forced off by AUTH_ENABLED in the environment")
                        : flag("no_auth") && !flag("has_password") ? String(localized: "No password is set, so the web UI is open")
                        : flag("no_auth") ? String(localized: "Anyone who can reach the web UI is let straight in")
                        : String(localized: "The browser login is required"))
                fact(String(localized: "Two-factor"),
                     value: flag("otp_enabled") ? String(localized: "Enabled") : String(localized: "Disabled"),
                     tone: flag("otp_enabled") ? Palette.green : Palette.muted,
                     note: String(localized: "Covers the browser login. This app authenticates with its API key."))
                fact("OIDC",
                     value: flag("oidc_active") ? String(localized: "Active") : flag("oidc_enabled") ? String(localized: "Configured") : String(localized: "Off"),
                     tone: flag("oidc_active") ? Palette.green : Palette.muted,
                     note: flag("oidc_enabled") && !flag("oidc_active")
                        ? String(localized: "Switched on but not answering, so the password login still applies")
                        : String(localized: "Single sign-on for the browser login"))
            } header: {
                Text("How this server lets people in")
            } footer: {
                Text("Read-only here. Passwords, two-factor and OIDC are changed in the web UI.")
            }

            Section {
                Button("Sign out other sessions", role: .destructive) { confirmSignOut = true }.disabled(busy)
            } header: {
                Text("Browser sessions")
            } footer: {
                Text("Signs out every browser signed in to the web UI. API keys keep working.")
            }

            Section {
                HStack {
                    TextField("My phone", text: $newKeyName)
                        .onChange(of: newKeyName) { _, value in if value.count > 50 { newKeyName = String(value.prefix(50)) } }
                    Button("Create key", action: generate).disabled(busy || newKeyName.isBlank)
                }
                ForEach(Array(keys.enumerated()), id: \.offset) { _, key in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(key.name.ifEmpty(String(localized: "Unnamed key"))).font(.subheadline)
                            Text(key.preview.ifEmpty(String(localized: "legacy key - revoke it from manager.yml")))
                                .font(.caption.monospaced())
                                .foregroundStyle(Palette.muted)
                            if !key.createdAt.isEmpty {
                                Text("created \(key.createdAt) UTC").font(.caption2).foregroundStyle(Palette.muted)
                            }
                        }
                        Spacer()
                        Button { revokeTarget = key } label: { Image(systemName: "trash").foregroundStyle(key.revocable ? Palette.red : Palette.muted) }
                            .buttonStyle(.borderless)
                            .disabled(!key.revocable)
                            .accessibilityLabel(String(localized: "Revoke \(key.name)"))
                    }
                }
            } header: {
                Text("API keys \(keys.count)")
            } footer: {
                Text("Each device that talks to this server needs its own key. Revoking one only locks out that device.")
            }

            if let error {
                Section { Text(error).font(.footnote).foregroundStyle(Palette.red) }
            }
        }
    }

    private func fact(_ label: String, value: String, tone: Color, note: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label)
                Spacer()
                Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(tone)
            }
            Text(note).font(.caption).foregroundStyle(Palette.muted)
        }
    }

    private func load() async {
        guard let service = app.hostService else { return }
        error = nil
        async let settings = try? service.settingsRaw()
        do {
            keys = try await service.apiKeyStatus().keys
        } catch {
            self.error = error.localizedDescription.ifEmpty(String(localized: "Could not read the auth settings"))
        }
        raw = await settings ?? raw
        loading = false
    }

    private func run(_ success: String, _ block: @escaping (TMService) async throws -> Void) {
        guard let service = app.hostService else { return }
        busy = true
        Task {
            do {
                try await block(service)
                message = success
            } catch {
                message = error.localizedDescription
            }
            busy = false
            await load()
        }
    }

    private func generate() {
        let name = newKeyName
        run(String(localized: "API key created")) { service in
            let response = try await service.generateApiKey(deviceName: name)
            guard response.ok, let key = response.key else { throw APIError.server(response.error ?? String(localized: "Could not create the key")) }
            issued = IssuedApiKey(key: key)
            newKeyName = ""
        }
    }

    private func revoke(_ key: ApiKeyEntry) {
        run(String(localized: "API key revoked")) { service in
            let response = try await service.revokeApiKey(preview: key.preview)
            guard response.worked else { throw APIError.server(response.error ?? String(localized: "Could not revoke the key")) }
        }
    }

    private func revokeSessions() {
        run(String(localized: "Other browser sessions signed out")) { service in
            let response = try await service.revokeSessions()
            guard response.worked else { throw APIError.server(response.error ?? String(localized: "Could not sign out other sessions")) }
        }
    }
}

import SwiftUI

enum TestState: Equatable {
    case idle, running
    case ok(String)
    case failed(String)
}

/// Where the manager reaches the Traefik API, with a connection test. Port of `ConnectionSettingsScreen`.
struct ConnectionSettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var loading = true
    @State private var saving = false
    @State private var url = ""
    @State private var savedUrl = ""
    @State private var user = ""
    @State private var password = ""
    @State private var passwordStored = false
    @State private var domains: [String] = []
    @State private var certResolver = ""
    @State private var test: TestState = .idle
    @State private var error: String?
    @State private var message: String?

    private var urlValid: Bool { url.hasPrefix("http://") || url.hasPrefix("https://") }
    private var passwordRequired: Bool { passwordStored && !user.isBlank && !ApiOrigin.same(url, savedUrl) }
    private var canSave: Bool { !saving && urlValid && !domains.isEmpty && !(passwordRequired && password.isEmpty) }

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                form
            }
        }
        .navigationTitle("Traefik connection")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving { ProgressView() } else { Button("Save", action: save).disabled(!canSave) }
            }
        }
        .task { await load() }
        .toast($message)
    }

    private var form: some View {
        Form {
            Section {
                TextField("http://traefik:8080", text: $url)
                    .keyboardType(.URL)
                    .onChange(of: url) { test = .idle }
                TextField("Username (optional)", text: $user)
                    .onChange(of: user) { test = .idle }
                SecureField(passwordStored ? String(localized: "New password") : String(localized: "Password (optional)"), text: $password)
                    .onChange(of: password) { test = .idle }
            } header: {
                Text("Traefik API")
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    if !url.isEmpty && !urlValid {
                        Text("Must start with http:// or https://").foregroundStyle(Palette.red)
                    } else {
                        Text("Where this server reaches the Traefik API, not where you reach Traefik.")
                    }
                    if passwordRequired {
                        Text("Re-enter the password. The saved one is only sent to the saved API URL.").foregroundStyle(Palette.red)
                    } else if passwordStored {
                        Text("A password is already saved. Leave this empty to keep it.")
                    } else {
                        Text("Only needed if the Traefik API is behind basic auth.")
                    }
                }
            }
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            Section {
                Button(action: runTest) {
                    HStack {
                        Text("Test connection")
                        Spacer()
                        switch test {
                        case .idle: EmptyView()
                        case .running: ProgressView()
                        case let .ok(version):
                            Label(String(localized: "Connected - Traefik v\(version)"), systemImage: "checkmark.circle.fill")
                                .foregroundStyle(Palette.green).font(.footnote)
                        case .failed: Image(systemName: "xmark.octagon.fill").foregroundStyle(Palette.red)
                        }
                    }
                }
                .disabled(test == .running || !urlValid)
                if case let .failed(text) = test {
                    Text(text).font(.footnote).foregroundStyle(Palette.red)
                }
            } footer: {
                Text("Verify the API URL and credentials against your Traefik instance.")
            }

            Section {
                LabeledContent("Domains", value: domains.isEmpty ? "none" : domains.joined(separator: ", "))
                LabeledContent("Certificate resolvers", value: certResolver.ifEmpty("none"))
            } header: {
                Text("Routing defaults")
            } footer: {
                Text("Edit these in the web UI. Saving here keeps them exactly as they are.")
            }

            if let error {
                Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
            }
        }
    }

    private func load() async {
        guard let service = app.hostService else { return }
        do {
            let raw = try await service.settingsRaw()
            url = raw.text("traefik_api_url")
            savedUrl = url
            user = raw.text("traefik_api_user")
            passwordStored = raw.flag("traefik_api_password_set")
            certResolver = raw.text("cert_resolver")
            domains = raw.strings("domains")
            test = .idle
        } catch {
            self.error = error.localizedDescription.ifEmpty(String(localized: "Could not read the settings"))
        }
        loading = false
    }

    private func runTest() {
        guard let service = app.hostService else { return }
        test = .running
        Task {
            do {
                let result = try await service.testConnection(url: url, user: user, password: password)
                test = result.ok
                    ? .ok(result.version ?? "?")
                    : .failed(result.error.flatMap { $0.isBlank ? nil : $0 } ?? String(localized: "No response from the API"))
            } catch {
                let text = error.localizedDescription
                test = .failed(text.contains("Target address not allowed")
                    ? String(localized: "Could not resolve that host, or the address is not allowed.")
                    : text.contains("Invalid URL") ? String(localized: "That URL is not valid. It must start with http:// or https://.") : text)
            }
        }
    }

    private func save() {
        guard canSave, let service = app.hostService else { return }
        saving = true
        error = nil
        var changes: JSONObject = ["traefik_api_url": .string(url.trimmed), "traefik_api_user": .string(user.trimmed)]
        if !password.isEmpty { changes["traefik_api_password"] = .string(password) }
        Task {
            do {
                try await service.patchSettings(changes)
                savedUrl = url.trimmed
                passwordStored = passwordStored || !password.isEmpty
                password = ""
                message = String(localized: "Connection settings saved")
                await app.refresh()
            } catch {
                self.error = error.localizedDescription
            }
            saving = false
        }
    }
}

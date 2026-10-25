import SwiftUI

/// Every setting the hub keeps for one agent; only changed fields are sent. Port of `AgentConfigScreen`.
struct AgentConfigView: View {
    let agentId: String

    @Environment(AppModel.self) private var app
    @State private var original: [String: String] = [:]
    @State private var form: [String: String] = [:]
    @State private var insecure = false
    @State private var originalInsecure = false
    @State private var tabs: [String: Bool] = [:]
    @State private var originalTabs: [String: Bool] = [:]
    @State private var keySet = false
    @State private var passwordSet = false
    @State private var agentName = ""
    @State private var loading = true
    @State private var saving = false
    @State private var error: String?
    @State private var message: String?

    /// Text fields saved as-is under their API key.
    private static let textKeys = [
        "name", "url", "tma_port", "tma_rate_limit", "traefik_api_url", "cert_resolver", "config_path", "static_config_path",
        "backup_dir", "backup_keep_count", "acme_json_path", "access_log_path", "plugins_dir", "restart_method",
        "traefik_container", "docker_host", "signal_file_path", "crowdsec_lapi_url", "crowdsec_machine_id",
        "crowdsec_client_cert", "crowdsec_client_key", "crowdsec_ca_cert", "domains",
    ]

    private static let restartMethods: [(String, LocalizedStringKey)] = [
        ("", "None"), ("proxy", "Socket proxy"), ("poison-pill", "Poison pill"), ("socket", "Direct socket"),
    ]

    private static let agentTabs: [(String, String)] = [
        ("logs", String(localized: "Logs")), ("crowdsec", "CrowdSec"),
        ("certs", String(localized: "Certificates")), ("plugins", String(localized: "Plugins")),
    ]

    private var dirty: Bool {
        Self.textKeys.contains { (form[$0] ?? "").trimmed != (original[$0] ?? "").trimmed }
            || insecure != originalInsecure || tabs != originalTabs
            || !(form["crowdsec_api_key"] ?? "").isBlank || !(form["crowdsec_machine_password"] ?? "").isBlank
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { form[key] ?? "" }, set: { form[key] = $0 })
    }

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error, original.isEmpty {
                ContentUnavailableView {
                    Label("Could not read the agent", systemImage: "exclamationmark.triangle")
                } description: { Text(error) } actions: {
                    Button("Retry") { Task { await load() } }.buttonStyle(.borderedProminent)
                }
            } else {
                formView
            }
        }
        .navigationTitle(agentName.ifEmpty(String(localized: "Agent")))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving { ProgressView() } else { Button("Save", action: save).disabled(!dirty) }
            }
        }
        .task { await load() }
        .toast($message)
    }

    private var formView: some View {
        Form {
            Section("Agent") {
                field("Name", "name")
                field("Agent URL", "url", help: "Where the manager reaches this agent, for example http://host:8090", keyboard: .URL)
                field("Listen port", "tma_port", help: "TMA_PORT on the agent. Blank keeps its default.", keyboard: .numberPad)
                field("Rate limit", "tma_rate_limit", help: "Requests a minute the agent accepts. Blank keeps its default.", keyboard: .numberPad)
            }
            Section("Traefik") {
                field("Traefik API URL", "traefik_api_url", help: "Where the agent reaches Traefik, not where you do.", keyboard: .URL)
                field("Cert resolver", "cert_resolver")
                field("Domains", "domains", help: "Comma separated. Offered when you create a route on this server.")
                Toggle(isOn: $insecure) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Skip TLS verification")
                        Text("Only for a Traefik API behind a self-signed certificate.").font(.caption2).foregroundStyle(Palette.muted)
                    }
                }
            }
            Section("Paths on the agent") {
                field("Dynamic config directory", "config_path")
                field("Static config file", "static_config_path")
                field("Backup directory", "backup_dir")
                field("Backups to keep", "backup_keep_count", keyboard: .numberPad)
                field("acme.json", "acme_json_path")
                field("Access log", "access_log_path")
                field("Plugins directory", "plugins_dir")
            }
            Section {
                Picker("Method", selection: binding("restart_method")) {
                    ForEach(Self.restartMethods, id: \.0) { Text($0.1).tag($0.0) }
                }
                switch form["restart_method"] ?? "" {
                case "proxy":
                    field("Docker host", "docker_host", help: "The socket proxy, for example tcp://socket-proxy:2375")
                    field("Traefik container", "traefik_container")
                case "socket":
                    field("Traefik container", "traefik_container", help: "The agent needs /var/run/docker.sock mounted.")
                case "poison-pill":
                    field("Signal file", "signal_file_path", help: "A watcher container restarts Traefik when this file changes.")
                default:
                    EmptyView()
                }
            } header: {
                Text("Restart")
            } footer: {
                Text("How the agent restarts Traefik after a config change.")
            }
            Section("CrowdSec") {
                field("LAPI URL", "crowdsec_lapi_url", help: "Used when generating this agent's compose file.", keyboard: .URL)
                secret("Bouncer API key", "crowdsec_api_key",
                       help: keySet ? "A key is stored. Leave this empty to keep it." : "Reads /v1/decisions.")
                field("Machine id", "crowdsec_machine_id")
                secret("Machine password", "crowdsec_machine_password",
                       help: passwordSet ? "A password is stored. Leave this empty to keep it." : "Needed for alerts, which the bouncer key cannot read.")
                field("Client certificate", "crowdsec_client_cert")
                field("Client key", "crowdsec_client_key")
                field("CA certificate", "crowdsec_ca_cert")
            }
            Section {
                ForEach(Self.agentTabs, id: \.0) { tab, label in
                    Toggle(label, isOn: Binding(get: { tabs[tab] ?? true }, set: { tabs[tab] = $0 }))
                }
            } header: {
                Text("Tabs on this server")
            } footer: {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Hiding a tab here hides it in the app and the web UI for this server.")
                    Text("Git backup for this agent is configured in the web UI.")
                }
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
    }

    private func field(_ label: LocalizedStringKey, _ key: String, help: LocalizedStringKey? = nil, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(Palette.muted)
            TextField("", text: binding(key)).keyboardType(keyboard)
            if let help { Text(help).font(.caption2).foregroundStyle(Palette.muted) }
        }
    }

    private func secret(_ label: LocalizedStringKey, _ key: String, help: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(Palette.muted)
            SecureField("", text: binding(key))
            Text(help).font(.caption2).foregroundStyle(Palette.muted)
        }
    }

    private func load() async {
        guard let service = app.hostService else { return }
        error = nil
        do {
            guard let config = try await service.agentConfigs().first(where: { $0.id == agentId }) else {
                error = String(localized: "That agent is no longer registered")
                loading = false
                return
            }
            var values: [String: String] = [:]
            for key in Self.textKeys where key != "domains" { values[key] = config.text(key) }
            values["domains"] = config.domains.joined(separator: ", ")
            original = values
            form = values
            insecure = config.flag("traefik_insecure_skip_verify")
            originalInsecure = insecure
            tabs = config.visibleTabs
            originalTabs = tabs
            keySet = !config.text("crowdsec_api_key").isEmpty
            passwordSet = !config.text("crowdsec_machine_password").isEmpty
            agentName = config.name
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func save() {
        if (form["name"] ?? "").isBlank || (form["url"] ?? "").isBlank {
            message = String(localized: "Name and URL are required")
            return
        }
        var changes: JSONObject = [:]
        for key in Self.textKeys where key != "domains" {
            let value = (form[key] ?? "").trimmed
            if value != (original[key] ?? "").trimmed { changes[key] = .string(value) }
        }
        if insecure != originalInsecure { changes["traefik_insecure_skip_verify"] = .bool(insecure) }
        if (form["domains"] ?? "").trimmed != (original["domains"] ?? "").trimmed {
            changes["domains"] = .array((form["domains"] ?? "").split(separator: ",").map { String($0).trimmed }.filter { !$0.isEmpty }.map(JSONValue.string))
        }
        if tabs != originalTabs { changes["visible_tabs"] = .object(tabs.mapValues(JSONValue.bool)) }
        for key in ["crowdsec_api_key", "crowdsec_machine_password"] where !(form[key] ?? "").isBlank {
            changes[key] = .string(form[key] ?? "")
        }
        guard !changes.isEmpty else {
            message = String(localized: "Nothing to save")
            return
        }
        guard let service = app.hostService else { return }
        saving = true
        Task {
            do {
                let response = try await service.updateAgent(agentId, changes: changes)
                guard response.worked else { throw APIError.server(response.error ?? String(localized: "Could not save the agent")) }
                message = String(localized: "Agent saved")
                form["crowdsec_api_key"] = ""
                form["crowdsec_machine_password"] = ""
                await load()
                await app.refresh()
            } catch {
                message = error.localizedDescription
            }
            saving = false
        }
    }
}

import SwiftUI

private struct IssuedKey: Identifiable {
    var agentName: String
    var key: String
    var rotated: Bool
    var id: String { key }
}

private struct ComposeSnippet: Identifiable {
    var name: String
    var text: String
    var id: String { name }
}

/// Agents this manager reaches: add, rename, configure, rotate keys, install snippet, remove. Port of `ServersScreen`.
struct ServersSettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var configs: [AgentConfig] = []
    @State private var loading = true
    @State private var busy = false
    @State private var addOpen = false
    @State private var newName = ""
    @State private var newURL = ""
    @State private var renameTarget: ServerEntry?
    @State private var renameText = ""
    @State private var renameOpen = false
    @State private var deleteTarget: ServerEntry?
    @State private var rotateTarget: ServerEntry?
    @State private var issued: IssuedKey?
    @State private var compose: ComposeSnippet?
    @State private var configuring: String?
    @State private var message: String?

    private var agents: [ServerEntry] { app.servers.filter { !$0.isHost } }

    var body: some View {
        List {
            Section {
                Text("Agents let one Traefik Manager reach Traefik on other machines. The host itself is always available.")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
                    .listRowBackground(Color.clear)
            }
            Section(String(localized: "Agents \(agents.count)")) {
                if agents.isEmpty && !loading {
                    ContentUnavailableView("No agents yet", systemImage: "server.rack", description: Text("Add one to manage Traefik on another machine from this app."))
                }
                ForEach(agents) { entry in agentRow(entry) }
            }
        }
        .navigationTitle("Servers")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    newName = ""
                    newURL = ""
                    addOpen = true
                } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Add a server")
            }
        }
        .overlay { if busy || (loading && agents.isEmpty) { ProgressView() } }
        .alert("Add a server", isPresented: $addOpen) {
            TextField("edge-01", text: $newName)
            TextField("http://10.0.0.5:8090", text: $newURL)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Add") { add() }.disabled(newName.isBlank || newURL.isBlank)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Agent URL: where this Traefik Manager reaches the agent.")
        }
        .alert(String(localized: "Rename \(renameTarget?.name ?? "")"), isPresented: $renameOpen) {
            TextField("Name", text: $renameText)
            Button("Rename") { rename() }.disabled(renameText.isBlank || renameText == renameTarget?.name)
            Button("Cancel", role: .cancel) {}
        }
        .alert(String(localized: "Remove \(deleteTarget?.name ?? "")?"), isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } }), presenting: deleteTarget) { entry in
            Button("Remove", role: .destructive) { remove(entry) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("This only removes it from this Traefik Manager. The agent keeps running on that machine with the same key.")
        }
        .alert(String(localized: "Rotate the key for \(rotateTarget?.name ?? "")?"), isPresented: Binding(get: { rotateTarget != nil }, set: { if !$0 { rotateTarget = nil } }), presenting: rotateTarget) { entry in
            Button("Rotate key", role: .destructive) { rotate(entry) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("The old key stops working immediately and every request to this server fails until you update its compose file and recreate the container. Health checks will keep looking green, because they are unauthenticated.")
        }
        .sheet(item: $issued) { key in
            NavigationStack { KeySheet(title: key.rotated ? String(localized: "New key for \(key.agentName)") : String(localized: "Key for \(key.agentName)"),
                                       body: key.rotated ? String(localized: "Save this key - it will not be shown again. The agent stops working until you update it.")
                                                         : String(localized: "Save this key - it will not be shown again."),
                                       key: key.key) }
                .interactiveDismissDisabled()
        }
        .navigationDestination(item: $configuring) { AgentConfigView(agentId: $0) }
        .sheet(item: $compose) { snippet in
            NavigationStack { ComposeSheet(snippet: snippet) }
        }
        .task(id: app.generation) { await load() }
        .refreshable { await load() }
        .toast($message)
    }

    private func agentRow(_ entry: ServerEntry) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.name).font(.subheadline.weight(.semibold))
                    Text(entry.url).font(.caption.monospaced()).foregroundStyle(Palette.muted).lineLimit(1)
                    Text(entry.detail).font(.caption2).foregroundStyle(entry.reachable ? Palette.muted : Palette.red)
                }
                Spacer()
                if entry.agentId == app.activeAgentId {
                    Text("active").font(.caption2.weight(.semibold)).foregroundStyle(Palette.blue)
                }
            }
            HStack(spacing: Spacing.lg) {
                Button {
                    renameTarget = entry
                    renameText = entry.name
                    renameOpen = true
                } label: { Image(systemName: "pencil") }
                    .accessibilityLabel(String(localized: "Rename \(entry.name)"))
                if let id = entry.agentId {
                    Button { configuring = id } label: { Image(systemName: "slider.horizontal.3") }
                        .accessibilityLabel(String(localized: "Configure \(entry.name)"))
                }
                Button { compose = ComposeSnippet(name: entry.name, text: composeText(entry)) } label: { Image(systemName: "terminal") }
                    .accessibilityLabel("Install snippet")
                Button { rotateTarget = entry } label: { Image(systemName: "key") }
                    .accessibilityLabel("Rotate the key")
                Spacer()
                Button { deleteTarget = entry } label: { Image(systemName: "trash").foregroundStyle(Palette.red) }
                    .accessibilityLabel(String(localized: "Remove \(entry.name)"))
            }
            .buttonStyle(.borderless)
            .padding(.top, Spacing.xs)
        }
        .padding(.vertical, 2)
    }

    private func composeText(_ entry: ServerEntry) -> String {
        let config = configs.first { $0.id == entry.agentId } ?? AgentConfig(raw: [
            "id": .string(entry.agentId ?? ""), "name": .string(entry.name), "url": .string(entry.url),
        ])
        return AgentCompose.compose(config, apiKey: nil)
    }

    private func load() async {
        guard let service = app.hostService else { return }
        async let fetched = try? service.agentConfigs()
        await app.refresh()
        configs = await fetched ?? configs
        loading = false
    }

    private func mutate(_ block: @escaping (TMService) async throws -> String) {
        guard let service = app.hostService else { return }
        busy = true
        Task {
            do {
                message = try await block(service)
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "That did not work"))
            }
            busy = false
            await load()
        }
    }

    private func add() {
        let name = newName.trimmed
        let url = newURL
        mutate { service in
            let response = try await service.createAgent(name: name, url: url)
            guard response.ok else { throw APIError.server(response.error ?? String(localized: "Could not add the server")) }
            if let key = response.rawKey { issued = IssuedKey(agentName: name, key: key, rotated: false) }
            return String(localized: "Added \(name)")
        }
    }

    private func rename() {
        guard let id = renameTarget?.agentId else { return }
        let name = renameText.trimmed
        mutate { service in
            let response = try await service.updateAgent(id, changes: ["name": .string(name)])
            guard response.worked else { throw APIError.server(response.error ?? String(localized: "Could not save the server")) }
            return String(localized: "Renamed to \(name)")
        }
    }

    private func remove(_ entry: ServerEntry) {
        guard let id = entry.agentId else { return }
        mutate { service in
            let response = try await service.deleteAgent(id)
            guard response.worked else { throw APIError.server(response.error ?? String(localized: "Could not remove the server")) }
            if app.activeAgentId == id { await app.switchServer(to: nil) }
            return String(localized: "Removed \(entry.name)")
        }
    }

    private func rotate(_ entry: ServerEntry) {
        guard let id = entry.agentId else { return }
        mutate { service in
            let response = try await service.rotateAgentKey(id)
            guard response.ok, let key = response.rawKey else {
                throw APIError.server(response.error ?? String(localized: "Could not rotate the key"))
            }
            issued = IssuedKey(agentName: entry.name, key: key, rotated: true)
            return String(localized: "API key rotated - update your agent")
        }
    }
}

/// Shows a one-time secret with copy, and only closes once the user confirms they saved it.
struct KeySheet: View {
    let title: String
    let body_: String
    let key: String

    @Environment(\.dismiss) private var dismiss
    @State private var copied = false

    init(title: String, body: String, key: String) {
        self.title = title
        self.body_ = body
        self.key = key
    }

    var body: some View {
        Form {
            Section {
                Text(body_).font(.footnote)
                Text(key).font(.callout.monospaced()).textSelection(.enabled)
                Button(copied ? String(localized: "Copied") : String(localized: "Copy")) {
                    UIPasteboard.general.string = key
                    copied = true
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("I have saved it") { dismiss() } } }
    }
}

private struct ComposeSheet: View {
    let snippet: ComposeSnippet
    @Environment(\.dismiss) private var dismiss
    @State private var copied = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text("Run this on the remote machine, with the key you saved when the server was added.")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
                Card {
                    ScrollView(.horizontal) {
                        Text(YamlHighlighter.attributed(snippet.text)).font(.caption.monospaced()).textSelection(.enabled)
                    }
                }
            }
            .padding(Spacing.lg)
        }
        .background(Palette.bg)
        .navigationTitle(String(localized: "Install \(snippet.name)"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                Button(copied ? String(localized: "Copied") : String(localized: "Copy")) {
                    UIPasteboard.general.string = snippet.text
                    copied = true
                }
            }
        }
    }
}

import SwiftUI

/// Traefik plugins with usage, update badges, install/edit/remove and the restart prompt. Port of `PluginsScreen`.
struct PluginsView: View {
    @Environment(AppModel.self) private var app
    @State private var plugins: [PluginEntry] = []
    @State private var middlewares: [MiddlewareDef] = []
    @State private var updates: [String: String] = [:]
    @State private var canManage = false
    @State private var loading = true
    @State private var loadError: String?
    @State private var serverError: String?
    @State private var query = ""
    @State private var installing = DebugLaunch.form == "new-plugin"
    @State private var editing: PluginEntry?
    @State private var pendingDelete: PluginEntry?
    @State private var busy = false
    @State private var restartDetail: String?
    @State private var restarting = false
    @State private var message: String?
    @State private var debugDetail: String? = DebugLaunch.route

    private var visible: [PluginEntry] {
        let needle = query.trimmed.lowercased()
        guard !needle.isEmpty else { return plugins }
        return plugins.filter { $0.name.lowercased().contains(needle) || $0.moduleName.lowercased().contains(needle) }
    }

    private func usage(_ name: String) -> Int { PluginUsage.usersOf(name, middlewares).count }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle("Plugins")
            .searchable(text: $query, prompt: Text("Search plugins"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    if canManage {
                        Button { installing = true } label: { Image(systemName: "plus") }
                            .accessibilityLabel("Add a plugin")
                    }
                    Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh plugins")
                }
            }
            .navigationDestination(for: PluginEntry.self) { plugin in
                PluginDetailView(plugin: plugin, latest: updates[plugin.name], users: PluginUsage.usersOf(plugin.name, middlewares))
            }
            .navigationDestination(item: $debugDetail) { name in
                if let plugin = plugins.first(where: { $0.name == name }) {
                    PluginDetailView(plugin: plugin, latest: updates[plugin.name], users: PluginUsage.usersOf(plugin.name, middlewares))
                }
            }
            .sheet(isPresented: $installing) {
                NavigationStack {
                    PluginInstallSheet { response in
                        restartDetail = Self.installedText(response)
                        if let warning = response.warning, !warning.isBlank { message = warning }
                        Task { await load() }
                    }
                }
            }
            .sheet(item: $editing) { plugin in
                NavigationStack {
                    PluginEditSheet(plugin: plugin) {
                        message = String(localized: "Plugin saved - restart Traefik to apply")
                        Task { await load() }
                    }
                }
            }
            .alert("Remove plugin", isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }), presenting: pendingDelete) { plugin in
                Button("Remove", role: .destructive) { remove(plugin) }
                Button("Cancel", role: .cancel) {}
            } message: { plugin in
                Text("Remove plugin \"\(plugin.name)\"? Traefik keeps running it until it restarts.")
            }
            .task(id: app.generation) {
                plugins = []
                loading = true
                restartDetail = nil
                await load()
            }
            .refreshable { await load() }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if loading && plugins.isEmpty {
            ProgressView("Loading plugins").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError, plugins.isEmpty {
            ContentUnavailableView {
                Label("Could not load plugin data", systemImage: "exclamationmark.triangle")
            } description: {
                Text(loadError)
            } actions: {
                Button("Retry") { Task { await load() } }.buttonStyle(.borderedProminent)
            }
        } else if plugins.isEmpty {
            ContentUnavailableView {
                Label(serverError == nil ? "No plugins configured" : "No plugins declared", systemImage: "puzzlepiece.extension")
            } description: {
                Text(serverError ?? String(localized: "Plugins are declared under experimental.plugins in the Traefik static config."))
            } actions: {
                if canManage { Button("Add a plugin") { installing = true }.buttonStyle(.borderedProminent) }
            }
        } else {
            List {
                Group {
                    if let restartDetail {
                        RestartNotice(detail: restartDetail, busy: restarting, onRestart: restart) { self.restartDetail = nil }
                    }
                    if visible.isEmpty {
                        Text("No plugins match your search").font(.footnote).foregroundStyle(Palette.muted)
                    }
                    ForEach(visible) { plugin in
                        ZStack {
                            NavigationLink(value: plugin) { EmptyView() }.opacity(0)
                            PluginCard(
                                plugin: plugin,
                                usage: usage(plugin.name),
                                latest: updates[plugin.name],
                                canManage: canManage,
                                onEdit: { editing = plugin },
                                onDelete: { pendingDelete = plugin }
                            )
                        }
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .overlay { if busy { ProgressView().padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12)) } }
        }
    }

    static func installedText(_ response: PluginInstallResponse) -> String {
        let names = response.plugins.joined(separator: ", ")
        if let file = response.middlewareFile, !file.isBlank {
            return response.plugins.count == 1
                ? String(localized: "Plugin \"\(names)\" saved to the static config, middleware saved to \(file).")
                : String(localized: "Plugins \"\(names)\" saved to the static config, middleware saved to \(file).")
        }
        return response.plugins.count == 1
            ? String(localized: "Plugin \"\(names)\" saved to the static config.")
            : String(localized: "Plugins \"\(names)\" saved to the static config.")
    }

    private func load() async {
        guard let service = app.service else { return }
        loadError = nil
        async let manageable = service.staticManageable()
        async let fetched = service.plugins()
        async let routes = try? service.routes()
        do {
            let response = try await fetched
            plugins = response.plugins.sorted { $0.name.lowercased() < $1.name.lowercased() }
            serverError = response.error.flatMap { $0.isBlank ? nil : $0 }
            middlewares = await routes?.middlewares ?? middlewares
            let catalog = plugins.isEmpty ? [:] : ((try? await service.pluginCatalog())?.plugins ?? [:])
            updates = PluginVersions.updates(plugins, catalog: catalog)
        } catch {
            loadError = error.localizedDescription
        }
        canManage = await manageable
        loading = false
    }

    private func remove(_ plugin: PluginEntry) {
        guard let service = app.service else { return }
        busy = true
        Task {
            do {
                try await StaticPlugins.remove(plugin.name, service: service)
                message = String(localized: "Plugin removed - restart Traefik to apply")
                await load()
            } catch {
                message = error.localizedDescription
            }
            busy = false
        }
    }

    private func restart() {
        guard let service = app.service else { return }
        restarting = true
        Task {
            do {
                try await service.restartTraefik()
                restartDetail = nil
                message = String(localized: "Traefik restarted")
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Restart failed"))
            }
            restarting = false
        }
    }
}

/// Plugin edits staged through `api/static/section` and written straight back. Port of `StaticConfigRepository.section`.
enum StaticPlugins {
    static func save(name: String, oldName: String, moduleName: String, version: String, service: TMService) async throws {
        try await apply(.init(section: StaticSection.plugins, action: "edit", name: name, oldName: oldName,
                              data: ["moduleName": .string(moduleName), "version": .string(version)]), service: service)
    }

    static func remove(_ name: String, service: TMService) async throws {
        try await apply(.init(section: StaticSection.plugins, action: "remove", name: name, oldName: name), service: service)
    }

    private static func apply(_ request: StaticSectionRequest, service: TMService) async throws {
        var request = request
        if service.agentId != nil {
            let raw = try await service.staticConfig().raw
            request.currentRaw = raw.isBlank ? nil : raw
        }
        let response = try await service.staticSection(request)
        guard response.ok, !response.raw.isBlank else {
            throw APIError.server(response.error ?? String(localized: "Could not change the plugin"))
        }
        try await service.saveStaticConfig(response.raw)
    }
}

private struct PluginCard: View {
    let plugin: PluginEntry
    let usage: Int
    let latest: String?
    let canManage: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Card(spacing: Spacing.xs) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "puzzlepiece.extension")
                    .font(.system(size: 15))
                    .foregroundStyle(Palette.blue)
                    .frame(width: 32, height: 32)
                    .background(Palette.blue.opacity(0.12), in: RoundedRectangle(cornerRadius: Radius.sm))
                VStack(alignment: .leading, spacing: 1) {
                    Text(plugin.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(plugin.displayModule).font(.caption2.monospaced()).foregroundStyle(Palette.muted).lineLimit(1)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 1) {
                    Text(plugin.displayVersion).font(.caption2.monospaced()).foregroundStyle(Palette.blue)
                    if let latest {
                        Text("\(latest) available").font(.caption2).foregroundStyle(Palette.yellow)
                    }
                }
            }
            Divider().overlay(Palette.border).padding(.top, Spacing.xs)
            HStack {
                Text(usage == 0 ? String(localized: "not referenced") : String(localized: "used by \(usage) middlewares"))
                    .font(.caption2)
                    .foregroundStyle(usage == 0 ? Palette.yellow : Palette.muted)
                Spacer()
                if canManage {
                    Button(action: onEdit) { Image(systemName: "pencil") }
                        .accessibilityLabel(String(localized: "Edit \(plugin.name)"))
                    Button(action: onDelete) { Image(systemName: "trash").foregroundStyle(Palette.red) }
                        .accessibilityLabel(String(localized: "Remove \(plugin.name)"))
                }
            }
            .buttonStyle(.borderless)
            .font(.system(size: 13))
            .foregroundStyle(Palette.muted)
        }
        .accessibilityElement(children: .contain)
    }
}

/// "Restart Traefik to activate" card, port of `PluginRestartNotice`.
struct RestartNotice: View {
    var title = String(localized: "Restart Traefik to activate")
    let detail: String
    let busy: Bool
    let onRestart: () -> Void
    var onDismiss: (() -> Void)?

    var body: some View {
        Card(accent: Palette.yellow, spacing: Spacing.xs) {
            Text(title).font(.subheadline.weight(.semibold))
            Text(detail.isBlank ? String(localized: "The static config changed. Traefik is still running the old one.") : detail)
                .font(.caption)
                .foregroundStyle(Palette.muted)
            HStack(spacing: Spacing.sm) {
                Button(action: onRestart) {
                    if busy { ProgressView().controlSize(.small) } else { Label("Restart now", systemImage: "arrow.clockwise.circle") }
                }
                .buttonStyle(.borderedProminent)
                .disabled(busy)
                if let onDismiss { Button("Later", action: onDismiss).buttonStyle(.borderless) }
            }
            .font(.footnote)
            .padding(.top, Spacing.xs)
        }
    }
}

struct PluginDetailView: View {
    let plugin: PluginEntry
    let latest: String?
    let users: [MiddlewareDef]

    @Environment(\.openURL) private var openURL
    @State private var message: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                DetailSection(title: String(localized: "Plugin"), systemImage: "puzzlepiece.extension", tint: Palette.blue) {
                    DetailRow(label: String(localized: "Name"), value: plugin.name, mono: true)
                    DetailRow(label: String(localized: "Module"), value: plugin.displayModule, mono: true)
                    DetailRow(label: String(localized: "Version"), value: plugin.displayVersion, mono: true, last: latest == nil)
                    if let latest {
                        DetailRow(label: String(localized: "Update"), value: String(localized: "\(latest) available"), mono: true, last: true)
                    }
                }

                SectionLabel(text: String(localized: "Used by middlewares"))
                Card(spacing: 0) {
                    if users.isEmpty {
                        Text("No middleware references this plugin").font(.footnote).foregroundStyle(Palette.muted)
                    } else {
                        ForEach(Array(users.enumerated()), id: \.offset) { index, middleware in
                            HStack(spacing: Spacing.sm) {
                                Image(systemName: "square.3.layers.3d").font(.system(size: 13)).foregroundStyle(Palette.purple)
                                Text(middleware.name).font(.footnote.monospaced())
                            }
                            .padding(.vertical, Spacing.sm)
                            if index < users.count - 1 { Divider().overlay(Palette.border) }
                        }
                    }
                }

                if let settings = plugin.settingsText {
                    SectionLabel(text: String(localized: "Settings"))
                    Card {
                        Text(YamlHighlighter.attributed(settings))
                            .font(.caption.monospaced())
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(Spacing.lg)
        }
        .background(Palette.bg)
        .navigationTitle(plugin.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    UIPasteboard.general.string = plugin.moduleName
                    message = String(localized: "Copied")
                } label: { Image(systemName: "doc.on.doc") }
                    .accessibilityLabel("Copy module name")
                if let url = plugin.repoURL {
                    Button { openURL(url) } label: { Image(systemName: "arrow.up.right.square") }
                        .accessibilityLabel("Open repository")
                }
            }
        }
        .toast($message)
    }
}

import SwiftUI

enum StaticEditing: Identifiable {
    case entrypoint(EntrypointForm, adding: Bool)
    case resolver(ResolverForm, adding: Bool)
    case plugin(StaticPluginForm, adding: Bool)
    case providers, api, log, observability, system

    var id: String {
        switch self {
        case let .entrypoint(form, adding): "ep:\(adding):\(form.name)"
        case let .resolver(form, adding): "res:\(adding):\(form.name)"
        case let .plugin(form, adding): "plugin:\(adding):\(form.name)"
        case .providers: "providers"
        case .api: "api"
        case .log: "log"
        case .observability: "observability"
        case .system: "system"
        }
    }
}

private struct PendingRemoval: Identifiable {
    var section: String
    var name: String
    var id: String { "\(section):\(name)" }
}

/// Traefik static config: section forms plus a raw YAML editor, save and restart. Port of `StaticConfigScreen`.
struct StaticConfigView: View {
    @Environment(AppModel.self) private var app
    @State private var model = StaticConfigModel()
    @State private var rawOpen = false
    @State private var editing: StaticEditing?
    @State private var removing: PendingRemoval?
    @State private var confirmDiscard = false

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle(rawOpen ? String(localized: "Raw YAML") : String(localized: "Static config"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button { rawOpen.toggle() } label: {
                        Image(systemName: rawOpen ? "list.bullet.rectangle" : "chevron.left.forwardslash.chevron.right")
                    }
                    .accessibilityLabel(rawOpen ? "Back to sections" : "Raw YAML editor")
                    Button {
                        if model.hasChanges { confirmDiscard = true } else { Task { await reload() } }
                    } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Reload from the server")
                    Button("Save") { Task { await save() } }
                        .disabled(model.saving || !model.hasChanges)
                }
            }
            .sheet(item: $editing) { open in
                NavigationStack { sheet(for: open) }
            }
            .alert("Remove item", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }), presenting: removing) { target in
                Button("Remove", role: .destructive) {
                    Task { await stage(target.section, "remove", target.name, "", [:]) }
                }
                Button("Cancel", role: .cancel) {}
            } message: { target in
                Text("Remove \"\(target.name)\"? It goes when you save.")
            }
            .alert("Discard changes", isPresented: $confirmDiscard) {
                Button("Discard", role: .destructive) { Task { if let service = app.service { await model.discard(service) } } }
                Button("Keep editing", role: .cancel) {}
            } message: {
                Text("Reload the file from the server and lose what you have staged?")
            }
            .task(id: app.generation) {
                model.reset()
                await reload()
            }
            .toast(Binding(get: { model.message }, set: { model.message = $0 }))
    }

    @ViewBuilder
    private var content: some View {
        if model.loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = model.loadError {
            ContentUnavailableView {
                Label("Could not read the static config", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await reload() } }.buttonStyle(.borderedProminent)
            }
        } else {
            VStack(spacing: 0) {
                stateBar
                if let error = model.saveError {
                    Text(error).font(.footnote).foregroundStyle(Palette.red).padding(.horizontal, Spacing.lg)
                }
                if rawOpen {
                    YamlEditor(text: $model.content)
                        .padding(.horizontal, Spacing.sm)
                        .padding(.top, Spacing.xs)
                } else {
                    sections
                }
            }
        }
    }

    @ViewBuilder
    private var stateBar: some View {
        if model.hasChanges {
            Card(accent: Palette.yellow, spacing: Spacing.xs) {
                Text("Unsaved changes - nothing is written until you save.").font(.footnote)
                HStack(spacing: Spacing.sm) {
                    Button("Save") { Task { await save() } }.buttonStyle(.borderedProminent).disabled(model.saving)
                    Button("Discard") { confirmDiscard = true }.buttonStyle(.borderless)
                }
                .font(.footnote)
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.xs)
        } else if model.restartPending {
            RestartNotice(
                title: String(localized: "Restart Traefik to activate"),
                detail: String(localized: "Saved. Traefik is still running the previous config."),
                busy: model.restarting,
                onRestart: { Task { if let service = app.service { await model.restart(service) } } },
                onDismiss: { model.restartPending = false }
            )
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.xs)
        }
    }

    private var sections: some View {
        List {
            Group {
                if !model.path.isEmpty {
                    Text(model.path).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
                }
                SectionLabel(text: String(localized: "Traffic in"))
                ListSectionCard(title: String(localized: "Entrypoints"), count: model.entrypoints.count, empty: String(localized: "No entrypoints configured")) {
                    editing = .entrypoint(EntrypointForm(), adding: true)
                } rows: {
                    ForEach(model.entrypoints) { item in
                        let form = EntrypointForm.read(item.name, item.entry)
                        ItemRow(title: item.name, subtitle: form.address.ifEmpty(String(localized: "no address")),
                                onEdit: { editing = .entrypoint(form, adding: false) },
                                onRemove: { removing = PendingRemoval(section: StaticSection.entrypoints, name: item.name) })
                    }
                }
                SummaryCard(title: String(localized: "Providers"), summary: providersSummary) { editing = .providers }

                SectionLabel(text: String(localized: "Certificates")).padding(.top, Spacing.sm)
                ListSectionCard(title: String(localized: "Certificate resolvers"), count: model.resolvers.count, empty: String(localized: "No certificate resolvers configured")) {
                    editing = .resolver(ResolverForm(), adding: true)
                } rows: {
                    ForEach(model.resolvers) { item in
                        let form = ResolverForm.read(item.name, item.entry)
                        ItemRow(title: item.name, subtitle: form.email.ifEmpty(String(localized: "no email set")), warn: form.email.isBlank,
                                onEdit: { editing = .resolver(form, adding: false) },
                                onRemove: { removing = PendingRemoval(section: StaticSection.resolvers, name: item.name) })
                    }
                }

                SectionLabel(text: String(localized: "Operations")).padding(.top, Spacing.sm)
                SummaryCard(title: String(localized: "API and dashboard"), summary: apiSummary, warn: model.api.insecure || !model.api.enabled) { editing = .api }
                SummaryCard(title: String(localized: "Logging"), summary: logSummary, warn: !model.log.accessLog) { editing = .log }
                SummaryCard(title: String(localized: "Observability"), summary: observabilitySummary) { editing = .observability }
                SummaryCard(title: String(localized: "System"), summary: systemSummary) { editing = .system }
                ListSectionCard(title: String(localized: "Plugins"), count: model.plugins.count, empty: String(localized: "No plugins installed")) {
                    editing = .plugin(StaticPluginForm(), adding: true)
                } rows: {
                    ForEach(model.plugins) { row in
                        ItemRow(title: row.name, subtitle: row.local ? String(localized: "local plugin") : row.module,
                                onEdit: { editing = .plugin(StaticPluginForm(name: row.name, moduleName: row.module, version: pluginVersion(row), local: row.local), adding: false) },
                                onRemove: { removing = PendingRemoval(section: StaticSection.plugins, name: row.name) })
                    }
                }
                Text("Anything these forms do not cover is in the raw YAML, top right.")
                    .font(.caption)
                    .foregroundStyle(Palette.muted)
                    .padding(.top, Spacing.xs)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .refreshable { await reload() }
    }

    private func pluginVersion(_ row: StaticPluginRow) -> String {
        model.parsed?["experimental"]?.objectValue?["plugins"]?.objectValue?[row.name]?.objectValue?["version"]?.stringValue ?? ""
    }

    private var providersSummary: String {
        let providers = model.providers
        var parts = [
            providers.docker ? "docker" : String(localized: "\("docker") off"),
            providers.file ? "file" : String(localized: "\("file") off"),
        ]
        parts += model.otherProviders
        return parts.joined(separator: " · ")
    }

    private var apiSummary: String {
        let api = model.api
        var parts = [
            api.enabled ? String(localized: "enabled") : String(localized: "disabled"),
            api.dashboard ? String(localized: "dashboard on") : String(localized: "dashboard off"),
        ]
        if api.insecure { parts.append(String(localized: "insecure on")) }
        return parts.joined(separator: " · ")
    }

    private var logSummary: String {
        let log = model.log
        return [
            log.level,
            log.logFormat.ifEmpty("text"),
            log.logFile.ifEmpty("stdout"),
            log.accessLog ? String(localized: "access log on") : String(localized: "access log off"),
        ].joined(separator: " · ")
    }

    private var observabilitySummary: String {
        let obs = model.observability
        return [
            obs.ping ? String(localized: "ping on") : String(localized: "ping off"),
            obs.prometheus ? String(localized: "metrics on") : String(localized: "metrics off"),
            obs.tracing ? String(localized: "tracing on") : String(localized: "tracing off"),
        ].joined(separator: " · ")
    }

    private var systemSummary: String {
        let system = model.system
        return [
            system.checkNewVersion ? String(localized: "version check on") : String(localized: "version check off"),
            system.sendUsage ? String(localized: "usage stats on") : String(localized: "usage stats off"),
            String(localized: "rule syntax \(system.ruleSyntax == "v2" ? "v2" : "v3")"),
        ].joined(separator: " · ")
    }

    @ViewBuilder
    private func sheet(for open: StaticEditing) -> some View {
        let save: (String, String, String, String, JSONObject) async -> String? = { section, action, name, oldName, data in
            await stage(section, action, name, oldName, data)
        }
        switch open {
        case let .entrypoint(form, adding):
            EntrypointSheet(initial: form, adding: adding) { await save(StaticSection.entrypoints, adding ? "add" : "edit", $0.name.trimmed, form.name, $0.data) }
        case let .resolver(form, adding):
            ResolverSheet(initial: form, adding: adding) { await save(StaticSection.resolvers, adding ? "add" : "edit", $0.name.trimmed, form.name, $0.data) }
        case let .plugin(form, adding):
            StaticPluginSheet(initial: form, adding: adding) { await save(StaticSection.plugins, adding ? "add" : "edit", $0.name.trimmed, form.name, $0.data) }
        case .providers:
            ProvidersSheet(initial: model.providers) { await save(StaticSection.providers, "set", "", "", $0.data) }
        case .api:
            ApiSheet(initial: model.api) { await save(StaticSection.api, "set", "", "", $0.data) }
        case .log:
            LogSheet(initial: model.log) { await save(StaticSection.log, "set", "", "", $0.data) }
        case .observability:
            ObservabilitySheet(initial: model.observability) { await save(StaticSection.observability, "set", "", "", $0.data) }
        case .system:
            SystemSheet(initial: model.system) { await save(StaticSection.system, "set", "", "", $0.data) }
        }
    }

    @discardableResult
    private func stage(_ section: String, _ action: String, _ name: String, _ oldName: String, _ data: JSONObject) async -> String? {
        guard let service = app.service else { return nil }
        let error = await model.apply(service, section: section, action: action, name: name, oldName: oldName, data: data)
        if let error, editing == nil { model.message = error }
        return error
    }

    private func reload() async {
        guard let service = app.service else { return }
        await model.load(service)
    }

    private func save() async {
        guard let service = app.service else { return }
        await model.save(service)
    }
}

private struct ListSectionCard<Rows: View>: View {
    let title: String
    let count: Int
    let empty: String
    let onAdd: () -> Void
    @ViewBuilder var rows: Rows

    var body: some View {
        Card(spacing: Spacing.xs) {
            HStack {
                Text(title).font(.subheadline.weight(.semibold))
                Text("\(count)").font(.caption.monospaced()).foregroundStyle(Palette.muted)
                Spacer()
                Button(action: onAdd) { Image(systemName: "plus") }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(String(localized: "Add to \(title)"))
            }
            if count == 0 {
                Text(empty).font(.caption).foregroundStyle(Palette.muted)
            } else {
                Divider().overlay(Palette.border)
                rows
            }
        }
    }
}

private struct ItemRow: View {
    let title: String
    let subtitle: String
    var warn = false
    let onEdit: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.footnote.monospaced().weight(.semibold))
                Text(subtitle).font(.caption2.monospaced()).foregroundStyle(warn ? Palette.yellow : Palette.muted).lineLimit(1)
            }
            Spacer()
            Button("Edit", action: onEdit).font(.footnote)
            Button(action: onRemove) { Image(systemName: "trash").foregroundStyle(Palette.red) }
                .accessibilityLabel(String(localized: "Remove \(title)"))
        }
        .buttonStyle(.borderless)
        .padding(.vertical, 2)
    }
}

private struct SummaryCard: View {
    let title: String
    let summary: String
    var warn = false
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Card(spacing: 2) {
                HStack {
                    Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.text)
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(Palette.muted)
                }
                Text(summary).font(.caption.monospaced()).foregroundStyle(warn ? Palette.yellow : Palette.muted)
            }
        }
        .buttonStyle(.plain)
    }
}

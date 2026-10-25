import SwiftUI

/// Per-app dashboard customisation. Port of `CardSettingsSheet`.
struct CardSettingsSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let item: LauncherApp
    var onSaved: (String) -> Void

    @State private var displayName = ""
    @State private var iconType = "auto"
    @State private var iconSlug = ""
    @State private var iconURL = ""
    @State private var group = ""
    @State private var url = ""
    @State private var linkDisabled = false
    @State private var hidden = false
    @State private var error: String?
    @State private var saving = false

    private var draft: RouteOverride {
        var override = RouteOverride()
        override.iconType = iconType == "auto" ? "" : iconType
        override.iconUrl = iconType == "url" ? iconURL : ""
        override.iconSlug = iconType == "slug" ? iconSlug : ""
        override.displayName = displayName
        override.hidden = hidden
        override.url = url
        override.linkDisabled = linkDisabled
        override.group = group
        return override
    }

    private var previewConfig: DashboardConfig {
        var config = app.launcher.config
        config.routeOverrides[item.id] = draft
        return config
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Display name", text: $displayName, prompt: Text("Leave blank to use route name"))
                } footer: {
                    Text("These settings change how this app appears on the dashboard. They do not touch the route itself.")
                }

                Section("Icon") {
                    Picker("Icon", selection: $iconType) {
                        Text("Auto").tag("auto")
                        Text("selfh.st slug").tag("slug")
                        Text("Custom URL").tag("url")
                    }
                    .pickerStyle(.segmented)
                    if iconType == "slug" {
                        TextField("selfh.st slug", text: $iconSlug, prompt: Text(verbatim: "jellyfin"))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    } else if iconType == "url" {
                        TextField("Custom URL", text: $iconURL, prompt: Text(verbatim: "https://…/icon.png"))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                    }
                    HStack {
                        Spacer()
                        AppPlate(item: item, size: 52, config: previewConfig)
                        Spacer()
                    }
                }

                Section {
                    Picker("Group", selection: $group) {
                        Text("Auto-detect").tag("")
                        ForEach(app.launcher.config.customGroups, id: \.name) { Text($0.name).tag($0.name) }
                        ForEach(LauncherBuilder.builtInGroups, id: \.self) { Text(LauncherBuilder.label(for: $0)).tag($0) }
                    }
                }

                Section {
                    TextField("Link URL", text: $url, prompt: Text("Leave blank to use the route's URL"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    Toggle("Do not make this card clickable", isOn: $linkDisabled)
                    Toggle("Hide from the dashboard", isOn: $hidden)
                } footer: {
                    Text("Useful when the route has a wildcard host, or the app lives on a different port.")
                }

                if let error {
                    Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
                }
            }
            .navigationTitle("Card settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else { Button("Save", action: save) }
                }
            }
        }
        .onAppear(perform: fill)
    }

    private func fill() {
        let current = app.launcher.config.routeOverrides[item.id] ?? RouteOverride()
        displayName = current.displayName
        iconType = current.iconType.ifEmpty("auto")
        iconSlug = current.iconSlug
        iconURL = current.iconUrl
        group = current.group
        url = current.url
        linkDisabled = current.linkDisabled
        hidden = current.hidden
    }

    private func save() {
        guard let service = app.service else { return }
        saving = true
        Task {
            defer { saving = false }
            do {
                try await app.launcher.saveOverride(draft, for: item.id, service: service, routes: app.routes.routes)
                onSaved(String(localized: "Card saved"))
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

/// Custom groups, hidden apps and layout. Port of `DashboardSettingsSheet`.
struct DashboardSettingsSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    var onMessage: (String) -> Void

    @State private var newGroup = ""
    @State private var error: String?

    private var store: LauncherStore { app.launcher }

    var body: some View {
        NavigationStack {
            Form {
                Section("Layout") {
                    Picker("Layout", selection: Binding(
                        get: { store.density },
                        set: { value in
                            guard let service = app.service else { return }
                            Task {
                                await store.setDensity(value, service: service)
                                onMessage(String(localized: "Layout saved"))
                            }
                        }
                    )) {
                        Label("List", systemImage: "list.bullet").tag(LauncherDensity.list)
                        Label("Icons", systemImage: "square.grid.3x3").tag(LauncherDensity.icons)
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    ForEach(store.config.customGroups, id: \.name) { group in
                        Text(group.name)
                    }
                    .onDelete { offsets in
                        for index in offsets {
                            run(String(localized: "Group removed")) { service, routes in
                                try await store.removeGroup(store.config.customGroups[index].name, service: service, routes: routes)
                            }
                        }
                    }
                    HStack {
                        TextField("New group name…", text: $newGroup)
                        Button("Add") {
                            let name = newGroup
                            newGroup = ""
                            run(String(localized: "Group added")) { service, routes in
                                try await store.addGroup(name, service: service, routes: routes)
                            }
                        }
                        .disabled(newGroup.isBlank)
                    }
                } header: {
                    Text("Custom groups \(store.config.customGroups.count)")
                } footer: {
                    Text("Assign a route to a group in its card settings. A group disappears when nothing uses it.")
                }

                Section {
                    ForEach(store.hidden) { item in
                        HStack {
                            Text(item.route.name).font(.body.monospaced())
                            Spacer()
                            Button {
                                run(String(localized: "Shown")) { service, routes in
                                    try await store.setHidden(false, routeId: item.id, service: service, routes: routes)
                                }
                            } label: {
                                Label("show", systemImage: "eye")
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                } header: {
                    Text("Hidden apps \(store.hidden.count)")
                } footer: {
                    Text("Hidden apps stay off the dashboard only. They keep running and still appear on the Routes tab.")
                }

                if let error {
                    Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
                }
            }
            .navigationTitle("Dashboard settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Close") { dismiss() } }
            }
        }
    }

    private func run(_ success: String, _ action: @escaping (TMService, [Route]) async throws -> Void) {
        guard let service = app.service else { return }
        Task {
            do {
                try await action(service, app.routes.routes)
                error = nil
                onMessage(success)
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

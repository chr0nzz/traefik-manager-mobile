import SwiftUI

/// One service: details, servers, targets, routers and actions. Port of `ServiceDetailPane`.
struct ServiceDetailView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    let key: String
    @Binding var message: String?

    @State private var editing: ServiceDraft?
    @State private var confirmDelete = false
    @State private var blocked: (message: String, routers: [String], parents: [String])?

    private var row: ServiceRow? { app.services.rows.first { $0.key == key || $0.shortName == key } }

    var body: some View {
        if let row {
            content(row)
        } else {
            ContentUnavailableView("Select a service", systemImage: "hand.tap", description: Text("Pick one from the list to see its backends."))
        }
    }

    private func content(_ row: ServiceRow) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                HStack(spacing: Spacing.sm) {
                    Text(row.proto.rawValue).font(.caption2.weight(.bold)).foregroundStyle(protoColor(row.proto))
                    Text(row.shortName).font(.title2.weight(.semibold)).lineLimit(2)
                }

                DetailSection(
                    title: String(localized: "Service details"),
                    systemImage: row.isComposite ? "point.3.filled.connected.trianglepath.dotted" : "info.circle",
                    tint: Palette.blue
                ) {
                    DetailRow(label: String(localized: "Type"), value: row.kindLabel, mono: true)
                    if row.isComposite {
                        DetailRow(
                            label: String(localized: "Managed"),
                            value: row.owned ? String(localized: "by Traefik Manager") : String(localized: "in the config file")
                        )
                    }
                    DetailRow(label: String(localized: "Provider"), value: row.provider, mono: true)
                    DetailRow(label: String(localized: "Status"), value: statusText(row.health), status: row.status, last: row.passHostHeader == nil)
                    if let pass = row.passHostHeader {
                        DetailRow(label: String(localized: "Pass host header"), value: pass ? String(localized: "Yes") : String(localized: "No"), last: true)
                    }
                }

                if !row.errors.isEmpty {
                    Card(accent: Palette.red) {
                        SectionLabel(text: String(localized: "Errors"))
                        ForEach(row.errors, id: \.self) { Text($0).font(.caption.monospaced()).foregroundStyle(Palette.red) }
                    }
                }

                if !row.servers.isEmpty || !row.isComposite {
                    DetailSection(title: String(localized: "Servers") + (row.servers.isEmpty ? "" : "  \(row.servers.count)"), systemImage: "globe", tint: Palette.green) {
                        if row.servers.isEmpty {
                            Text("No servers configured").font(.footnote).foregroundStyle(Palette.muted)
                        }
                        ForEach(Array(row.servers.enumerated()), id: \.offset) { index, server in
                            HStack(spacing: Spacing.sm) {
                                StatusDot(status: server.health == .up ? .ok : server.health == .down ? .error : .unknown)
                                Text(server.target).font(.caption.monospaced()).textSelection(.enabled)
                                Spacer()
                                Text(serverText(server.health))
                                    .font(.caption2)
                                    .foregroundStyle(server.health == .up ? Palette.green : server.health == .down ? Palette.red : Palette.muted)
                            }
                            .padding(.vertical, Spacing.sm)
                            if index < row.servers.count - 1 { Divider().overlay(Palette.border) }
                        }
                    }
                }

                if row.isComposite {
                    DetailSection(title: String(localized: "Targets") + "  \(row.composite.count)", systemImage: "arrow.triangle.branch", tint: Palette.orange) {
                        ForEach(Array(row.composite.enumerated()), id: \.offset) { index, target in
                            Text(target).font(.caption.monospaced()).padding(.vertical, Spacing.sm)
                            if index < row.composite.count - 1 { Divider().overlay(Palette.border) }
                        }
                    }
                }

                DetailSection(title: String(localized: "Used by routers"), systemImage: "server.rack", tint: Palette.purple) {
                    if row.usedBy.isEmpty {
                        Text("No router references this service").font(.footnote).foregroundStyle(Palette.muted)
                    }
                    ForEach(Array(row.usedBy.enumerated()), id: \.offset) { index, router in
                        Text(router).font(.caption.monospaced()).padding(.vertical, Spacing.sm)
                        if index < row.usedBy.count - 1 { Divider().overlay(Palette.border) }
                    }
                }
            }
            .padding(Spacing.lg)
            .frame(maxWidth: 820)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.bg)
        .navigationTitle(row.shortName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { actions(row) }
        .sheet(item: $editing) { draft in
            NavigationStack {
                ServiceEditorView(draft: draft) { message = String(localized: "Service \($0) saved") }
            }
        }
        .confirmationDialog(String(localized: "Delete \"\(row.shortName)\"?"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { delete(row, force: false) }
        }
        .alert("Still in use", isPresented: Binding(get: { blocked != nil }, set: { if !$0 { blocked = nil } })) {
            Button("Delete anyway", role: .destructive) { delete(row, force: true) }
            Button("Keep it", role: .cancel) {}
        } message: {
            if let blocked { Text(blockedText(blocked)) }
        }
    }

    @ToolbarContentBuilder
    private func actions(_ row: ServiceRow) -> some ToolbarContent {
        let canAuthor = row.provider == "file" && row.authorable
        ToolbarItem(placement: .primaryAction) {
            Menu {
                if !row.servers.isEmpty {
                    Button {
                        UIPasteboard.general.string = row.servers.map(\.target).joined(separator: "\n")
                        message = String(localized: "Copied")
                    } label: { Label("Copy backend URLs", systemImage: "doc.on.doc") }
                }
                if canAuthor && row.isComposite {
                    Button { ownership(row, adopt: !row.owned) } label: {
                        Label(row.owned ? "Stop managing" : "Manage this service", systemImage: row.owned ? "link.badge.plus" : "link")
                    }
                }
                if canAuthor && (row.owned || !row.isComposite) {
                    Button {
                        editing = ServiceDraft.from(name: row.name, service: app.services.raw[row.shortName])
                    } label: { Label("Edit service", systemImage: "pencil") }
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Delete service", systemImage: "trash") }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
            .accessibilityLabel("Actions")
            .disabled(row.servers.isEmpty && !canAuthor)
        }
    }

    // MARK: - Actions

    private func delete(_ row: ServiceRow, force: Bool) {
        guard let service = app.service else { return }
        Task {
            do {
                try await app.services.delete(row.shortName, force: force, service: service)
                message = String(localized: "Service \(row.shortName) deleted")
                dismiss()
                await app.refreshServices()
                app.routes.noteExternalChange()
            } catch let APIError.inUse(text, routers, parents) {
                blocked = (text ?? String(localized: "Could not delete the service"), routers, parents)
            } catch {
                message = error.localizedDescription
            }
        }
    }

    private func ownership(_ row: ServiceRow, adopt: Bool) {
        guard let service = app.service else { return }
        Task {
            do {
                let owned = try await app.services.setOwnership(row.shortName, adopt: adopt, service: service)
                message = owned
                    ? String(localized: "Traefik Manager now manages \(row.shortName)")
                    : String(localized: "\(row.shortName) is no longer managed here")
                await app.refreshServices()
            } catch {
                message = error.localizedDescription
            }
        }
    }

    // MARK: - Text

    private func blockedText(_ blocked: (message: String, routers: [String], parents: [String])) -> String {
        var lines = [blocked.message]
        if !blocked.routers.isEmpty {
            lines.append(String(localized: "Deleting it also deletes \(blocked.routers.count) routes: \(blocked.routers.joined(separator: ", "))."))
        }
        if !blocked.parents.isEmpty {
            lines.append(String(localized: "It is removed from \(blocked.parents.joined(separator: ", ")), and any left with no backends goes too."))
        }
        return lines.joined(separator: "\n")
    }

    private func statusText(_ health: ServiceHealth) -> String {
        switch health {
        case .ok: String(localized: "Success")
        case .warning: String(localized: "Warning")
        case .error: String(localized: "Error")
        }
    }

    private func serverText(_ health: ServerHealth) -> String {
        switch health {
        case .up: String(localized: "Up")
        case .down: String(localized: "Down")
        case .unknown: String(localized: "Unchecked")
        }
    }

    private func protoColor(_ proto: ServiceProtocol) -> Color {
        switch proto {
        case .http: Palette.blue
        case .tcp: Palette.teal
        case .udp: Palette.orange
        }
    }
}

extension ServiceDraft: Identifiable {
    var id: String { originalName.isEmpty ? "new" : originalName }
}

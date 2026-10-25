import SwiftUI

enum ServiceStatusFilter: CaseIterable, Identifiable {
    case all, success, warnings, errors

    var id: Self { self }

    var label: String {
        switch self {
        case .all: String(localized: "All")
        case .success: String(localized: "Success")
        case .warnings: String(localized: "Warnings")
        case .errors: String(localized: "Errors")
        }
    }

    func matches(_ health: ServiceHealth) -> Bool {
        switch self {
        case .all: true
        case .success: health == .ok
        case .warnings: health == .warning
        case .errors: health == .error
        }
    }
}

/// Services with health, backends and providers. Port of `ServicesScreen`.
struct ServicesView: View {
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var status: ServiceStatusFilter = .all
    @State private var proto: ServiceProtocol?
    @State private var provider: String?
    @State private var creating = DebugLaunch.form == "new-svc"
    @State private var message: String?
    @State private var debugDetail: String? = DebugLaunch.route

    private var store: ServicesStore { app.services }

    private var visible: [ServiceRow] {
        let needle = query.trimmed.lowercased()
        return store.rows.filter { row in
            (needle.isEmpty || row.name.lowercased().contains(needle))
                && status.matches(row.health)
                && (proto == nil || row.proto == proto)
                && (provider == nil || row.provider == provider)
        }
    }

    private var filtersActive: Bool { status != .all || proto != nil || provider != nil }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle("Services")
            .searchable(text: $query, prompt: Text("Search services"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    filterMenu
                    if store.loaded {
                        Button { creating = true } label: { Image(systemName: "plus") }
                            .accessibilityLabel("Add a service")
                    }
                }
            }
            .navigationDestination(for: ServiceRow.ID.self) { key in
                ServiceDetailView(key: key, message: $message)
            }
            .navigationDestination(item: $debugDetail) { key in
                ServiceDetailView(key: key, message: $message)
            }
            .sheet(isPresented: $creating) {
                NavigationStack {
                    ServiceEditorView(draft: ServiceDraft()) { message = String(localized: "Service \($0) saved") }
                }
            }
            .task(id: app.generation) { await app.refreshServices() }
            .refreshable { await app.refreshServices() }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if !store.loaded && store.loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if !store.loaded, let error = store.error {
            ContentUnavailableView {
                Label("Traefik API not reachable", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await app.refreshServices() } }.buttonStyle(.borderedProminent)
            }
        } else if !store.reachable {
            ContentUnavailableView(
                "Traefik API not reachable",
                systemImage: "exclamationmark.triangle",
                description: Text("Set TRAEFIK_API_URL and enable api: {} in the Traefik static config.")
            )
        } else if store.rows.isEmpty {
            ContentUnavailableView(
                "No services registered",
                systemImage: "server.rack",
                description: Text("Traefik answered, but it has no services yet.")
            )
        } else {
            List {
                summary.listRowBackground(Color.clear).listRowSeparator(.hidden)
                if visible.isEmpty {
                    ContentUnavailableView {
                        Label("No services match", systemImage: "line.3.horizontal.decrease.circle")
                    } description: {
                        Text("Clear the filters to see everything.")
                    } actions: {
                        Button("Clear filters", action: clearFilters)
                    }
                    .listRowBackground(Color.clear)
                }
                ForEach(visible) { row in
                    ZStack {
                        NavigationLink(value: row.id) { EmptyView() }.opacity(0)
                        ServiceCard(row: row)
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    /// Backends up across every service plus provider chips, like the Android top bar.
    private var summary: some View {
        let up = store.rows.reduce(0) { $0 + $1.backendsUp }
        let total = store.rows.reduce(0) { $0 + $1.backendsTotal }
        let providers = providerCounts
        return VStack(alignment: .leading, spacing: Spacing.sm) {
            if total > 0 {
                HStack(spacing: Spacing.sm) {
                    StatusDot(status: up == total ? .ok : up == 0 ? .error : .warn, size: 7)
                    Text(String(localized: "\(up)/\(total) active"))
                        .font(.caption.monospaced())
                        .foregroundStyle(Palette.muted)
                }
            }
            ProviderChips(providers: providers, active: provider) { name in
                provider = provider == name ? nil : name
            }
        }
        .listRowInsets(EdgeInsets(top: 0, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
    }

    private var providerCounts: [ProviderCount] {
        let grouped = Dictionary(grouping: store.rows, by: \.provider)
        var counts: [ProviderCount] = []
        for (name, rows) in grouped {
            let worst: TMStatus
            if rows.contains(where: { $0.health == .error }) {
                worst = .error
            } else if rows.contains(where: { $0.health == .warning }) {
                worst = .warn
            } else {
                worst = .ok
            }
            counts.append(ProviderCount(name: name, count: rows.count, worst: worst))
        }
        return counts.sorted { $0.count != $1.count ? $0.count > $1.count : $0.name < $1.name }
    }

    private var filterMenu: some View {
        Menu {
            Picker("Status", selection: $status) {
                ForEach(ServiceStatusFilter.allCases) { Text($0.label).tag($0) }
            }
            Picker("Protocol", selection: $proto) {
                Text("All").tag(ServiceProtocol?.none)
                ForEach(ServiceProtocol.allCases.filter { p in store.rows.contains { $0.proto == p } }) {
                    Text($0.rawValue).tag(Optional($0))
                }
            }
            if filtersActive {
                Button("Clear filters", role: .destructive, action: clearFilters)
            }
        } label: {
            Image(systemName: filtersActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
        }
        .accessibilityLabel("Filters")
    }

    private func clearFilters() {
        status = .all
        proto = nil
        provider = nil
    }
}

/// One service row. Port of `ServiceCard`.
struct ServiceCard: View {
    let row: ServiceRow

    var body: some View {
        Card(accent: row.health == .ok ? nil : row.status.color, spacing: Spacing.xs) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: row.status)
                if row.proto != .http {
                    Text(row.proto.rawValue).font(.caption2.weight(.bold)).foregroundStyle(row.proto == .udp ? Palette.orange : Palette.teal)
                }
                Text(row.shortName).font(.subheadline.weight(.semibold)).lineLimit(1)
                Spacer(minLength: 0)
                if row.owned {
                    Image(systemName: "link").font(.system(size: 11)).foregroundStyle(Palette.blue)
                }
                Text(row.kindLabel).font(.caption2.monospaced()).foregroundStyle(Palette.purple)
            }
            if row.isComposite {
                Text(row.composite.joined(separator: " · "))
                    .font(.caption.monospaced())
                    .foregroundStyle(Palette.text)
                    .lineLimit(2)
            } else if let first = row.servers.first {
                HStack(spacing: Spacing.xs) {
                    Image(systemName: "arrow.turn.down.right").font(.system(size: 11)).foregroundStyle(Palette.muted)
                    Text(first.target).font(.caption.monospaced()).foregroundStyle(Palette.green).lineLimit(1)
                    if row.servers.count > 1 {
                        Text(verbatim: "+\(row.servers.count - 1)").font(.caption2.monospaced()).foregroundStyle(Palette.muted)
                    }
                }
            }
            let meta = [row.provider] + row.metaParts + (row.usedBy.isEmpty ? [] : [String(localized: "used by \(row.usedBy.count) routes")])
            Divider().overlay(Palette.border).padding(.top, Spacing.xs)
            Text(meta.joined(separator: " · ")).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
        }
        .accessibilityElement(children: .combine)
    }
}

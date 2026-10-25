import SwiftUI

enum ProtocolFilter: String, CaseIterable, Identifiable {
    case all, http, tcp, udp

    var id: String { rawValue }

    var label: String {
        self == .all ? String(localized: "All") : rawValue.uppercased()
    }
}

enum StatusFilter: String, CaseIterable, Identifiable {
    case all, active, inactive

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: String(localized: "All")
        case .active: String(localized: "Active")
        case .inactive: String(localized: "Inactive")
        }
    }
}

/// Routes list with search and filters. Port of `RoutesScreen` / `RoutesViewModel`.
struct RoutesView: View {
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var protocolFilter: ProtocolFilter = .all
    @State private var statusFilter: StatusFilter = .all
    @State private var message: String?
    @State private var debugRoute: String? = DebugLaunch.route
    @State private var creating = DebugLaunch.form == "new"
    @State private var debugEdit: String? = DebugLaunch.form.flatMap { $0 == "new" ? nil : $0 }

    private var store: RoutesStore { app.routes }

    private var visible: [Route] {
        let needle = query.trimmingCharacters(in: .whitespaces).lowercased()
        return store.routes.filter { route in
            let matchesProtocol = protocolFilter == .all || route.protocol == protocolFilter.rawValue
            let matchesStatus = statusFilter == .all || (statusFilter == .active) == route.enabled
            let matchesQuery = needle.isEmpty || [route.name, route.rule, route.target, route.serviceName]
                .contains { $0.lowercased().contains(needle) }
            return matchesProtocol && matchesStatus && matchesQuery
        }
    }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle("Routes")
            .searchable(text: $query, prompt: Text("Search routes"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    filterMenu
                    Button { creating = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel("New route")
                }
            }
            .sheet(item: $debugEdit) { id in
                NavigationStack { RouteFormView(routeId: id) }
            }
            .sheet(isPresented: $creating) {
                NavigationStack {
                    RouteFormView(routeId: nil) { name in message = String(localized: "\(name) saved") }
                }
            }
            .navigationDestination(for: Route.ID.self) { id in
                RouteDetailView(routeId: id, message: $message)
            }
            .navigationDestination(item: $debugRoute) { id in
                RouteDetailView(routeId: id, message: $message)
            }
            .task(id: app.generation) { await app.refreshRoutes() }
            .refreshable { await app.refreshRoutes() }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if store.snapshot == nil && store.loading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if store.snapshot == nil, let error = store.error {
            ContentUnavailableView {
                Label("Could not load routes", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await app.refreshRoutes() } }.buttonStyle(.borderedProminent)
            }
        } else if visible.isEmpty {
            ContentUnavailableView {
                Label(store.routes.isEmpty ? "No routes yet" : "No routes match", systemImage: "arrow.triangle.branch")
            } description: {
                Text(store.routes.isEmpty ? "Routes you create in Traefik Manager appear here." : "Try a different search or filter.")
            }
        } else {
            List {
                if let errors = store.snapshot?.configErrors, !errors.isEmpty {
                    Text(errors.map { "\($0.file): \($0.error)" }.joined(separator: "; "))
                        .font(.footnote)
                        .foregroundStyle(Palette.red)
                        .listRowBackground(Color.clear)
                }
                ForEach(visible, id: \.listKey) { route in
                    // Hidden link behind the card so the row has no disclosure chevron.
                    ZStack {
                        NavigationLink(value: route.id) { EmptyView() }.opacity(0)
                        RouteCard(route: route, health: store.health[route.id])
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
                    .swipeActions(edge: .leading) {
                        if route.isFileRoute {
                            Button(route.enabled ? "Disable" : "Enable") { toggle(route) }
                                .tint(route.enabled ? Palette.muted : Palette.green)
                        }
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    private var filterMenu: some View {
        Menu {
            Picker("Protocol", selection: $protocolFilter) {
                ForEach(ProtocolFilter.allCases) { Text($0.label).tag($0) }
            }
            Picker("Status", selection: $statusFilter) {
                ForEach(StatusFilter.allCases) { Text($0.label).tag($0) }
            }
            if protocolFilter != .all || statusFilter != .all {
                Button("Clear filters", role: .destructive) {
                    protocolFilter = .all
                    statusFilter = .all
                }
            }
        } label: {
            Image(systemName: protocolFilter == .all && statusFilter == .all
                ? "line.3.horizontal.decrease.circle"
                : "line.3.horizontal.decrease.circle.fill")
        }
        .accessibilityLabel("Filters")
    }

    private func toggle(_ route: Route) {
        guard let service = app.service else { return }
        Task {
            do {
                try await store.toggle(route, service: service)
                message = route.enabled
                    ? String(localized: "\(route.name) disabled")
                    : String(localized: "\(route.name) enabled")
                await app.refreshRoutes()
            } catch {
                message = error.localizedDescription
            }
        }
    }
}

private extension Route {
    /// Two providers can expose a route with the same id, so the list key includes the protocol.
    var listKey: String { "\(self.protocol):\(id):\(provider)" }
}

/// One route row. Port of `RouteCard`.
struct RouteCard: View {
    let route: Route
    var health: RouteHealth?

    var body: some View {
        let status = route.status(health: health)
        Card(accent: status == .ok ? nil : status.color, spacing: Spacing.xs) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: status)
                if route.protocol != "http" {
                    Text(route.protocol.uppercased())
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(route.protocol == "udp" ? Palette.orange : Palette.teal)
                }
                Text(route.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Spacer(minLength: 0)
                if route.insecureSkipVerify {
                    glyph("arrow.up.right", Palette.orange)
                }
                if route.protocol != "udp" {
                    route.tlsEnabled ? glyph("lock", Palette.muted) : glyph("lock.open", Palette.yellow)
                }
            }

            let hosts = route.hosts
            if let first = hosts.first {
                valueRow("globe", first, Palette.blue, extra: hosts.count > 1 ? "+\(hosts.count - 1)" : nil)
            } else if !route.rule.isEmpty {
                valueRow("globe", route.rule, Palette.muted)
            }
            valueRow(
                "arrow.turn.down.right",
                route.target,
                route.target == "N/A" ? Palette.red : Palette.green,
                extra: route.backendCount > 1 ? "+\(route.backendCount - 1)" : nil
            )

            let meta = route.entryPointNames + route.middlewareNames + (route.serviceName.isEmpty ? [] : [route.serviceName])
            if !meta.isEmpty {
                Divider().overlay(Palette.border).padding(.top, Spacing.xs)
                Text(meta.joined(separator: " · "))
                    .font(.caption2)
                    .foregroundStyle(Palette.muted)
                    .lineLimit(1)
            }
        }
        .opacity(route.enabled ? 1 : 0.5)
        .accessibilityElement(children: .combine)
        .accessibilityValue(status.label)
    }

    private func glyph(_ name: String, _ tint: Color) -> some View {
        Image(systemName: name).font(.system(size: 12)).foregroundStyle(tint)
    }

    private func valueRow(_ icon: String, _ value: String, _ color: Color, extra: String? = nil) -> some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: icon).font(.system(size: 11)).foregroundStyle(Palette.muted)
            Text(value).font(.caption.monospaced()).foregroundStyle(color).lineLimit(1)
            if let extra {
                Text(extra).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
            }
        }
    }
}

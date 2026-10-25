import SwiftUI

/// Read-only routes and middlewares from one Traefik provider. Port of `ProviderScreen`.
struct ProviderView: View {
    @Environment(AppModel.self) private var app
    let page: Destination

    @State private var routes: [ProviderRoute] = []
    @State private var middlewares: [ProviderMiddleware] = []
    @State private var reachable = true
    @State private var loading = true
    @State private var error: String?
    @State private var query = ""
    @State private var protocolFilter: ProtocolFilter = .all
    @State private var statusFilter: StatusFilter = .all
    @State private var selected: ProviderRoute?
    @State private var serviceEnvelope: ServiceEnvelope?
    @State private var message: String?

    private var visible: [ProviderRoute] {
        let needle = query.trimmed.lowercased()
        return routes.filter { route in
            (protocolFilter == .all || route.proto.rawValue.lowercased() == protocolFilter.rawValue)
                && (statusFilter == .all || (statusFilter == .active) == route.serving)
                && (needle.isEmpty || route.name.lowercased().contains(needle) || route.rule.lowercased().contains(needle))
        }
    }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle(page.providerLabel)
            .searchable(text: $query, prompt: Text("Search \(page.providerLabel) routes"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Menu {
                        Picker("Protocol", selection: $protocolFilter) {
                            ForEach(ProtocolFilter.allCases) { Text($0.label).tag($0) }
                        }
                        Picker("Status", selection: $statusFilter) {
                            ForEach(StatusFilter.allCases) { Text($0.label).tag($0) }
                        }
                    } label: {
                        Image(systemName: protocolFilter == .all && statusFilter == .all
                            ? "line.3.horizontal.decrease.circle" : "line.3.horizontal.decrease.circle.fill")
                    }
                    .accessibilityLabel("Filters")
                    Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh \(page.providerLabel) routes")
                }
            }
            .navigationDestination(item: $selected) { route in
                RouteDetailView(routeId: route.id, message: $message, fixedRoute: route.asRoute(services: serviceEnvelope))
            }
            .toast($message)
            .task(id: "\(page.rawValue)-\(app.generation)") {
                routes = []
                loading = true
                await load()
            }
            .refreshable { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if loading && routes.isEmpty {
            ProgressView(String(localized: "Loading \(page.providerLabel) routes")).frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error, routes.isEmpty {
            ContentUnavailableView {
                Label("Traefik API not reachable", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await load() } }.buttonStyle(.borderedProminent)
            }
        } else if !reachable {
            ContentUnavailableView("Traefik API not reachable", systemImage: "exclamationmark.triangle", description: Text("Check the Traefik API URL in Settings."))
        } else if routes.isEmpty && middlewares.isEmpty {
            ContentUnavailableView(String(localized: "No \(page.providerLabel) routes found"), systemImage: page.systemImage, description: Text(page.providerEmpty))
        } else {
            List {
                Group {
                    verdict
                    if visible.isEmpty && !routes.isEmpty {
                        Text("No routes match").font(.footnote).foregroundStyle(Palette.muted)
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))

                ForEach(visible) { route in
                    Button { selected = route } label: { ProviderRouteCard(route: route) }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
                }

                if !middlewares.isEmpty {
                    Section {
                        ForEach(middlewares) { middleware in
                            HStack(spacing: Spacing.sm) {
                                StatusDot(status: middleware.status.isEmpty || middleware.status == "enabled" ? .ok : .warn, size: 6)
                                Text(middleware.name).font(.caption.monospaced())
                                Spacer()
                                Text(middleware.type).font(.caption2.monospaced()).foregroundStyle(Palette.purple)
                            }
                            .listRowBackground(Color.clear)
                        }
                    } header: {
                        SectionLabel(text: String(localized: "Middlewares \(middlewares.count)"))
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    private var verdict: some View {
        let notServing = routes.filter { !$0.serving }.count
        var detail: [String] = []
        for proto in ServiceProtocol.allCases {
            let count = routes.filter { $0.proto == proto }.count
            if count > 0 { detail.append("\(count) \(proto.rawValue)") }
        }
        if !middlewares.isEmpty { detail.append(String(localized: "\(middlewares.count) middlewares")) }
        let status: TMStatus = notServing > 0 ? .warn : .ok
        return Card(accent: status.color, spacing: 2) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: status)
                Text(notServing > 0 ? String(localized: "\(notServing) routes not serving") : String(localized: "\(routes.count) routes live"))
                    .font(.subheadline.weight(.semibold))
            }
            Text(detail.isEmpty ? String(localized: "No routes") : detail.joined(separator: " · "))
                .font(.footnote)
                .foregroundStyle(Palette.muted)
            Text(page.providerNote)
                .font(.caption)
                .foregroundStyle(Palette.muted)
                .padding(.top, Spacing.xs)
        }
    }

    private func load() async {
        guard let service = app.service else { return }
        error = nil
        do {
            async let routers = service.routers()
            async let services = try? service.serviceEnvelope()
            async let middlewareList = try? service.middlewares()
            var managed = Set<String>()
            if page == .fileExternal {
                managed = Set(((try? await RoutesStore.fetch(service: service))?.routes ?? []).map { RouteDetailView.shortName($0.name) })
            }
            let fetched = try await routers
            reachable = fetched.reachable && !fetched.all.isEmpty
            let loadedServices = await services
            serviceEnvelope = loadedServices
            routes = ProviderRows.routes(page: page, routers: fetched, services: loadedServices, managed: managed)
            if let wanted = DebugLaunch.route, selected == nil { selected = routes.first { $0.shortName == wanted || $0.name == wanted } }
            middlewares = ProviderRows.middlewares(page: page, envelope: await middlewareList)
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private struct ProviderRouteCard: View {
    let route: ProviderRoute

    var body: some View {
        let status: TMStatus = route.serving ? .ok : .disabled
        Card(accent: route.serving ? nil : Palette.muted, spacing: Spacing.xs) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: status)
                if route.proto != .http {
                    Text(route.proto.rawValue).font(.caption2.weight(.bold)).foregroundStyle(route.proto == .udp ? Palette.orange : Palette.teal)
                }
                Text(route.shortName).font(.subheadline.weight(.semibold)).lineLimit(1)
                Spacer(minLength: 0)
                if route.proto != .udp {
                    Image(systemName: route.tls ? "lock" : "lock.open")
                        .font(.system(size: 12))
                        .foregroundStyle(route.tls ? Palette.muted : Palette.yellow)
                }
            }
            if let host = route.plainHost {
                row("globe", host, Palette.blue, extra: route.hosts.count > 1 ? "+\(route.hosts.count - 1)" : nil)
            } else if !route.rule.isEmpty {
                row("globe", route.rule, Palette.muted)
            }
            if let target = route.target {
                row("arrow.turn.down.right", target, Palette.green)
            }
            let meta = route.entryPoints + route.middlewares.map(RouteDetailView.shortName) + (route.service.isEmpty ? [] : [RouteDetailView.shortName(route.service)])
            if !meta.isEmpty {
                Divider().overlay(Palette.border).padding(.top, Spacing.xs)
                Text(meta.joined(separator: " · ")).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
            }
        }
        .opacity(route.serving ? 1 : 0.55)
        .accessibilityElement(children: .combine)
    }

    private func row(_ icon: String, _ value: String, _ color: Color, extra: String? = nil) -> some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: icon).font(.system(size: 11)).foregroundStyle(Palette.muted)
            Text(value).font(.caption.monospaced()).foregroundStyle(color).lineLimit(1)
            if let extra { Text(extra).font(.caption2.monospaced()).foregroundStyle(Palette.muted) }
        }
    }
}


extension ProviderRoute {
    /// The router as a read-only `Route`, so it opens in the same detail screen as file routes.
    func asRoute(services: ServiceEnvelope?) -> Route {
        var route = Route(id: id, name: name)
        route.protocol = proto.rawValue.lowercased()
        route.rule = rule
        route.serviceName = service
        route.target = target ?? ""
        route.entryPoints = .array(entryPoints.map(JSONValue.string))
        route.middlewares = .array(middlewares.map(JSONValue.string))
        route.tls = .bool(tls)
        route.enabled = serving
        route.provider = provider
        let pool: [TraefikService] = switch proto {
        case .tcp: services?.tcp ?? []
        case .udp: services?.udp ?? []
        default: services?.http ?? []
        }
        if let match = pool.first(where: { $0.name == service || RouteDetailView.shortName($0.name) == RouteDetailView.shortName(service) }) {
            let listed = (match.loadBalancer?.servers ?? []).compactMap { $0.url ?? $0.address }.filter { !$0.isEmpty }
            route.servers = listed.isEmpty ? (match.serverStatus ?? [:]).keys.sorted() : listed
            route.passHostHeader = match.loadBalancer?.passHostHeader
            if let type = match.type, !type.isEmpty, type.lowercased() != "loadbalancer" { route.serviceType = type }
        }
        return route
    }
}

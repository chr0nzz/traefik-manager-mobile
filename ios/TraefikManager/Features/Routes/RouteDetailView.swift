import SwiftUI

struct PingState: Equatable {
    var running = false
    var ok: Bool?
    var degraded = false
    var detail: String?
}

/// Everything about one route plus its actions. Port of `RouteDetailPane`.
struct RouteDetailView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    let routeId: String
    @Binding var message: String?

    @State private var ping: PingState?
    @State private var confirmDelete = false
    @State private var showRaw = false
    @State private var editing = false
    @State private var working = false

    private var route: Route? { app.routes.routes.first { $0.id == routeId } }

    var body: some View {
        if let route {
            content(route)
        } else {
            ContentUnavailableView(
                "Select a route",
                systemImage: "hand.tap",
                description: Text("Pick a route from the list to see its configuration.")
            )
        }
    }

    private func content(_ route: Route) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                header(route)
                TrafficFlow(route: route)
                if route.protocol == "http" && !route.hosts.isEmpty {
                    reachability(route)
                }
                if let health = app.routes.health[route.id], health.known || health.note != nil {
                    healthSection(health)
                }
                detailsSection(route)
                if route.protocol != "udp" { tlsSection(route) }
                middlewaresSection(route)
                serviceSection(route)
            }
            .padding(Spacing.lg)
            .frame(maxWidth: 820)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.bg)
        .navigationTitle(route.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { actions(route) }
        .task { if !app.services.loaded { await app.refreshServices() } }
        .sheet(isPresented: $showRaw) {
            NavigationStack {
                RouteRawView(routeId: route.id) { saved in
                    showRaw = false
                    if saved {
                        message = String(localized: "Saved")
                        Task { await app.refreshRoutes() }
                    }
                }
            }
        }
        .sheet(isPresented: $editing) {
            NavigationStack {
                RouteFormView(routeId: route.id) { name in message = String(localized: "\(name) saved") }
            }
        }
        .confirmationDialog(
            String(localized: "Delete \(route.name)?"),
            isPresented: $confirmDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) { delete(route) }
        } message: {
            Text("This removes the router and its service from the config file.")
        }
    }

    // MARK: - Sections

    private func header(_ route: Route) -> some View {
        HStack(spacing: Spacing.sm) {
            Text(route.protocol.uppercased())
                .font(.caption2.weight(.bold))
                .foregroundStyle(protoColor(route.protocol))
            Text(route.name)
                .font(.title2.weight(.semibold))
                .lineLimit(2)
            Spacer()
            if route.isFileRoute {
                Toggle(isOn: Binding(get: { route.enabled }, set: { _ in toggle(route) })) {
                    Text(route.enabled ? "Disable \(route.name)" : "Enable \(route.name)")
                }
                .labelsHidden()
                .disabled(working)
            }
        }
    }

    private func reachability(_ route: Route) -> some View {
        Card {
            HStack(spacing: Spacing.sm) {
                SectionLabel(text: String(localized: "Reachability"))
                Spacer()
                if let ping {
                    if ping.running {
                        Text("Checking").font(.caption).foregroundStyle(Palette.muted)
                    } else if ping.degraded {
                        HealthLabel(status: .warn, text: ping.detail ?? String(localized: "degraded"))
                    } else if ping.ok == true {
                        HealthLabel(status: .ok, text: ping.detail ?? String(localized: "reachable"))
                    } else {
                        HealthLabel(status: .error, text: ping.detail ?? String(localized: "unreachable"))
                    }
                    if !ping.running {
                        Button { runPing(route) } label: { Image(systemName: "arrow.clockwise") }
                            .accessibilityLabel("Ping again")
                    }
                } else {
                    Button("Ping") { runPing(route) }
                }
            }
        }
    }

    private func healthSection(_ health: RouteHealth) -> some View {
        DetailSection(title: String(localized: "Reachability"), systemImage: "waveform.path.ecg", tint: Palette.teal) {
            DetailRow(label: String(localized: "Checked"), value: health.summary, status: health.tmStatus)
            if !health.downServers.isEmpty {
                DetailRow(label: String(localized: "Not answering"), value: health.downServers.joined(separator: ", "), mono: true)
            }
            if health.state == RouteHealth.down, let error = health.error, !error.isEmpty {
                DetailRow(label: String(localized: "Error"), value: error)
            }
            if health.unverified {
                DetailRow(
                    label: String(localized: "Note"),
                    value: String(localized: "Behind forward auth, so the check could not confirm the app itself")
                )
            }
            DetailRow(label: String(localized: "Source"), value: healthSource(health.source), last: true)
        }
    }

    private func detailsSection(_ route: Route) -> some View {
        let none = String(localized: "none")
        return DetailSection(title: String(localized: "Router Details"), systemImage: "info.circle", tint: Palette.blue) {
            DetailRow(
                label: String(localized: "Status"),
                value: route.enabled ? String(localized: "Enabled") : String(localized: "Disabled"),
                status: route.enabled ? .ok : .disabled
            )
            DetailRow(label: String(localized: "Provider"), value: route.provider)
            if !route.rule.isEmpty {
                DetailRow(label: String(localized: "Rule"), value: route.rule, mono: true)
            }
            DetailRow(label: String(localized: "Name"), value: route.name, mono: true)
            DetailRow(label: String(localized: "Entry points"), value: route.entryPointNames.joined(separator: ", ").ifEmpty(none))
            DetailRow(
                label: String(localized: "Service"),
                value: route.serviceName.ifEmpty(none),
                mono: true,
                last: route.priority == nil && route.configFile.isEmpty
            )
            if let priority = route.priority {
                DetailRow(label: String(localized: "Priority"), value: String(priority), last: route.configFile.isEmpty)
            }
            if !route.configFile.isEmpty {
                DetailRow(label: String(localized: "Config file"), value: route.configFile, mono: true, last: true)
            }
        }
    }

    private func tlsSection(_ route: Route) -> some View {
        DetailSection(title: "TLS", systemImage: "lock.shield", tint: Palette.green) {
            DetailRow(label: "TLS", value: route.tlsEnabled ? String(localized: "Enabled") : String(localized: "Disabled"))
            DetailRow(label: String(localized: "Certificate resolver"), value: route.certResolver.ifEmpty(String(localized: "none")))
            DetailRow(label: String(localized: "Options"), value: route.tlsOptionsProfile.ifEmpty(String(localized: "default")))
            DetailRow(
                label: String(localized: "Skip verify"),
                value: route.insecureSkipVerify ? String(localized: "Yes") : String(localized: "No"),
                last: true
            )
        }
    }

    private func middlewaresSection(_ route: Route) -> some View {
        let names = route.middlewareNames + route.entrypointMiddlewares.map {
            String(localized: "\(Self.shortName($0)) (entry point)")
        }
        return DetailSection(title: String(localized: "Middlewares"), systemImage: "puzzlepiece.extension", tint: Palette.purple) {
            if names.isEmpty {
                Text("No middlewares configured")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.lg)
            } else {
                ForEach(Array(names.enumerated()), id: \.offset) { index, name in
                    DetailRow(label: "", value: name, mono: true, last: index == names.count - 1)
                }
            }
        }
    }

    private func serviceSection(_ route: Route) -> some View {
        let servers = route.servers.isEmpty ? [route.target] : route.servers
        let serviceRow = app.services.byBackendKey[BackendHealth.key(route.protocol, route.serviceName)]
        return DetailSection(title: String(localized: "Service"), systemImage: "bolt", tint: Palette.yellow) {
            DetailRow(
                label: String(localized: "Type"),
                value: route.serviceType == "loadBalancer" ? String(localized: "Load Balancer") : route.serviceType
            )
            if let summary = BackendHealth.summary(serviceRow), let serviceRow {
                DetailRow(
                    label: String(localized: "Health"),
                    value: summary,
                    status: serviceRow.backendsUp == serviceRow.backendsTotal ? .ok : serviceRow.backendsUp == 0 ? .error : .warn
                )
            }
            if let passHostHeader = route.passHostHeader {
                DetailRow(label: String(localized: "Pass host header"), value: String(passHostHeader))
            }
            ForEach(Array(servers.enumerated()), id: \.offset) { index, server in
                let health = serviceRow?.servers.first { $0.target == server }?.health
                DetailRow(
                    label: servers.count == 1 ? String(localized: "Server") : String(localized: "Server \(index + 1)"),
                    value: server,
                    mono: true,
                    status: health == .up ? .ok : health == .down ? .error : nil,
                    last: index == servers.count - 1
                )
            }
        }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private func actions(_ route: Route) -> some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Menu {
                if let host = route.hosts.first, let url = URL(string: "https://\(host)") {
                    Button { openURL(url) } label: { Label("Open \(host)", systemImage: "arrow.up.right.square") }
                }
                Button {
                    UIPasteboard.general.string = route.target.isEmpty ? route.name : route.target
                    message = String(localized: "Copied")
                } label: {
                    Label("Copy target", systemImage: "doc.on.doc")
                }
                if route.isFileRoute {
                    Divider()
                    Button { editing = true } label: { Label("Edit route", systemImage: "pencil") }
                    Button { showRaw = true } label: { Label("Edit raw YAML", systemImage: "chevron.left.forwardslash.chevron.right") }
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Delete route", systemImage: "trash") }
                }
            } label: {
                Image(systemName: "ellipsis.circle")
            }
            .accessibilityLabel("Actions")
        }
    }

    // MARK: - Actions

    private func toggle(_ route: Route) {
        guard let service = app.service else { return }
        working = true
        Task {
            defer { working = false }
            do {
                try await app.routes.toggle(route, service: service)
                message = route.enabled
                    ? String(localized: "\(route.name) disabled")
                    : String(localized: "\(route.name) enabled")
                await app.refreshRoutes()
            } catch {
                message = error.localizedDescription
            }
        }
    }

    private func delete(_ route: Route) {
        guard let service = app.service else { return }
        Task {
            do {
                try await app.routes.delete(route, service: service)
                message = String(localized: "\(route.name) deleted")
                dismiss()
                await app.refreshRoutes()
            } catch {
                message = error.localizedDescription
            }
        }
    }

    private func runPing(_ route: Route) {
        guard let service = app.service, let host = route.hosts.first else { return }
        ping = PingState(running: true)
        let target = route.target
        let fallback = !target.isEmpty && target != "N/A" && !target.hasSuffix("@internal") ? target : nil
        Task {
            do {
                let result = try await service.ping(url: "https://\(host)", fallback: fallback, servers: route.servers)
                ping = PingState(running: false, ok: result.ok, degraded: result.degraded, detail: Self.describe(result))
            } catch {
                ping = PingState(running: false, ok: false, detail: error.localizedDescription)
            }
        }
    }

    static func describe(_ ping: PingResult) -> String? {
        if ping.isSelf { return String(localized: "this server") }
        if ping.degraded {
            if let tally = ping.servers { return String(localized: "\(tally.up) of \(tally.total) servers up") }
            return String(localized: "some servers down")
        }
        if ping.ok {
            let parts = [ping.statusCode.map { "HTTP \($0)" }, ping.latencyMs.map { "\($0)ms" }].compactMap { $0 }
            return parts.isEmpty ? nil : parts.joined(separator: " · ")
        }
        return ping.error ?? String(localized: "No response")
    }

    // MARK: - Helpers

    private func healthSource(_ source: String) -> String {
        switch source {
        case "traefik": String(localized: "Traefik health check")
        case "servers": String(localized: "Each backend server")
        case "ping": String(localized: "Request to the route")
        case "self": String(localized: "This app")
        default: source.isEmpty ? String(localized: "unknown") : source
        }
    }

    private func protoColor(_ proto: String) -> Color {
        switch proto {
        case "udp": Palette.orange
        case "tcp": Palette.teal
        default: Palette.blue
        }
    }

    static func shortName(_ name: String) -> String {
        name.split(separator: "@", maxSplits: 1).first.map(String.init) ?? name
    }
}

/// Entry point → router → service diagram at the top of the detail screen.
private struct TrafficFlow: View {
    let route: Route

    var body: some View {
        let none = String(localized: "none")
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SectionLabel(text: String(localized: "Traffic flow"))
            HStack(spacing: 2) {
                FlowBox(
                    label: String(localized: "Entry point"),
                    title: (route.entryPointNames.first ?? none).uppercased(),
                    lines: [
                        route.entryPointNames.count > 1 ? String(localized: "+\(route.entryPointNames.count - 1) more") : nil,
                        route.tlsEnabled ? "TLS" : nil,
                    ].compactMap { $0 },
                    lineColor: Palette.green
                )
                arrow
                FlowBox(
                    label: String(localized: "Router"),
                    title: route.name,
                    lines: [
                        route.tlsEnabled ? "TLS" : nil,
                        route.enabled ? String(localized: "Enabled") : String(localized: "Disabled"),
                    ].compactMap { $0 },
                    lineColor: route.enabled ? Palette.green : Palette.muted,
                    highlighted: true
                )
                arrow
                FlowBox(
                    label: String(localized: "Service"),
                    title: route.serviceName.ifEmpty(none),
                    mono: !route.target.isEmpty && route.target != "N/A" ? route.target : nil
                )
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    private var arrow: some View {
        Image(systemName: "arrow.right")
            .font(.system(size: 11))
            .foregroundStyle(Palette.muted)
    }
}

private struct FlowBox: View {
    var label: String
    var title: String
    var lines: [String] = []
    var lineColor: Color?
    var mono: String?
    var highlighted = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.sm)
        VStack(spacing: 2) {
            Text(label.uppercased())
                .font(.caption2.monospaced())
                .foregroundStyle(highlighted ? Palette.blue : Palette.muted)
                .lineLimit(1)
            Text(title)
                .font(.subheadline)
                .lineLimit(1)
                .padding(.top, 1)
            ForEach(lines, id: \.self) { line in
                Text(line).font(.caption2).foregroundStyle(lineColor ?? Palette.muted).lineLimit(1)
            }
            if let mono {
                Text(mono)
                    .font(.caption2.monospaced())
                    .foregroundStyle(Palette.green)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 2)
                    .background(Palette.card, in: RoundedRectangle(cornerRadius: 4))
                    .padding(.top, 1)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, Spacing.sm)
        .padding(.horizontal, Spacing.xs)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(highlighted ? Palette.blue.opacity(0.08) : Palette.card, in: shape)
        .overlay(shape.strokeBorder(highlighted ? Palette.blue : Palette.border, lineWidth: 1))
    }
}

extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}

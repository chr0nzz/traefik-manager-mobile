import SwiftUI

/// Entry point → router → middleware → service graph with pinch, pan and tap-to-focus. Port of `RouteMapScreen`.
struct RouteMapView: View {
    @Environment(AppModel.self) private var app
    @State private var proto: String?
    @State private var provider: String?
    @State private var entryPoint: String?
    @State private var query = ""
    @State private var focus: MapNode?
    @State private var editing: String?
    @State private var message: String?

    private var routes: [Route] { app.routes.routes }
    private var filtered: Bool { proto != nil || provider != nil || entryPoint != nil || !query.isEmpty }

    var body: some View {
        let graph = RouteMapBuilder.build(routes: routes, protocol: proto, provider: provider, entryPoint: entryPoint, query: query.trimmed)
        content(graph)
            .background(Palette.bg)
            .navigationTitle("Route map")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query)
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    filterMenu
                    Button { Task { await app.refreshRoutes() } } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh the map")
                }
            }
            .onChange(of: [proto, provider, entryPoint, query].map { $0 ?? "" }) { focus = nil }
            .sheet(item: $focus) { node in
                NodeSheet(node: node, graph: graph) { routeId in
                    focus = nil
                    editing = routeId
                }
                .presentationDetents([.medium, .large])
            }
            .sheet(item: $editing) { id in
                NavigationStack {
                    RouteFormView(routeId: id) { message = String(localized: "\($0) saved") }
                }
            }
            .task(id: app.generation) { await app.refreshRoutes() }
            .toast($message)
    }

    @ViewBuilder
    private func content(_ graph: RouteMapGraph) -> some View {
        if app.routes.snapshot == nil && app.routes.loading {
            ProgressView("Reading the routes").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if app.routes.snapshot == nil, let error = app.routes.error {
            ContentUnavailableView {
                Label("Could not read the routes", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await app.refreshRoutes() } }.buttonStyle(.borderedProminent)
            }
        } else if graph.nodes.isEmpty {
            ContentUnavailableView {
                Label(filtered ? "Nothing matches" : "No routes yet", systemImage: "point.3.connected.trianglepath.dotted")
            } description: {
                Text(filtered ? "Try a different filter." : "Routes you create appear here as a map.")
            } actions: {
                if filtered { Button("Clear filters", action: clearFilters) }
            }
        } else {
            RouteMapCanvas(graph: graph, focusIds: focus.map { graph.connected($0.id) } ?? []) { node in
                focus = node?.id == focus?.id ? nil : node
            }
            .ignoresSafeArea(edges: .bottom)
        }
    }

    private var filterMenu: some View {
        let protocols = Array(Set(routes.map(\.protocol))).sorted()
        let providers = Array(Set(routes.map(\.provider).filter { !$0.isEmpty })).sorted()
        let entryPoints = Array(Set(routes.flatMap(\.entryPointNames))).sorted()
        return Menu {
            Picker("Protocol", selection: $proto) {
                Text("All").tag(String?.none)
                ForEach(protocols, id: \.self) { Text($0.uppercased()).tag(Optional($0)) }
            }
            Picker("Provider", selection: $provider) {
                Text("All").tag(String?.none)
                ForEach(providers, id: \.self) { Text($0).tag(Optional($0)) }
            }
            Picker("Entry point", selection: $entryPoint) {
                Text("All").tag(String?.none)
                ForEach(entryPoints, id: \.self) { Text($0).tag(Optional($0)) }
            }
            if filtered { Button("Clear filters", role: .destructive, action: clearFilters) }
        } label: {
            Image(systemName: filtered ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
        }
        .accessibilityLabel("Filters")
    }

    private func clearFilters() {
        proto = nil
        provider = nil
        entryPoint = nil
        query = ""
    }
}

/// Draws the graph and handles pinch, pan and taps. Port of `RouteMapCanvas`.
struct RouteMapCanvas: View {
    let graph: RouteMapGraph
    let focusIds: Set<String>
    let onTap: (MapNode?) -> Void

    @State private var zoom: CGFloat = 1
    @State private var offset: CGSize = .zero
    @GestureState private var pinch: CGFloat = 1
    @GestureState private var drag: CGSize = .zero

    var body: some View {
        GeometryReader { proxy in
            let fit = fitted(proxy.size)
            let scale = fit * zoom * pinch
            let pan = CGSize(width: offset.width + drag.width, height: offset.height + drag.height)
            let origin = CGPoint(
                x: max((proxy.size.width - graph.width * scale) / 2, 0) + pan.width,
                y: max((proxy.size.height - graph.height * scale) / 2, 0) + pan.height
            )
            Canvas { context, _ in
                context.translateBy(x: origin.x, y: origin.y)
                context.scaleBy(x: scale, y: scale)
                draw(into: &context)
            }
            .contentShape(Rectangle())
            .gesture(
                SimultaneousGesture(
                    MagnifyGesture()
                        .updating($pinch) { value, state, _ in state = value.magnification }
                        .onEnded { value in zoom = min(max(zoom * value.magnification, 0.4), 6) },
                    DragGesture(minimumDistance: 4)
                        .updating($drag) { value, state, _ in state = value.translation }
                        .onEnded { value in
                            offset.width += value.translation.width
                            offset.height += value.translation.height
                        }
                )
            )
            .simultaneousGesture(
                SpatialTapGesture().onEnded { value in
                    let world = CGPoint(x: (value.location.x - origin.x) / scale, y: (value.location.y - origin.y) / scale)
                    onTap(graph.nodes.last { $0.frame.contains(world) })
                }
            )
            .onTapGesture(count: 2) {
                withAnimation {
                    zoom = 1
                    offset = .zero
                }
            }
            .onChange(of: graph.nodes.count) {
                zoom = 1
                offset = .zero
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(String(localized: "Route map"))
            .accessibilityValue(graph.nodes.map(\.label).joined(separator: ", "))
        }
    }

    private func fitted(_ size: CGSize) -> CGFloat {
        guard graph.width > 0, size.width > 0 else { return 1 }
        return min(max(min(size.width / graph.width, size.height / max(graph.height, 1)), 0.3), 2.5)
    }

    private func accent(_ node: MapNode) -> Color {
        switch node.kind {
        case .entryPoint: Palette.teal
        case .route:
            switch node.route?.protocol.lowercased() {
            case "tcp": Palette.green
            case "udp": Palette.yellow
            default: Palette.blue
            }
        case .group: Palette.muted
        case .middleware: Palette.purple
        case .service: Palette.orange
        }
    }

    private func draw(into context: inout GraphicsContext) {
        let byId = graph.byId
        for edge in graph.edges {
            guard let from = byId[edge.from], let to = byId[edge.to] else { continue }
            let lit = focusIds.isEmpty || (focusIds.contains(edge.from) && focusIds.contains(edge.to))
            let colour: Color = to.kind == .middleware ? Palette.purple : to.kind == .service ? Palette.orange : Palette.teal
            let start = CGPoint(x: from.x + from.width, y: from.centreY)
            let end = CGPoint(x: to.x, y: to.centreY)
            let control = (end.x - start.x) / 2
            var path = Path()
            path.move(to: start)
            path.addCurve(to: end, control1: CGPoint(x: start.x + control, y: start.y), control2: CGPoint(x: end.x - control, y: end.y))
            context.stroke(
                path,
                with: .color(lit ? colour.opacity(to.kind == .service ? 0.5 : 0.6) : Palette.border.opacity(0.2)),
                lineWidth: 1.5
            )
        }
        for node in graph.nodes {
            drawNode(node, lit: focusIds.isEmpty || focusIds.contains(node.id), into: &context)
        }
    }

    private func drawNode(_ node: MapNode, lit: Bool, into context: inout GraphicsContext) {
        let alpha = lit ? 1.0 : 0.22
        let tint = accent(node)
        let shape = RoundedRectangle(cornerRadius: 9).path(in: node.frame)
        context.fill(shape, with: .color(Palette.card.opacity(alpha)))
        context.stroke(shape, with: .color(tint.opacity(alpha * 0.55)), lineWidth: 1.2)

        var cursor = node.x + 8
        let midY = node.centreY
        if node.kind != .route {
            drawGlyph(node.kind, left: cursor, centreY: midY, colour: tint.opacity(alpha), into: &context)
            cursor += 17
        }
        if let chip = chip(node) {
            let resolved = context.resolve(Text(chip).font(.system(size: 8, design: .monospaced)).foregroundStyle(tint.opacity(alpha)))
            let size = resolved.measure(in: CGSize(width: 100, height: 20))
            let box = CGRect(x: cursor, y: midY - 8, width: size.width + 8, height: 16)
            context.fill(RoundedRectangle(cornerRadius: 4).path(in: box), with: .color(tint.opacity(alpha * 0.16)))
            context.draw(resolved, at: CGPoint(x: box.midX, y: midY), anchor: .center)
            cursor += box.width + 6
        }
        let dotRoom: CGFloat = node.kind == .route ? 12 : 0
        let available = max(node.x + node.width - 8 - dotRoom - cursor, 1)
        context.draw(fitted(node.label, width: available, alpha: alpha, in: context), at: CGPoint(x: cursor, y: midY), anchor: .leading)
        if node.kind == .route {
            let colour: Color = switch node.health {
            case .down: Palette.red
            case .warn: Palette.yellow
            case .idle: Palette.muted
            case .quiet: Palette.green
            }
            let dot = CGRect(x: node.x + node.width - 12, y: midY - 3, width: 6, height: 6)
            context.fill(Circle().path(in: dot), with: .color(colour.opacity(alpha)))
        }
    }

    private func chip(_ node: MapNode) -> String? {
        switch node.kind {
        case .route: node.route?.protocol.uppercased() ?? "HTTP"
        case .group: String(node.members.count)
        case .middleware: node.uses > 1 ? "\(node.uses)×" : nil
        case .service, .entryPoint: nil
        }
    }

    private func drawGlyph(_ kind: MapNodeKind, left: CGFloat, centreY: CGFloat, colour: Color, into context: inout GraphicsContext) {
        let style = StrokeStyle(lineWidth: 1.1)
        switch kind {
        case .entryPoint:
            context.stroke(Path(CGRect(x: left + 1, y: centreY - 6, width: 9, height: 12)), with: .color(colour), style: style)
            context.fill(Circle().path(in: CGRect(x: left + 6.4, y: centreY - 1.1, width: 2.2, height: 2.2)), with: .color(colour))
        case .middleware:
            for dy in [-4.0, 0, 4] {
                var line = Path()
                line.move(to: CGPoint(x: left, y: centreY + dy))
                line.addLine(to: CGPoint(x: left + 11, y: centreY + dy))
                context.stroke(line, with: .color(colour), lineWidth: 1.4)
            }
        case .service:
            for dy in [-6.0, 0.5] {
                context.stroke(Path(CGRect(x: left, y: centreY + dy, width: 11, height: 5.5)), with: .color(colour), style: style)
            }
        case .group:
            for (dx, dy) in [(0.0, 0.0), (6, 0), (0, 6), (6, 6)] {
                context.stroke(Path(CGRect(x: left + dx, y: centreY - 6 + dy, width: 4.5, height: 4.5)), with: .color(colour), style: style)
            }
        case .route:
            break
        }
    }

    /// The label, shortened with an ellipsis until it measures within `width`.
    private func fitted(_ text: String, width: CGFloat, alpha: Double, in context: GraphicsContext) -> GraphicsContext.ResolvedText {
        func resolve(_ value: String) -> GraphicsContext.ResolvedText {
            context.resolve(Text(value).font(.system(size: 11)).foregroundStyle(Palette.text.opacity(alpha)))
        }
        let bounds = CGSize(width: CGFloat.greatestFiniteMagnitude, height: 40)
        var resolved = resolve(text)
        var count = text.count
        while resolved.measure(in: bounds).width > width && count > 1 {
            count -= 1
            resolved = resolve(String(text.prefix(count)) + "…")
        }
        return resolved
    }
}

/// Details for a tapped node. Port of `NodeSheet`.
private struct NodeSheet: View {
    let node: MapNode
    let graph: RouteMapGraph
    let onEdit: (String) -> Void

    var body: some View {
        let feeds = graph.edges.filter { $0.to == node.id }.compactMap { graph.byId[$0.from] }
        let serves = graph.edges.filter { $0.from == node.id }.compactMap { graph.byId[$0.to] }
        let chain = graph.subgraph(node.id)
        let routeCount = chain.nodes.filter { $0.kind == .route }.count
            + chain.nodes.filter { $0.kind == .group }.reduce(0) { $0 + $1.members.count }
        let none = String(localized: "none")
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Card(spacing: 0) {
                        switch node.kind {
                        case .route:
                            let route = node.route
                            DetailRow(label: String(localized: "Protocol"), value: (route?.protocol.uppercased()).flatMap { $0.isEmpty ? nil : $0 } ?? "HTTP")
                            if !node.detail.isEmpty { DetailRow(label: String(localized: "Host"), value: node.detail, mono: true) }
                            if let rule = route?.rule, !rule.isEmpty { DetailRow(label: String(localized: "Rule"), value: rule, mono: true) }
                            DetailRow(label: String(localized: "Entry points"), value: feeds.map(\.label).joined(separator: ", ").ifEmpty(none))
                            DetailRow(
                                label: String(localized: "Middlewares"),
                                value: serves.filter { $0.kind == .middleware }.map(\.label).joined(separator: ", ").ifEmpty(none)
                            )
                            DetailRow(label: String(localized: "Service"), value: route.map { RouteDetailView.shortName($0.serviceName) }?.ifEmpty(none) ?? none, mono: true)
                            DetailRow(label: String(localized: "Provider"), value: route?.provider.ifEmpty("file") ?? "file", last: true)
                        case .middleware:
                            DetailRow(label: String(localized: "Used by"), value: String(localized: "\(routeCount) routes"))
                            DetailRow(label: String(localized: "Routers"), value: feeds.map(\.label).joined(separator: ", ").ifEmpty(none), last: true)
                        case .service:
                            if !node.detail.isEmpty { DetailRow(label: String(localized: "Target"), value: node.detail, mono: true) }
                            DetailRow(label: String(localized: "Routes"), value: String(routeCount))
                            DetailRow(
                                label: String(localized: "Reached via"),
                                value: feeds.map(\.label).joined(separator: ", ").ifEmpty(String(localized: "directly")),
                                last: true
                            )
                        case .entryPoint:
                            DetailRow(label: String(localized: "Routers"), value: String(serves.count))
                            DetailRow(label: String(localized: "Routes"), value: String(routeCount), last: true)
                        case .group:
                            DetailRow(label: String(localized: "Provider"), value: node.label)
                            DetailRow(label: String(localized: "Routes"), value: String(node.members.count))
                            DetailRow(
                                label: String(localized: "Services"),
                                value: serves.map(\.label).joined(separator: ", ").ifEmpty(none),
                                last: true
                            )
                        }
                    }
                    if node.kind == .route, let route = node.route, route.isFileRoute {
                        Button { onEdit(route.id) } label: {
                            Text("Edit route").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
                .padding(Spacing.lg)
            }
            .background(Palette.bg)
            .navigationTitle(node.label)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text(kindLabel).font(.caption2).foregroundStyle(Palette.muted)
                        Text(node.label).font(.headline)
                    }
                }
            }
        }
    }

    private var kindLabel: String {
        switch node.kind {
        case .entryPoint: String(localized: "Entry point")
        case .route: String(localized: "Router")
        case .group: String(localized: "Provider")
        case .middleware: String(localized: "Middleware")
        case .service: String(localized: "Service")
        }
    }
}

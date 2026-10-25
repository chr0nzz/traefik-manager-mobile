import CoreGraphics
import Foundation

enum MapNodeKind: Int { case entryPoint, route, group, middleware, service }
enum MapHealth { case quiet, warn, down, idle }

struct MapNode: Identifiable, Equatable {
    var id: String
    var kind: MapNodeKind
    var label: String
    var detail = ""
    var route: Route?
    var members: [Route] = []
    var uses = 0
    var health: MapHealth = .quiet
    var column = 0
    var x: CGFloat = 0
    var y: CGFloat = 0
    var width: CGFloat = 0
    var height: CGFloat = 0

    var centreY: CGFloat { y + height / 2 }
    var frame: CGRect { CGRect(x: x, y: y, width: width, height: height) }

    static func == (lhs: MapNode, rhs: MapNode) -> Bool { lhs.id == rhs.id && lhs.x == rhs.x && lhs.y == rhs.y }
}

struct MapEdge: Hashable {
    var from: String
    var to: String
}

/// Entry points → routers → middlewares → services, laid out in columns. Port of `RouteMapGraph`.
struct RouteMapGraph {
    var nodes: [MapNode] = []
    var edges: [MapEdge] = []
    var width: CGFloat = 0
    var height: CGFloat = 0

    var byId: [String: MapNode] { Dictionary(nodes.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first }) }

    /// Every node upstream or downstream of `id`.
    func connected(_ id: String) -> Set<String> {
        var out: Set<String> = [id]
        walk(from: id, into: &out) { edge, frontier in frontier.contains(edge.to) ? edge.from : nil }
        walk(from: id, into: &out) { edge, frontier in frontier.contains(edge.from) ? edge.to : nil }
        return out
    }

    func subgraph(_ id: String) -> RouteMapGraph {
        let ids = connected(id)
        return RouteMapBuilder.layout(nodes.filter { ids.contains($0.id) }, edges.filter { ids.contains($0.from) && ids.contains($0.to) })
    }

    private func walk(from start: String, into out: inout Set<String>, step: (MapEdge, Set<String>) -> String?) {
        var frontier: Set<String> = [start]
        while !frontier.isEmpty {
            var next = Set<String>()
            for edge in edges {
                if let found = step(edge, frontier), out.insert(found).inserted { next.insert(found) }
            }
            frontier = next
        }
    }
}

/// Port of `RouteMapBuilder`, including the barycentre ordering and column layout.
enum RouteMapBuilder {
    static let collapseMin = 6
    private static let columnGap: CGFloat = 92
    private static let rowGap: CGFloat = 16
    private static let nodeHeight: CGFloat = 34
    private static let margin: CGFloat = 20
    private static let charWidth: CGFloat = 6.1
    private static let sidePadding: CGFloat = 16
    private static let glyphRoom: CGFloat = 17
    private static let dotRoom: CGFloat = 13
    private static let minWidth: CGFloat = 104
    private static let maxWidth: CGFloat = 320

    static func build(routes: [Route], protocol proto: String? = nil, provider: String? = nil, entryPoint: String? = nil, query: String = "") -> RouteMapGraph {
        let wanted = routes.filter { route in
            (proto == nil || route.protocol.caseInsensitiveCompare(proto!) == .orderedSame)
                && (provider == nil || route.provider == provider)
                && (entryPoint == nil || route.entryPointNames.contains(entryPoint!))
                && (query.isEmpty || [route.name, route.rule, route.serviceName].contains { $0.localizedCaseInsensitiveContains(query) })
        }
        guard !wanted.isEmpty else { return RouteMapGraph() }

        var usage: [String: Int] = [:]
        for name in wanted.flatMap(\.middlewareNames) { usage[name, default: 0] += 1 }
        var nodes: [MapNode] = []
        var edges: [MapEdge] = []
        var seen = Set<String>()
        var edgeSet = Set<MapEdge>()
        func add(_ node: MapNode) { if seen.insert(node.id).inserted { nodes.append(node) } }
        func link(_ from: String, _ to: String) {
            let edge = MapEdge(from: from, to: to)
            if edgeSet.insert(edge).inserted { edges.append(edge) }
        }

        let collapsed = Dictionary(grouping: wanted.filter { !$0.provider.isEmpty && $0.provider != "file" }, by: \.provider)
            .filter { $0.value.count >= collapseMin }
        let collapsedIds = Set(collapsed.values.flatMap { $0.map(\.id) })

        var epSeen = Set<String>()
        for name in wanted.flatMap(\.entryPointNames) where epSeen.insert(name).inserted {
            add(MapNode(id: "ep:\(name)", kind: .entryPoint, label: name))
        }

        for (name, members) in collapsed.sorted(by: { $0.key < $1.key }) {
            let id = "group:\(name)"
            add(MapNode(id: id, kind: .group, label: name, detail: String(localized: "\(members.count) routes"), members: members, health: worst(members)))
            var eps = Set<String>()
            for ep in members.flatMap(\.entryPointNames) where eps.insert(ep).inserted { link("ep:\(ep)", id) }
            var services = Set<String>()
            for member in members where !member.serviceName.isEmpty && services.insert(member.serviceName).inserted {
                add(serviceNode(member.serviceName, owner: member))
                link(id, "svc:\(member.serviceName)")
            }
        }

        for route in wanted where !collapsedIds.contains(route.id) {
            let id = "route:\(route.id)"
            add(MapNode(id: id, kind: .route, label: route.name, detail: route.hosts.first ?? "", route: route, health: health(route)))
            for ep in route.entryPointNames { link("ep:\(ep)", id) }
            let service = route.serviceName.isEmpty ? nil : route.serviceName
            if route.middlewareNames.isEmpty {
                if let service {
                    add(serviceNode(service, owner: route))
                    link(id, "svc:\(service)")
                }
            } else {
                for name in route.middlewareNames {
                    add(MapNode(id: "mw:\(name)", kind: .middleware, label: RouteDetailView.shortName(name), uses: usage[name] ?? 0))
                    link(id, "mw:\(name)")
                    if let service {
                        add(serviceNode(service, owner: route))
                        link("mw:\(name)", "svc:\(service)")
                    }
                }
            }
        }
        return layout(nodes, edges)
    }

    private static func serviceNode(_ name: String, owner: Route) -> MapNode {
        MapNode(
            id: "svc:\(name)",
            kind: .service,
            label: RouteDetailView.shortName(name),
            detail: owner.target.ifEmpty(owner.compositeChildren.map { RouteDetailView.shortName($0.name) }.joined(separator: ", ")),
            route: owner,
            health: owner.backendCount == 0 ? .down : .quiet
        )
    }

    private static func health(_ route: Route) -> MapHealth {
        if !route.enabled { return .idle }
        if route.target.isEmpty && route.servers.isEmpty { return .down }
        return .quiet
    }

    private static func worst(_ routes: [Route]) -> MapHealth {
        if routes.contains(where: { health($0) == .down }) { return .down }
        if routes.contains(where: { health($0) == .idle }) { return .idle }
        return .quiet
    }

    private static func columnOf(_ kind: MapNodeKind) -> Int {
        switch kind {
        case .entryPoint: 0
        case .route, .group: 1
        case .middleware: 2
        case .service: 3
        }
    }

    private static func widthOf(_ node: MapNode) -> CGFloat {
        let chip: CGFloat = switch node.kind {
        case .route: 30
        case .group: 20
        case .middleware where node.uses > 1: 22
        default: 0
        }
        let chrome = sidePadding + chip + (node.kind == .route ? dotRoom : glyphRoom)
        return min(max(chrome + CGFloat(node.label.count) * charWidth, minWidth), maxWidth)
    }

    static func layout(_ nodes: [MapNode], _ edges: [MapEdge]) -> RouteMapGraph {
        var columns: [Int: [MapNode]] = Dictionary(grouping: nodes) { columnOf($0.kind) }
            .mapValues { $0.sorted { $0.label.lowercased() < $1.label.lowercased() } }
        let order = columns.keys.sorted()
        let outgoing = Dictionary(grouping: edges, by: \.from)
        let incoming = Dictionary(grouping: edges, by: \.to)

        // Four barycentre sweeps to reduce crossings.
        for pass in 0..<4 {
            let sweep = pass % 2 == 0 ? order : order.reversed()
            var position: [String: Double] = [:]
            for column in columns.values {
                for (index, node) in column.enumerated() { position[node.id] = Double(index) }
            }
            for index in sweep {
                guard let column = columns[index] else { continue }
                let scored = column.enumerated().map { current, node -> (MapNode, Double) in
                    let neighbours = pass % 2 == 0 ? (incoming[node.id] ?? []).map(\.from) : (outgoing[node.id] ?? []).map(\.to)
                    let places = neighbours.compactMap { position[$0] }
                    return (node, places.isEmpty ? Double(current) : places.reduce(0, +) / Double(places.count))
                }
                let sorted = scored.enumerated().sorted { $0.element.1 != $1.element.1 ? $0.element.1 < $1.element.1 : $0.offset < $1.offset }.map(\.element.0)
                columns[index] = sorted
                for (i, node) in sorted.enumerated() { position[node.id] = Double(i) }
            }
        }

        var columnX: [Int: CGFloat] = [:]
        var cursor = margin
        for index in order {
            columnX[index] = cursor
            cursor += (columns[index]?.map(widthOf).max() ?? minWidth) + columnGap
        }

        var desired: [String: CGFloat] = [:]
        let anchor = columns[1] ?? columns[order.first ?? 0] ?? []
        var y = margin
        for node in anchor {
            desired[node.id] = y
            y += nodeHeight + rowGap
        }
        let preds = Dictionary(grouping: edges, by: \.to).mapValues { $0.map(\.from) }
        let succs = Dictionary(grouping: edges, by: \.from).mapValues { $0.map(\.to) }
        let anchorColumn = anchor.first.map { columnOf($0.kind) } ?? 0

        func settle(_ index: Int, _ neighbours: [String: [String]]) {
            guard let column = columns[index] else { return }
            let wanted = column.map { node -> (MapNode, CGFloat) in
                let places = (neighbours[node.id] ?? []).compactMap { desired[$0] }
                return (node, places.isEmpty ? margin : places.reduce(0, +) / CGFloat(places.count))
            }
            .enumerated()
            .sorted { $0.element.1 != $1.element.1 ? $0.element.1 < $1.element.1 : $0.offset < $1.offset }
            .map(\.element)
            var floor = margin
            for (node, want) in wanted {
                let place = max(want, floor)
                desired[node.id] = place
                floor = place + nodeHeight + rowGap
            }
            columns[index] = wanted.map(\.0)
        }
        for index in order.filter({ $0 > anchorColumn }).sorted() { settle(index, preds) }
        for index in order.filter({ $0 < anchorColumn }).sorted(by: >) { settle(index, succs) }

        var placed: [MapNode] = []
        var widest: CGFloat = 0
        var tallest: CGFloat = 0
        for index in order {
            for var node in columns[index] ?? [] {
                node.column = index
                node.x = columnX[index] ?? margin
                node.y = desired[node.id] ?? margin
                node.width = widthOf(node)
                node.height = nodeHeight
                widest = max(widest, node.x + node.width + margin)
                tallest = max(tallest, node.y + nodeHeight + margin)
                placed.append(node)
            }
        }
        return RouteMapGraph(nodes: placed, edges: edges, width: widest, height: tallest)
    }
}

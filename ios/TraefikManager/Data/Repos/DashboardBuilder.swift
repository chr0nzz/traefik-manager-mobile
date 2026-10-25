import Foundation

/// Everything the dashboard fetches in one go. Codable so it can be cached per server.
struct RawDashboard: Codable, Sendable {
    var overview: Overview?
    var entrypoints: [Entrypoint]?
    var routers: ProtoEnvelope?
    var services: ProtoEnvelope?
    var middlewares: ProtoEnvelope?
    var version: TraefikVersion?
}

enum ObjectState: Int, Comparable {
    case err, warn, idle, ok

    static func < (lhs: ObjectState, rhs: ObjectState) -> Bool { lhs.rawValue < rhs.rawValue }

    var status: TMStatus {
        switch self {
        case .err: .error
        case .warn: .warn
        case .idle: .unknown
        case .ok: .ok
        }
    }
}

struct ObjectSignal {
    var name: String
    var state: ObjectState
    var reason: String?
    var provider: String
}

struct SignalFlag: Hashable {
    var count: Int
    var label: String
    var status: TMStatus
}

struct ProviderCount: Hashable, Identifiable {
    var name: String
    var count: Int
    var worst: TMStatus
    var id: String { name }
}

struct SignalCard: Identifiable {
    enum Kind: String { case http, stream, services, middlewares }

    var kind: Kind
    var title: String
    var total: Int?
    var cells: [TMStatus]
    var flags: [SignalFlag]
    var sub: String
    var health: TMStatus
    var healthLabel: String
    var providers: [ProviderCount]
    var stripEmptyLabel: String

    var id: String { kind.rawValue }

    var alertCount: Int { flags.filter { $0.status == .error || $0.status == .warn }.reduce(0) { $0 + $1.count } }
}

struct EntrypointRow: Identifiable {
    var name: String
    var address: String
    var proto: String
    var routerCount: Int?
    var cells: [TMStatus]
    var facts: String
    var health: TMStatus
    var idle: Bool
    var id: String { name }
}

struct Verdict {
    var headline: String
    var detail: String
    var status: TMStatus
}

struct RuntimeInfo {
    var version: String?
    var codename: String?
    var metrics: String?
    var tracing: String?
    var accessLog: Bool?
}

struct DashboardSnapshot {
    var verdict: Verdict
    var cards: [SignalCard]
    var entrypoints: [EntrypointRow]
    var traefikReachable: Bool
    var providers: [ProviderCount]
    var runtime: RuntimeInfo
    var providerFilter: String?
}

/// Turns raw Traefik API data into the dashboard's cards and verdict. Line-for-line port of `DashboardBuilder.kt`.
enum DashboardBuilder {
    static func build(_ raw: RawDashboard, providerFilter: String?) -> DashboardSnapshot {
        let allProviders = providerCounts(
            (raw.routers?.all ?? []).map(classifyRouter)
                + (raw.services?.all ?? []).map(classifyService)
                + (raw.middlewares?.all ?? []).map { classifyMiddleware($0, referenced: []) }
        )
        return build(
            overview: raw.overview,
            entrypoints: raw.entrypoints,
            routers: raw.routers?.filtered(provider: providerFilter),
            services: raw.services?.filtered(provider: providerFilter),
            middlewares: raw.middlewares?.filtered(provider: providerFilter),
            runtime: RuntimeInfo(
                version: raw.version?.version,
                codename: raw.version?.codename,
                metrics: raw.overview?.features?.metrics,
                tracing: raw.overview?.features?.tracing,
                accessLog: raw.overview?.features?.accessLog
            ),
            providerFilter: providerFilter,
            allProviders: allProviders
        )
    }

    static func build(
        overview: Overview?,
        entrypoints: [Entrypoint]?,
        routers: ProtoEnvelope?,
        services: ProtoEnvelope?,
        middlewares: ProtoEnvelope?,
        runtime: RuntimeInfo = RuntimeInfo(),
        providerFilter: String? = nil,
        allProviders: [ProviderCount]? = nil
    ) -> DashboardSnapshot {
        let routersListed = routers?.reachable == true
        let servicesListed = services?.reachable == true
        let middlewaresListed = middlewares?.reachable == true
        let reachable = routersListed || overview?.isBlind == false

        let httpRouters = (routers?.http ?? []).map(classifyRouter)
        let tcpRouters = (routers?.tcp ?? []).map(classifyRouter)
        let udpRouters = (routers?.udp ?? []).map(classifyRouter)
        let streamRouters = tcpRouters + udpRouters
        let serviceObjects = services?.all ?? []
        let allServices = serviceObjects.map(classifyService)
        let referenced = referencedMiddlewares(entrypoints)
        let allMiddlewares = (middlewares?.all ?? []).map { classifyMiddleware($0, referenced: referenced) }

        let backendsUp = serviceObjects.reduce(0) { sum, obj in
            sum + (obj.serverStatus ?? [:]).values.filter { $0.uppercased() == "UP" }.count
        }
        let backendsTotal = serviceObjects.reduce(0) { $0 + ($1.serverStatus?.count ?? 0) }

        let cards = [
            card(
                kind: .http,
                title: String(localized: "HTTP Routers"),
                signals: httpRouters,
                listed: routersListed,
                overviewTotal: overview?.http?.routers?.total,
                emptyLabel: String(localized: "no HTTP routers configured"),
                okSub: { ok in String(localized: "\(ok) live") }
            ),
            card(
                kind: .stream,
                title: String(localized: "TCP / UDP Routers"),
                signals: streamRouters,
                listed: routersListed,
                overviewTotal: sum(overview?.tcp?.routers?.total, overview?.udp?.routers?.total),
                emptyLabel: String(localized: "no stream routers configured"),
                okSub: { _ in
                    var parts: [String] = []
                    if !tcpRouters.isEmpty { parts.append("TCP \(tcpRouters.count)") }
                    if !udpRouters.isEmpty { parts.append("UDP \(udpRouters.count)") }
                    parts.append(String(localized: "all forwarding"))
                    return joined(parts)
                }
            ),
            card(
                kind: .services,
                title: String(localized: "Services"),
                signals: allServices,
                listed: servicesListed,
                overviewTotal: sum(overview?.http?.services?.total, overview?.tcp?.services?.total, overview?.udp?.services?.total),
                emptyLabel: String(localized: "no services configured"),
                okSub: { _ in
                    backendsTotal == 0
                        ? String(localized: "no health checks configured")
                        : backendsUpText(backendsUp, backendsTotal)
                }
            ),
            card(
                kind: .middlewares,
                title: String(localized: "Middlewares"),
                signals: allMiddlewares,
                listed: middlewaresListed,
                overviewTotal: sum(overview?.http?.middlewares?.total, overview?.tcp?.middlewares?.total),
                emptyLabel: String(localized: "no middlewares configured"),
                okSub: { ok in
                    let unused = allMiddlewares.filter { $0.state == .idle }.count
                    var parts = [String(localized: "\(ok) in use")]
                    if unused > 0 { parts.append(String(localized: "\(unused) unused")) }
                    return joined(parts)
                }
            ),
        ]

        return DashboardSnapshot(
            verdict: verdict(cards: cards, reachable: reachable, backendsUp: backendsUp, backendsTotal: backendsTotal),
            cards: cards,
            entrypoints: entrypointRows(entrypoints, routers: routers),
            traefikReachable: reachable,
            providers: allProviders ?? providerCounts(httpRouters + streamRouters + allServices + allMiddlewares),
            runtime: runtime,
            providerFilter: providerFilter
        )
    }

    // MARK: - Classification

    static func classifyRouter(_ obj: TraefikObject) -> ObjectSignal {
        if let common = commonState(obj) { return common }
        if obj.boundEntryPoints.isEmpty {
            return ObjectSignal(name: obj.shortName, state: .idle, reason: String(localized: "bound to no entry point"), provider: obj.provider)
        }
        return ObjectSignal(name: obj.shortName, state: .ok, provider: obj.provider)
    }

    static func classifyService(_ obj: TraefikObject) -> ObjectSignal {
        if let common = commonState(obj) { return common }
        let servers = obj.serverStatus ?? [:]
        let down = servers.values.filter { $0.uppercased() != "UP" }.count
        if servers.isEmpty, let type = obj.type, type != "loadBalancer" {
            return ObjectSignal(name: obj.shortName, state: .ok, provider: obj.provider)
        }
        if servers.isEmpty {
            return ObjectSignal(name: obj.shortName, state: .idle, reason: String(localized: "no health check configured"), provider: obj.provider)
        }
        if down == servers.count {
            return ObjectSignal(name: obj.shortName, state: .err, reason: String(localized: "all \(servers.count) backends down"), provider: obj.provider)
        }
        if down > 0 {
            return ObjectSignal(name: obj.shortName, state: .warn, reason: String(localized: "\(down) of \(servers.count) backends down"), provider: obj.provider)
        }
        return ObjectSignal(name: obj.shortName, state: .ok, provider: obj.provider)
    }

    static func classifyMiddleware(_ obj: TraefikObject, referenced: Set<String>) -> ObjectSignal {
        if let common = commonState(obj) { return common }
        let used = obj.usedBy?.isEmpty == false || referenced.contains(obj.shortName) || referenced.contains(obj.name)
        if !used {
            return ObjectSignal(name: obj.shortName, state: .idle, reason: String(localized: "referenced by no router"), provider: obj.provider)
        }
        return ObjectSignal(name: obj.shortName, state: .ok, provider: obj.provider)
    }

    private static func commonState(_ obj: TraefikObject) -> ObjectSignal? {
        switch obj.status {
        case "disabled":
            ObjectSignal(name: obj.shortName, state: .err, reason: String(localized: "disabled"), provider: obj.provider)
        case "warning":
            ObjectSignal(name: obj.shortName, state: .warn, reason: String(localized: "warning"), provider: obj.provider)
        case "enabled":
            nil
        default:
            ObjectSignal(name: obj.shortName, state: .idle, reason: String(localized: "no status reported"), provider: obj.provider)
        }
    }

    private static func referencedMiddlewares(_ entrypoints: [Entrypoint]?) -> Set<String> {
        Set((entrypoints ?? []).flatMap { $0.http?.middlewares ?? [] }.flatMap { [$0, shortName($0)] })
    }

    // MARK: - Helpers

    private static func shortName(_ name: String) -> String {
        name.split(separator: "@", maxSplits: 1).first.map(String.init) ?? name
    }

    private static func sum(_ values: Int?...) -> Int? {
        values.allSatisfy { $0 == nil } ? nil : values.reduce(0) { $0 + ($1 ?? 0) }
    }

    static func joined(_ parts: [String], separator: String = " · ") -> String {
        parts.filter { !$0.isEmpty }.joined(separator: separator)
    }

    static func backendsUpText(_ up: Int, _ total: Int) -> String {
        String(localized: "\(up) of \(total) backends up")
    }

    private static func cells(_ signals: [ObjectSignal]) -> [TMStatus] {
        signals.sorted { $0.state < $1.state }.map(\.state.status)
    }

    private static func providerCounts(_ signals: [ObjectSignal]) -> [ProviderCount] {
        Dictionary(grouping: signals.filter { !$0.provider.isEmpty }, by: \.provider)
            .map { name, group in
                ProviderCount(
                    name: name,
                    count: group.count,
                    worst: group.contains { $0.state == .err } ? .error : group.contains { $0.state == .warn } ? .warn : .ok
                )
            }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.name < $1.name }
    }

    private static func named(_ signal: ObjectSignal) -> String {
        joined([signal.name, signal.reason].compactMap { $0 }, separator: ": ")
    }

    private static func card(
        kind: SignalCard.Kind,
        title: String,
        signals: [ObjectSignal],
        listed: Bool,
        overviewTotal: Int?,
        emptyLabel: String,
        okSub: (Int) -> String
    ) -> SignalCard {
        let err = signals.filter { $0.state == .err }.count
        let warn = signals.filter { $0.state == .warn }.count
        let idle = signals.filter { $0.state == .idle }.count
        let ok = signals.filter { $0.state == .ok }.count
        let total: Int? = listed ? signals.count : overviewTotal
        let hidden = listed && overviewTotal != nil ? max((overviewTotal ?? 0) - signals.count, 0) : 0

        var flags: [SignalFlag] = []
        if err > 0 { flags.append(SignalFlag(count: err, label: String(localized: "disabled"), status: .error)) }
        if warn > 0 { flags.append(SignalFlag(count: warn, label: String(localized: "warnings"), status: .warn)) }
        if idle > 0 {
            let label = kind == .middlewares ? String(localized: "unused") : String(localized: "idle")
            flags.append(SignalFlag(count: idle, label: label, status: .unknown))
        }

        let sub: String
        if !listed && total == nil {
            sub = String(localized: "Traefik API unreachable")
        } else if !listed {
            sub = String(localized: "total from overview, the list is unavailable")
        } else if total == 0 {
            sub = emptyLabel
        } else if hidden > 0 && err == 0 && warn == 0 {
            sub = joined([okSub(ok), String(localized: "+\(hidden) internal or other providers")])
        } else if let first = signals.first(where: { $0.state == .err }) {
            sub = named(first)
        } else if let first = signals.first(where: { $0.state == .warn }) {
            sub = named(first)
        } else {
            sub = okSub(ok)
        }

        let unknown = !listed && total == nil
        let health: TMStatus = unknown ? .warn : err > 0 ? .error : warn > 0 ? .warn : .ok
        let healthLabel = unknown
            ? String(localized: "unknown")
            : err > 0 ? String(localized: "down") : warn > 0 ? String(localized: "degraded") : String(localized: "healthy")

        return SignalCard(
            kind: kind,
            title: title,
            total: total,
            cells: listed ? cells(signals) : [],
            flags: flags,
            sub: sub,
            health: health,
            healthLabel: healthLabel,
            providers: listed ? providerCounts(signals) : [],
            stripEmptyLabel: listed ? String(localized: "nothing configured") : String(localized: "no data")
        )
    }

    private static func entrypointRows(_ entrypoints: [Entrypoint]?, routers: ProtoEnvelope?) -> [EntrypointRow] {
        guard let entrypoints else { return [] }
        let listed = routers?.reachable == true
        return entrypoints.filter { !$0.name.isEmpty }.map { ep in
            let bound = (routers?.all ?? []).filter { $0.boundEntryPoints.contains(ep.name) }
            let signals = bound.map(classifyRouter)
            let httpBound = (routers?.http ?? []).filter { $0.boundEntryPoints.contains(ep.name) }.count
            let tcpBound = (routers?.tcp ?? []).contains { $0.boundEntryPoints.contains(ep.name) }
            let proto: String
            if ep.isUDP {
                proto = "UDP"
            } else if httpBound == 0 && tcpBound {
                proto = "TCP"
            } else if ep.hasTLS || ep.address.hasSuffix(":443") || ep.address.hasSuffix(":8443") {
                proto = "HTTPS"
            } else {
                proto = "HTTP"
            }
            return EntrypointRow(
                name: ep.name,
                address: ep.address,
                proto: proto,
                routerCount: listed ? bound.count : nil,
                cells: listed ? cells(signals) : [],
                facts: entrypointFacts(ep, boundCount: bound.count, proto: proto, listed: listed),
                health: signals.contains { $0.state == .err } ? .error : signals.contains { $0.state == .warn } ? .warn : .ok,
                idle: listed && bound.isEmpty
            )
        }
    }

    private static func entrypointFacts(_ ep: Entrypoint, boundCount: Int, proto: String, listed: Bool) -> String {
        if !listed { return String(localized: "router list unavailable, bindings unknown") }
        if boundCount == 0 { return String(localized: "no router binds this entry point") }
        var facts: [String] = []
        if ep.asDefault { facts.append("asDefault") }
        let resolver = ep.http?.tls?.certResolver ?? ""
        if !resolver.isEmpty { facts.append(String(localized: "TLS via \(resolver)")) }
        if ep.hasTLS && resolver.isEmpty { facts.append(String(localized: "TLS, no certResolver")) }
        if let options = ep.http?.tls?.options, !options.isEmpty { facts.append(String(localized: "TLS options \(options)")) }
        if let middlewares = ep.http?.middlewares, !middlewares.isEmpty {
            facts.append(middlewares.map(shortName).joined(separator: ", "))
        }
        if !facts.isEmpty { return joined(facts) }
        switch proto {
        case "UDP": return String(localized: "raw UDP datagrams")
        case "TCP": return String(localized: "raw TCP passthrough")
        case "HTTPS": return String(localized: "HTTPS front door")
        default: return String(localized: "plain HTTP entry point")
        }
    }

    private static func verdict(cards: [SignalCard], reachable: Bool, backendsUp: Int, backendsTotal: Int) -> Verdict {
        guard reachable else {
            return Verdict(
                headline: String(localized: "Traefik API unreachable"),
                detail: String(localized: "counts and health cannot be read"),
                status: .warn
            )
        }
        let errors = cards.reduce(0) { $0 + $1.flags.filter { $0.status == .error }.reduce(0) { $0 + $1.count } }
        let warnings = cards.reduce(0) { $0 + $1.flags.filter { $0.status == .warn }.reduce(0) { $0 + $1.count } }
        if errors > 0 {
            return Verdict(
                headline: String(localized: "\(errors) objects are down"),
                detail: warnings > 0 ? String(localized: "\(warnings) degraded") : String(localized: "traffic is not flowing for them"),
                status: .error
            )
        }
        if warnings > 0 {
            return Verdict(
                headline: String(localized: "\(warnings) objects are degraded"),
                detail: String(localized: "still serving, worth a look"),
                status: .warn
            )
        }
        var detail = [String(localized: "no errors or warnings reported")]
        if backendsTotal > 0 { detail.append(backendsUpText(backendsUp, backendsTotal)) }
        return Verdict(headline: String(localized: "All healthy"), detail: joined(detail), status: .ok)
    }
}

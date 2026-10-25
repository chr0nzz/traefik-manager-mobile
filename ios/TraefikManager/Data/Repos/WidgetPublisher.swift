import Foundation
import UIKit
import WidgetKit

/// Builds the widget snapshot in the app and hands it to the extension through `SharedVault`.
/// Port of `WidgetDataSource` / `LauncherWidgetSource`; runs on launch, on return to the app and in background refresh.
enum WidgetPublisher {
    private static let cellLimit = 72
    private static let rowLimit = 8
    private static var lastRun = Date.distantPast

    /// Rebuilds everything unless it ran within `minimumGap`.
    static func publish(connection: Connection?, minimumGap: TimeInterval = 300) async {
        guard let connection, Date().timeIntervalSince(lastRun) >= minimumGap else { return }
        lastRun = Date()
        let host: TMService = connection.demo
            ? DemoService()
            : LiveService(client: APIClient(baseURL: connection.baseURL, apiKey: connection.apiKey, agentId: nil))
        let snapshot = await build(host: host, connection: connection)
        SharedVault.write(snapshot)
        WidgetCenter.shared.reloadAllTimelines()
    }

    static func clear() {
        SharedVault.clear()
        WidgetCenter.shared.reloadAllTimelines()
    }

    static func build(host: TMService, connection: Connection) async -> WidgetSnapshot {
        let servers = await ServersRepository.servers(using: host, probeHealth: false)
        let wanted = await configuredServers()
        async let overview = overviewRows(servers: servers, host: host)
        async let launcher = launcherApps(host: host, connection: connection)
        var cards: [String: [String: WidgetCard]] = [:]
        await withTaskGroup(of: (String, [String: WidgetCard]).self) { group in
            for server in servers where wanted.contains(server.agentId ?? "") {
                group.addTask { (server.agentId ?? "", await cardsFor(host.scoped(to: server.agentId))) }
            }
            for await (id, built) in group { cards[id] = built }
        }
        return WidgetSnapshot(servers: await overview, cards: cards, launcher: await launcher, updatedAt: Date(), demo: connection.demo)
    }

    /// The host plus every server a placed status widget points at.
    private static func configuredServers() async -> Set<String> {
        var ids: Set<String> = [""]
        let configured: [WidgetInfo] = await withCheckedContinuation { continuation in
            WidgetCenter.shared.getCurrentConfigurations { continuation.resume(returning: (try? $0.get()) ?? []) }
        }
        do {
            for info in configured {
                if let intent = info.widgetConfigurationIntent(of: StatusWidgetIntent.self), let server = intent.server {
                    ids.insert(server.id)
                }
            }
        }
        return ids
    }

    // MARK: Overview

    private static func overviewRows(servers: [ServerEntry], host: TMService) async -> [WidgetServerRow] {
        await withTaskGroup(of: (Int, WidgetServerRow).self) { group in
            for (index, server) in servers.enumerated() {
                group.addTask {
                    let service = host.scoped(to: server.agentId)
                    let id = server.agentId ?? ""
                    guard let routers = try? await service.routers() else {
                        return (index, WidgetServerRow(id: id, name: server.label, reachable: false))
                    }
                    let classified = routers.all.map { DashboardBuilder.classifyRouter($0).state.status }
                    let services = (try? await service.services())?.all ?? []
                    let crowdsec = await CrowdSecModel.read(service: service, full: false, previous: nil)
                    return (index, WidgetServerRow(
                        id: id,
                        name: server.label,
                        routers: classified.count,
                        warn: classified.filter { $0 == .warn }.count,
                        err: classified.filter { $0 == .error }.count,
                        services: services.count,
                        cells: services.prefix(cellLimit).map { wire(DashboardBuilder.classifyService($0).state.status) },
                        bans: crowdsec.decisions.ok ? crowdsec.decisionTotal : -1,
                        reachable: routers.reachable
                    ))
                }
            }
            var rows = [(Int, WidgetServerRow)]()
            for await row in group { rows.append(row) }
            return rows.sorted { $0.0 < $1.0 }.map(\.1)
        }
    }

    // MARK: Cards

    private static func cardsFor(_ service: TMService) async -> [String: WidgetCard] {
        async let dashboard = dashboardCards(service)
        async let certs = try? certsCard(service)
        async let crowdsec = crowdSecCards(service)
        var built = await dashboard
        if let certs = await certs { built[WidgetCardKind.certs.rawValue] = certs }
        built.merge(await crowdsec) { a, _ in a }
        for kind in WidgetCardKind.allCases where built[kind.rawValue] == nil {
            built[kind.rawValue] = WidgetCard(
                key: kind.rawValue, title: kind.title, hero: "-", health: .unknown,
                healthLabel: String(localized: "no answer"),
                sub: kind.crowdsec ? String(localized: "CrowdSec did not answer") : String(localized: "The server did not answer")
            )
        }
        return built
    }

    private static func dashboardCards(_ service: TMService) async -> [String: WidgetCard] {
        async let overview = try? service.overview()
        async let entrypoints = try? service.entrypoints()
        async let routers = try? service.routers()
        async let services = try? service.services()
        async let middlewares = try? service.middlewares()
        let raw = await RawDashboard(overview: overview, entrypoints: entrypoints, routers: routers, services: services, middlewares: middlewares)
        guard raw.routers != nil || raw.services != nil || raw.overview != nil else { return [:] }
        let snapshot = DashboardBuilder.build(raw, providerFilter: nil)
        var cards: [String: WidgetCard] = [:]
        for card in snapshot.cards {
            cards[card.kind.rawValue] = WidgetCard(
                key: card.kind.rawValue,
                title: card.title,
                hero: "\(card.total ?? card.cells.count)",
                unit: card.kind == .services ? String(localized: "services") : card.kind == .middlewares ? String(localized: "middlewares") : String(localized: "routers"),
                health: wire(card.health),
                healthLabel: card.healthLabel,
                sub: card.sub,
                cells: card.cells.prefix(cellLimit).map(wire),
                chips: card.flags.map { WidgetChip(label: "\($0.count) \($0.label)", health: wire($0.status)) },
                footer: card.providers.map { WidgetChip(label: "\($0.name) \($0.count)", health: wire($0.worst)) }
            )
        }
        let rows = snapshot.entrypoints
        let idle = rows.filter(\.idle).count
        cards[WidgetCardKind.entrypoints.rawValue] = WidgetCard(
            key: WidgetCardKind.entrypoints.rawValue,
            title: WidgetCardKind.entrypoints.title,
            hero: "\(rows.count)",
            unit: String(localized: "entry points"),
            health: rows.contains { $0.health == .error } ? .error : idle > 0 ? .warn : .ok,
            sub: rows.first { !$0.idle }.map { String(localized: "busiest \($0.name) · \($0.address)") } ?? String(localized: "nothing bound"),
            cells: rows.flatMap { $0.cells.map(wire) }.prefix(cellLimit).map { $0 },
            chips: idle > 0 ? [WidgetChip(label: String(localized: "\(idle) idle"), health: .warn)] : [],
            rows: rows.prefix(rowLimit).map {
                WidgetRow(name: "\($0.name) \($0.address)", count: $0.routerCount.map(String.init) ?? "-", health: wire($0.health))
            }
        )
        return cards
    }

    private static func certsCard(_ service: TMService) async throws -> WidgetCard {
        let certs = CertRows.from(try await service.certs().certs)
        let expiring = certs.filter { ($0.daysLeft ?? 999) <= 30 }.count
        let critical = certs.filter { ($0.daysLeft ?? 999) <= 7 }.count
        func health(_ days: Int?) -> WidgetHealth { (days ?? 999) <= 7 ? .error : (days ?? 999) <= 30 ? .warn : .ok }
        var chips: [WidgetChip] = []
        if critical > 0 { chips.append(WidgetChip(label: String(localized: "\(critical) critical"), health: .error)) }
        if expiring > critical { chips.append(WidgetChip(label: String(localized: "\(expiring - critical) expiring"), health: .warn)) }
        if expiring == 0 { chips.append(WidgetChip(label: String(localized: "all current"), health: .ok)) }
        let first = certs.first
        return WidgetCard(
            key: WidgetCardKind.certs.rawValue,
            title: WidgetCardKind.certs.title,
            hero: "\(certs.count)",
            unit: String(localized: "certificates"),
            health: critical > 0 ? .error : expiring > 0 ? .warn : .ok,
            sub: first.map { cert in cert.daysLeft.map { String(localized: "soonest \(cert.main) · \($0)d left") } ?? String(localized: "soonest \(cert.main)") }
                ?? String(localized: "no certificates"),
            cells: certs.prefix(cellLimit).map { health($0.daysLeft) },
            chips: chips,
            rows: certs.prefix(rowLimit).map { WidgetRow(name: $0.main, count: $0.daysLeft.map { "\($0)d" } ?? "-", health: health($0.daysLeft)) }
        )
    }

    private static func crowdSecCards(_ service: TMService) async -> [String: WidgetCard] {
        let snapshot = await CrowdSecModel.read(service: service, full: false, previous: nil)
        guard snapshot.decisions.ok || snapshot.alerts.ok else { return [:] }
        let alerts = snapshot.alertList
        let banned = snapshot.bannedIPs
        let handled: (CsAlert) -> Bool = { snapshot.handled($0, banned: banned) }

        let sources = CrowdSecAnalytics.sources(alerts, handled: handled)
        let loose = sources.filter { $0.open > 0 }.count
        let severe = sources.contains { $0.open > 0 && $0.count > 1 }
        let repeats = sources.filter { $0.count > 1 }.count
        let scenarios = CrowdSecAnalytics.scenarios(alerts, handled: handled)
        let paths = CrowdSecAnalytics.paths(alerts, handled: handled)
        let events = alerts.reduce(0) { $0 + $1.eventsCount }
        let total = snapshot.decisionTotal
        let own = snapshot.ownBans
        let banCells = min(total, cellLimit)
        let ownCells = total == 0 ? 0 : (own * banCells + total - 1) / total

        return [
            WidgetCardKind.sources.rawValue: WidgetCard(
                key: WidgetCardKind.sources.rawValue,
                title: WidgetCardKind.sources.title,
                hero: "\(sources.count)",
                unit: String(localized: "sources"),
                health: severe ? .error : loose > 0 ? .warn : .ok,
                sub: sources.first.map { String(localized: "worst \($0.label)") + " · " + String(localized: "\($0.weight) events") } ?? String(localized: "nobody tripped a scenario"),
                cells: sources.prefix(cellLimit).map { $0.open > 0 && $0.count > 1 ? .error : $0.open > 0 ? .warn : .idle },
                chips: sources.isEmpty ? [] : loose > 0
                    ? [WidgetChip(label: String(localized: "\(loose) loose"), health: severe ? .error : .warn),
                       WidgetChip(label: String(localized: "\(sources.count - loose) banned"))]
                    : [WidgetChip(label: String(localized: "every source banned"))],
                footer: [WidgetChip(label: String(localized: "\(repeats) repeat")), WidgetChip(label: String(localized: "\(sources.count - repeats) one-shot"))]
            ),
            WidgetCardKind.scenarios.rawValue: WidgetCard(
                key: WidgetCardKind.scenarios.rawValue,
                title: WidgetCardKind.scenarios.title,
                hero: "\(alerts.count)",
                unit: String(localized: "alerts"),
                health: scenarios.contains { $0.open > 0 } ? .warn : .ok,
                sub: scenarios.first.map { String(localized: "worst \($0.label)") + " · " + String(localized: "\(events) events rolled up") } ?? String(localized: "nothing to rank in the retained window"),
                rows: scenarios.prefix(rowLimit).map(rankRow)
            ),
            WidgetCardKind.paths.rawValue: WidgetCard(
                key: WidgetCardKind.paths.rawValue,
                title: WidgetCardKind.paths.title,
                hero: "\(paths.count)",
                unit: String(localized: "paths"),
                health: paths.contains { $0.open > 0 } ? .warn : .ok,
                sub: paths.first.map { String(localized: "most wanted \($0.label)") } ?? String(localized: "nothing was aimed at"),
                rows: paths.prefix(rowLimit).map(rankRow)
            ),
            WidgetCardKind.bans.rawValue: WidgetCard(
                key: WidgetCardKind.bans.rawValue,
                title: WidgetCardKind.bans.title,
                hero: "\(total)",
                unit: String(localized: "bans"),
                health: total == 0 ? .idle : .ok,
                sub: String(localized: "\(own) from this host") + " · " + String(localized: "\(snapshot.subscribedBans) subscribed"),
                cells: (0..<banCells).map { $0 < ownCells ? .ok : .idle },
                footer: snapshot.origins.prefix(3).map { WidgetChip(label: "\($0.0) \($0.1)") }
            ),
        ]
    }

    private static func rankRow(_ row: CsRanked) -> WidgetRow {
        WidgetRow(
            name: row.label,
            count: "\(row.count)",
            flag: row.open > 0 ? "\(row.open)" : "",
            health: row.open > 0 && row.open == row.count ? .error : row.open > 0 ? .warn : .ok
        )
    }

    // MARK: Launcher

    private static func launcherApps(host: TMService, connection: Connection) async -> [WidgetLauncherApp] {
        guard let routes = try? await host.routes().apps else { return [] }
        let config = (try? await host.dashboardConfig()) ?? DashboardConfig()
        let apps = LauncherBuilder.build(routes: routes, config: config, includeHidden: false)
            .flatMap(\.apps)
            .filter { $0.url != nil }
            .prefix(16)
        return await withTaskGroup(of: (Int, WidgetLauncherApp).self) { group in
            for (index, app) in apps.enumerated() {
                group.addTask {
                    let iconURL = connection.demo ? nil : RouteIcons.url(for: app.route, config: config, baseURL: connection.baseURL)
                    let icon = await iconURL.asyncMap { await ImageLoader.shared.image(for: $0, auth: connection) }?.flatMap(thumbnail)
                    return (index, WidgetLauncherApp(id: app.id, name: app.name, url: app.url ?? "", icon: icon))
                }
            }
            var built = [(Int, WidgetLauncherApp)]()
            for await item in group { built.append(item) }
            return built.sorted { $0.0 < $1.0 }.map(\.1)
        }
    }

    /// Small PNG so the shared keychain item stays light.
    private static func thumbnail(_ image: UIImage) -> Data? {
        let side: CGFloat = 64
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side))
        return renderer.pngData { _ in
            let scale = min(side / max(image.size.width, 1), side / max(image.size.height, 1))
            let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            image.draw(in: CGRect(x: (side - size.width) / 2, y: (side - size.height) / 2, width: size.width, height: size.height))
        }
    }

    private static func wire(_ status: TMStatus) -> WidgetHealth {
        switch status {
        case .ok: .ok
        case .warn: .warn
        case .error: .error
        case .disabled: .idle
        case .unknown: .unknown
        }
    }
}

private extension Optional {
    func asyncMap<T>(_ transform: (Wrapped) async -> T) async -> T? {
        guard let value = self else { return nil }
        return await transform(value)
    }
}

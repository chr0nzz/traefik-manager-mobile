import Foundation
import Observation

/// Root app state: the saved connection, the active server and what it can show.
/// Combines `ApiProvider`, `ServerScope` and `RootViewModel` from the Android app.
@MainActor
@Observable
final class AppModel {
    private(set) var connection: Connection?
    private(set) var servers: [ServerEntry] = []
    private(set) var settings: ServerSettings?
    private(set) var switching = false
    /// Bumped whenever the target server changes so screens reload.
    private(set) var generation = 0 {
        didSet {
            dashboard.reset()
            routes.reset()
            launcher.reset()
            services.reset()
            notifications.reset(for: activeAgentId)
            listCounts = [:]
            Task { [geo] in await geo.reset() }
        }
    }

    /// The screen on show. Lives here rather than in the tab bar or sidebar shell so folding,
    /// unfolding or resizing (which swaps one shell for the other) keeps the user where they were.
    var selection: Destination = DebugLaunch.tab.flatMap(Destination.init(rawValue:)) ?? .home

    let dashboard = DashboardStore()
    let routes = RoutesStore()
    let launcher = LauncherStore()
    let services = ServicesStore()
    let geo = GeoStore()
    let notifications = NotificationsStore()

    private(set) var listCounts: [Destination: Int] = [:]

    private(set) var activeAgentId: String? {
        didSet { UserDefaults.standard.set(activeAgentId, forKey: PreferenceKey.activeAgent) }
    }

    private let connectionStore = ConnectionStore()

    /// Editor or result sheet shown above the tab bar / sidebar shells; see `shellSheet`.
    var sheet: ShellSheet?

    init() {
        #if DEBUG
        // Launch arguments for simulator screenshots: `-tm-demo` opens demo mode, `-tm-reset` signs out.
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-tm-reset") { connectionStore.clear() }
        if arguments.contains("-tm-demo") { connectionStore.enterDemo() }
        #endif
        connection = connectionStore.load()
        activeAgentId = UserDefaults.standard.string(forKey: PreferenceKey.activeAgent)
    }

    // MARK: - Service

    /// Host-scoped service for the saved connection.
    var hostService: TMService? {
        guard let connection else { return nil }
        if connection.demo { return DemoService() }
        return LiveService(client: APIClient(baseURL: connection.baseURL, apiKey: connection.apiKey, agentId: nil))
    }

    /// Service scoped to the active server.
    var service: TMService? { hostService?.scoped(to: activeAgentId) }

    var isDemo: Bool { connection?.demo ?? false }

    /// Snapshot cache key for the active server; nil in demo mode so fixtures never hit the disk.
    var cacheKey: String? {
        guard let connection, !connection.demo else { return nil }
        return SnapshotStore.key(baseURL: connection.baseURL, agentId: activeAgentId)
    }

    func refreshRoutes() async {
        guard let service else { return }
        await routes.load(service: service, cacheKey: cacheKey)
    }

    func refreshServices() async {
        guard let service else { return }
        await services.load(service: service)
    }

    /// Launcher groups come from the route list, so routes refresh first.
    func refreshLauncher() async {
        guard let service else { return }
        await refreshRoutes()
        await launcher.load(service: service, routes: routes.routes)
    }

    func refreshDashboard() async {
        guard let service else { return }
        dashboard.restore(cacheKey: cacheKey)
        await dashboard.refresh(service: service, cacheKey: cacheKey)
    }

    // MARK: - Servers

    var activeServer: ServerEntry {
        servers.first { $0.agentId == activeAgentId } ?? servers.first ?? .host
    }

    var capabilities: ServerCapabilities {
        ServerCapabilities(
            isHost: activeAgentId == nil,
            settings: settings,
            agent: servers.first { $0.agentId == activeAgentId }?.agent
        )
    }

    var navBadges: [Destination: NavBadge] {
        guard let raw = dashboard.raw else { return listCounts.mapValues { NavBadge(count: $0) } }
        let cards = DashboardBuilder.build(raw, providerFilter: nil).cards
        func badge(_ kinds: Set<SignalCard.Kind>) -> NavBadge {
            let picked = cards.filter { kinds.contains($0.kind) }
            return NavBadge(count: picked.reduce(0) { $0 + ($1.total ?? 0) }, alerts: picked.reduce(0) { $0 + $1.alertCount })
        }
        var badges: [Destination: NavBadge] = [.routes: badge([.http, .stream]), .services: badge([.services]), .middlewares: badge([.middlewares])]
        for (destination, count) in listCounts { badges[destination] = NavBadge(count: count) }
        return badges
    }

    var destinations: [Destination] {
        let capabilities = capabilities
        return Destination.allCases.filter { $0.visible(with: capabilities) }
    }

    func refresh() async {
        guard let hostService else { return }
        async let fetchedSettings = try? hostService.settings()
        async let fetchedServers = ServersRepository.servers(using: hostService)
        let (newSettings, newServers) = await (fetchedSettings, fetchedServers)
        settings = newSettings
        if newServers.count > 1 || servers.isEmpty { servers = newServers }
        reconcileActive()
        await refreshListCounts()
    }

    func reportCount(_ destination: Destination, _ count: Int) {
        if listCounts[destination] != count { listCounts[destination] = count }
    }

    private func refreshListCounts() async {
        guard let service else { return }
        async let certs = try? service.certs()
        async let plugins = try? service.plugins()
        if let certs = await certs { reportCount(.certificates, certs.certs.count) }
        if let plugins = await plugins { reportCount(.plugins, plugins.plugins.count) }
    }

    func switchServer(to agentId: String?) async {
        guard agentId != activeAgentId else { return }
        switching = true
        activeAgentId = agentId
        generation += 1
        await refresh()
        switching = false
    }

    /// Falls back to the host when the active agent was removed on the server.
    private func reconcileActive() {
        guard let active = activeAgentId, servers.count > 0 else { return }
        if !servers.contains(where: { $0.agentId == active }) {
            activeAgentId = nil
            generation += 1
        }
    }

    // MARK: - Connection

    func connect(baseURL: URL, apiKey: String?) async {
        connectionStore.save(baseURL: baseURL, apiKey: apiKey)
        activeAgentId = nil
        connection = connectionStore.load()
        generation += 1
        await refresh()
        await WidgetPublisher.publish(connection: connection, minimumGap: 0)
    }

    func enterDemo() async {
        connectionStore.enterDemo()
        activeAgentId = nil
        connection = connectionStore.load()
        generation += 1
        await refresh()
        await WidgetPublisher.publish(connection: connection, minimumGap: 0)
    }

    func disconnect() {
        connectionStore.clear()
        SnapshotStore.clear()
        WidgetPublisher.clear()
        activeAgentId = nil
        connection = nil
        servers = []
        settings = nil
        generation += 1
    }
}

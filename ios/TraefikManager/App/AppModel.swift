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
    private(set) var generation = 0

    private(set) var activeAgentId: String? {
        didSet { UserDefaults.standard.set(activeAgentId, forKey: PreferenceKey.activeAgent) }
    }

    private let connectionStore = ConnectionStore()

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
    }

    func enterDemo() async {
        connectionStore.enterDemo()
        activeAgentId = nil
        connection = connectionStore.load()
        generation += 1
        await refresh()
    }

    func disconnect() {
        connectionStore.clear()
        activeAgentId = nil
        connection = nil
        servers = []
        settings = nil
        generation += 1
    }
}

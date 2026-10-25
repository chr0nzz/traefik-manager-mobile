import Foundation

/// The host plus every agent, as shown in the server switcher.
struct ServerEntry: Identifiable, Hashable, Sendable {
    /// `nil` is the host.
    var agentId: String?
    var name: String
    var url = ""
    var health: AgentHealth?
    var agent: Agent?

    var id: String { agentId ?? "__host__" }
    var isHost: Bool { agentId == nil }
    var reachable: Bool { isHost || health?.ok == true }

    var label: String { isHost ? String(localized: "Host") : name }

    var detail: String {
        if isHost { return "Traefik Manager" }
        guard let health else { return url }
        if health.ok {
            let parts = [health.version.map { "v\($0)" }, health.latencyMs.map { "\($0)ms" }].compactMap { $0 }
            return parts.isEmpty ? url : parts.joined(separator: " · ")
        }
        return health.error ?? String(localized: "unreachable")
    }

    static let host = ServerEntry(agentId: nil, name: "Host")
}

/// Port of `ServersRepository.servers()`: lists agents and probes their health in parallel.
enum ServersRepository {
    static func servers(using service: TMService, probeHealth: Bool = true) async -> [ServerEntry] {
        let hostService = service.scoped(to: nil)
        let agents = (try? await hostService.agents().agents) ?? []
        let entries = agents.map { ServerEntry(agentId: $0.id, name: $0.name.isEmpty ? $0.id : $0.name, url: $0.url, agent: $0) }
        guard probeHealth, !agents.isEmpty else { return [.host] + entries }

        let probed = await withTaskGroup(of: (Int, AgentHealth?).self) { group in
            for (index, agent) in agents.enumerated() {
                group.addTask { (index, try? await hostService.agentHealth(agent.id)) }
            }
            var results = entries
            for await (index, health) in group {
                results[index].health = health
            }
            return results
        }
        return [.host] + probed
    }
}

/// Which tabs the active server shows, port of `ServerCapabilities`.
struct ServerCapabilities: Sendable {
    var isHost = true
    var settings: ServerSettings?
    var agent: Agent?

    func tabVisible(_ tab: String) -> Bool {
        if !isHost { return agent?.tabVisible(tab) ?? true }
        return settings?.tabVisible(tab) ?? true
    }

    func tabEnabled(_ tab: String) -> Bool {
        if !isHost { return agent?.visibleTabs[tab] == true }
        return settings?.visibleTabs[tab] == true
    }

    var crowdsecConfigured: Bool {
        if !isHost { return true }
        return settings?.crowdsecEnabled ?? true
    }
}

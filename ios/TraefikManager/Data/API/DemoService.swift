import Foundation

/// Offline fixtures for "Try Demo", ported from `DemoApi.kt`. Writes succeed without changing anything.
struct DemoService: TMService {
    var agentId: String?

    var isDemo: Bool { true }

    func scoped(to agentId: String?) -> TMService { DemoService(agentId: agentId) }

    private func settle() async {
        try? await Task.sleep(for: .milliseconds(220))
    }

    func apiKeyStatus() async throws -> ApiKeyStatus {
        ApiKeyStatus(
            enabled: true,
            count: 1,
            keys: [ApiKeyEntry(name: "This phone", preview: "abcd1234...ef56", createdAt: "2026-08-01 09:14")]
        )
    }

    func managerVersion() async throws -> ManagerVersion {
        ManagerVersion(version: "1.10.1", repo: "chr0nzz/traefik-manager")
    }

    func traefikVersion() async throws -> TraefikVersion {
        TraefikVersion(version: "3.5.0", codename: "chevrotin")
    }

    func settings() async throws -> ServerSettings {
        ServerSettings(
            domains: ["example.com", "xyzlab.dev"],
            certResolver: "letsencrypt,cloudflare",
            visibleTabs: ["certs": true, "plugins": true, "logs": true, "crowdsec": true],
            crowdsecEnabled: true
        )
    }

    func agents() async throws -> AgentsResponse {
        AgentsResponse(agents: [
            Agent(id: "demo-agent-1", name: "vps-frankfurt", url: "https://agent-fra.example.com:8090"),
            Agent(id: "demo-agent-2", name: "vps-nyc", url: "https://agent-nyc.example.com:8090"),
        ])
    }

    func agentHealth(_ agentId: String) async throws -> AgentHealth {
        await settle()
        return AgentHealth(ok: true, version: "1.10.1", latencyMs: 12)
    }
}

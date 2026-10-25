import Foundation

/// The Traefik Manager endpoints the app uses. `LiveService` hits a real instance, `DemoService` serves fixtures.
/// Grows phase by phase as screens are ported from `TmApi.kt`.
protocol TMService: Sendable {
    var isDemo: Bool { get }
    var agentId: String? { get }

    /// A copy of this service whose agent-scoped calls go to `agentId` (nil = host).
    func scoped(to agentId: String?) -> TMService

    func apiKeyStatus() async throws -> ApiKeyStatus
    func managerVersion() async throws -> ManagerVersion
    func traefikVersion() async throws -> TraefikVersion
    func settings() async throws -> ServerSettings
    func agents() async throws -> AgentsResponse
    func agentHealth(_ agentId: String) async throws -> AgentHealth

    // Traefik runtime (agent-proxied)
    func overview() async throws -> Overview
    func entrypoints() async throws -> [Entrypoint]
    func routers() async throws -> ProtoEnvelope
    func services() async throws -> ProtoEnvelope
    func middlewares() async throws -> ProtoEnvelope
}

struct LiveService: TMService {
    let client: APIClient

    var isDemo: Bool { false }
    var agentId: String? { client.agentId }

    func scoped(to agentId: String?) -> TMService {
        LiveService(client: client.scoped(to: agentId))
    }

    func apiKeyStatus() async throws -> ApiKeyStatus { try await client.get("api/auth/apikey/status") }
    func managerVersion() async throws -> ManagerVersion { try await client.get("api/manager/version") }
    func traefikVersion() async throws -> TraefikVersion { try await client.get("api/traefik/version") }
    func settings() async throws -> ServerSettings { try await client.get("api/settings") }
    func agents() async throws -> AgentsResponse { try await client.get("api/agents") }

    func agentHealth(_ agentId: String) async throws -> AgentHealth {
        try await client.get("api/agents/\(APIClient.segment(agentId))/health")
    }

    func overview() async throws -> Overview { try await client.get("api/traefik/overview") }
    func entrypoints() async throws -> [Entrypoint] { try await client.get("api/traefik/entrypoints") }
    func routers() async throws -> ProtoEnvelope { try await client.get("api/traefik/routers") }
    func services() async throws -> ProtoEnvelope { try await client.get("api/traefik/services") }
    func middlewares() async throws -> ProtoEnvelope { try await client.get("api/traefik/middlewares") }
}

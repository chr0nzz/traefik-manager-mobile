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

    // Routes (hub endpoints; agent routes are fetched explicitly)
    func routes() async throws -> RoutesResponse
    func agentRoutes(_ agentId: String) async throws -> RoutesResponse
    func toggleRoute(_ routeId: String, enable: Bool) async throws -> OkResponse
    func deleteRoute(_ route: Route) async throws -> OkResponse
    func routeRaw(_ routeId: String) async throws -> RawRoute
    func saveRouteRaw(_ routeId: String, content: String) async throws -> OkResponse
    func routeHealth() async throws -> RouteHealthSnapshot
    func ping(url: String, fallback: String?, servers: [String]) async throws -> PingResult
    func saveRoute(_ form: RouteForm) async throws -> OkResponse

    // Form options
    func configs() async throws -> ConfigsResponse
    func tlsOptions() async throws -> [TLSOptionProfile]
    func agentCertResolvers(_ agentId: String) async throws -> CertResolversResponse
    func staticConfig() async throws -> StaticConfigResponse

    // Dashboard launcher
    func dashboardConfig() async throws -> DashboardConfig
    func saveDashboardConfig(_ config: DashboardConfig) async throws -> OkResponse
    func uiPrefs() async throws -> UIPrefsResponse
    func saveUIPrefs(_ prefs: UIPrefs) async throws -> UIPrefsResponse

    // Middlewares
    func saveMiddleware(_ form: MiddlewareForm) async throws -> OkResponse
    func deleteMiddleware(_ name: String, configFile: String, force: Bool) async throws -> OkResponse
    func middlewareTemplates() async throws -> MiddlewareTemplatesResponse
    func createMiddlewareTemplate(name: String, yaml: String) async throws -> OkResponse
    func updateMiddlewareTemplate(id: String, name: String, yaml: String) async throws -> OkResponse
    func deleteMiddlewareTemplate(id: String) async throws -> OkResponse
    func htpasswd(username: String, password: String) async throws -> HtpasswdResponse
    func digestAuth(username: String, password: String, realm: String) async throws -> HtpasswdResponse

    // Services (hub endpoints that take agent_id)
    func serviceEnvelope() async throws -> ServiceEnvelope
    func saveService(_ draft: ServiceDraft) async throws -> ServiceSaveResponse
    func deleteService(_ name: String, force: Bool) async throws -> OkResponse
    func setServiceOwnership(_ name: String, adopt: Bool) async throws -> ServiceOwnershipResponse

    // Logs and GeoIP
    func logs(lines: Int) async throws -> LogsResponse
    func geoStatus() async throws -> GeoStatus
    func geoLookup(ips: [String]) async throws -> GeoLookupResponse

    // CrowdSec (agent-proxied)
    func crowdSecSummary(version: String?, full: Bool) async throws -> CsSummary
    func crowdSecDecisions(full: Bool) async throws -> [CsDecision]
    func crowdSecAlerts() async throws -> [CsAlert]
    func crowdSecSearch(_ query: CsDecisionQuery, page: Int, per: Int) async throws -> CsDecisionPage
    func crowdSecAddDecision(_ request: AddDecisionRequest) async throws -> OkResponse
    func crowdSecDeleteDecision(_ id: Int64) async throws -> OkResponse

    // Certificates
    func certs() async throws -> CertsResponse
    func certUsage(exclude: [String]) async throws -> CertUsage
    func certManage() async throws -> CertManageState
    func deleteCerts(_ certs: [CertRef]) async throws -> CertDeleteResponse

    // Plugins and static config
    func plugins() async throws -> PluginsResponse
    func pluginCatalog() async throws -> PluginCatalog
    func staticManageable() async -> Bool
    func saveStaticConfig(_ content: String) async throws
    func staticSection(_ request: StaticSectionRequest) async throws -> StaticSectionResponse
    func installPlugin(staticYaml: String, middlewareYaml: String, middlewareFile: String) async -> PluginInstallResponse
    func restartTraefik() async throws
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

    func routes() async throws -> RoutesResponse { try await client.get("api/routes") }

    func agentRoutes(_ agentId: String) async throws -> RoutesResponse {
        try await client.get("api/agents/\(APIClient.segment(agentId))/routes")
    }

    func toggleRoute(_ routeId: String, enable: Bool) async throws -> OkResponse {
        try await client.post(
            "api/routes/\(APIClient.segment(routeId))/toggle",
            body: ToggleRequest(enable: enable, agentId: agentId ?? "")
        )
    }

    func deleteRoute(_ route: Route) async throws -> OkResponse {
        try await client.postForm(
            "delete/\(APIClient.segment(route.id))",
            fields: [("configFile", route.configFile), ("agent_id", agentId ?? "")]
        )
    }

    func routeRaw(_ routeId: String) async throws -> RawRoute {
        try await client.get("api/routes/\(APIClient.segment(routeId))/raw")
    }

    func saveRouteRaw(_ routeId: String, content: String) async throws -> OkResponse {
        try await client.post("api/routes/\(APIClient.segment(routeId))/raw", body: RawRouteSave(content: content))
    }

    func routeHealth() async throws -> RouteHealthSnapshot {
        try await client.get("api/routes/health", query: agentId.map { [URLQueryItem(name: "agent_id", value: $0)] } ?? [])
    }

    func ping(url: String, fallback: String?, servers: [String]) async throws -> PingResult {
        var query = [URLQueryItem(name: "url", value: url)]
        if let fallback { query.append(URLQueryItem(name: "fallback", value: fallback)) }
        query += servers.map { URLQueryItem(name: "servers", value: $0) }
        return try await client.get("api/ping", query: query)
    }

    func saveRoute(_ form: RouteForm) async throws -> OkResponse {
        try await client.postForm("save", fields: RouteFormEncoder.fields(form, agentId: agentId))
    }

    func configs() async throws -> ConfigsResponse { try await client.get("api/configs") }

    func tlsOptions() async throws -> [TLSOptionProfile] {
        try await client.get("api/tls-options", query: agentId.map { [URLQueryItem(name: "server", value: $0)] } ?? [])
    }

    func agentCertResolvers(_ agentId: String) async throws -> CertResolversResponse {
        try await client.get("api/agents/\(APIClient.segment(agentId))/cert-resolvers")
    }


    private var serverQuery: [URLQueryItem] { agentId.map { [URLQueryItem(name: "server", value: $0)] } ?? [] }

    func dashboardConfig() async throws -> DashboardConfig {
        try await client.get("api/dashboard/config", query: serverQuery)
    }

    func saveDashboardConfig(_ config: DashboardConfig) async throws -> OkResponse {
        var body = config
        body.server = agentId ?? ""
        return try await client.post("api/dashboard/config", query: serverQuery, body: body)
    }

    func uiPrefs() async throws -> UIPrefsResponse { try await client.get("api/settings/ui") }

    func saveUIPrefs(_ prefs: UIPrefs) async throws -> UIPrefsResponse {
        try await client.post("api/settings/ui", body: UIPrefsRequest(uiPrefs: prefs))
    }

    func saveMiddleware(_ form: MiddlewareForm) async throws -> OkResponse {
        try await client.postForm("save-middleware", fields: form.fields(agentId: agentId))
    }

    func deleteMiddleware(_ name: String, configFile: String, force: Bool) async throws -> OkResponse {
        var fields = [("configFile", configFile), ("agent_id", agentId ?? "")]
        if force { fields.append(("force", "true")) }
        return try await client.postForm("delete-middleware/\(APIClient.segment(name))", fields: fields)
    }

    func middlewareTemplates() async throws -> MiddlewareTemplatesResponse { try await client.get("api/mw/templates") }

    func createMiddlewareTemplate(name: String, yaml: String) async throws -> OkResponse {
        try await client.post("api/mw/templates", body: TemplateBody(name: name, yaml: yaml))
    }

    func updateMiddlewareTemplate(id: String, name: String, yaml: String) async throws -> OkResponse {
        try await client.put("api/mw/templates/\(APIClient.segment(id))", body: TemplateBody(name: name, yaml: yaml))
    }

    func deleteMiddlewareTemplate(id: String) async throws -> OkResponse {
        try await client.delete("api/mw/templates/\(APIClient.segment(id))")
    }

    func htpasswd(username: String, password: String) async throws -> HtpasswdResponse {
        try await client.post("api/tools/htpasswd", body: HtpasswdRequest(username: username, password: password))
    }

    func digestAuth(username: String, password: String, realm: String) async throws -> HtpasswdResponse {
        try await client.post("api/tools/digestauth", body: DigestRequest(username: username, password: password, realm: realm))
    }

    private var agentQuery: [URLQueryItem] { agentId.map { [URLQueryItem(name: "agent_id", value: $0)] } ?? [] }

    func serviceEnvelope() async throws -> ServiceEnvelope {
        try await client.get("api/traefik/services", query: agentQuery)
    }

    func saveService(_ draft: ServiceDraft) async throws -> ServiceSaveResponse {
        try await client.post("api/services", body: draft.payload(agentId: agentId))
    }

    func deleteService(_ name: String, force: Bool) async throws -> OkResponse {
        try await client.delete(
            "api/services/\(APIClient.segment(name))",
            query: agentQuery + (force ? [URLQueryItem(name: "force", value: "1")] : [])
        )
    }

    func setServiceOwnership(_ name: String, adopt: Bool) async throws -> ServiceOwnershipResponse {
        try await client.post(
            "api/services/\(APIClient.segment(name))/ownership",
            query: agentQuery,
            body: ServiceOwnershipRequest(adopt: adopt)
        )
    }

    func logs(lines: Int) async throws -> LogsResponse {
        try await client.get("api/traefik/logs", query: [URLQueryItem(name: "lines", value: String(lines))])
    }

    func geoStatus() async throws -> GeoStatus { try await client.get("api/geoip/status") }

    func geoLookup(ips: [String]) async throws -> GeoLookupResponse {
        try await client.post("api/geoip/lookup", body: GeoLookupRequest(ips: ips, aggregate: true))
    }

    func crowdSecSummary(version: String?, full: Bool) async throws -> CsSummary {
        var query: [URLQueryItem] = []
        if let version { query.append(URLQueryItem(name: "version", value: version)) }
        if full { query.append(URLQueryItem(name: "full", value: "1")) }
        return try await client.get("api/crowdsec/summary", query: query)
    }

    func crowdSecDecisions(full: Bool) async throws -> [CsDecision] {
        try await client.get("api/crowdsec/decisions", query: full ? [URLQueryItem(name: "full", value: "1")] : [])
    }

    func crowdSecAlerts() async throws -> [CsAlert] { try await client.get("api/crowdsec/alerts") }

    func crowdSecSearch(_ query: CsDecisionQuery, page: Int, per: Int) async throws -> CsDecisionPage {
        var items = [URLQueryItem(name: "page", value: String(page)), URLQueryItem(name: "per", value: String(per))]
        for (name, value) in [("q", query.q), ("origin", query.origin), ("type", query.type), ("ip", query.ip), ("scenario", query.scenario)] {
            if let value { items.append(URLQueryItem(name: name, value: value)) }
        }
        return try await client.get("api/crowdsec/decisions/search", query: items)
    }

    func crowdSecAddDecision(_ request: AddDecisionRequest) async throws -> OkResponse {
        try await client.post("api/crowdsec/decisions", body: request)
    }

    func crowdSecDeleteDecision(_ id: Int64) async throws -> OkResponse {
        try await client.delete("api/crowdsec/decisions/\(id)")
    }

    func certs() async throws -> CertsResponse { try await client.get("api/traefik/certs") }

    func certUsage(exclude: [String]) async throws -> CertUsage {
        try await client.get("api/certs/usage", query: serverQuery + exclude.map { URLQueryItem(name: "exclude", value: $0) })
    }

    func certManage() async throws -> CertManageState { try await client.get("api/certs/manage", query: serverQuery) }

    /// 502/504 mean the connection dropped while Traefik restarted; a body with `ok:false` still decodes on error codes.
    func deleteCerts(_ certs: [CertRef]) async throws -> CertDeleteResponse {
        do {
            return try await client.post("api/certs/delete", body: CertDeleteRequest(server: agentId ?? "", certs: certs))
        } catch APIError.http(502, _), APIError.http(504, _), APIError.unreachable {
            throw APIError.server(String(localized: "Lost the connection while Traefik restarted. Pull to refresh to check."))
        }
    }

    // MARK: Plugins and static config

    /// The hub itself, for endpoints that take `server` instead of going through the agent proxy.
    private var hub: APIClient { client.scoped(to: nil) }

    func plugins() async throws -> PluginsResponse { try await client.get("api/traefik/plugins") }

    func pluginCatalog() async throws -> PluginCatalog { try await hub.get("api/plugins/catalog") }

    func staticManageable() async -> Bool {
        if agentId == nil {
            let available: StaticAvailable? = try? await hub.get("api/static/available")
            return available?.available ?? false
        }
        let status: AgentStaticStatus? = try? await client.get("api/static/status")
        return status?.configured ?? false
    }

    func staticConfig() async throws -> StaticConfigResponse { try await hub.get("api/static/config", query: serverQuery) }

    func saveStaticConfig(_ content: String) async throws {
        let response: OkResponse = agentId == nil
            ? try await hub.post("api/static/config", body: StaticSaveRequest(content: content))
            : try await client.post("api/static", body: StaticSaveRequest(content: content))
        if !response.worked, let error = response.error ?? response.message { throw APIError.server(error) }
    }

    func staticSection(_ request: StaticSectionRequest) async throws -> StaticSectionResponse {
        try await hub.post("api/static/section", body: request)
    }

    func installPlugin(staticYaml: String, middlewareYaml: String, middlewareFile: String) async -> PluginInstallResponse {
        let request = PluginInstallRequest(staticYaml: staticYaml, middlewareYaml: middlewareYaml, middlewareFile: middlewareFile, server: agentId ?? "")
        do {
            return try await hub.post("api/plugins/install", body: request)
        } catch {
            return PluginInstallResponse(ok: false, error: error.localizedDescription)
        }
    }

    /// 502/504 are expected while Traefik goes down for the restart.
    func restartTraefik() async throws {
        do {
            let _: OkResponse = try await client.post("api/static/restart")
        } catch APIError.http(502, _), APIError.http(504, _) {
        }
    }
}

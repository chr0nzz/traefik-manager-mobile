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

    // MARK: - Traefik runtime

    func overview() async throws -> Overview {
        await settle()
        return Overview(
            http: OverviewSection(routers: OverviewCounts(8, 1, 1), services: OverviewCounts(8, 0, 1), middlewares: OverviewCounts(5, 0, 0)),
            tcp: OverviewSection(routers: OverviewCounts(2, 0, 0), services: OverviewCounts(2, 0, 0)),
            udp: OverviewSection(routers: OverviewCounts(1, 0, 0), services: OverviewCounts(1, 0, 0)),
            providers: ["File", "Docker", "Internal"]
        )
    }

    func entrypoints() async throws -> [Entrypoint] {
        await settle()
        return [
            Entrypoint(name: "web", address: ":80"),
            Entrypoint(
                name: "websecure",
                address: ":443",
                asDefault: true,
                http: EntrypointHTTP(middlewares: ["crowdsec@file"], tls: EntrypointTLS(certResolver: "letsencrypt"))
            ),
            Entrypoint(name: "wireguard", address: ":51820/udp"),
        ]
    }

    func routers() async throws -> ProtoEnvelope {
        await settle()
        return ProtoEnvelope(
            http: Self.routes.filter { $0.proto == "http" }.map { route in
                var router = TraefikObject("\(route.name)@file", "file", route.name == "broken-app" ? "disabled" : "enabled", using: [route.entryPoint])
                router.rule = route.host.isEmpty ? nil : "Host(`\(route.host)`)"
                router.service = route.name
                router.entryPoints = [route.entryPoint]
                return router
            },
            tcp: [
                TraefikObject("postgres@file", "file", "enabled", using: ["postgres"]),
                TraefikObject("redis@file", "file", "enabled", using: ["redis"]),
            ],
            udp: [TraefikObject("wireguard@file", "file", "enabled", using: ["wireguard"])]
        )
    }

    func services() async throws -> ProtoEnvelope {
        await settle()
        let http = Self.routes.filter { $0.proto == "http" }.map { route in
            TraefikObject(
                "\(route.name)@file",
                "file",
                route.name == "broken-app" ? "disabled" : "enabled",
                usedBy: ["\(route.name)@file"],
                serverStatus: route.name == "media"
                    ? ["http://10.0.0.9:8096": "UP", "http://10.0.0.10:8096": "DOWN"]
                    : [route.target: "UP"],
                type: "loadbalancer"
            )
        } + [TraefikObject("media-pool@file", "file", "enabled", usedBy: ["media@file"], type: "weighted")]
        return ProtoEnvelope(
            http: http,
            tcp: [TraefikObject("postgres@file", "file", "enabled", usedBy: ["postgres@file"], serverStatus: ["10.0.0.20:5432": "UP"], type: "loadbalancer")]
        )
    }

    func middlewares() async throws -> ProtoEnvelope {
        await settle()
        return ProtoEnvelope(http: [
            TraefikObject("crowdsec@file", "file", "enabled", usedBy: ["dashboard@file"], type: "plugin"),
            TraefikObject("secure-headers@file", "file", "enabled", usedBy: ["media@file"], type: "headers"),
            TraefikObject("basic-auth@file", "file", "enabled", type: "basicauth"),
            TraefikObject("rate-limit@file", "file", "enabled", usedBy: ["api@file"], type: "ratelimit"),
            TraefikObject("redirect-https@file", "file", "enabled", usedBy: ["blog@file"], type: "redirectscheme"),
        ])
    }

    // MARK: - Routes

    func routes() async throws -> RoutesResponse {
        await settle()
        let disabled = DemoState.shared.disabledRoutes
        let apps = Self.routes.map { demo in
            var route = demo.route
            route.enabled = !disabled.contains(route.id)
            return route
        }
        return RoutesResponse(apps: apps, middlewares: Self.middlewares)
    }

    func agentRoutes(_ agentId: String) async throws -> RoutesResponse { try await routes() }

    func toggleRoute(_ routeId: String, enable: Bool) async throws -> OkResponse {
        await settle()
        DemoState.shared.setDisabled(routeId, !enable)
        return OkResponse(ok: true)
    }

    func deleteRoute(_ route: Route) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func routeRaw(_ routeId: String) async throws -> RawRoute {
        RawRoute(
            raw: "http:\n  routers:\n    demo:\n      rule: Host(`demo.example.com`)\n      service: demo\n",
            configFile: "routes.yml",
            proto: "http"
        )
    }

    func saveRouteRaw(_ routeId: String, content: String) async throws -> OkResponse { OkResponse(ok: true) }

    func routeHealth() async throws -> RouteHealthSnapshot {
        await settle()
        var media = RouteHealth(state: RouteHealth.up)
        media.source = "ping"
        media.latencyMs = 42
        return RouteHealthSnapshot(routes: ["routes.yml::media": media])
    }

    func ping(url: String, fallback: String?, servers: [String]) async throws -> PingResult {
        await settle()
        return PingResult(ok: true, latencyMs: 24, statusCode: 200)
    }

    func saveRoute(_ form: RouteForm) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func configs() async throws -> ConfigsResponse {
        ConfigsResponse(files: [ConfigFile(label: "routes.yml", path: "routes.yml")], configDirSet: true)
    }

    func tlsOptions() async throws -> [TLSOptionProfile] { [] }

    func agentCertResolvers(_ agentId: String) async throws -> CertResolversResponse {
        try JSONDecoder().decode(CertResolversResponse.self, from: Data(#"{"resolvers":["letsencrypt"]}"#.utf8))
    }

    func staticConfig() async throws -> StaticConfigResponse {
        StaticConfigResponse(raw: "", path: "/app/traefik.yml")
    }

    func dashboardConfig() async throws -> DashboardConfig { DashboardConfig() }

    func saveDashboardConfig(_ config: DashboardConfig) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func uiPrefs() async throws -> UIPrefsResponse {
        var prefs = UIPrefs()
        prefs.showRouteIcons = true
        return UIPrefsResponse(uiPrefs: prefs)
    }

    func saveUIPrefs(_ prefs: UIPrefs) async throws -> UIPrefsResponse {
        await settle()
        return UIPrefsResponse(uiPrefs: prefs)
    }

    // MARK: - Middlewares

    func saveMiddleware(_ form: MiddlewareForm) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func deleteMiddleware(_ name: String, configFile: String, force: Bool) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func middlewareTemplates() async throws -> MiddlewareTemplatesResponse {
        MiddlewareTemplatesResponse(templates: [
            MiddlewareTemplate(id: "1", name: "Office only", yaml: "ipAllowList:\n  sourceRange:\n    - \"203.0.113.0/24\""),
        ])
    }

    func createMiddlewareTemplate(name: String, yaml: String) async throws -> OkResponse { OkResponse(ok: true) }
    func updateMiddlewareTemplate(id: String, name: String, yaml: String) async throws -> OkResponse { OkResponse(ok: true) }
    func deleteMiddlewareTemplate(id: String) async throws -> OkResponse { OkResponse(ok: true) }

    func htpasswd(username: String, password: String) async throws -> HtpasswdResponse {
        await settle()
        return HtpasswdResponse(ok: true, entry: "\(username):$apr1$demo$notarealhash000000000")
    }

    func digestAuth(username: String, password: String, realm: String) async throws -> HtpasswdResponse {
        await settle()
        return HtpasswdResponse(ok: true, entry: "\(username):\(realm):0000000000000000demo00000000000")
    }

    // MARK: - Services

    func serviceEnvelope() async throws -> ServiceEnvelope {
        await settle()
        func service(_ json: [String: Any]) -> TraefikService {
            try! JSONDecoder().decode(TraefikService.self, from: JSONSerialization.data(withJSONObject: json))
        }
        var envelope = ServiceEnvelope()
        envelope.http = Self.routes.filter { $0.proto == "http" }.map { route in
            let servers = route.name == "media" ? ["http://10.0.0.9:8096", "http://10.0.0.10:8096"] : [route.target]
            var json: [String: Any] = [
                "name": "\(route.name)@file", "provider": "file", "type": "loadbalancer",
                "status": route.name == "broken-app" ? "disabled" : "enabled",
                "usedBy": ["\(route.name)@file"],
                "serverStatus": route.name == "media"
                    ? ["http://10.0.0.9:8096": "UP", "http://10.0.0.10:8096": "DOWN"]
                    : [route.target: "UP"],
                "loadBalancer": ["servers": servers.map { ["url": $0] }, "passHostHeader": true],
            ]
            if route.name == "media" { json["loadBalancer"] = ["servers": servers.map { ["url": $0] }, "passHostHeader": true, "healthCheck": ["path": "/health", "interval": "30s"]] }
            return service(json)
        } + [service([
            "name": "media-pool@file", "provider": "file", "status": "enabled", "type": "weighted",
            "usedBy": ["media@file"],
            "weighted": ["services": [["name": "media@file", "weight": 3], ["name": "media-backup@file", "weight": 1]]],
        ])]
        envelope.tcp = [service([
            "name": "postgres@file", "provider": "file", "status": "enabled", "type": "loadbalancer",
            "usedBy": ["postgres@file"], "serverStatus": ["10.0.0.20:5432": "UP"],
            "loadBalancer": ["servers": [["address": "10.0.0.20:5432"]]],
        ])]
        envelope.ownedServices = ["media-pool"]
        return envelope
    }

    func saveService(_ draft: ServiceDraft) async throws -> ServiceSaveResponse {
        await settle()
        return ServiceSaveResponse(ok: true, name: draft.name)
    }

    func deleteService(_ name: String, force: Bool) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func setServiceOwnership(_ name: String, adopt: Bool) async throws -> ServiceOwnershipResponse {
        await settle()
        return ServiceOwnershipResponse(ok: true, owned: adopt)
    }

    // MARK: - Logs

    func logs(lines: Int) async throws -> LogsResponse {
        await settle()
        let sample: [(String, String, Int)] = [
            ("GET", "/", 200), ("GET", "/api/routes", 200), ("POST", "/api/crowdsec/decisions", 201),
            ("GET", "/.env", 404), ("GET", "/favicon.ico", 404), ("HEAD", "/", 403), ("GET", "/api/health", 200),
            ("GET", "/media/stream/12345", 206), ("GET", "/wp-admin/setup-config.php", 404),
            ("GET", "/api/agents/proxy/1/traefik/overview", 502),
        ]
        let ips = ["62.210.142.174", "18.218.118.203", "212.87.212.246", "100.87.28.37", "45.148.10.125"]
        let services = ["tma@docker", "dash-service@file", "gk-auth-service@file"]
        let durations: [Int64] = [421_000, 1_200_000, 2_400_000, 96_000_000, 3_570_000_000]
        let formatter = ISO8601DateFormatter()
        let now = Date()
        let generated = (0..<min(lines, 120)).map { index -> String in
            let (method, path, status) = sample[index % sample.count]
            let stamp = formatter.string(from: now.addingTimeInterval(-Double(120 - index) * 37))
            return #"{"ClientHost":"\#(ips[index % ips.count])","RequestMethod":"\#(method)","RequestPath":"\#(path)","DownstreamStatus":\#(status),"OriginStatus":\#(status == 404 ? 0 : status),"Duration":\#(durations[index % 5]),"DownstreamContentSize":\#(status * 7),"RequestHost":"kwa.xyzlab.app","RequestScheme":"https","entryPointName":"websecure","RouterName":"tma@docker","ServiceName":"\#(services[index % services.count])","ServiceURL":"http://172.18.0.3:8090","TLSVersion":"1.3","RetryAttempts":0,"StartUTC":"\#(stamp)"}"#
        }
        return LogsResponse(lines: generated)
    }

    func geoStatus() async throws -> GeoStatus { GeoStatus(enabled: true, available: true) }

    func geoLookup(ips: [String]) async throws -> GeoLookupResponse {
        let codes = ["US", "DE", "FR", "NL", "RO"]
        return GeoLookupResponse(codes: Dictionary(uniqueKeysWithValues: ips.map { ip in
            (ip, codes[(Int(ip.components(separatedBy: ".")[0]) ?? 0) % 5])
        }))
    }

    // MARK: - Fixtures

    struct DemoRoute {
        var name: String
        var host: String
        var target: String
        var proto = "http"
        var entryPoint = "websecure"
        var middlewares: [String] = []

        var route: Route {
            var route = Route(id: "routes.yml::\(name)", name: name)
            let tls = proto == "http"
            route.rule = proto == "udp" ? "" : "Host(`\(host)`)"
            route.serviceName = name
            route.target = target
            route.servers = name == "media" ? ["http://10.0.0.9:8096", "http://10.0.0.10:8096"] : [target]
            route.middlewares = .array(middlewares.map(JSONValue.string))
            route.entryPoints = .array([.string(entryPoint)])
            route.protocol = proto
            route.tls = .bool(tls)
            route.certResolver = tls ? "letsencrypt" : ""
            route.configFile = "routes.yml"
            return route
        }
    }

    static let middlewares: [MiddlewareDef] = [
        demoMiddleware("crowdsec", "plugin:\n  crowdsec-bouncer:\n    enabled: true\n"),
        demoMiddleware("secure-headers", "headers:\n  frameDeny: true\n  stsSeconds: 31536000\n"),
        demoMiddleware("basic-auth", "basicAuth:\n  users:\n    - admin:$apr1$demo\n"),
    ]

    private static func demoMiddleware(_ name: String, _ yaml: String) -> MiddlewareDef {
        let json = try! JSONSerialization.data(withJSONObject: ["name": name, "type": "http", "yaml": yaml, "configFile": "routes.yml"])
        return try! JSONDecoder().decode(MiddlewareDef.self, from: json)
    }

    static let routes: [DemoRoute] = [
        DemoRoute(name: "dashboard", host: "dash.example.com", target: "http://10.0.0.4:3000", middlewares: ["crowdsec"]),
        DemoRoute(name: "media", host: "media.example.com", target: "http://10.0.0.9:8096", middlewares: ["secure-headers"]),
        DemoRoute(name: "api", host: "api.example.com", target: "http://10.0.0.12:8000", middlewares: ["rate-limit"]),
        DemoRoute(name: "blog", host: "blog.example.com", target: "http://10.0.0.14:2368", middlewares: ["redirect-https"]),
        DemoRoute(name: "git", host: "git.example.com", target: "http://10.0.0.16:3000"),
        DemoRoute(name: "photos", host: "photos.example.com", target: "http://10.0.0.18:2283"),
        DemoRoute(name: "broken-app", host: "broken.example.com", target: "N/A"),
        DemoRoute(name: "old-blog", host: "old.example.com", target: "http://10.0.0.20:8080"),
        DemoRoute(name: "postgres", host: "", target: "10.0.0.30:5432", proto: "tcp", entryPoint: "postgres"),
        DemoRoute(name: "redis", host: "", target: "10.0.0.31:6379", proto: "tcp", entryPoint: "redis"),
        DemoRoute(name: "wireguard", host: "", target: "10.0.0.32:51820", proto: "udp", entryPoint: "wireguard"),
    ]
}

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
            visibleTabs: ["certs": true, "plugins": true, "logs": true, "crowdsec": true, "docker": true, "internal": true],
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
        ) .withExtra(http: Self.dockerRouters)
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

    // MARK: - CrowdSec

    func crowdSecSummary(version: String?, full: Bool) async throws -> CsSummary {
        await settle()
        let current = "demo-crowdsec-1"
        var summary = CsSummary()
        summary.version = current
        if version == current {
            summary.unchanged = true
            return summary
        }
        let decisions = DemoCrowdSec.decisions
        let banned = Set(decisions.filter { $0.scope == "Ip" || $0.scope == "Range" }.map(\.value))
        let own = decisions.filter(\.own)
        var counts = CsDecisionsSummary()
        counts.ok = true
        counts.total = decisions.count
        counts.own = own.count
        counts.subscribed = decisions.count - own.count
        counts.origins = Dictionary(grouping: decisions, by: \.originKey).mapValues(\.count)
        counts.types = Dictionary(grouping: decisions) { $0.type.lowercased() }.mapValues(\.count)
        counts.rows = own.filter { $0.originKey != "crowdsec" }.sorted { $0.id > $1.id }
        var alerts = CsAlertsSummary()
        alerts.ok = true
        alerts.status = 200
        alerts.limit = 500
        alerts.rows = DemoCrowdSec.alerts().map { alert in
            var copy = alert
            copy.handled = banned.contains(alert.ip)
            return copy
        }
        summary.decisions = counts
        summary.alerts = alerts
        return summary
    }

    func crowdSecDecisions(full: Bool) async throws -> [CsDecision] { DemoCrowdSec.decisions }

    func crowdSecAlerts() async throws -> [CsAlert] { DemoCrowdSec.alerts() }

    func crowdSecSearch(_ query: CsDecisionQuery, page: Int, per: Int) async throws -> CsDecisionPage {
        await settle()
        let needle = query.q ?? ""
        let rows = DemoCrowdSec.decisions.filter { decision in
            let originOK: Bool = switch query.origin ?? "" {
            case "": true
            case "subscribed": !decision.own
            case "own": decision.own
            case let origin: decision.originKey == origin
            }
            let hay = [decision.value, decision.scenario, decision.originKey, decision.scope, decision.type].joined(separator: " ").lowercased()
            return originOK
                && (query.type == nil || decision.type.lowercased() == query.type!.lowercased())
                && (query.ip == nil || decision.value == query.ip)
                && (query.scenario == nil || decision.scenario == query.scenario)
                && (needle.isEmpty || hay.contains(needle))
        }
        .sorted { $0.own != $1.own ? $0.own : $0.id > $1.id }
        let size = min(max(per, 1), 200)
        let pages = max(1, (rows.count + size - 1) / size)
        let current = min(max(page, 1), pages)
        return CsDecisionPage(rows: Array(rows.dropFirst((current - 1) * size).prefix(size)), total: rows.count, page: current, pages: pages)
    }

    func crowdSecAddDecision(_ request: AddDecisionRequest) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func crowdSecDeleteDecision(_ id: Int64) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    // MARK: - Certificates

    func certs() async throws -> CertsResponse {
        await settle()
        let soon = { (days: Double) in ISO8601DateFormatter().string(from: Date().addingTimeInterval(days * 86_400)) }
        return CertsResponse(certs: [
            CertEntry(resolver: "letsencrypt", main: "example.com", sans: ["example.com", "www.example.com", "api.example.com", "blog.example.com"], notAfter: soon(64), source: "acme.json"),
            CertEntry(resolver: "letsencrypt", main: "media.example.com", notAfter: soon(21), source: "acme.json"),
            CertEntry(resolver: "cloudflare", main: "vpn.example.com", notAfter: soon(3), source: "cloudflare.json"),
            CertEntry(resolver: "file", main: "chain.pem", certFile: "/etc/traefik/certs/chain.pem"),
        ])
    }

    func certUsage(exclude: [String]) async throws -> CertUsage { CertUsage(unusedKnown: true) }

    func certManage() async throws -> CertManageState { CertManageState(reason: "Demo mode does not change certificates") }

    func deleteCerts(_ certs: [CertRef]) async throws -> CertDeleteResponse {
        await settle()
        return CertDeleteResponse(ok: true, removed: certs.count, restarted: true)
    }

    func plugins() async throws -> PluginsResponse {
        await settle()
        return PluginsResponse(plugins: [
            PluginEntry(
                name: "crowdsec-bouncer",
                moduleName: "github.com/maxlerebourg/crowdsec-bouncer-traefik-plugin",
                version: "v1.3.5",
                settings: .object(["enabled": .bool(true), "crowdsecMode": .string("stream"), "updateIntervalSeconds": .number(60)])
            ),
            PluginEntry(name: "geoblock", moduleName: "plugins-local/geoblock"),
        ])
    }

    func pluginCatalog() async throws -> PluginCatalog {
        PluginCatalog(plugins: ["github.com/maxlerebourg/crowdsec-bouncer-traefik-plugin": "v1.4.6"])
    }

    func staticManageable() async -> Bool { true }

    func staticConfig() async throws -> StaticConfigResponse {
        await settle()
        return StaticConfigResponse(path: agentId == nil ? "/app/traefik.yml" : "/etc/traefik/traefik.yml", raw: Self.demoStatic, parsed: Self.demoStaticParsed)
    }

    func saveStaticConfig(_ content: String) async throws { await settle() }

    func staticSection(_ request: StaticSectionRequest) async throws -> StaticSectionResponse {
        await settle()
        return StaticSectionResponse(ok: true, raw: request.currentRaw ?? Self.demoStatic, parsed: Self.demoStaticParsed)
    }

    func installPlugin(staticYaml: String, middlewareYaml: String, middlewareFile: String) async -> PluginInstallResponse {
        await settle()
        return PluginInstallResponse(ok: true, plugins: ["demoPlugin"])
    }

    func restartTraefik() async throws { await settle() }

    static let demoStatic = """
    api:
      dashboard: true

    entryPoints:
      web:
        address: ":80"
        http:
          redirections:
            entryPoint:
              to: websecure
      websecure:
        address: ":443"
        http:
          tls:
            certResolver: letsencrypt

    certificatesResolvers:
      letsencrypt:
        acme:
          email: admin@example.com
          storage: /acme.json
          dnsChallenge:
            provider: cloudflare

    providers:
      file:
        directory: /app/config/dynamic
        watch: true

    accessLog:
      filePath: /var/log/traefik/access.log
      format: json

    experimental:
      plugins:
        crowdsec-bouncer:
          moduleName: github.com/maxlerebourg/crowdsec-bouncer-traefik-plugin
          version: v1.3.5
      localPlugins:
        geoblock:
          moduleName: plugins-local/geoblock
    """

    static let demoStaticParsed: JSONObject = [
        "api": .object(["dashboard": .bool(true)]),
        "entryPoints": .object([
            "web": .object([
                "address": .string(":80"),
                "http": .object(["redirections": .object(["entryPoint": .object(["to": .string("websecure")])])]),
            ]),
            "websecure": .object([
                "address": .string(":443"),
                "http": .object(["tls": .object(["certResolver": .string("letsencrypt")])]),
            ]),
        ]),
        "certificatesResolvers": .object([
            "letsencrypt": .object(["acme": .object([
                "email": .string("admin@example.com"),
                "storage": .string("/acme.json"),
                "dnsChallenge": .object(["provider": .string("cloudflare")]),
            ])]),
        ]),
        "providers": .object(["file": .object(["directory": .string("/app/config/dynamic"), "watch": .bool(true)])]),
        "accessLog": .object(["filePath": .string("/var/log/traefik/access.log"), "format": .string("json")]),
        "experimental": .object([
            "plugins": .object(["crowdsec-bouncer": .object([
                "moduleName": .string("github.com/maxlerebourg/crowdsec-bouncer-traefik-plugin"),
                "version": .string("v1.3.5"),
            ])]),
            "localPlugins": .object(["geoblock": .object(["moduleName": .string("plugins-local/geoblock")])]),
        ]),
    ]

    func backups() async throws -> BackupsList {
        await settle()
        if agentId != nil {
            return BackupsList(entries: [BackupEntry(name: "dynamic.yml.20260812_143001.bak", size: 2048, stamp: "2026-08-12T14:30:01Z", kind: .routes)], staticConfigured: true)
        }
        return BackupsList(entries: [
            BackupEntry(name: "dynamic.yml.20260812_143001.bak", size: 2048, stamp: "2026-08-12 14:30:01", kind: .routes),
            BackupEntry(name: "traefik.yml.20260812_090012.bak", size: 1104, stamp: "2026-08-12 09:00:12", kind: .static),
            BackupEntry(name: "acme.json.20260812_080000.bak", size: 5120, stamp: "2026-08-12 08:00:00", kind: .certs),
            BackupEntry(name: "dynamic.yml.20260811_221500.bak", size: 1990, stamp: "2026-08-11 22:15:00", kind: .routes),
        ])
    }

    func createBackup() async throws -> CreateBackupResponse {
        await settle()
        return CreateBackupResponse(ok: true, names: ["dynamic.yml.20260812_150000.bak"])
    }

    func createStaticBackup() async throws -> CreateBackupResponse {
        await settle()
        return CreateBackupResponse(ok: true, names: ["traefik.yml.20260812_150000.bak"])
    }

    func deleteBackup(_ name: String) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func restoreBackup(_ name: String) async throws -> RestoreResponse {
        await settle()
        return RestoreResponse(ok: true, restarted: true)
    }

    func gitStatus(hostBacked: Bool) async throws -> GitStatus {
        await settle()
        return GitStatus(enabled: true, configured: true, lastSha: "a1b2c3d4", lastPush: "2026-08-12 05:15:00 +0000", branch: "main")
    }

    func gitCommits(hostBacked: Bool) async throws -> [GitCommit] {
        [
            GitCommit(sha: "a1b2c3d4e5f6", shaShort: "a1b2c3d4", timestamp: "2026-08-12 05:15:00 +0000", message: "Config backup 2026-08-12 05:15"),
            GitCommit(sha: "99887766aabb", shaShort: "99887766", timestamp: "2026-08-11 05:15:00 +0000", message: "Config backup 2026-08-11 05:15"),
        ]
    }

    func gitDiff(_ sha: String, hostBacked: Bool) async throws -> GitDiff {
        await settle()
        return GitDiff(
            stat: " config/dynamic.yml | 4 ++--\n 1 file changed, 2 insertions(+), 2 deletions(-)",
            files: [GitDiffFile(
                filename: "config/dynamic.yml",
                status: "M",
                old: "http:\n  routers:\n    blog:\n      rule: Host(`blog.example.com`)\n      service: blog\n",
                new: "http:\n  routers:\n    blog:\n      rule: Host(`blog.example.dev`)\n      service: blog\n"
            )]
        )
    }

    func gitPush(_ message: String, hostBacked: Bool) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func gitRestore(_ sha: String, hostBacked: Bool) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func settingsRaw() async throws -> JSONObject {
        [
            "domains": .array([.string("example.com"), .string("xyzlab.dev")]),
            "cert_resolver": .string("letsencrypt,cloudflare"),
            "traefik_api_url": .string("http://traefik:8080"),
            "traefik_api_user": .string("admin"),
            "traefik_api_password_set": .bool(true),
            "acme_json_path": .string("/app/acme.json"),
            "access_log_path": .string("/app/logs/access.log"),
            "static_config_path": .string("/app/traefik.yml"),
            "crowdsec_enabled": .bool(true),
            "auth_enabled": .bool(true),
            "has_password": .bool(true),
            "otp_enabled": .bool(true),
        ]
    }

    func saveSettings(_ body: JSONObject) async throws -> SaveSettingsResponse {
        await settle()
        return SaveSettingsResponse(success: true)
    }

    func testConnection(url: String, user: String, password: String) async throws -> TestConnectionResult {
        await settle()
        return TestConnectionResult(ok: true, version: "3.5.0")
    }

    func agentConfigs() async throws -> [AgentConfig] {
        [AgentConfig(raw: [
            "id": .string("demo-agent-1"), "name": .string("vps-frankfurt"), "url": .string("https://agent-fra.example.com:8090"),
            "traefik_api_url": .string("http://traefik:8080"), "config_path": .string("/app/config"),
            "restart_method": .string("proxy"), "traefik_container": .string("traefik"),
            "docker_host": .string("tcp://socket-proxy:2375"), "crowdsec_lapi_url": .string("http://crowdsec:8080"),
            "domains": .array([.string("edge.example.com")]),
            "visible_tabs": .object(["plugins": .bool(false)]),
        ]), AgentConfig(raw: [
            "id": .string("demo-agent-2"), "name": .string("vps-nyc"), "url": .string("https://agent-nyc.example.com:8090"),
            "config_path": .string("/app/config"), "restart_method": .string("socket"), "traefik_container": .string("traefik"),
        ])]
    }

    func createAgent(name: String, url: String) async throws -> AgentMutationResponse {
        await settle()
        return AgentMutationResponse(ok: true, agent: AgentConfig(raw: [
            "id": .string("demo-agent"), "name": .string(name), "url": .string(url),
            "api_key_raw": .string("demo-key-not-a-real-secret-0000000000000"),
        ]))
    }

    func updateAgent(_ id: String, changes: JSONObject) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func deleteAgent(_ id: String) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func rotateAgentKey(_ id: String) async throws -> AgentMutationResponse {
        await settle()
        return AgentMutationResponse(ok: true, agent: AgentConfig(raw: ["id": .string(id), "api_key_raw": .string("demo-rotated-key-0000000000000000000000")]))
    }

    func generateApiKey(deviceName: String) async throws -> GenerateKeyResponse {
        await settle()
        return GenerateKeyResponse(ok: true, key: "tm_demo_not_a_real_key_000000000000")
    }

    func revokeApiKey(preview: String) async throws -> OkResponse { OkResponse(ok: true) }

    func revokeSessions() async throws -> OkResponse { OkResponse(ok: true) }

    func notifications() async throws -> [TmNotification] {
        await settle()
        let now = Int64(Date().timeIntervalSince1970)
        return [
            TmNotification(ts: "", type: "warning", msg: "Ping all: 7/8 online - unreachable: bin", category: "traefik", id: 4, at: now - 240),
            TmNotification(ts: "", type: "success", msg: "Route media saved", category: "config", id: 3, at: now - 4_000),
            TmNotification(ts: "", type: "info", msg: "Traefik v3.7.10 is available - update now", category: "update", id: 2, at: now - 50_000),
            TmNotification(ts: "2026-08-11 21:58:12", type: "error", msg: "CrowdSec LAPI unreachable", category: "crowdsec", id: 1, at: 0),
        ]
    }

    func notificationState() async throws -> NotificationState { NotificationState(readUntil: 2, count: 4, unread: 2) }

    func markNotificationsRead(upTo id: Int) async throws -> OkResponse { OkResponse(ok: true) }

    func deleteNotification(_ notification: TmNotification) async throws -> OkResponse { OkResponse(ok: true) }

    func clearNotifications() async throws -> OkResponse { OkResponse(ok: true) }

    func testWebhook(_ request: WebhookTestRequest) async throws -> OkResponse {
        await settle()
        return OkResponse(ok: true)
    }

    func notificationChannels() async throws -> [NotificationChannel] {
        await settle()
        var discord = NotificationChannel()
        discord.id = "ch_1"
        discord.name = "Ops Discord"
        discord.kind = "discord"
        discord.url = "https://discord.com/api/webhooks/demo"
        var ntfy = NotificationChannel()
        ntfy.id = "ch_2"
        ntfy.name = "Phone"
        ntfy.kind = "ntfy"
        ntfy.url = "https://ntfy.sh/traefik-demo"
        ntfy.categories = ["security", "certs", "crowdsec"]
        ntfy.minSeverity = "warning"
        ntfy.quietHours = "23:00-07:00"
        ntfy.breakThrough = true
        var telegram = NotificationChannel()
        telegram.id = "ch_3"
        telegram.name = "Telegram"
        telegram.kind = "telegram"
        telegram.enabled = false
        telegram.digest = "daily"
        return [discord, ntfy, telegram]
    }

    func createChannel(_ channel: NotificationChannel) async throws -> ChannelSaveResponse {
        await settle()
        var saved = channel
        saved.id = "ch_demo_\(Int.random(in: 100...999))"
        return ChannelSaveResponse(ok: true, channel: saved)
    }

    func updateChannel(_ id: String, _ channel: NotificationChannel) async throws -> ChannelSaveResponse {
        await settle()
        var saved = channel
        saved.id = id
        return ChannelSaveResponse(ok: true, channel: saved)
    }

    func deleteChannel(_ id: String) async throws -> OkResponse { OkResponse(ok: true) }

    func testChannel(_ id: String) async throws -> ChannelTestResult {
        await settle()
        return ChannelTestResult(ok: true)
    }

    func saveRouteHealth(enabled: Bool, interval: Int) async throws -> RouteHealthSaveResponse {
        await settle()
        return RouteHealthSaveResponse(ok: true, enabled: enabled, interval: interval)
    }

    func clientIpDiagnostic() async throws -> ClientIpDiagnostic {
        var d = ClientIpDiagnostic()
        d.effectiveIp = "203.0.113.7"
        d.effectiveClass = "public"
        d.socketPeer = "172.18.0.1"
        d.socketPeerClass = "private"
        d.headers = ["X-Forwarded-For": "203.0.113.7, 172.18.0.1", "X-Real-IP": "203.0.113.7", "CF-Connecting-IP": "",
                     "X-Forwarded-Proto": "https", "X-Forwarded-Host": "manager.example.com"]
        d.forwardedForChain = ["203.0.113.7", "172.18.0.1"]
        d.proxyHops = 1
        d.proxyTrusted = true
        d.trustedProxies = ["172.16.0.0/12"]
        d.classes = ["203.0.113.7": "public", "172.18.0.1": "private"]
        return d
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

    /// Docker-label and internal routers so the provider pages have something to show.
    static let dockerRouters: [TraefikObject] = [
        ("tma@docker", "Host(`tm.example.com`)", "tma@docker", "enabled"),
        ("whoami@docker", "Host(`whoami.example.com`)", "whoami@docker", "enabled"),
        ("grafana@docker", "Host(`grafana.example.com`) && PathPrefix(`/`)", "grafana@docker", "disabled"),
        ("api@internal", "PathPrefix(`/api`)", "api@internal", "enabled"),
        ("dashboard@internal", "PathPrefix(`/dashboard`)", "dashboard@internal", "enabled"),
    ].map { name, rule, service, status in
        var router = TraefikObject(name, name.components(separatedBy: "@")[1], status, using: ["websecure"])
        router.rule = rule
        router.service = service
        router.entryPoints = ["websecure"]
        router.tls = .object([:])
        return router
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

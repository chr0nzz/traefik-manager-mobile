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

    // MARK: - Fixtures

    struct DemoRoute {
        var name: String
        var host: String
        var target: String
        var proto = "http"
        var entryPoint = "websecure"
        var middlewares: [String] = []
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

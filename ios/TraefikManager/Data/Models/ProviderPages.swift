import Foundation

extension Destination {
    /// Traefik provider names shown on this provider page.
    var providerNames: Set<String> {
        switch self {
        case .docker: ["docker"]
        case .kubernetes: ["kubernetes", "kubernetescrd", "kubernetesingress", "kubernetesgateway"]
        case .swarm: ["swarm"]
        case .nomad: ["nomad"]
        case .ecs: ["ecs"]
        case .consulCatalog: ["consulcatalog"]
        case .redis: ["redis"]
        case .etcd: ["etcd"]
        case .consul: ["consul"]
        case .zookeeper: ["zookeeper"]
        case .httpProvider: ["http"]
        case .fileExternal: ["file"]
        case .internal: ["internal"]
        default: []
        }
    }

    var providerLabel: String {
        switch self {
        case .docker: "Docker"
        case .kubernetes: "Kubernetes"
        case .swarm: "Swarm"
        case .nomad: "Nomad"
        case .ecs: "ECS"
        case .consulCatalog: "Consul Catalog"
        case .redis: "Redis"
        case .etcd: "etcd"
        case .consul: "Consul KV"
        case .zookeeper: "ZooKeeper"
        case .httpProvider: String(localized: "HTTP Provider")
        case .fileExternal: String(localized: "File (external)")
        case .internal: String(localized: "Internal")
        default: ""
        }
    }

    var providerNote: String {
        switch self {
        case .docker: String(localized: "Managed by Docker labels and read-only. Edit them through your container's labels.")
        case .kubernetes: String(localized: "Managed by Kubernetes (CRD, Ingress or Gateway API) and read-only. Edit them through your Kubernetes resources.")
        case .swarm: String(localized: "Discovered through Docker Swarm labels and read-only. Edit them through your Swarm service definitions.")
        case .nomad: String(localized: "Discovered through HashiCorp Nomad and read-only. Edit them through your Nomad job definitions.")
        case .ecs: String(localized: "Discovered through Amazon ECS and read-only. Edit them through your ECS task definitions.")
        case .consulCatalog: String(localized: "Discovered through the Consul service catalog and read-only. Edit them through your Consul service registrations.")
        case .redis: String(localized: "Stored in Redis and read-only here. Edit them in your Redis KV store.")
        case .etcd: String(localized: "Stored in etcd and read-only here. Edit them in your etcd KV store.")
        case .consul: String(localized: "Stored in Consul's key-value store and read-only here. Edit them in Consul KV.")
        case .zookeeper: String(localized: "Stored in ZooKeeper and read-only here. Edit them in your ZooKeeper instance.")
        case .httpProvider: String(localized: "Sourced from an HTTP endpoint configured in Traefik and read-only here.")
        case .fileExternal: String(localized: "From file provider configs Traefik Manager does not manage, and read-only here. Managed routes are on Routes.")
        case .internal: String(localized: "Traefik creates these itself. They are read-only here and turned on or off in your static config.")
        default: ""
        }
    }

    var providerEmpty: String {
        switch self {
        case .docker: String(localized: "Routes discovered through Docker labels show up here.")
        case .kubernetes: String(localized: "Routes from Kubernetes CRDs, Ingress and Gateway API show up here.")
        case .swarm: String(localized: "Routes discovered through Swarm labels show up here.")
        case .nomad: String(localized: "Routes discovered through Nomad show up here.")
        case .ecs: String(localized: "Routes discovered through ECS show up here.")
        case .consulCatalog: String(localized: "Routes discovered through the Consul catalog show up here.")
        case .redis: String(localized: "Routes stored in Redis show up here.")
        case .etcd: String(localized: "Routes stored in etcd show up here.")
        case .consul: String(localized: "Routes stored in Consul KV show up here.")
        case .zookeeper: String(localized: "Routes stored in ZooKeeper show up here.")
        case .httpProvider: String(localized: "Routes from the HTTP provider show up here.")
        case .fileExternal: String(localized: "Routes from external file provider configs show up here.")
        case .internal: String(localized: "Traefik serves these itself. The dashboard, API and ping live here.")
        default: ""
        }
    }
}

/// A read-only router from a non-managed provider. Port of `ProviderRoute`.
struct ProviderRoute: Identifiable, Hashable {
    var name: String
    var shortName: String
    var proto: ServiceProtocol
    var status: String
    var rule: String
    var service: String
    var target: String?
    var entryPoints: [String]
    var middlewares: [String]
    var tls: Bool
    var provider: String

    var id: String { "\(proto.rawValue):\(name)" }
    var serving: Bool { status.isEmpty || status.caseInsensitiveCompare("enabled") == .orderedSame }
    var hosts: [String] { RouteFormParsing.hosts(inRule: rule) }

    /// The host when the rule is nothing but `Host(...)` matchers.
    var plainHost: String? {
        guard rule.trimmed.wholeMatch(of: #/Host\(`[^`]+`\)(\s*\|\|\s*Host\(`[^`]+`\))*/#) != nil else { return nil }
        return hosts.first
    }
}

struct ProviderMiddleware: Identifiable, Hashable {
    var name: String
    var type: String
    var status: String
    var id: String { name }
}

/// Port of `ProviderRows`.
enum ProviderRows {
    static func providerOf(_ name: String, _ provider: String) -> String {
        provider.ifEmpty(LogParser.providerOf(name)).lowercased()
    }

    static func routes(page: Destination, routers: ProtoEnvelope, services: ServiceEnvelope?, managed: Set<String>) -> [ProviderRoute] {
        let targets = targets(page: page, services: services)
        let tagged = routers.http.map { ($0, ServiceProtocol.http) } + routers.tcp.map { ($0, .tcp) } + routers.udp.map { ($0, .udp) }
        return tagged.compactMap { router, proto -> ProviderRoute? in
            let provider = providerOf(router.name, router.provider)
            guard page.providerNames.contains(provider) else { return nil }
            if page == .fileExternal && managed.contains(router.shortName) { return nil }
            let service = router.service ?? ""
            let tls: Bool = if case .null? = router.tls { false } else { router.tls != nil }
            return ProviderRoute(
                name: router.name,
                shortName: router.shortName,
                proto: proto,
                status: router.status ?? "",
                rule: router.rule ?? "",
                service: service,
                target: targets[service] ?? targets[RouteDetailView.shortName(service)],
                entryPoints: router.entryPoints ?? [],
                middlewares: router.middlewares ?? [],
                tls: tls,
                provider: provider
            )
        }
        .sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    static func middlewares(page: Destination, envelope: ProtoEnvelope?) -> [ProviderMiddleware] {
        ((envelope?.http ?? []) + (envelope?.tcp ?? []))
            .filter { page.providerNames.contains(providerOf($0.name, $0.provider)) }
            .map { ProviderMiddleware(name: $0.shortName, type: $0.type ?? "", status: $0.status ?? "") }
            .sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    private static func targets(page: Destination, services: ServiceEnvelope?) -> [String: String] {
        guard let services else { return [:] }
        var out: [String: String] = [:]
        for service in services.http + services.tcp + services.udp {
            guard page.providerNames.contains(providerOf(service.name, service.provider)) else { continue }
            let first = service.loadBalancer?.servers.first?.target
            guard let target = (first?.isEmpty == false ? first : nil) ?? service.serverStatus?.keys.sorted().first else { continue }
            out[service.name] = target
            if out[service.shortName] == nil { out[service.shortName] = target }
        }
        return out
    }
}

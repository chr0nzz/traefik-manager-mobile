import Foundation

struct OverviewCounts: Codable, Hashable, Sendable {
    var total: Int?
    var warnings: Int?
    var errors: Int?

    init(_ total: Int?, _ warnings: Int? = nil, _ errors: Int? = nil) {
        self.total = total
        self.warnings = warnings
        self.errors = errors
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        total = c.contains(.total) ? c.lenientInt(.total) : nil
        warnings = c.contains(.warnings) ? c.lenientInt(.warnings) : nil
        errors = c.contains(.errors) ? c.lenientInt(.errors) : nil
    }
}

struct OverviewSection: Codable, Hashable, Sendable {
    var routers: OverviewCounts?
    var services: OverviewCounts?
    var middlewares: OverviewCounts?

    init(routers: OverviewCounts? = nil, services: OverviewCounts? = nil, middlewares: OverviewCounts? = nil) {
        self.routers = routers
        self.services = services
        self.middlewares = middlewares
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        routers = c.optional(.routers)
        services = c.optional(.services)
        middlewares = c.optional(.middlewares)
    }
}

struct OverviewFeatures: Codable, Hashable, Sendable {
    var tracing: String?
    var metrics: String?
    var accessLog: Bool?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tracing = c.optional(.tracing)
        metrics = c.optional(.metrics)
        accessLog = c.optional(.accessLog)
    }
}

struct Overview: Codable, Hashable, Sendable {
    var http: OverviewSection?
    var tcp: OverviewSection?
    var udp: OverviewSection?
    var providers: [String]?
    var features: OverviewFeatures?

    var isBlind: Bool { http == nil && tcp == nil && udp == nil }

    init(http: OverviewSection? = nil, tcp: OverviewSection? = nil, udp: OverviewSection? = nil, providers: [String]? = nil) {
        self.http = http
        self.tcp = tcp
        self.udp = udp
        self.providers = providers
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        http = c.optional(.http)
        tcp = c.optional(.tcp)
        udp = c.optional(.udp)
        providers = c.optional(.providers)
        features = c.optional(.features)
    }
}

/// A router, service or middleware as reported by the Traefik API.
struct TraefikObject: Codable, Hashable, Sendable {
    var name = ""
    var provider = ""
    var status: String?
    var rule: String?
    var service: String?
    var using: [String]?
    var entryPoints: [String]?
    var usedBy: [String]?
    var serverStatus: [String: String]?
    var type: String?
    var middlewares: [String]?
    var tls: JSONValue?

    var shortName: String { name.split(separator: "@", maxSplits: 1).first.map(String.init) ?? name }

    /// Entry points the router is actually bound to.
    var boundEntryPoints: [String] { using ?? entryPoints ?? [] }

    init(
        _ name: String,
        _ provider: String,
        _ status: String?,
        using: [String]? = nil,
        usedBy: [String]? = nil,
        serverStatus: [String: String]? = nil,
        type: String? = nil
    ) {
        self.name = name
        self.provider = provider
        self.status = status
        self.using = using
        self.usedBy = usedBy
        self.serverStatus = serverStatus
        self.type = type
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        provider = c.value(.provider, default: "")
        status = c.optional(.status)
        rule = c.optional(.rule)
        service = c.optional(.service)
        using = c.optional(.using)
        entryPoints = c.optional(.entryPoints)
        usedBy = c.optional(.usedBy)
        serverStatus = c.optional(.serverStatus)
        type = c.optional(.type)
        middlewares = c.optional(.middlewares)
        tls = c.optional(.tls)
    }
}

struct ProtoEnvelope: Codable, Hashable, Sendable {
    var http: [TraefikObject] = []
    var tcp: [TraefikObject] = []
    var udp: [TraefikObject] = []
    var reachable = true

    var all: [TraefikObject] { http + tcp + udp }

    init(http: [TraefikObject] = [], tcp: [TraefikObject] = [], udp: [TraefikObject] = [], reachable: Bool = true) {
        self.http = http
        self.tcp = tcp
        self.udp = udp
        self.reachable = reachable
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        http = c.value(.http, default: [])
        tcp = c.value(.tcp, default: [])
        udp = c.value(.udp, default: [])
        reachable = c.value(.reachable, default: true)
    }

    func withExtra(http extra: [TraefikObject]) -> ProtoEnvelope {
        ProtoEnvelope(http: http + extra, tcp: tcp, udp: udp, reachable: reachable)
    }

    func filtered(provider: String?) -> ProtoEnvelope {
        guard let provider else { return self }
        return ProtoEnvelope(
            http: http.filter { $0.provider == provider },
            tcp: tcp.filter { $0.provider == provider },
            udp: udp.filter { $0.provider == provider },
            reachable: reachable
        )
    }
}

struct EntrypointTLS: Codable, Hashable, Sendable {
    var certResolver: String?
    var options: String?
}

struct EntrypointHTTP: Codable, Hashable, Sendable {
    var middlewares: [String]?
    var tls: EntrypointTLS?
}

struct Entrypoint: Codable, Hashable, Sendable {
    var name = ""
    var address = ""
    var asDefault = false
    var http: EntrypointHTTP?

    var isUDP: Bool { address.hasSuffix("/udp") }
    var hasTLS: Bool { http?.tls != nil }

    init(name: String, address: String, asDefault: Bool = false, http: EntrypointHTTP? = nil) {
        self.name = name
        self.address = address
        self.asDefault = asDefault
        self.http = http
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        address = c.value(.address, default: "")
        asDefault = c.value(.asDefault, default: false)
        http = c.optional(.http)
    }
}

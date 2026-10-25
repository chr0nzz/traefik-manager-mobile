import Foundation

struct CompositeChild: Codable, Hashable, Sendable {
    var name = ""
    var url = ""
    var weight = 0
    var percent = 0

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        url = c.value(.url, default: "")
        weight = c.lenientInt(.weight)
        percent = c.lenientInt(.percent)
    }
}

struct StickyCookie: Codable, Hashable, Sendable {
    var name = ""
    var secure = false
    var httpOnly = false
    var sameSite = ""

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.lenientString(.name)
        secure = c.value(.secure, default: false)
        httpOnly = c.value(.httpOnly, default: false)
        sameSite = c.lenientString(.sameSite)
    }
}

struct HealthCheck: Codable, Hashable, Sendable {
    var path = ""
    var interval = ""
    var timeout = ""

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        path = c.lenientString(.path)
        interval = c.lenientString(.interval)
        timeout = c.lenientString(.timeout)
    }
}

struct TLSDomain: Codable, Hashable, Sendable {
    var main = ""
    var sans: [String] = []

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        main = c.value(.main, default: "")
        sans = c.value(.sans, default: [])
    }
}

struct HeadersPresetToggles: Codable, Hashable, Sendable {
    var perms: [String: String] = [:]
    var hsts = true
    var nosniff = true
    var frameDeny = true
    var referrer = ""

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        perms = c.value(.perms, default: [:])
        hsts = c.value(.hsts, default: true)
        nosniff = c.value(.nosniff, default: true)
        frameDeny = c.value(.frameDeny, default: true)
        referrer = c.value(.referrer, default: "")
    }
}

struct HeadersPreset: Codable, Hashable, Sendable {
    var owned = false
    var exists = false
    var state = "off"
    var toggles = HeadersPresetToggles()

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        owned = c.value(.owned, default: false)
        exists = c.value(.exists, default: false)
        state = c.value(.state, default: "off")
        toggles = c.value(.toggles, default: HeadersPresetToggles())
    }
}

/// A route as returned by `GET /api/routes`: router + service merged, port of `Route`.
struct Route: Codable, Hashable, Identifiable, Sendable {
    var id = ""
    var name = ""
    var rule = ""
    var serviceName = ""
    var target = ""
    var servers: [String] = []
    var middlewares: JSONValue?
    var entryPoints: JSONValue?
    var entrypointMiddlewares: [String] = []
    var `protocol` = "http"
    var tls: JSONValue?
    var enabled = true
    var certResolver = ""
    var tlsOptionsProfile = ""
    var insecureSkipVerify = false
    var passHostHeader: Bool?
    var priority: Int?
    var serviceType = "loadBalancer"
    var serviceOwned = false
    var compositeChildren: [CompositeChild] = []
    var configFile = ""
    var provider = "file"
    var sticky: StickyCookie?
    var stickyEnabled = false
    var healthCheck: HealthCheck?
    var tlsDomains: [TLSDomain] = []
    var streaming = false
    var headersPreset: HeadersPreset?

    private enum CodingKeys: String, CodingKey {
        case id, name, rule, target, servers, middlewares, entryPoints, entrypointMiddlewares
        case `protocol`, tls, enabled, certResolver, tlsOptionsProfile, insecureSkipVerify, passHostHeader
        case priority, serviceType, serviceOwned, compositeChildren, configFile, provider, sticky
        case stickyEnabled, healthCheck, tlsDomains, streaming, headersPreset
        case serviceName = "service_name"
    }

    init(id: String, name: String) {
        self.id = id
        self.name = name
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.lenientString(.id)
        name = c.lenientString(.name)
        rule = c.value(.rule, default: "")
        serviceName = c.lenientString(.serviceName)
        target = c.lenientString(.target)
        servers = c.value(.servers, default: [])
        middlewares = c.optional(.middlewares)
        entryPoints = c.optional(.entryPoints)
        entrypointMiddlewares = c.value(.entrypointMiddlewares, default: [])
        `protocol` = c.value(.protocol, default: "http")
        tls = c.optional(.tls)
        enabled = c.value(.enabled, default: true)
        certResolver = c.value(.certResolver, default: "")
        tlsOptionsProfile = c.value(.tlsOptionsProfile, default: "")
        insecureSkipVerify = c.value(.insecureSkipVerify, default: false)
        passHostHeader = c.optional(.passHostHeader)
        priority = c.contains(.priority) && (try? c.decodeNil(forKey: .priority)) == false ? c.lenientInt(.priority) : nil
        serviceType = c.value(.serviceType, default: "loadBalancer")
        serviceOwned = c.value(.serviceOwned, default: false)
        compositeChildren = c.value(.compositeChildren, default: [])
        configFile = c.value(.configFile, default: "")
        provider = c.value(.provider, default: "file")
        sticky = c.optional(.sticky)
        stickyEnabled = c.value(.stickyEnabled, default: false)
        healthCheck = c.optional(.healthCheck)
        tlsDomains = c.value(.tlsDomains, default: [])
        streaming = c.value(.streaming, default: false)
        headersPreset = c.optional(.headersPreset)
    }

    // MARK: Derived

    var tlsEnabled: Bool {
        switch tls {
        case .none, .null?: false
        case let .bool(value)?: value
        case let .string(value)?: !value.isEmpty
        case .object?: true
        default: false
        }
    }

    var middlewareNames: [String] { Self.stringList(middlewares) }
    var entryPointNames: [String] { Self.stringList(entryPoints) }
    var isComposite: Bool { serviceType != "loadBalancer" }
    var isFileRoute: Bool { provider.isEmpty || provider == "file" }

    var backendCount: Int {
        if !servers.isEmpty { return servers.count }
        if !compositeChildren.isEmpty { return compositeChildren.count }
        return !target.isEmpty && target != "N/A" ? 1 : 0
    }

    /// Hostnames from `Host(`…`)` matchers, ignoring negated ones.
    var hosts: [String] {
        let pattern = #/(!?)\s*Host\(`([^`]+)`\)/#
        return rule.matches(of: pattern).filter { $0.output.1 != "!" }.map { String($0.output.2) }
    }

    var isPlainHostRule: Bool {
        !hosts.isEmpty && rule == hosts.map { "Host(`\($0)`)" }.joined(separator: " || ")
    }

    var tlsPassthrough: Bool { tls?.objectValue?["passthrough"]?.boolValue == true }
    var hasWildcard: Bool { !tlsDomains.isEmpty }

    func status(health: RouteHealth? = nil) -> TMStatus {
        if !enabled { return .disabled }
        if target == "N/A" || backendCount == 0 { return .error }
        switch health?.state {
        case RouteHealth.down: return .error
        case RouteHealth.degraded: return .warn
        default: return .ok
        }
    }

    private static func stringList(_ value: JSONValue?) -> [String] {
        switch value {
        case let .string(text)?: text.isEmpty ? [] : [text]
        case let .array(items)?: items.compactMap(\.stringValue)
        default: []
        }
    }
}

struct MiddlewareDef: Codable, Hashable, Sendable {
    var name = ""
    var type = "http"
    var yaml = ""
    var configFile = ""

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.lenientString(.name)
        type = c.value(.type, default: "http")
        yaml = c.value(.yaml, default: "")
        configFile = c.value(.configFile, default: "")
    }
}

struct ConfigError: Codable, Hashable, Sendable {
    var file = ""
    var error = ""

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        file = c.value(.file, default: "")
        error = c.lenientString(.error)
    }
}

struct ServicesByProtocol: Codable, Hashable, Sendable {
    var http: [String] = []
    var tcp: [String] = []
    var udp: [String] = []

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        http = c.value(.http, default: [])
        tcp = c.value(.tcp, default: [])
        udp = c.value(.udp, default: [])
    }

    func forProtocol(_ proto: String) -> [String] {
        switch proto {
        case "tcp": tcp
        case "udp": udp
        default: http
        }
    }
}

struct RoutesResponse: Codable, Sendable {
    var apps: [Route] = []
    var middlewares: [MiddlewareDef] = []
    var configErrors: [ConfigError] = []
    var services = ServicesByProtocol()

    init(apps: [Route] = [], middlewares: [MiddlewareDef] = [], configErrors: [ConfigError] = []) {
        self.apps = apps
        self.middlewares = middlewares
        self.configErrors = configErrors
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        apps = c.value(.apps, default: [])
        middlewares = c.value(.middlewares, default: [])
        configErrors = c.value(.configErrors, default: [])
        services = c.value(.services, default: ServicesByProtocol())
    }
}

struct ToggleRequest: Encodable {
    var enable: Bool
    var agentId: String

    private enum CodingKeys: String, CodingKey {
        case enable
        case agentId = "agent_id"
    }
}

struct RawRoute: Decodable, Sendable {
    var raw = ""
    var configFile = ""
    var proto = ""
    var error: String?

    private enum CodingKeys: String, CodingKey { case raw, configFile, proto, error }

    init(raw: String, configFile: String, proto: String) {
        self.raw = raw
        self.configFile = configFile
        self.proto = proto
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        raw = c.value(.raw, default: "")
        configFile = c.value(.configFile, default: "")
        proto = c.value(.proto, default: "")
        error = c.optional(.error)
    }
}

struct RawRouteSave: Encodable {
    var content: String
}

struct RouteServerTally: Codable, Hashable, Sendable {
    var up = 0
    var total = 0

    init(up: Int, total: Int) {
        self.up = up
        self.total = total
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        up = c.lenientInt(.up)
        total = c.lenientInt(.total)
    }
}

struct PingResult: Decodable, Sendable {
    var ok = false
    var latencyMs: Int?
    var statusCode: Int?
    var isSelf = false
    var error: String?
    var state = ""
    var servers: RouteServerTally?

    var degraded: Bool { state == RouteHealth.degraded }

    private enum CodingKeys: String, CodingKey {
        case ok, error, state, servers
        case latencyMs = "latency_ms"
        case statusCode = "status_code"
        case isSelf = "self"
    }

    init(ok: Bool, latencyMs: Int? = nil, statusCode: Int? = nil) {
        self.ok = ok
        self.latencyMs = latencyMs
        self.statusCode = statusCode
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        latencyMs = c.optional(.latencyMs)
        statusCode = c.optional(.statusCode)
        isSelf = c.value(.isSelf, default: false)
        error = c.optional(.error)
        state = c.value(.state, default: "")
        servers = c.optional(.servers)
    }
}

/// Background health check result for one route, port of `RouteHealth`.
struct RouteHealth: Codable, Hashable, Sendable {
    static let up = "up"
    static let degraded = "degraded"
    static let down = "down"
    static let pending = "pending"

    var state = RouteHealth.pending
    var source = ""
    var latencyMs: Int?
    var error: String?
    var servers: RouteServerTally?
    var downServers: [String] = []
    var unverified = false
    var note: String?
    var isSelf = false

    var known: Bool { [Self.up, Self.degraded, Self.down].contains(state) }

    var tmStatus: TMStatus {
        switch state {
        case Self.up: .ok
        case Self.degraded: .warn
        case Self.down: .error
        default: .unknown
        }
    }

    var summary: String {
        switch state {
        case Self.up where isSelf:
            return String(localized: "Reachable, this app")
        case Self.up where servers != nil:
            return String(localized: "Reachable, \(servers!.up) of \(servers!.total) servers up")
        case Self.up where latencyMs != nil:
            return String(localized: "Reachable in \(latencyMs!)ms")
        case Self.up:
            return String(localized: "Reachable")
        case Self.degraded where servers != nil:
            return String(localized: "Degraded, \(servers!.up) of \(servers!.total) servers up")
        case Self.degraded:
            return String(localized: "Degraded")
        case Self.down where servers != nil:
            return String(localized: "Down, 0 of \(servers!.total) servers up")
        case Self.down:
            if let error, !error.trimmingCharacters(in: .whitespaces).isEmpty { return String(localized: "Down: \(error)") }
            return String(localized: "Down")
        default:
            return note ?? String(localized: "Not checked yet")
        }
    }

    private enum CodingKeys: String, CodingKey {
        case state, source, error, servers, unverified, note
        case latencyMs = "latency_ms"
        case downServers = "down_servers"
        case isSelf = "self"
    }

    init(state: String) { self.state = state }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        state = c.value(.state, default: Self.pending)
        source = c.value(.source, default: "")
        latencyMs = c.optional(.latencyMs)
        error = c.optional(.error)
        servers = c.optional(.servers)
        downServers = c.value(.downServers, default: [])
        unverified = c.value(.unverified, default: false)
        note = c.optional(.note)
        isSelf = c.value(.isSelf, default: false)
    }
}

struct RouteHealthSnapshot: Decodable, Sendable {
    var enabled = true
    var interval = 300
    var checkedAt: Double?
    var routes: [String: RouteHealth] = [:]

    private enum CodingKeys: String, CodingKey {
        case enabled, interval, routes
        case checkedAt = "checked_at"
    }

    init(routes: [String: RouteHealth]) { self.routes = routes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = c.value(.enabled, default: true)
        interval = c.lenientInt(.interval, default: 300)
        checkedAt = c.optional(.checkedAt)
        routes = c.value(.routes, default: [:])
    }
}

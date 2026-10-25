import Foundation

struct ServiceServer: Codable, Hashable, Sendable {
    var url: String?
    var address: String?
    var target: String { url ?? address ?? "" }
}

struct ServiceLoadBalancer: Codable, Hashable, Sendable {
    var servers: [ServiceServer] = []
    var passHostHeader: Bool?
    var sticky: JSONValue?
    var healthCheck: JSONValue?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        servers = c.value(.servers, default: [])
        passHostHeader = c.optional(.passHostHeader)
        sticky = c.optional(.sticky)
        healthCheck = c.optional(.healthCheck)
    }
}

struct WeightedChild: Codable, Hashable, Sendable {
    var name = ""
    var weight: Int?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        weight = c.contains(.weight) ? c.lenientInt(.weight) : nil
    }
}

struct WeightedService: Codable, Hashable, Sendable {
    var services: [WeightedChild] = []

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        services = c.value(.services, default: [])
    }
}

struct MirrorChild: Codable, Hashable, Sendable {
    var name = ""
    var percent: Int?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        percent = c.contains(.percent) ? c.lenientInt(.percent) : nil
    }
}

struct MirroringService: Codable, Hashable, Sendable {
    var service: String?
    var mirrors: [MirrorChild] = []

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        service = c.optional(.service)
        mirrors = c.value(.mirrors, default: [])
    }
}

struct FailoverService: Codable, Hashable, Sendable {
    var service: String?
    var fallback: String?
}

/// A service with its full definition, from `GET /api/traefik/services?agent_id=`.
struct TraefikService: Codable, Hashable, Sendable {
    var name = ""
    var provider = ""
    var status: String?
    var type: String?
    var usedBy: [String]?
    var serverStatus: [String: String]?
    var error: JSONValue?
    var loadBalancer: ServiceLoadBalancer?
    var weighted: WeightedService?
    var mirroring: MirroringService?
    var failover: FailoverService?
    var highestRandomWeight: WeightedService?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        provider = c.value(.provider, default: "")
        status = c.optional(.status)
        type = c.optional(.type)
        usedBy = c.optional(.usedBy)
        serverStatus = c.optional(.serverStatus)
        error = c.optional(.error)
        loadBalancer = c.optional(.loadBalancer)
        weighted = c.optional(.weighted)
        mirroring = c.optional(.mirroring)
        failover = c.optional(.failover)
        highestRandomWeight = c.optional(.highestRandomWeight)
    }

    var shortName: String { RouteDetailView.shortName(name) }
}

struct ServiceEnvelope: Codable, Sendable {
    var http: [TraefikService] = []
    var tcp: [TraefikService] = []
    var udp: [TraefikService] = []
    var reachable = true
    var ownedServices: [String] = []
    var ownedChildren: [String] = []

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        http = c.value(.http, default: [])
        tcp = c.value(.tcp, default: [])
        udp = c.value(.udp, default: [])
        reachable = c.value(.reachable, default: true)
        ownedServices = c.value(.ownedServices, default: [])
        ownedChildren = c.value(.ownedChildren, default: [])
    }
}

enum ServiceProtocol: String, CaseIterable, Identifiable, Sendable {
    case http = "HTTP", tcp = "TCP", udp = "UDP"
    var id: String { rawValue }
}

enum ServiceHealth: Sendable { case ok, warning, error }
enum ServerHealth: Sendable { case up, down, unknown }

struct ServiceServerRow: Hashable, Sendable {
    var target: String
    var health: ServerHealth
}

/// One service as listed, port of `ServiceRow`.
struct ServiceRow: Identifiable, Hashable, Sendable {
    var key: String
    var proto: ServiceProtocol
    var name: String
    var shortName: String
    var provider: String
    var kind: String?
    var health: ServiceHealth
    var backendsUp: Int
    var backendsTotal: Int
    var servers: [ServiceServerRow]
    var composite: [String]
    var sticky: Bool
    var healthCheck: Bool
    var passHostHeader: Bool?
    var usedBy: [String]
    var errors: [String]
    var owned = false

    var id: String { key }
    var kindLabel: String { kind ?? "service" }
    var authorable: Bool { proto == .http }
    var isComposite: Bool { !composite.isEmpty }

    var backendSummary: String? {
        backendsTotal > 0 ? String(localized: "\(backendsUp)/\(backendsTotal) active") : nil
    }

    var metaParts: [String] {
        var parts: [String] = []
        if composite.isEmpty && !servers.isEmpty { parts.append(String(localized: "\(servers.count) servers")) }
        if let backendSummary { parts.append(backendSummary) }
        if sticky { parts.append(String(localized: "sticky")) }
        if healthCheck { parts.append(String(localized: "health check")) }
        return parts
    }

    var status: TMStatus {
        switch health {
        case .ok: .ok
        case .warning: .warn
        case .error: .error
        }
    }
}

enum ServiceRows {
    static func from(_ envelope: ServiceEnvelope) -> [ServiceRow] {
        let owned = Set(envelope.ownedServices)
        let generated = Set(envelope.ownedChildren)
        let tagged = envelope.http.map { ($0, ServiceProtocol.http) }
            + envelope.tcp.map { ($0, ServiceProtocol.tcp) }
            + envelope.udp.map { ($0, ServiceProtocol.udp) }
        return tagged
            .map { row($0.0, proto: $0.1) }
            .filter { !generated.contains($0.shortName) }
            .map { row in
                var copy = row
                copy.owned = owned.contains(row.shortName)
                return copy
            }
            .sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    static func row(_ service: TraefikService, proto: ServiceProtocol) -> ServiceRow {
        let statuses = service.serverStatus ?? [:]
        let up = statuses.values.filter { $0.uppercased() == "UP" }.count
        let lb = service.loadBalancer
        let targets = (lb?.servers ?? []).map(\.target).filter { !$0.isEmpty }
        let shown = targets.isEmpty ? statuses.keys.sorted() : targets
        let provider = service.provider.ifEmpty(service.name.contains("@") ? String(service.name.split(separator: "@").last!) : "file")
        return ServiceRow(
            key: "\(proto.rawValue):\(service.name)",
            proto: proto,
            name: service.name,
            shortName: service.shortName,
            provider: provider,
            kind: kind(of: service),
            health: health(of: service),
            backendsUp: up,
            backendsTotal: statuses.count,
            servers: shown.map { target in
                let state = statuses[target]?.uppercased()
                return ServiceServerRow(target: target, health: state == nil ? .unknown : state == "UP" ? .up : .down)
            },
            composite: compositeTargets(service),
            sticky: lb?.sticky != nil,
            healthCheck: lb?.healthCheck != nil,
            passHostHeader: lb.map { $0.passHostHeader ?? true },
            usedBy: service.usedBy ?? [],
            errors: errors(service.error)
        )
    }

    static func kind(of service: TraefikService) -> String? {
        if let type = service.type?.lowercased(),
           ["loadbalancer", "weighted", "mirroring", "failover", "highestrandomweight"].contains(type) {
            return type
        }
        if service.loadBalancer != nil { return "loadbalancer" }
        if service.mirroring != nil { return "mirroring" }
        if service.failover != nil { return "failover" }
        if service.weighted != nil { return "weighted" }
        if service.highestRandomWeight != nil { return "highestrandomweight" }
        return nil
    }

    static func health(of service: TraefikService) -> ServiceHealth {
        let statuses = service.serverStatus ?? [:]
        let down = statuses.values.filter { $0.uppercased() != "UP" }.count
        switch service.status?.lowercased() {
        case "disabled", "error": return .error
        case "enabled": return down == 0 ? .ok : down == statuses.count ? .error : .warning
        default: return .warning
        }
    }

    private static func compositeTargets(_ service: TraefikService) -> [String] {
        var targets: [String] = []
        let weighted = { (child: WeightedChild) in child.weight.map { "\(child.name) (\($0))" } ?? child.name }
        targets += (service.weighted?.services ?? []).map(weighted)
        if let primary = service.mirroring?.service { targets.append(primary) }
        targets += (service.mirroring?.mirrors ?? []).map { String(localized: "\($0.name) mirror (\($0.percent ?? 0)%)") }
        if let primary = service.failover?.service { targets.append(primary) }
        if let fallback = service.failover?.fallback { targets.append(String(localized: "\(fallback) fallback")) }
        targets += (service.highestRandomWeight?.services ?? []).map(weighted)
        return targets
    }

    static func errors(_ value: JSONValue?) -> [String] {
        func text(_ item: JSONValue) -> String? {
            switch item {
            case let .string(message): message.isBlank ? nil : message
            case let .object(object): object["message"]?.stringValue ?? String(describing: object)
            case .null: nil
            default: item.stringValue
            }
        }
        guard let value else { return [] }
        if case let .array(items) = value { return items.compactMap(text) }
        return [text(value)].compactMap { $0 }
    }

    static func typeLabel(_ type: String) -> String {
        switch type {
        case "loadBalancer": String(localized: "Load balancer")
        case "weighted": String(localized: "Weighted")
        case "mirroring": String(localized: "Mirroring")
        case "failover": String(localized: "Failover")
        case "highestRandomWeight": String(localized: "Highest random weight")
        default: type
        }
    }
}

/// Backend health line shown on route details, port of `BackendHealth`.
enum BackendHealth {
    static func key(_ proto: String, _ serviceName: String) -> String {
        "\(proto.lowercased()):\(RouteDetailView.shortName(serviceName))"
    }

    static func summary(_ row: ServiceRow?) -> String? {
        guard let row, row.healthCheck, row.backendsTotal > 0 else { return nil }
        let down = row.backendsTotal - row.backendsUp
        if down == 0 { return String(localized: "\(row.backendsUp) of \(row.backendsTotal) servers up") }
        if row.backendsUp == 0 { return String(localized: "all \(row.backendsTotal) servers down") }
        return String(localized: "\(down) of \(row.backendsTotal) servers down")
    }
}

// MARK: - Editing

struct HealthCheckPayload: Encodable {
    var enabled: Bool
    var path, interval, timeout, unhealthyInterval, method, status, scheme, port, hostname, mode: String
    var followRedirects: Bool
    var headers: [String: String]
}

struct ServiceHealthDraft: Hashable {
    var enabled = false
    var path = ""
    var interval = ""
    var timeout = ""
    var unhealthyInterval = ""
    var method = ""
    var status = ""
    var scheme = ""
    var port = ""
    var hostname = ""
    var mode = ""
    var followRedirects = true
    var headers: [HeaderPair] = []

    struct HeaderPair: Hashable, Identifiable {
        var id = UUID()
        var name = ""
        var value = ""
    }

    var payload: HealthCheckPayload {
        HealthCheckPayload(
            enabled: enabled, path: path.trimmed, interval: interval.trimmed, timeout: timeout.trimmed,
            unhealthyInterval: unhealthyInterval.trimmed, method: method.trimmed, status: status.trimmed,
            scheme: scheme.trimmed, port: port.trimmed, hostname: hostname.trimmed, mode: mode.trimmed,
            followRedirects: followRedirects,
            headers: Dictionary(headers.filter { !$0.name.isBlank }.map { ($0.name.trimmed, $0.value.trimmed) }, uniquingKeysWith: { $1 })
        )
    }

    /// `1m30s` instead of Traefik's `1m30.000s` style.
    static func shortDuration(_ value: String) -> String {
        let text = value.trimmed
        guard !text.isEmpty,
              let match = text.wholeMatch(of: #/^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?(?:(\d+)ms)?$/#) else { return text }
        let groups: [(Substring?, String)] = [(match.output.1, "h"), (match.output.2, "m"), (match.output.3, "s"), (match.output.4, "ms")]
        let parts = groups.compactMap { value, unit -> String? in
            guard let value, let number = Double(value), number != 0 else { return nil }
            return "\(value)\(unit)"
        }
        return parts.isEmpty ? text : parts.joined()
    }

    static func from(_ value: JSONValue?) -> ServiceHealthDraft {
        guard let object = value?.objectValue, !object.isEmpty else { return ServiceHealthDraft() }
        let text = { (key: String) in object[key]?.stringValue ?? "" }
        var draft = ServiceHealthDraft(enabled: true)
        draft.path = text("path")
        draft.interval = shortDuration(text("interval"))
        draft.timeout = shortDuration(text("timeout"))
        draft.unhealthyInterval = shortDuration(text("unhealthyInterval"))
        draft.method = text("method")
        draft.status = text("status")
        draft.scheme = text("scheme")
        draft.port = text("port")
        draft.hostname = text("hostname")
        draft.mode = text("mode")
        draft.followRedirects = object["followRedirects"]?.boolValue ?? true
        draft.headers = (object["headers"]?.objectValue ?? [:]).sorted { $0.key < $1.key }
            .map { HeaderPair(name: $0.key, value: $0.value.stringValue ?? "") }
        return draft
    }
}

struct ServiceChildDraft: Hashable, Identifiable {
    static let manual = "manual"
    static let service = "service"

    var id = UUID()
    var kind = ServiceChildDraft.manual
    var name = ""
    var address = ""
    var scheme = "http"
    var share = "1"
}

struct ServiceChildPayload: Encodable {
    var kind: String
    var name: String
    var address: String
    var scheme: String
    var weight: Int
    var percent: Int
}

struct ServicePayload: Encodable {
    var name: String
    var type: String
    var originalName: String
    var configFile: String
    var children: [ServiceChildPayload]
    var healthCheck: HealthCheckPayload?
    var agentId: String

    private enum CodingKeys: String, CodingKey {
        case name, type, originalName, configFile, children, healthCheck
        case agentId = "agent_id"
    }
}

struct ServiceSaveResponse: Decodable {
    var ok = false
    var name = ""
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, name, error }

    init(ok: Bool, name: String) {
        self.ok = ok
        self.name = name
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        name = c.value(.name, default: "")
        error = c.optional(.error)
    }
}

struct ServiceOwnershipRequest: Encodable { var adopt: Bool }

struct ServiceOwnershipResponse: Decodable {
    var ok = false
    var owned = false
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, owned, error }

    init(ok: Bool, owned: Bool) {
        self.ok = ok
        self.owned = owned
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        owned = c.value(.owned, default: false)
        error = c.optional(.error)
    }
}

/// The service editor's state, port of `ServiceDraft`.
struct ServiceDraft: Hashable {
    static let authorableTypes = ["loadBalancer", "weighted", "mirroring", "failover"]

    var originalName = ""
    var name = ""
    var type = "loadBalancer"
    var configFile = ""
    var children = [ServiceChildDraft()]
    var healthCheck = ServiceHealthDraft()

    var adding: Bool { originalName.isEmpty }
    var readOnly: Bool { type == "highestRandomWeight" }
    var usesShare: Bool { type == "weighted" || type == "mirroring" }
    var maxRows: Int { type == "failover" ? 2 : .max }

    func payload(agentId: String?) -> ServicePayload {
        ServicePayload(
            name: name.trimmed,
            type: type,
            originalName: originalName,
            configFile: configFile.trimmed,
            children: children.compactMap { child in
                let share = Int(child.share.trimmed) ?? (type == "mirroring" ? 0 : 1)
                if child.kind == ServiceChildDraft.service && child.name.isBlank { return nil }
                if child.kind == ServiceChildDraft.manual && child.address.isBlank { return nil }
                return ServiceChildPayload(
                    kind: child.kind, name: child.name.trimmed, address: child.address.trimmed,
                    scheme: child.scheme, weight: share, percent: share
                )
            },
            healthCheck: type == "loadBalancer" ? healthCheck.payload : nil,
            agentId: agentId ?? ""
        )
    }

    var problem: String? {
        if name.isBlank { return String(localized: "Give the service a name") }
        if let problem = EntityNames.problem(name) { return problem }
        let children = payload(agentId: nil).children
        if children.isEmpty { return String(localized: "Add at least one backend") }
        if type == "loadBalancer" && !children.contains(where: { $0.kind == ServiceChildDraft.manual }) {
            return String(localized: "A load balancer needs at least one address")
        }
        return nil
    }

    func rowHint(_ index: Int) -> String? {
        switch (type, index) {
        case ("mirroring", 0): String(localized: "serves traffic")
        case ("mirroring", _): String(localized: "mirror")
        case ("failover", 0): String(localized: "primary")
        case ("failover", 1): String(localized: "fallback")
        default: nil
        }
    }

    var shareLabel: String { type == "mirroring" ? String(localized: "Percent") : String(localized: "Weight") }

    static func from(name: String, service: TraefikService?) -> ServiceDraft {
        let bare = RouteDetailView.shortName(name)
        let reference = { (target: String, share: Int) in
            ServiceChildDraft(kind: ServiceChildDraft.service, name: RouteDetailView.shortName(target), share: String(share))
        }
        var draft = ServiceDraft(originalName: bare, name: bare)
        if let weighted = service?.weighted?.services {
            draft.type = "weighted"
            draft.children = weighted.map { reference($0.name, $0.weight ?? 1) }
        } else if let mirroring = service?.mirroring {
            draft.type = "mirroring"
            draft.children = (mirroring.service.map { [reference($0, 0)] } ?? []) + mirroring.mirrors.map { reference($0.name, $0.percent ?? 0) }
        } else if let failover = service?.failover {
            draft.type = "failover"
            draft.children = [failover.service, failover.fallback].compactMap { $0 }.map { reference($0, 1) }
        } else if let hrw = service?.highestRandomWeight?.services {
            draft.type = "highestRandomWeight"
            draft.children = hrw.map { reference($0.name, $0.weight ?? 1) }
        } else {
            draft.children = (service?.loadBalancer?.servers ?? []).map { server in
                let parts = server.target.components(separatedBy: "://")
                return ServiceChildDraft(
                    kind: ServiceChildDraft.manual,
                    address: parts.count > 1 ? parts[1] : server.target,
                    scheme: parts.count > 1 ? parts[0].lowercased() : "http"
                )
            }
        }
        if draft.children.isEmpty { draft.children = [ServiceChildDraft()] }
        draft.healthCheck = ServiceHealthDraft.from(service?.loadBalancer?.healthCheck)
        return draft
    }
}

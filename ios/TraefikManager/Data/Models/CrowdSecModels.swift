import Foundation

struct CsDecision: Codable, Hashable, Identifiable, Sendable {
    static let subscribedOrigins: Set<String> = ["capi", "lists"]

    var id: Int64 = 0
    var value = ""
    var type = "ban"
    var scope = "Ip"
    var origin = ""
    var scenario = ""
    var duration = ""
    var simulated = false

    var originKey: String { origin.lowercased() }
    var own: Bool { !Self.subscribedOrigins.contains(originKey) }

    init(id: Int64, value: String, type: String, scope: String, origin: String, scenario: String, duration: String) {
        self.id = id
        self.value = value
        self.type = type
        self.scope = scope
        self.origin = origin
        self.scenario = scenario
        self.duration = duration
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = Int64(c.lenientInt(.id))
        value = c.value(.value, default: "")
        type = c.value(.type, default: "ban")
        scope = c.value(.scope, default: "Ip")
        origin = c.value(.origin, default: "")
        scenario = c.value(.scenario, default: "")
        duration = c.value(.duration, default: "")
        simulated = c.value(.simulated, default: false)
    }
}

struct CsSource: Codable, Hashable, Sendable {
    var ip = ""
    var value = ""
    var scope = "Ip"
    var cn = ""
    var asName = ""
    var asNumber = ""
    var range = ""

    private enum CodingKeys: String, CodingKey {
        case ip, value, scope, cn, range
        case asName = "as_name"
        case asNumber = "as_number"
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ip = c.value(.ip, default: "")
        value = c.value(.value, default: "")
        scope = c.value(.scope, default: "Ip")
        cn = c.value(.cn, default: "")
        asName = c.value(.asName, default: "")
        asNumber = c.lenientString(.asNumber)
        range = c.value(.range, default: "")
    }
}

struct CsMetaEntry: Codable, Hashable, Sendable {
    var key = ""
    var value = ""

    init(key: String, value: String) {
        self.key = key
        self.value = value
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = c.value(.key, default: "")
        value = c.lenientString(.value)
    }
}

/// One CrowdSec alert with its evidence. Port of `CsAlert`.
struct CsAlert: Codable, Hashable, Sendable {
    var uuid = ""
    var id: Int64 = 0
    var scenario = ""
    var eventsCount = 0
    var simulated = false
    var machineId = ""
    var message = ""
    var startAt = ""
    var createdAt = ""
    var source = CsSource()
    var meta: [CsMetaEntry] = []
    var handled: Bool?

    private enum CodingKeys: String, CodingKey {
        case uuid, id, scenario, simulated, message, source, meta, handled
        case eventsCount = "events_count"
        case machineId = "machine_id"
        case startAt = "start_at"
        case createdAt = "created_at"
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        uuid = c.value(.uuid, default: "")
        id = Int64(c.lenientInt(.id))
        scenario = c.value(.scenario, default: "")
        eventsCount = c.lenientInt(.eventsCount)
        simulated = c.value(.simulated, default: false)
        machineId = c.value(.machineId, default: "")
        message = c.value(.message, default: "")
        startAt = c.value(.startAt, default: "")
        createdAt = c.value(.createdAt, default: "")
        source = c.value(.source, default: CsSource())
        meta = c.value(.meta, default: [])
        handled = c.optional(.handled)
    }

    var ip: String { source.ip.ifEmpty(source.value) }
    var countryCode: String { source.cn.count == 2 && source.cn.allSatisfy(\.isLetter) ? source.cn.uppercased() : "" }
    var scenarioName: String { scenario.ifEmpty("unknown") }
    var startMillis: Double { LogParser.parseTimestamp(startAt.ifEmpty(createdAt)) ?? 0 }

    var metaMap: [String: [String]] { CrowdSecMeta.parse(meta) }
    var uris: [String] { metaMap["target_uri"] ?? [] }
    var users: [String] { metaMap["target_user"] ?? [] }
    var verbs: [String] { metaMap["method"] ?? [] }
    var userAgents: [String] { metaMap["user_agent"] ?? [] }
    var hosts: [String] { metaMap["target_fqdn"] ?? [] }
    var routers: [String] {
        let map = metaMap
        if let leaf = map["traefik_router_name_leaf"], !leaf.isEmpty { return leaf }
        return map["traefik_router_name"] ?? []
    }

    func key(_ index: Int) -> String { uuid.ifEmpty(id != 0 ? String(id) : "cs\(index)") }
}

struct AddDecisionRequest: Encodable {
    var value: String
    var type: String
    var duration: String
    var reason: String
}

/// Meta values arrive as JSON arrays encoded inside strings.
enum CrowdSecMeta {
    static func parse(_ entries: [CsMetaEntry]) -> [String: [String]] {
        var out: [String: [String]] = [:]
        for entry in entries where !entry.key.isEmpty {
            let values = decode(entry.value)
            if !values.isEmpty { out[entry.key, default: []] += values }
        }
        return out
    }

    private static func decode(_ raw: String) -> [String] {
        if raw.isEmpty { return [] }
        if let array = (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [Any] {
            return array.compactMap { item -> String? in
                let text = (item as? String) ?? (item as? NSNumber)?.stringValue
                return text?.isEmpty == false ? text : nil
            }
        }
        return [raw]
    }
}

struct CsDecisionsSummary: Codable, Sendable {
    var ok = false
    var error = ""
    var stale = ""
    var total = 0
    var own = 0
    var subscribed = 0
    var origins: [String: Int] = [:]
    var types: [String: Int] = [:]
    var rows: [CsDecision] = []

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        error = c.value(.error, default: "")
        stale = c.value(.stale, default: "")
        total = c.lenientInt(.total)
        own = c.lenientInt(.own)
        subscribed = c.lenientInt(.subscribed)
        origins = c.value(.origins, default: [:])
        types = c.value(.types, default: [:])
        rows = c.value(.rows, default: [])
    }
}

struct CsAlertsSummary: Codable, Sendable {
    var ok = false
    var error = ""
    var status = 0
    var limit = 0
    var capped = false
    var rows: [CsAlert] = []

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        error = c.value(.error, default: "")
        status = c.lenientInt(.status)
        limit = c.lenientInt(.limit)
        capped = c.value(.capped, default: false)
        rows = c.value(.rows, default: [])
    }
}

struct CsSummary: Codable, Sendable {
    var version = ""
    var unchanged = false
    var decisions: CsDecisionsSummary?
    var alerts: CsAlertsSummary?

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = c.lenientString(.version)
        unchanged = c.value(.unchanged, default: false)
        decisions = c.optional(.decisions)
        alerts = c.optional(.alerts)
    }
}

struct CsDecisionPage: Decodable, Sendable {
    var rows: [CsDecision] = []
    var total = 0
    var page = 1
    var pages = 1

    private enum CodingKeys: String, CodingKey { case rows, total, page, pages }

    init(rows: [CsDecision], total: Int, page: Int, pages: Int) {
        self.rows = rows
        self.total = total
        self.page = page
        self.pages = pages
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rows = c.value(.rows, default: [])
        total = c.lenientInt(.total)
        page = c.lenientInt(.page, default: 1)
        pages = c.lenientInt(.pages, default: 1)
    }
}

/// One read of decisions or alerts: loaded, failed, or CrowdSec not set up on this server.
enum CsRead<T> {
    case loaded(T)
    case failed(String)
    case notConfigured

    var value: T? { if case let .loaded(value) = self { value } else { nil } }
    var ok: Bool { value != nil }
    var error: String? { if case let .failed(message) = self { message } else { nil } }
}

/// Everything read from the CrowdSec LAPI for one server. Port of `CrowdSecSnapshot`.
struct CrowdSecSnapshot {
    var decisions: CsRead<[CsDecision]> = .loaded([])
    var alerts: CsRead<[CsAlert]> = .loaded([])
    var alertLimit: Int?
    var alertsCapped: Bool?
    var decisionsStale: String?
    var counts: CsDecisionsSummary?
    var version: String?

    var decisionList: [CsDecision] { decisions.value ?? [] }
    var alertList: [CsAlert] { alerts.value ?? [] }
    var serverSearch: Bool { version != nil }
    var decisionTotal: Int { counts?.total ?? decisionList.count }
    var ownBans: Int { counts?.own ?? decisionList.filter(\.own).count }
    var subscribedBans: Int { counts?.subscribed ?? decisionList.filter { !$0.own }.count }

    var origins: [(String, Int)] {
        if let counts { return CrowdSecAnalytics.origins(counts.origins) }
        var tally: [String: Int] = [:]
        for decision in decisionList { tally[decision.originKey, default: 0] += 1 }
        return CrowdSecAnalytics.origins(tally)
    }

    func typeCount(_ type: String) -> Int { counts?.types[type] ?? decisionList.filter { $0.type.lowercased() == type }.count }

    var bannedIPs: Set<String> { Set(decisionList.filter { $0.scope == "Ip" || $0.scope == "Range" }.map(\.value)) }

    /// An alert is handled when a ban covers its source (or the server says so).
    func handled(_ alert: CsAlert, banned: Set<String>) -> Bool {
        decisions.ok && !alert.simulated && (alert.handled ?? banned.contains(alert.ip))
    }
}

struct CsRanked: Identifiable {
    var key: String
    var label: String
    var count: Int
    var weight: Int
    var open: Int
    var extra = ""
    var id: String { key }
}

/// Port of `CrowdSecAnalytics`.
enum CrowdSecAnalytics {
    static func filterAlerts(_ alerts: [CsAlert]) -> [CsAlert] {
        alerts.filter { !["capi", "lists"].contains($0.source.scope.lowercased()) }
    }

    static func origins(_ counts: [String: Int]) -> [(String, Int)] {
        var merged: [String: Int] = [:]
        for (origin, count) in counts { merged[origin.trimmed.lowercased().ifEmpty("other"), default: 0] += count }
        return merged.map { ($0.key, $0.value) }.sorted { $0.1 > $1.1 }
    }

    static func rank(
        _ alerts: [CsAlert],
        handled: (CsAlert) -> Bool,
        extra: ([CsAlert]) -> String = { _ in "" },
        label: (String, [CsAlert]) -> String = { key, _ in key },
        keys: (CsAlert) -> [String]
    ) -> [CsRanked] {
        var buckets: [String: [CsAlert]] = [:]
        for alert in alerts {
            for key in Set(keys(alert).filter { !$0.isEmpty }) { buckets[key, default: []].append(alert) }
        }
        return buckets.map { key, rows in
            CsRanked(
                key: key,
                label: label(key, rows),
                count: rows.count,
                weight: rows.reduce(0) { $0 + $1.eventsCount },
                open: rows.filter { !handled($0) }.count,
                extra: extra(rows)
            )
        }
        .sorted { a, b in
            if a.open != b.open { return a.open > b.open }
            if a.count != b.count { return a.count > b.count }
            if a.weight != b.weight { return a.weight > b.weight }
            return a.key < b.key
        }
    }

    static func sources(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] { rank(alerts, handled: handled) { [$0.ip] } }

    static func networks(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] {
        rank(
            alerts,
            handled: handled,
            extra: { $0.first?.countryCode ?? "" },
            label: { key, rows in rows.first?.source.asName.ifEmpty("AS\(key)") ?? "AS\(key)" },
            keys: { $0.source.asNumber.isEmpty ? [] : [$0.source.asNumber] }
        )
    }

    static func scenarios(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] { rank(alerts, handled: handled) { [$0.scenarioName] } }
    static func paths(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] { rank(alerts, handled: handled) { $0.uris } }
    static func accounts(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] { rank(alerts, handled: handled) { $0.users } }
    static func tooling(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] { rank(alerts, handled: handled) { $0.userAgents } }
    static func hosts(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] { rank(alerts, handled: handled) { $0.hosts } }

    static func routes(_ alerts: [CsAlert], handled: (CsAlert) -> Bool) -> [CsRanked] {
        rank(
            alerts,
            handled: handled,
            extra: { rows in rows.lazy.compactMap(\.hosts.first).first ?? "" },
            label: { key, _ in RouteDetailView.shortName(key).ifEmpty(key) },
            keys: { $0.routers }
        )
    }

    static func spanMillis(_ alerts: [CsAlert]) -> Double? {
        let stamps = alerts.map(\.startMillis).filter { $0 > 0 }
        guard stamps.count >= 2, let low = stamps.min(), let high = stamps.max() else { return nil }
        return high - low
    }
}

/// Tap-to-filter dimensions on the CrowdSec screen. Port of `CsFacet`.
enum CsFacet: String, CaseIterable, Identifiable {
    case scenario, ip, asn, country = "cc", uri, user, agent, verb, router, host, outcome, origin, type

    enum Reads { case alerts, decisions, both }

    var id: String { rawValue }

    var reads: Reads {
        switch self {
        case .scenario, .ip: .both
        case .origin, .type: .decisions
        default: .alerts
        }
    }

    var label: String {
        switch self {
        case .scenario: String(localized: "scenario")
        case .ip: "IP"
        case .asn: String(localized: "network")
        case .country: String(localized: "country")
        case .uri: String(localized: "path")
        case .user: String(localized: "account")
        case .agent: String(localized: "tool")
        case .verb: String(localized: "method")
        case .router: String(localized: "route")
        case .host: String(localized: "host")
        case .outcome: String(localized: "outcome")
        case .origin: String(localized: "origin")
        case .type: String(localized: "type")
        }
    }
}

struct CsFacets: Equatable {
    var values: [CsFacet: String] = [:]

    subscript(_ facet: CsFacet) -> String? { values[facet].flatMap { $0.isEmpty ? nil : $0 } }
    var active: [(CsFacet, String)] { CsFacet.allCases.compactMap { facet in self[facet].map { (facet, $0) } } }
    var isEmpty: Bool { active.isEmpty }

    mutating func toggle(_ facet: CsFacet, _ value: String) {
        guard !value.isEmpty else { return }
        if self[facet] == value { values[facet] = nil } else { values[facet] = value }
    }

    func matches(_ alert: CsAlert, country: (CsAlert) -> String, handled: (CsAlert) -> Bool, skip: CsFacet? = nil) -> Bool {
        func on(_ facet: CsFacet) -> String? { facet == skip ? nil : self[facet] }
        if let value = on(.scenario), alert.scenarioName != value { return false }
        if let value = on(.ip), alert.ip != value { return false }
        if let value = on(.asn), alert.source.asNumber != value { return false }
        if let value = on(.country), country(alert) != value { return false }
        if let value = on(.uri), !alert.uris.contains(value) { return false }
        if let value = on(.user), !alert.users.contains(value) { return false }
        if let value = on(.verb), !alert.verbs.contains(value) { return false }
        if let value = on(.agent), !alert.userAgents.contains(value) { return false }
        if let value = on(.router), !alert.routers.contains(value) { return false }
        if let value = on(.host), !alert.hosts.contains(value) { return false }
        switch on(.outcome) {
        case "sim": if !alert.simulated { return false }
        case "banned": if !handled(alert) { return false }
        case "loose": if handled(alert) { return false }
        default: break
        }
        return true
    }

    func matches(_ decision: CsDecision) -> Bool {
        if let value = self[.type], decision.type != value { return false }
        switch self[.origin] {
        case "subscribed": if decision.own { return false }
        case "own": if !decision.own { return false }
        case let origin?: if decision.originKey != origin { return false }
        case nil: break
        }
        if let value = self[.ip], decision.value != value { return false }
        if let value = self[.scenario], decision.scenario != value { return false }
        return true
    }

    func decisionQuery(_ query: String) -> CsDecisionQuery {
        CsDecisionQuery(q: query.trimmed.lowercased().isEmpty ? nil : query.trimmed.lowercased(), origin: self[.origin], type: self[.type], ip: self[.ip], scenario: self[.scenario])
    }
}

struct CsDecisionQuery: Equatable {
    var q: String?
    var origin: String?
    var type: String?
    var ip: String?
    var scenario: String?

    var filtered: Bool { origin != nil || type != nil || ip != nil || scenario != nil }
}

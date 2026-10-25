import Foundation

struct CertEntry: Codable, Hashable, Sendable {
    var resolver = ""
    var main = ""
    var sans: [String] = []
    var notAfter: String?
    var source: String?
    var certFile: String?

    private enum CodingKeys: String, CodingKey {
        case resolver, main, sans, source, certFile
        case notAfter = "not_after"
    }

    init(resolver: String, main: String, sans: [String] = [], notAfter: String? = nil, source: String? = nil, certFile: String? = nil) {
        self.resolver = resolver
        self.main = main
        self.sans = sans
        self.notAfter = notAfter
        self.source = source
        self.certFile = certFile
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        resolver = c.lenientString(.resolver)
        main = c.lenientString(.main)
        sans = ((try? c.decodeIfPresent([JSONValue].self, forKey: .sans)) ?? []).compactMap(\.stringValue)
        notAfter = c.optional(.notAfter)
        source = c.optional(.source)
        certFile = c.optional(.certFile)
    }
}

struct CertsResponse: Codable, Sendable {
    var certs: [CertEntry] = []
    var error: String?

    init(certs: [CertEntry]) { self.certs = certs }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        certs = c.value(.certs, default: [])
        error = c.optional(.error)
    }
}

enum CertHealth { case healthy, expiring, critical, unknown }

struct CertRow: Identifiable, Hashable {
    var key: String
    var main: String
    var resolver: String
    var extraDomains: [String]
    var domainCount: Int
    var daysLeft: Int?
    var expiresOn: Date?
    var health: CertHealth
    var origin: String?

    var id: String { key }
    var title: String { main.ifEmpty(String(localized: "Unknown")) }

    var daysLeftLabel: String? {
        guard let daysLeft else { return nil }
        return daysLeft <= 0 ? String(localized: "expired") : String(localized: "\(daysLeft)d left")
    }

    var status: TMStatus {
        switch health {
        case .healthy: .ok
        case .expiring: .warn
        case .critical: .error
        case .unknown: .unknown
        }
    }
}

/// Port of `CertRows`.
enum CertRows {
    static func from(_ certs: [CertEntry], now: Date = Date()) -> [CertRow] {
        certs.enumerated().map { row($0.element, now: now, index: $0.offset) }.sorted { a, b in
            if (a.daysLeft == nil) != (b.daysLeft == nil) { return a.daysLeft != nil }
            if (a.daysLeft ?? .max) != (b.daysLeft ?? .max) { return (a.daysLeft ?? .max) < (b.daysLeft ?? .max) }
            return a.main.lowercased() < b.main.lowercased()
        }
    }

    static func row(_ entry: CertEntry, now: Date, index: Int = 0) -> CertRow {
        let expiry = parseExpiry(entry.notAfter)
        let daysLeft = expiry.map { Int(ceil($0.timeIntervalSince(now) / 86_400)) }
        let extras = entry.sans.filter { !$0.isBlank && $0 != entry.main }
        let origin = entry.source.flatMap { $0.isBlank ? nil : $0 }
            ?? entry.certFile.flatMap { $0.isBlank ? nil : $0.components(separatedBy: "/").last }
        return CertRow(
            key: "\(index):\(entry.resolver):\(entry.main)",
            main: entry.main,
            resolver: entry.resolver,
            extraDomains: extras,
            domainCount: extras.isEmpty ? 1 : extras.count + 1,
            daysLeft: daysLeft,
            expiresOn: expiry,
            health: health(daysLeft),
            origin: origin
        )
    }

    static func health(_ daysLeft: Int?) -> CertHealth {
        guard let daysLeft else { return .unknown }
        return daysLeft < 7 ? .critical : daysLeft < 30 ? .expiring : .healthy
    }

    static func parseExpiry(_ value: String?) -> Date? {
        guard let value, !value.isBlank else { return nil }
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: value)
    }
}

struct CertVerdict: Codable, Hashable, Sendable {
    var main = ""
    var resolver = ""
    var unused = false
    var orphaned = false
    var why = ""

    var key: String { "\(resolver)|\(main)" }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        main = c.value(.main, default: "")
        resolver = c.value(.resolver, default: "")
        unused = c.value(.unused, default: false)
        orphaned = c.value(.orphaned, default: false)
        why = c.value(.why, default: "")
    }
}

struct CertUsage: Decodable, Sendable {
    var certs: [CertVerdict] = []
    var unusedKnown = false
    var why = ""

    private enum CodingKeys: String, CodingKey {
        case certs, why
        case unusedKnown = "unused_known"
    }

    init(unusedKnown: Bool) { self.unusedKnown = unusedKnown }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        certs = c.value(.certs, default: [])
        unusedKnown = c.value(.unusedKnown, default: false)
        why = c.value(.why, default: "")
    }

    func verdict(_ resolver: String, _ main: String) -> CertVerdict? { certs.first { $0.key == "\(resolver)|\(main)" } }
}

struct CertManageState: Decodable, Sendable {
    static let tooOld = "too_old"

    var available = false
    var reason = ""

    private enum CodingKeys: String, CodingKey { case available, reason }

    init(available: Bool = false, reason: String = "") {
        self.available = available
        self.reason = reason
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        available = c.value(.available, default: false)
        reason = c.value(.reason, default: "")
    }

    var reasonText: String { reason == Self.tooOld ? String(localized: "This server is too old to remove certificates") : reason }
}

struct CertRef: Codable, Hashable {
    var resolver: String
    var main: String
}

struct CertDeleteRequest: Encodable {
    var server: String
    var certs: [CertRef]
}

struct CertDeleteResponse: Decodable, Sendable {
    var ok = false
    var removed = 0
    var restarted = false
    var restartError = ""
    var partial = false
    var error: String?

    private enum CodingKeys: String, CodingKey {
        case ok, removed, restarted, partial, error
        case restartError = "restart_error"
    }

    init(ok: Bool, removed: Int, restarted: Bool) {
        self.ok = ok
        self.removed = removed
        self.restarted = restarted
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        removed = c.lenientInt(.removed)
        restarted = c.value(.restarted, default: false)
        restartError = c.value(.restartError, default: "")
        partial = c.value(.partial, default: false)
        error = c.optional(.error)
    }

    /// The toast after a removal, port of `CertCleanup.outcome`.
    func outcome(asked: Int) -> String {
        let count = removed > 0 ? removed : asked
        if ok && restarted { return String(localized: "Removed \(count) certificates") }
        if ok {
            if !restartError.isBlank {
                return String(localized: "Removed \(count) certificates, but Traefik did not restart: \(restartError). The change is undone until it does.")
            }
            return String(localized: "Removed \(count) certificates, but Traefik did not restart. The change is undone until it does.")
        }
        if partial { return error ?? String(localized: "Certificate removal stopped partway") }
        return error ?? String(localized: "Could not remove the certificate")
    }
}

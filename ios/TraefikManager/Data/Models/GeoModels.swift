import Foundation

struct GeoStatus: Decodable, Sendable {
    var enabled = false
    var available = false
    var dbPath = ""
    var dbDate: String?

    var usable: Bool { enabled && available }

    private enum CodingKeys: String, CodingKey {
        case enabled, available
        case dbPath = "db_path"
        case dbDate = "db_date"
    }

    init(enabled: Bool, available: Bool) {
        self.enabled = enabled
        self.available = available
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = c.value(.enabled, default: false)
        available = c.value(.available, default: false)
        dbPath = c.value(.dbPath, default: "")
        dbDate = c.optional(.dbDate)
    }
}

struct GeoLookupRequest: Encodable {
    var ips: [String]
    var aggregate: Bool
}

struct GeoResult: Decodable, Sendable {
    var countryCode = ""

    private enum CodingKeys: String, CodingKey { case countryCode = "country_code" }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        countryCode = c.value(.countryCode, default: "")
    }
}

struct GeoLookupResponse: Decodable, Sendable {
    var enabled = false
    var available = false
    var results: [String: GeoResult] = [:]
    var codes: [String: String] = [:]

    private enum CodingKeys: String, CodingKey { case enabled, available, results, codes }

    init(codes: [String: String]) {
        enabled = true
        available = true
        self.codes = codes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = c.value(.enabled, default: false)
        available = c.value(.available, default: false)
        results = c.value(.results, default: [:])
        codes = c.value(.codes, default: [:])
    }
}

struct CountryCount: Identifiable, Hashable {
    var code: String
    var name: String
    var count: Int
    var id: String { code }
    var flag: String { Countries.flag(code) }
}

enum Countries {
    static func flag(_ code: String) -> String {
        guard code.count == 2, code.allSatisfy(\.isLetter) else { return "" }
        return code.uppercased().unicodeScalars
            .compactMap { UnicodeScalar(0x1F1E6 + $0.value - 65) }
            .map(String.init)
            .joined()
    }

    static func name(_ code: String) -> String {
        guard code.count == 2 else { return code }
        return Locale.current.localizedString(forRegionCode: code.uppercased()) ?? code.uppercased()
    }

    /// Private, loopback and CGNAT addresses never resolve, so they are not sent for lookup.
    static func worthLookingUp(_ ip: String) -> Bool {
        let kind = LogParser.ipClass(ip)
        return kind == "public" || kind == "unknown"
    }

    static func counts(codesByIP: [String: String], ips: [String]) -> [CountryCount] {
        var tally: [String: Int] = [:]
        for ip in ips {
            if let code = codesByIP[ip] { tally[code, default: 0] += 1 }
        }
        return tally.map { CountryCount(code: $0.key, name: name($0.key), count: $0.value) }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.name < $1.name }
    }
}

/// Country lookups through the server's GeoIP database, cached per session. Port of `GeoRepository`.
actor GeoStore {
    private var status: GeoStatus?
    private var cache: [String: String?] = [:]
    private let batch = 1000
    private let limit = 5000

    func reset() {
        status = nil
        cache = [:]
    }

    func status(service: TMService) async -> GeoStatus {
        if let status { return status }
        let fetched = (try? await service.geoStatus()) ?? GeoStatus(enabled: false, available: false)
        status = fetched
        return fetched
    }

    func lookup(_ ips: [String], service: TMService) async -> [String: String] {
        guard await status(service: service).usable else { return [:] }
        var seen = Set<String>()
        let wanted = ips.map(\.trimmed).filter { !$0.isEmpty && Countries.worthLookingUp($0) && seen.insert($0).inserted }
        let missing = wanted.filter { cache[$0] == nil }
        for start in stride(from: 0, to: missing.count, by: batch) {
            let chunk = Array(missing[start..<min(start + batch, missing.count)])
            guard let response = try? await service.geoLookup(ips: chunk) else { continue }
            if !response.available {
                status?.available = false
                break
            }
            let resolved = response.codes.isEmpty ? response.results.mapValues(\.countryCode) : response.codes
            for ip in chunk { cache[ip] = .some(resolved[ip].flatMap { $0.isEmpty ? nil : $0 }) }
        }
        if cache.count > limit { cache = [:] }
        var result: [String: String] = [:]
        for ip in wanted { if let code = cache[ip] ?? nil { result[ip] = code } }
        return result
    }
}

import Foundation

struct LogsResponse: Decodable, Sendable {
    var lines: [String]?
    var error: String?

    private enum CodingKeys: String, CodingKey { case lines, error }

    init(lines: [String]) { self.lines = lines }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        lines = c.optional(.lines)
        error = c.optional(.error)
    }
}

enum LogFormat: Sendable { case json, clf, genericCLF }

/// One parsed access log request, port of `LogEntry`.
struct LogEntry: Hashable, Sendable {
    var format: LogFormat
    var ip = ""
    var date = ""
    var method = ""
    var path = ""
    var status = 0
    var origin: Int?
    var size = ""
    var domain = ""
    var scheme = ""
    var entryPoint = ""
    var router = ""
    var service = ""
    var serviceUrl = ""
    var retries = 0
    var tls = ""
    var durMs: Double?
    var duration = ""
    var raw = ""

    var statusClass: String { LogParser.statusClass(status) }
    var statusName: String? { LogParser.statusName(status) }
    var heldOpen: Bool { status == 101 || status == 0 }
    var pattern: String { LogParser.pattern(path) }
    var timestamp: Double? { LogParser.parseTimestamp(date) }
}

struct LogLine: Identifiable, Hashable, Sendable {
    var index: Int
    var raw: String
    var entry: LogEntry?
    var id: Int { index }
}

/// Parses Traefik JSON and CLF access log lines. Line-for-line port of `LogParser`.
enum LogParser {
    nonisolated(unsafe) private static let clfFull = #/^(\S+) \S+ \S+ \[([^\]]+)] "(\S+) (\S+)[^"]*" (\d+|-) (\S+) "[^"]*" "[^"]*" \S+ "([^"]*)" "([^"]*)" (\S+)/#
    nonisolated(unsafe) private static let clfBasic = #/^(\S+) \S+ \S+ \[([^\]]+)] "(\S+) (\S+)[^"]*" (\d+|-) (\S+)/#
    nonisolated(unsafe) private static let clfDate = #/^(\d{2})\/([A-Za-z]{3})\/(\d{4}):(\d{2}):(\d{2}):(\d{2})\s*([+-]\d{2})(\d{2})$/#
    nonisolated(unsafe) private static let durationPart = #/(\d+(?:\.\d+)?)(ns|µs|us|ms|h|m|s)/#
    private static let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

    static func parse(_ raw: String) -> LogEntry? {
        if raw.drop(while: \.isWhitespace).hasPrefix("{"), let entry = parseJSON(raw) { return entry }
        if let match = raw.firstMatch(of: clfFull) {
            let durationText = String(match.output.9)
            return LogEntry(
                format: .clf,
                ip: String(match.output.1),
                date: String(match.output.2),
                method: String(match.output.3),
                path: String(match.output.4),
                status: Int(match.output.5) ?? 0,
                size: String(match.output.6),
                router: match.output.7 == "-" ? "" : String(match.output.7),
                serviceUrl: match.output.8 == "-" ? "" : String(match.output.8),
                durMs: parseDuration(durationText),
                duration: durationText,
                raw: raw
            )
        }
        if let match = raw.firstMatch(of: clfBasic) {
            return LogEntry(
                format: .genericCLF,
                ip: String(match.output.1),
                date: String(match.output.2),
                method: String(match.output.3),
                path: String(match.output.4),
                status: Int(match.output.5) ?? 0,
                size: String(match.output.6),
                raw: raw
            )
        }
        return nil
    }

    private static func parseJSON(_ raw: String) -> LogEntry? {
        guard let object = (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [String: Any] else { return nil }
        func text(_ key: String) -> String {
            switch object[key] {
            case let value as String: value
            case let value as NSNumber: value.stringValue
            default: ""
            }
        }
        func number(_ key: String) -> Int64? {
            switch object[key] {
            case let value as NSNumber: value.int64Value
            case let value as String:
                value.firstMatch(of: #/^\s*-?\d+/#).flatMap { Int64($0.output.trimmingCharacters(in: .whitespaces)) }
            default: nil
            }
        }
        let method = text("RequestMethod")
        let path = text("RequestPath")
        let downstream = number("DownstreamStatus")
        if method.isEmpty && path.isEmpty && (downstream == nil || downstream == 0) { return nil }
        let originStatus = number("OriginStatus")
        let durationNs = number("Duration")
        let size: String = if object["DownstreamContentSize"] != nil && !(object["DownstreamContentSize"] is NSNull) {
            text("DownstreamContentSize")
        } else if object["OriginContentSize"] != nil && !(object["OriginContentSize"] is NSNull) {
            text("OriginContentSize")
        } else {
            ""
        }
        return LogEntry(
            format: .json,
            ip: text("ClientHost").ifEmpty(hostOnly(text("ClientAddr"))),
            date: text("StartUTC").ifEmpty(text("StartLocal").ifEmpty(text("time"))),
            method: method,
            path: path,
            status: Int(downstream ?? originStatus ?? 0),
            origin: originStatus.map(Int.init),
            size: size,
            domain: text("RequestHost").ifEmpty(hostOnly(text("RequestAddr"))),
            scheme: text("RequestScheme"),
            entryPoint: text("entryPointName").ifEmpty(text("EntryPointName")),
            router: text("RouterName"),
            service: text("ServiceName"),
            serviceUrl: text("ServiceURL").ifEmpty(text("ServiceAddr")),
            retries: Int(number("RetryAttempts") ?? 0),
            tls: text("TLSVersion"),
            durMs: durationNs.map { Double($0) / 1e6 },
            duration: formatDuration(durationNs ?? 0),
            raw: raw
        )
    }

    static func hostOnly(_ address: String) -> String {
        if address.isEmpty { return "" }
        if address.hasPrefix("[") {
            let end = address.firstIndex(of: "]") ?? address.endIndex
            return String(address[address.index(after: address.startIndex)..<end])
        }
        let parts = address.components(separatedBy: ":")
        return parts.count == 2 ? parts[0] : address
    }

    static func parseDuration(_ value: String?) -> Double? {
        guard let value, !value.isEmpty, value != "-" else { return nil }
        var total = 0.0
        var matched = false
        for match in value.matches(of: durationPart) {
            guard let amount = Double(match.output.1) else { continue }
            let factor: Double
            switch match.output.2 {
            case "ns": factor = 1e-6
            case "µs", "us": factor = 1e-3
            case "ms": factor = 1
            case "h": factor = 3_600_000
            case "m": factor = 60_000
            case "s": factor = 1000
            default: continue
            }
            total += amount * factor
            matched = true
        }
        return matched ? total : Double(value.trimmed)
    }

    static func formatDuration(_ nanos: Int64) -> String {
        if nanos <= 0 { return "" }
        if nanos >= 1_000_000_000 { return String(format: "%.2fs", Double(nanos) / 1e9) }
        if nanos >= 1_000_000 { return "\(Int((Double(nanos) / 1e6).rounded()))ms" }
        if nanos >= 1000 { return "\(Int((Double(nanos) / 1e3).rounded()))µs" }
        return "\(nanos)ns"
    }

    /// Epoch milliseconds from a CLF or ISO-8601 date.
    static func parseTimestamp(_ date: String) -> Double? {
        if date.isEmpty { return nil }
        if let match = date.firstMatch(of: clfDate) {
            guard let month = months.firstIndex(of: String(match.output.2)) else { return nil }
            var components = DateComponents()
            components.year = Int(match.output.3)
            components.month = month + 1
            components.day = Int(match.output.1)
            components.hour = Int(match.output.4)
            components.minute = Int(match.output.5)
            components.second = Int(match.output.6)
            components.timeZone = TimeZone(secondsFromGMT: 0)
            guard let base = Calendar(identifier: .gregorian).date(from: components) else { return nil }
            let hours = Int(match.output.7) ?? 0
            let minutes = Int(match.output.8) ?? 0
            let sign = match.output.7.hasPrefix("-") ? -1.0 : 1.0
            return base.timeIntervalSince1970 * 1000 - sign * Double(abs(hours) * 60 + minutes) * 60_000
        }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let parsed = formatter.date(from: date) { return parsed.timeIntervalSince1970 * 1000 }
        formatter.formatOptions = [.withInternetDateTime]
        if let parsed = formatter.date(from: date) { return parsed.timeIntervalSince1970 * 1000 }
        return nil
    }

    static func statusClass(_ status: Int) -> String {
        switch status {
        case 500...599: "5xx"
        case 400...499: "4xx"
        case 300...399: "3xx"
        case 200...299: "2xx"
        case 100...199: "1xx"
        default: "other"
        }
    }

    static func statusName(_ status: Int) -> String? {
        if status == 0 { return String(localized: "tunnel") }
        return statusNames[status]
    }

    private static let statusNames: [Int: String] = [
        100: String(localized: "Continue"), 101: String(localized: "Switching Protocols"), 103: String(localized: "Early Hints"),
        200: String(localized: "OK"), 201: String(localized: "Created"), 202: String(localized: "Accepted"),
        204: String(localized: "No Content"), 206: String(localized: "Partial Content"),
        301: String(localized: "Moved Permanently"), 302: String(localized: "Found"), 303: String(localized: "See Other"),
        304: String(localized: "Not Modified"), 307: String(localized: "Temporary Redirect"), 308: String(localized: "Permanent Redirect"),
        400: String(localized: "Bad Request"), 401: String(localized: "Unauthorized"), 402: String(localized: "Payment Required"),
        403: String(localized: "Forbidden"), 404: String(localized: "Not Found"), 405: String(localized: "Method Not Allowed"),
        406: String(localized: "Not Acceptable"), 407: String(localized: "Proxy Authentication Required"),
        408: String(localized: "Request Timeout"), 409: String(localized: "Conflict"), 410: String(localized: "Gone"),
        411: String(localized: "Length Required"), 413: String(localized: "Payload Too Large"), 414: String(localized: "URI Too Long"),
        415: String(localized: "Unsupported Media Type"), 418: String(localized: "I am a teapot"),
        421: String(localized: "Misdirected Request"), 422: String(localized: "Unprocessable Entity"),
        426: String(localized: "Upgrade Required"), 429: String(localized: "Too Many Requests"),
        431: String(localized: "Request Header Fields Too Large"), 451: String(localized: "Unavailable For Legal Reasons"),
        500: String(localized: "Internal Server Error"), 501: String(localized: "Not Implemented"), 502: String(localized: "Bad Gateway"),
        503: String(localized: "Service Unavailable"), 504: String(localized: "Gateway Timeout"),
        505: String(localized: "HTTP Version Not Supported"), 507: String(localized: "Insufficient Storage"),
        511: String(localized: "Network Authentication Required"),
    ]

    static func durBand(_ durMs: Double?) -> String {
        guard let durMs else { return "" }
        return durMs < 100 ? "fast" : durMs < 500 ? "med" : "slow"
    }

    /// Collapses ids, dates, UUIDs and hashes in a path so similar requests group together.
    static func pattern(_ path: String) -> String {
        let clean = path.components(separatedBy: "?")[0]
        guard clean.contains("/") else { return clean }
        return clean.components(separatedBy: "/").map { segment in
            if segment.isEmpty { return segment }
            if segment.wholeMatch(of: #/\d+/#) != nil
                || segment.wholeMatch(of: #/\d{4}-\d{2}-\d{2}/#) != nil
                || segment.wholeMatch(of: #/(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/#) != nil
                || segment.wholeMatch(of: #/(?i)[0-9a-f]{12,}/#) != nil {
                return "<_>"
            }
            return segment
        }.joined(separator: "/")
    }

    static func providerOf(_ name: String) -> String {
        guard let index = name.firstIndex(of: "@") else { return "" }
        return String(name[name.index(after: index)...])
    }

    static func ipClass(_ ip: String) -> String {
        let raw = ip.trimmed
        if raw.isEmpty { return "unknown" }
        if let match = raw.wholeMatch(of: #/(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/#) {
            let octets = [match.output.1, match.output.2, match.output.3, match.output.4].map { Int($0) ?? 256 }
            if octets.contains(where: { $0 > 255 }) { return "unknown" }
            let (a, b) = (octets[0], octets[1])
            if a == 127 { return "loopback" }
            if a == 169 && b == 254 { return "link-local" }
            if a == 10 || (a == 172 && (16...31).contains(b)) || (a == 192 && b == 168) { return "private" }
            if a == 100 && (64...127).contains(b) { return "cgnat" }
            return "public"
        }
        var v6 = raw
        if v6.hasPrefix("[") { v6.removeFirst() }
        if v6.hasSuffix("]") { v6.removeLast() }
        v6 = (v6.components(separatedBy: "%").first ?? v6).lowercased()
        guard v6.contains(":") else { return "unknown" }
        if v6 == "::1" { return "loopback" }
        if v6.hasPrefix("fe80") { return "link-local" }
        if v6.hasPrefix("fc") || v6.hasPrefix("fd") { return "private" }
        if v6.hasPrefix("::ffff:") && v6.dropFirst(7).contains(".") { return ipClass(String(v6.dropFirst(7))) }
        return "public"
    }

    static func shortName(_ name: String) -> String { RouteDetailView.shortName(name) }

    static func formatCount(_ value: Int) -> String { value.formatted(.number) }

    static func formatMs(_ value: Double?) -> String {
        guard let value else { return "-" }
        if value >= 10_000 { return "\(Int((value / 1000).rounded()))s" }
        if value >= 1000 { return String(format: "%.2fs", value / 1000) }
        if value >= 1 { return "\(Int(value.rounded()))ms" }
        if value > 0 { return trimZeros(String(format: "%.2f", value)) + "ms" }
        return "0ms"
    }

    static func heroMs(_ value: Double?) -> (String, String) {
        guard let value else { return ("-", "") }
        if value >= 1000 { return (String(format: "%.1f", value / 1000), "s") }
        if value >= 1 { return (String(Int(value.rounded())), "ms") }
        return (trimZeros(String(format: "%.2f", value)).ifEmpty("0"), "ms")
    }

    static func spanText(_ millis: Double) -> String {
        let seconds = max(0, Int((millis / 1000).rounded()))
        if seconds < 60 { return "\(seconds)s" }
        if seconds < 3600 { return "\(seconds / 60)m \(String(format: "%02d", seconds % 60))s" }
        if seconds < 86_400 { return "\(seconds / 3600)h \(String(format: "%02d", (seconds % 3600) / 60))m" }
        return "\(seconds / 86_400)d \((seconds % 86_400) / 3600)h"
    }

    private static func trimZeros(_ value: String) -> String {
        var text = value
        while text.hasSuffix("0") { text.removeLast() }
        while text.hasSuffix(".") || text.hasSuffix(",") { text.removeLast() }
        return text.ifEmpty("0")
    }
}

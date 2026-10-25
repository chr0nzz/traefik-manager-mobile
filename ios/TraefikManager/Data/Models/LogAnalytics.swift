import Foundation

enum LogHealth { case up, warn, down }

struct StatusBuckets {
    var oneXx = 0, twoXx = 0, threeXx = 0, fourXx = 0, fiveXx = 0, other = 0
    var total: Int { oneXx + twoXx + threeXx + fourXx + fiveXx + other }
}

struct LatencyStats {
    var p50: Double?
    var p95: Double?
    var max: Double?
    var average: Double?
    var timed = 0
    var fast = 0
    var medium = 0
    var slow = 0
    var verySlow = 0
    var held = 0
    var heldMax: Double?
    var untimed = 0
    var slowest: LogEntry?
}

struct RankedGroup: Identifiable {
    var key: String
    var count: Int
    var clientErrors: Int
    var serverErrors: Int
    var worstCode: Int?
    var worstCount: Int

    var id: String { key }
    var errors: Int { clientErrors + serverErrors }
    var share: Double { count == 0 ? 0 : Double(errors) / Double(count) }
}

struct FailureRow: Identifiable {
    var status: Int
    var path: String
    var count: Int
    var pathTotal: Int

    var id: String { "\(status) \(path)" }
    var share: Int { pathTotal == 0 ? 0 : Int((Double(count) / Double(pathTotal) * 100).rounded()) }
}

/// Aggregate view of the visible log lines. Port of `LogWindow`.
struct LogWindow {
    var fetched = 0
    var parsed = 0
    var unparsed = 0
    var spanMillis: Double?
    var requestsPerMinute: Int?
    var statuses = StatusBuckets()
    var codeRank: [(Int, Int)] = []
    var latency = LatencyStats()
    var retries = 0
    var methods: [RankedGroup] = []
    var domains: [RankedGroup] = []
    var paths: [RankedGroup] = []
    var clients: [RankedGroup] = []
    var services: [RankedGroup] = []
    var failures: [FailureRow] = []
    var jsonFormat = false

    var health: LogHealth {
        if statuses.fiveXx > 0 { return .down }
        if statuses.fourXx > 0 || latency.slow > 0 { return .warn }
        return .up
    }

    var clientErrors: Int { statuses.fourXx }
    var serverErrors: Int { statuses.fiveXx }
}

/// Port of `LogAnalytics`.
enum LogAnalytics {
    static func build(_ entries: [LogEntry], fetched: Int, unparsed: Int) -> LogWindow {
        guard !entries.isEmpty else { return LogWindow(fetched: fetched, parsed: 0, unparsed: unparsed) }
        let failing: [LogEntry] = entries.filter { $0.status >= 400 }
        let grouped: [Int: [LogEntry]] = Dictionary(grouping: failing, by: \.status)
        let codes: [(Int, Int)] = grouped
            .map { ($0.key, $0.value.count) }
            .sorted { $0.1 != $1.1 ? $0.1 > $1.1 : $0.0 < $1.0 }
        var window = LogWindow(fetched: fetched, parsed: entries.count, unparsed: unparsed)
        window.spanMillis = span(entries)
        window.requestsPerMinute = rate(entries)
        window.statuses = buckets(entries)
        window.codeRank = codes
        window.latency = latency(entries)
        window.retries = entries.reduce(0) { $0 + $1.retries }
        window.methods = rank(entries, key: { (entry: LogEntry) -> String in entry.method })
        window.domains = rank(entries, key: { (entry: LogEntry) -> String in entry.domain })
        window.paths = rank(entries, key: { (entry: LogEntry) -> String in entry.pattern })
        window.clients = rank(entries, key: { (entry: LogEntry) -> String in entry.ip })
        window.services = rank(entries, key: { (entry: LogEntry) -> String in entry.service.ifEmpty(entry.router) })
        window.failures = failures(entries)
        window.jsonFormat = entries.contains { $0.format == .json }
        return window
    }

    static func buckets(_ entries: [LogEntry]) -> StatusBuckets {
        var buckets = StatusBuckets()
        for entry in entries {
            switch entry.statusClass {
            case "1xx": buckets.oneXx += 1
            case "2xx": buckets.twoXx += 1
            case "3xx": buckets.threeXx += 1
            case "4xx": buckets.fourXx += 1
            case "5xx": buckets.fiveXx += 1
            default: buckets.other += 1
            }
        }
        return buckets
    }

    static func span(_ entries: [LogEntry]) -> Double? {
        let stamps = entries.compactMap(\.timestamp)
        guard stamps.count >= 2, let low = stamps.min(), let high = stamps.max(), high >= low else { return nil }
        return high - low
    }

    private static func rate(_ entries: [LogEntry]) -> Int? {
        guard let spanMillis = span(entries), spanMillis > 1000 else { return nil }
        return Int((Double(entries.count) / (spanMillis / 60_000)).rounded())
    }

    static func latency(_ entries: [LogEntry]) -> LatencyStats {
        let held = entries.filter { $0.durMs != nil && $0.heldOpen }
        let timed = entries.filter { $0.durMs != nil && !$0.heldOpen }
        let durations = timed.compactMap(\.durMs).sorted()
        func pick(_ quantile: Double) -> Double? {
            guard !durations.isEmpty else { return nil }
            return durations[min(durations.count - 1, Int(floor(Double(durations.count) * quantile)))]
        }
        return LatencyStats(
            p50: pick(0.5),
            p95: pick(0.95),
            max: durations.last,
            average: durations.isEmpty ? nil : durations.reduce(0, +) / Double(durations.count),
            timed: timed.count,
            fast: durations.filter { $0 < 100 }.count,
            medium: durations.filter { $0 >= 100 && $0 < 500 }.count,
            slow: durations.filter { $0 >= 500 }.count,
            verySlow: durations.filter { $0 >= 2000 }.count,
            held: held.count,
            heldMax: held.compactMap(\.durMs).max(),
            untimed: entries.count - timed.count - held.count,
            slowest: timed.max { ($0.durMs ?? 0) < ($1.durMs ?? 0) }
        )
    }

    /// Groups by key, worst first: server errors, client errors, error share, volume.
    static func rank(_ entries: [LogEntry], key: (LogEntry) -> String) -> [RankedGroup] {
        Dictionary(grouping: entries.filter { !key($0).isEmpty && key($0) != "-" }, by: key)
            .map { name, rows in
                let codes = Dictionary(grouping: rows.filter { $0.status >= 400 }, by: \.status).mapValues(\.count)
                let worst = codes.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key > $1.key }.first
                return RankedGroup(
                    key: name,
                    count: rows.count,
                    clientErrors: rows.filter { (400...499).contains($0.status) }.count,
                    serverErrors: rows.filter { (500...599).contains($0.status) }.count,
                    worstCode: worst?.key,
                    worstCount: worst?.value ?? 0
                )
            }
            .sorted { a, b in
                if a.serverErrors != b.serverErrors { return a.serverErrors > b.serverErrors }
                if a.clientErrors != b.clientErrors { return a.clientErrors > b.clientErrors }
                if a.share != b.share { return a.share > b.share }
                if a.count != b.count { return a.count > b.count }
                return a.key < b.key
            }
    }

    static func failures(_ entries: [LogEntry]) -> [FailureRow] {
        let failing = entries.filter { $0.status >= 400 }
        guard !failing.isEmpty else { return [] }
        let totals = Dictionary(grouping: entries.filter { !$0.path.isEmpty }, by: \.pattern).mapValues(\.count)
        return Dictionary(grouping: failing) { "\($0.status)\u{1}\($0.pattern)" }
            .map { _, rows in
                FailureRow(status: rows[0].status, path: rows[0].pattern, count: rows.count, pathTotal: totals[rows[0].pattern] ?? rows.count)
            }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.status < $1.status }
    }
}

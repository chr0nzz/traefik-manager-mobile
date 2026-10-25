import Foundation
import Observation

let logLineSteps = [100, 200, 500, 1000]

/// Tap-to-filter dimensions, port of `LogFacet`.
enum LogFacet: String, CaseIterable, Identifiable {
    case status, method, domain, path, ip, ipClass = "ipclass", service, router, provider, duration = "dur"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .status: String(localized: "status")
        case .method: String(localized: "method")
        case .domain: String(localized: "domain")
        case .path: String(localized: "path")
        case .ip: "ip"
        case .ipClass: String(localized: "ipclass")
        case .service: String(localized: "service")
        case .router: String(localized: "router")
        case .provider: String(localized: "provider")
        case .duration: String(localized: "dur")
        }
    }

    func hit(_ value: String, _ entry: LogEntry) -> Bool {
        switch self {
        case .status: Self.statusMatch(entry.status, value)
        case .method: entry.method == value
        case .domain: entry.domain == value
        case .path: value.hasPrefix("~") ? entry.pattern == String(value.dropFirst()) : entry.path == value
        case .ip: entry.ip == value
        case .ipClass: LogParser.ipClass(entry.ip) == value
        case .service: entry.service == value
        case .router: entry.router == value
        case .provider: LogParser.providerOf(entry.service.ifEmpty(entry.router)) == value
        case .duration:
            entry.durMs == nil ? false : value == "held" ? entry.heldOpen : !entry.heldOpen && LogParser.durBand(entry.durMs) == value
        }
    }

    func display(_ value: String) -> String {
        switch self {
        case .path: value.hasPrefix("~") ? String(value.dropFirst()) : value
        case .service, .router: LogParser.shortName(value)
        default: value
        }
    }

    static func statusMatch(_ status: Int, _ spec: String) -> Bool {
        if spec == "errors" { return status >= 400 }
        if spec == "other" { return LogParser.statusClass(status) == "other" }
        if spec.count == 3 && spec.hasSuffix("xx") { return LogParser.statusClass(status) == spec }
        return String(status) == spec
    }
}

/// State for the logs screen. Port of `LogsViewModel` + the derived values on `LogsUiState`.
@MainActor
@Observable
final class LogsModel {
    private(set) var lines: [LogLine] = []
    private(set) var loading = true
    private(set) var refreshing = false
    private(set) var serverError: String?
    private(set) var loadError: String?
    private(set) var geoEnabled = false
    private(set) var countryByIP: [String: String] = [:]
    var lineCount = 100
    var query = ""
    var facets: [LogFacet: String] = [:]
    var country: String?
    private(set) var autoRefresh = false

    private var autoTask: Task<Void, Never>?

    // MARK: Derived

    var parsedEntries: [LogEntry] { lines.compactMap(\.entry) }
    var fetched: Int { lines.count }
    var unparsed: Int { lines.filter { $0.entry == nil }.count }
    var selected: Bool { !facets.isEmpty || country != nil || !query.isBlank }

    private func matchesQuery(_ line: LogLine) -> Bool {
        let needle = query.trimmed.lowercased()
        return needle.isEmpty || line.raw.lowercased().contains(needle)
    }

    private func matchesFacets(_ entry: LogEntry?) -> Bool {
        if facets.isEmpty { return true }
        guard let entry else { return false }
        return facets.allSatisfy { $0.key.hit($0.value, entry) }
    }

    var faceted: [LogLine] { lines.filter { matchesQuery($0) && matchesFacets($0.entry) } }

    var visible: [LogLine] {
        faceted.filter { line in country == nil || (line.entry.map { countryByIP[$0.ip] == country } ?? false) }
    }

    var window: LogWindow { LogAnalytics.build(visible.compactMap(\.entry), fetched: fetched, unparsed: unparsed) }

    var countries: [CountryCount] {
        Countries.counts(codesByIP: countryByIP, ips: faceted.compactMap(\.entry).map(\.ip))
    }

    /// Facets that no longer match anything given the other filters.
    var deadFacets: Set<LogFacet> {
        Set(facets.keys.filter { facet in
            let others = facets.filter { $0.key != facet }
            return !lines.contains { line in
                guard let entry = line.entry, matchesQuery(line) else { return false }
                return others.allSatisfy { $0.key.hit($0.value, entry) } && facet.hit(facets[facet]!, entry)
            }
        })
    }

    var nextLineStep: Int? { logLineSteps.first { $0 > lineCount } }

    var selectionSpan: Double? { selected ? LogAnalytics.span(visible.compactMap(\.entry)) : nil }

    var isJSON: Bool { parsedEntries.contains { $0.format == .json } }

    var nanoPrecision: Bool {
        parsedEntries.contains { $0.format == .json && ($0.durMs ?? 0).truncatingRemainder(dividingBy: 1) != 0 }
    }

    var tlsFields: Bool { parsedEntries.contains { !$0.tls.isEmpty } }

    var formatLabel: String {
        if parsedEntries.isEmpty { return String(localized: "no parsed lines") }
        if isJSON { return String(localized: "json access log") }
        if parsedEntries.contains(where: { $0.format == .clf }) { return String(localized: "clf access log") }
        return String(localized: "generic clf access log")
    }

    // MARK: Actions

    func toggleFacet(_ facet: LogFacet, _ value: String) {
        if facets[facet] == value { facets[facet] = nil } else { facets[facet] = value }
    }

    func clearAll() {
        facets = [:]
        country = nil
        query = ""
    }

    func toggleCountry(_ code: String) { country = country == code ? nil : code }

    func setLineCount(_ value: Int, app: AppModel) {
        guard value != lineCount else { return }
        lineCount = value
        Task { await load(app: app, initial: false) }
    }

    func toggleAutoRefresh(app: AppModel) {
        autoRefresh.toggle()
        autoTask?.cancel()
        guard autoRefresh else { return }
        autoTask = Task { [weak self] in
            while !Task.isCancelled {
                let delay = (self?.lineCount ?? 100) <= 200 ? 10.0 : 30.0
                try? await Task.sleep(for: .seconds(delay))
                guard let self, !Task.isCancelled else { return }
                await self.load(app: app, initial: false)
            }
        }
    }

    func stop() {
        autoTask?.cancel()
        autoTask = nil
        autoRefresh = false
    }

    func reset() {
        lines = []
        countryByIP = [:]
        facets = [:]
        country = nil
        loading = true
    }

    func load(app: AppModel, initial: Bool) async {
        guard let service = app.service else { return }
        loading = initial && lines.isEmpty
        refreshing = !initial
        loadError = nil
        do {
            let response = try await service.logs(lines: lineCount)
            lines = (response.lines ?? []).enumerated().map { LogLine(index: $0.offset, raw: $0.element, entry: LogParser.parse($0.element)) }
            serverError = response.error.flatMap { $0.isBlank ? nil : $0 }
        } catch {
            loadError = error.localizedDescription
        }
        loading = false
        refreshing = false
        await resolveCountries(app: app, service: service)
    }

    private func resolveCountries(app: AppModel, service: TMService) async {
        let status = await app.geo.status(service: service)
        guard status.usable else {
            geoEnabled = false
            return
        }
        let codes = await app.geo.lookup(parsedEntries.map(\.ip), service: service)
        geoEnabled = true
        countryByIP.merge(codes) { $1 }
    }
}

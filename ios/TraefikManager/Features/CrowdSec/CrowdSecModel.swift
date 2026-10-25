import Foundation
import Observation

enum CrowdSecTab: Hashable { case evidence, bans }

/// Server-side decision search results, paged. Port of `CsDecisionFeed`.
struct CsDecisionFeed {
    static let per = 50

    var query: CsDecisionQuery
    var version: String?
    var rows: [CsDecision] = []
    var total = 0
    var page = 0
    var pages = 1
    var loading = false
    var error: String?

    var loaded: Bool { page > 0 }
    var more: Bool { loaded && page < pages }

    mutating func accept(_ next: CsDecisionPage) {
        rows = next.page <= 1 || next.page != page + 1 ? next.rows : rows + next.rows
        total = next.total
        page = max(next.page, 1)
        pages = max(next.pages, 1)
        loading = false
        error = nil
    }
}

/// State for the CrowdSec screen. Port of `CrowdSecViewModel`, `CrowdSecReader` and `CrowdSecRepository`.
@MainActor
@Observable
final class CrowdSecModel {
    private(set) var snapshot = CrowdSecSnapshot()
    private(set) var loading = true
    private(set) var refreshing = false
    private(set) var notConfigured = false
    private(set) var readAt: Date?
    private(set) var countryByIP: [String: String] = [:]
    var feed: CsDecisionFeed?
    var tab: CrowdSecTab = .evidence
    var query = ""
    var facets = CsFacets()

    private var tabFollowsFacets = false
    private var loadingNow = false
    private var feedTask: Task<Void, Never>?

    // MARK: Derived

    var alertsOK: Bool { snapshot.alerts.ok }
    var decisionsOK: Bool { snapshot.decisions.ok }
    var filtersActive: Bool { !facets.isEmpty || !query.isEmpty }
    var country: String? { facets[.country] }

    func countryOf(_ alert: CsAlert) -> String { alert.countryCode.ifEmpty(countryByIP[alert.ip] ?? "") }

    func handled(_ alert: CsAlert) -> Bool { snapshot.handled(alert, banned: snapshot.bannedIPs) }

    private func matchesQuery(_ alert: CsAlert) -> Bool {
        let needle = query.trimmed.lowercased()
        if needle.isEmpty { return true }
        let parts: [String] = [
            alert.ip, alert.scenarioName, alert.source.asName, countryOf(alert), alert.message, alert.machineId,
            alert.uris.joined(separator: " "), alert.userAgents.joined(separator: " "), alert.users.joined(separator: " "),
            alert.source.range, alert.routers.joined(separator: " "), alert.hosts.joined(separator: " "),
        ]
        return parts.joined(separator: " ").lowercased().contains(needle)
    }

    var visibleAlerts: [CsAlert] {
        let banned = snapshot.bannedIPs
        return snapshot.alertList
            .filter { facets.matches($0, country: countryOf, handled: { snapshot.handled($0, banned: banned) }) && matchesQuery($0) }
            .sorted { $0.startMillis > $1.startMillis }
    }

    var visibleDecisions: [CsDecision] {
        let needle = query.trimmed.lowercased()
        return snapshot.decisionList.filter { decision in
            let hit = needle.isEmpty || [decision.value, decision.scenario, decision.originKey, decision.scope, decision.type]
                .contains { $0.lowercased().contains(needle) }
            return facets.matches(decision) && hit
        }
        .sorted { $0.own != $1.own ? $0.own : $0.id > $1.id }
    }

    var decisionQuery: CsDecisionQuery { facets.decisionQuery(tab == .bans ? query : "") }

    var decisionsNeeded: Bool { tab == .bans || decisionQuery.filtered }

    var currentFeed: CsDecisionFeed? {
        guard let feed, snapshot.serverSearch, feed.query == decisionQuery, feed.version == snapshot.version else { return nil }
        return feed
    }

    var bansCount: Int? {
        guard decisionsOK else { return nil }
        if !snapshot.serverSearch { return visibleDecisions.count }
        let wanted = decisionQuery
        if !wanted.filtered && wanted.q == nil { return snapshot.decisionTotal }
        return currentFeed.flatMap { $0.loaded ? $0.total : nil }
    }

    var countries: [CountryCount] {
        let banned = snapshot.bannedIPs
        var tally: [String: Int] = [:]
        for alert in snapshot.alertList
        where facets.matches(alert, country: countryOf, handled: { snapshot.handled($0, banned: banned) }, skip: .country) && matchesQuery(alert) {
            let code = countryOf(alert)
            if !code.isEmpty { tally[code, default: 0] += 1 }
        }
        return tally.map { CountryCount(code: $0.key, name: Countries.name($0.key), count: $0.value) }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.name < $1.name }
    }

    // MARK: Actions

    func reset() {
        feedTask?.cancel()
        snapshot = CrowdSecSnapshot()
        feed = nil
        loading = true
        notConfigured = false
        countryByIP = [:]
    }

    func toggleFacet(_ facet: CsFacet, _ value: String, app: AppModel) {
        facets.toggle(facet, value)
        let applied = facets[facet] != nil
        if !applied {
            if tabFollowsFacets && facets.isEmpty {
                tabFollowsFacets = false
                tab = .evidence
            }
        } else if facet.reads == .decisions && tab != .bans {
            tabFollowsFacets = true
            tab = .bans
        } else if facet.reads == .alerts && tab != .evidence {
            tabFollowsFacets = true
            tab = .evidence
        }
        syncDecisions(app: app)
    }

    func setTab(_ value: CrowdSecTab, app: AppModel) {
        tabFollowsFacets = false
        tab = value
        syncDecisions(app: app)
    }

    func clearFilters(app: AppModel) {
        tabFollowsFacets = false
        facets = CsFacets()
        query = ""
        syncDecisions(app: app)
    }

    func queryChanged(app: AppModel) { syncDecisions(app: app, delay: 0.3) }

    func load(app: AppModel, full: Bool) async {
        guard let service = app.service, !loadingNow else { return }
        loadingNow = true
        defer { loadingNow = false }
        refreshing = !loading
        let next = await Self.read(service: service, full: full, previous: snapshot.version == nil ? nil : snapshot)
        snapshot = next
        if case .notConfigured = next.decisions, case .notConfigured = next.alerts { notConfigured = true } else { notConfigured = false }
        loading = false
        refreshing = false
        readAt = Date()
        syncDecisions(app: app)
        await resolveCountries(app: app, service: service)
    }

    func loadMore(app: AppModel) {
        guard let current = currentFeed, current.more, !current.loading, let service = app.service else { return }
        feed?.loading = true
        feedTask = Task { await fetchDecisions(service: service, query: current.query, version: current.version, page: current.page + 1) }
    }

    func addDecision(value: String, type: String, duration: String, reason: String, app: AppModel) async throws {
        guard let service = app.service else { return }
        let request = AddDecisionRequest(
            value: value.trimmed, type: type, duration: duration, reason: reason.isBlank ? "manual ban from Traefik Manager" : reason
        )
        let result = try await service.crowdSecAddDecision(request)
        if !result.worked && result.error != nil { throw APIError.server(result.error ?? String(localized: "Could not add the decision")) }
        feed = nil
        await load(app: app, full: true)
    }

    func deleteDecision(_ decision: CsDecision, app: AppModel) async throws {
        guard let service = app.service else { return }
        let result = try await service.crowdSecDeleteDecision(decision.id)
        if !result.worked && result.error != nil { throw APIError.server(result.error ?? String(localized: "Could not delete the decision")) }
        feed = nil
        await load(app: app, full: true)
    }

    private func syncDecisions(app: AppModel, delay: Double = 0) {
        guard snapshot.serverSearch, decisionsOK, decisionsNeeded, let service = app.service else { return }
        let query = decisionQuery
        if let feed, feed.query == query, feed.version == snapshot.version { return }
        feedTask?.cancel()
        let version = snapshot.version
        feed = CsDecisionFeed(query: query, version: version, loading: true)
        feedTask = Task {
            if delay > 0 { try? await Task.sleep(for: .seconds(delay)) }
            guard !Task.isCancelled else { return }
            await fetchDecisions(service: service, query: query, version: version, page: 1)
        }
    }

    private func fetchDecisions(service: TMService, query: CsDecisionQuery, version: String?, page: Int) async {
        let result: Result<CsDecisionPage, Error>
        do {
            result = .success(try await service.crowdSecSearch(query, page: page, per: CsDecisionFeed.per))
        } catch {
            result = .failure(error)
        }
        guard var current = feed, current.query == query, current.version == version else { return }
        switch result {
        case let .success(next): current.accept(next)
        case let .failure(error):
            current.loading = false
            current.error = error.localizedDescription
        }
        feed = current
    }

    private func resolveCountries(app: AppModel, service: TMService) async {
        let alerts = snapshot.alertList
        guard !alerts.isEmpty, !alerts.contains(where: { !$0.countryCode.isEmpty }) else { return }
        guard await app.geo.status(service: service).usable else { return }
        countryByIP.merge(await app.geo.lookup(alerts.map(\.ip), service: service)) { $1 }
    }

    // MARK: Reading the LAPI

    /// Summary endpoint first (with change detection), falling back to the two legacy list endpoints.
    static func read(service: TMService, full: Bool, previous: CrowdSecSnapshot?) async -> CrowdSecSnapshot {
        let sent = previous.flatMap { !full && $0.decisions.ok && $0.alerts.ok ? $0.version : nil }.flatMap { $0.isEmpty ? nil : $0 }
        do {
            let summary = try await service.crowdSecSummary(version: sent, full: full)
            if summary.unchanged, let sent, summary.version == sent, let previous { return previous }
            if summary.decisions == nil && summary.alerts == nil {
                let message = String(localized: "CrowdSec summary was empty")
                return CrowdSecSnapshot(decisions: .failed(message), alerts: .failed(message))
            }
            return snapshot(from: summary)
        } catch let APIError.http(status, message) where status == 404 {
            _ = message
            return await legacy(service: service, full: full)
        } catch let APIError.http(status, message) where status == 503 && (message ?? "").localizedCaseInsensitiveContains("not configured") {
            return CrowdSecSnapshot(decisions: .notConfigured, alerts: .notConfigured)
        } catch let APIError.http(status, message) {
            let text = message ?? String(localized: "CrowdSec LAPI unavailable (HTTP \(status))")
            return CrowdSecSnapshot(decisions: .failed(withHint(text, status: status)), alerts: .failed(text))
        } catch APIError.unreachable(let detail) {
            return CrowdSecSnapshot(decisions: .failed(detail), alerts: .failed(detail))
        } catch {
            return await legacy(service: service, full: full)
        }
    }

    private static func snapshot(from summary: CsSummary) -> CrowdSecSnapshot {
        let decisions = summary.decisions ?? CsDecisionsSummary()
        let alerts = summary.alerts ?? CsAlertsSummary()
        var counts = decisions
        counts.rows = []
        return CrowdSecSnapshot(
            decisions: decisions.ok ? .loaded(decisions.rows) : .failed(withHint(decisions.error.ifEmpty(String(localized: "CrowdSec LAPI unavailable")), status: nil)),
            alerts: alerts.ok ? .loaded(CrowdSecAnalytics.filterAlerts(alerts.rows)) : .failed(alerts.error.ifEmpty(String(localized: "CrowdSec LAPI unavailable"))),
            alertLimit: alerts.ok && alerts.limit > 0 ? alerts.limit : nil,
            alertsCapped: alerts.ok ? alerts.capped : nil,
            decisionsStale: decisions.ok && !decisions.stale.isBlank ? decisions.stale : nil,
            counts: decisions.ok ? counts : nil,
            version: summary.version
        )
    }

    private static func legacy(service: TMService, full: Bool) async -> CrowdSecSnapshot {
        async let decisions = readList { try await service.crowdSecDecisions(full: full) }
        async let alerts = readList { try await service.crowdSecAlerts() }
        let decisionRead = await decisions
        let alertRead = await alerts
        let filteredAlerts: CsRead<[CsAlert]> = switch alertRead {
        case let .loaded(list): .loaded(CrowdSecAnalytics.filterAlerts(list))
        case let .failed(message): .failed(message)
        case .notConfigured: .notConfigured
        }
        let hinted: CsRead<[CsDecision]> = if case let .failed(message) = decisionRead { .failed(withHint(message, status: nil)) } else { decisionRead }
        return CrowdSecSnapshot(decisions: hinted, alerts: filteredAlerts)
    }

    private static func readList<T>(_ call: () async throws -> [T]) async -> CsRead<[T]> {
        do {
            return .loaded(try await call())
        } catch APIError.http(404, _) {
            return .notConfigured
        } catch let APIError.http(status, message) {
            return .failed(message ?? String(localized: "CrowdSec LAPI unavailable (HTTP \(status))"))
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// A 403 on decisions almost always means the machine token was used instead of a bouncer key.
    private static func withHint(_ message: String, status: Int?) -> String {
        let forbidden = status == 403 || message.contains("403")
        guard forbidden, !message.localizedCaseInsensitiveContains("bouncer") else { return message }
        return String(localized: "\(message). CrowdSec only accepts a bouncer key on /v1/decisions, the machine token is refused there, so CROWDSEC_API_KEY has to be set as well.")
    }
}

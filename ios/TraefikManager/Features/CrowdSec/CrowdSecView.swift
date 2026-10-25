import SwiftUI

/// CrowdSec alerts (evidence) and decisions (bans) with the analytics desk. Port of `CrowdSecScreen`.
struct CrowdSecView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var model = CrowdSecModel()
    @State private var adding: String?
    @State private var pendingDelete: CsDecision?
    @State private var message: String?

    var body: some View {
        @Bindable var model = model
        content
            .background(Palette.bg)
            .navigationTitle("CrowdSec")
            .searchable(text: $model.query, prompt: Text("Filter by address, scenario, network or path"))
            .onChange(of: model.query) { model.queryChanged(app: app) }
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button { adding = "" } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add decision")
                        .disabled(model.notConfigured)
                    Button {
                        model.feed = nil
                        Task { await model.load(app: app, full: true) }
                    } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh CrowdSec")
                }
            }
            .sheet(item: $adding) { value in
                AddDecisionSheet(initialValue: value, ownDecisions: model.snapshot.decisionList.filter(\.own)) { value, type, duration, reason in
                    try await model.addDecision(value: value, type: type, duration: duration, reason: reason, app: app)
                    message = String(localized: "Decision added for \(value.trimmed)")
                } onDelete: { decision in
                    pendingDelete = decision
                }
            }
            .confirmationDialog(
                "Remove decision",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                titleVisibility: .visible,
                presenting: pendingDelete
            ) { decision in
                Button("Unban", role: .destructive) { delete(decision) }
            } message: { decision in
                Text("Unban \(decision.value)? CrowdSec will stop blocking it immediately.")
            }
            .task(id: app.generation) {
                model.reset()
                await model.load(app: app, full: false)
            }
            .refreshable {
                model.feed = nil
                await model.load(app: app, full: true)
            }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if model.loading {
            ProgressView("Loading CrowdSec").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.notConfigured {
            ContentUnavailableView(
                "CrowdSec is not configured",
                systemImage: "shield.slash",
                description: Text("Set CROWDSEC_LAPI_URL and a bouncer API key on this server, then recreate the container, to see decisions and alerts.")
            )
        } else {
            body(alerts: model.visibleAlerts)
        }
    }

    private func body(alerts: [CsAlert]) -> some View {
        let banned = model.snapshot.bannedIPs
        let handled: (CsAlert) -> Bool = { model.snapshot.handled($0, banned: banned) }
        let sources = CrowdSecAnalytics.sources(alerts, handled: handled)
        let scenarios = CrowdSecAnalytics.scenarios(alerts, handled: handled)
        let span = CrowdSecAnalytics.spanMillis(alerts)
        return List {
            Group {
                CrowdSecVerdict(model: model, sources: sources, scenarios: scenarios, span: span)
                windowRow(span: span)
                if model.filtersActive { filterBar }
                CrowdSecDesk(model: model, alerts: alerts, sources: sources, scenarios: scenarios, wide: sizeClass == .regular) {
                    model.toggleFacet($0, $1, app: app)
                }
                if !model.countries.isEmpty {
                    GeographyCard(countries: model.countries, selected: model.country) { model.toggleFacet(.country, $0, app: app) }
                }
                Picker("View", selection: Binding(get: { model.tab }, set: { model.setTab($0, app: app) })) {
                    Text(String(localized: "Evidence \(model.alertsOK ? String(alerts.count) : "?")")).tag(CrowdSecTab.evidence)
                    Text(String(localized: "Bans \(model.bansCount.map(String.init) ?? "?")")).tag(CrowdSecTab.bans)
                }
                .pickerStyle(.segmented)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))

            Group {
                if model.tab == .evidence {
                    ForEach(Array(alerts.prefix(200).enumerated()), id: \.offset) { _, alert in
                        AlertRow(alert: alert, country: model.countryOf(alert), handled: handled(alert), decisionsOK: model.decisionsOK) {
                            adding = alert.ip
                        } onFacet: { model.toggleFacet($0, $1, app: app) }
                    }
                    if alerts.isEmpty {
                        Text(model.alertsOK ? "No alerts match" : "Alerts unavailable").font(.footnote).foregroundStyle(Palette.muted)
                    }
                } else if model.snapshot.serverSearch {
                    ForEach(Array((model.currentFeed?.rows ?? []).enumerated()), id: \.offset) { _, decision in
                        DecisionRow(decision: decision) { pendingDelete = decision }
                    }
                    feedFooter
                } else {
                    ForEach(Array(model.visibleDecisions.prefix(200).enumerated()), id: \.offset) { _, decision in
                        DecisionRow(decision: decision) { pendingDelete = decision }
                    }
                    if model.visibleDecisions.isEmpty {
                        Text(model.decisionsOK ? "No active decisions" : "Decisions unavailable").font(.footnote).foregroundStyle(Palette.muted)
                    }
                }
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func windowRow(span: Double?) -> some View {
        let snapshot = model.snapshot
        let parts = [
            model.alertsOK ? String(localized: "retained \(snapshot.alertList.count) alerts") : String(localized: "retained ? alerts"),
            span.map { String(localized: "span \(LogParser.spanText($0))") },
            model.decisionsOK ? String(localized: "\(snapshot.decisionTotal) bans") : String(localized: "? bans"),
        ].compactMap { $0 }
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: Spacing.sm) {
                SectionLabel(text: String(localized: "Window"))
                Text(parts.joined(separator: " · ")).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
            }
            if snapshot.alertsCapped == true, let limit = snapshot.alertLimit {
                Text(String(localized: "showing the newest \(limit) alerts, the LAPI holds more (CROWDSEC_ALERT_LIMIT)"))
                    .font(.caption2)
                    .foregroundStyle(Palette.yellow)
            }
            if let error = snapshot.decisions.error { Text(error).font(.caption2).foregroundStyle(Palette.red) }
            if let error = snapshot.alerts.error { Text(error).font(.caption2).foregroundStyle(Palette.red) }
        }
    }

    private var filterBar: some View {
        FlowLayout(spacing: Spacing.sm) {
            SectionLabel(text: String(localized: "Filtered by"))
            if !model.query.isEmpty {
                chip(String(localized: "search"), model.query) { model.query = "" }
            }
            ForEach(model.facets.active, id: \.0) { facet, value in
                chip(facet.label, facet == .country ? "\(Countries.flag(value)) \(value)" : value) {
                    model.toggleFacet(facet, value, app: app)
                }
            }
            Button("Clear all") { model.clearFilters(app: app) }.font(.caption2).buttonStyle(.plain).foregroundStyle(Palette.blue)
        }
    }

    private func chip(_ label: String, _ value: String, remove: @escaping () -> Void) -> some View {
        Button(action: remove) {
            HStack(spacing: 3) {
                Text(label).font(.caption2)
                Text(value).font(.caption2.monospaced().weight(.bold)).lineLimit(1)
                Image(systemName: "xmark").imageScale(.small)
            }
            .foregroundStyle(Palette.blue)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Palette.blue.opacity(0.1), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(String(localized: "Remove the \(label) filter"))
    }

    @ViewBuilder
    private var feedFooter: some View {
        let feed = model.currentFeed
        VStack(spacing: Spacing.xs) {
            Group {
                if !model.decisionsOK {
                    Text("Decisions unavailable")
                } else if feed == nil || (feed!.loading && !feed!.loaded) {
                    Text("Loading decisions")
                } else if let error = feed?.error, feed?.rows.isEmpty == true {
                    Text(error)
                } else if feed?.rows.isEmpty == true {
                    Text("No active decisions")
                } else if let feed {
                    Text(String(localized: "Showing \(feed.rows.count) of \(feed.total)"))
                }
            }
            .font(.footnote)
            .foregroundStyle(Palette.muted)
            if let feed, feed.more {
                Button(feed.loading ? "Loading" : "Load more") { model.loadMore(app: app) }.disabled(feed.loading)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func delete(_ decision: CsDecision) {
        Task {
            do {
                try await model.deleteDecision(decision, app: app)
                message = String(localized: "\(decision.value) unbanned")
            } catch {
                message = error.localizedDescription
            }
        }
    }
}

private struct CrowdSecVerdict: View {
    let model: CrowdSecModel
    let sources: [CsRanked]
    let scenarios: [CsRanked]
    let span: Double?

    var body: some View {
        let alerts = model.snapshot.alertList
        let events = alerts.reduce(0) { $0 + $1.eventsCount }
        let unbanned = sources.filter { $0.open > 0 }.count
        let accent: Color = !model.alertsOK || !model.decisionsOK ? Palette.red : alerts.isEmpty ? Palette.green : Palette.yellow
        let headline = !model.alertsOK ? String(localized: "Alerts unavailable")
            : alerts.isEmpty ? String(localized: "Nothing probing right now") : String(localized: "Actively probed")
        Card(accent: accent, spacing: Spacing.xs) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: model.alertsOK && alerts.isEmpty ? "checkmark.circle" : "exclamationmark.triangle").foregroundStyle(accent)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(headline).font(.subheadline.weight(.semibold))
                    if alerts.isEmpty {
                        Text("no alerts in the retained window").font(.caption2).foregroundStyle(Palette.muted)
                    } else {
                        FlowLayout(spacing: Spacing.sm) {
                            item("scope", String(localized: "\(sources.count) sources"), Palette.text)
                            item("bolt", String(localized: "\(scenarios.count) scenarios"), Palette.text)
                            item("waveform", String(localized: "\(events) events"), Palette.text)
                            if unbanned > 0 { item("lock.open", String(localized: "\(unbanned) no active ban"), Palette.yellow) }
                        }
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    if let span { Text(String(localized: "\(LogParser.spanText(span)) of alerts")).font(.caption2.monospaced()) }
                    if let readAt = model.readAt {
                        Text(String(localized: "read \(LogParser.spanText(Date().timeIntervalSince(readAt) * 1000)) ago")).font(.caption2.monospaced())
                    }
                }
                .foregroundStyle(Palette.muted)
            }
        }
    }

    private func item(_ icon: String, _ text: String, _ color: Color) -> some View {
        Label(text, systemImage: icon).labelStyle(TightLabelStyle()).font(.caption2).foregroundStyle(color)
    }
}

/// The analytics cards. Port of the desk section of `CrowdSecBody`.
private struct CrowdSecDesk: View {
    let model: CrowdSecModel
    let alerts: [CsAlert]
    let sources: [CsRanked]
    let scenarios: [CsRanked]
    let wide: Bool
    let onFacet: (CsFacet, String) -> Void

    var body: some View {
        let banned = model.snapshot.bannedIPs
        let handled: (CsAlert) -> Bool = { model.snapshot.handled($0, banned: banned) }
        let networks = CrowdSecAnalytics.networks(alerts, handled: handled)
        let paths = CrowdSecAnalytics.paths(alerts, handled: handled)
        let accounts = CrowdSecAnalytics.accounts(alerts, handled: handled)
        let tooling = CrowdSecAnalytics.tooling(alerts, handled: handled)
        let routes = CrowdSecAnalytics.routes(alerts, handled: handled)
        let hosts = CrowdSecAnalytics.hosts(alerts, handled: handled)
        VStack(spacing: Spacing.sm) {
            pair { sourcesCard } and: {
                rankCard(
                    title: String(localized: "Networks"), glyph: "globe", accent: Palette.purple, rows: networks, facet: .asn,
                    blind: String(localized: "AS names ride on alert.source"), empty: String(localized: "this agent reports ip only"),
                    subtitle: networks.first.map { String(localized: "worst \($0.label) \($0.count) alerts") },
                    rowFlag: { $0.extra.isEmpty ? nil : Countries.flag($0.extra) }
                )
            }
            rankCard(
                title: String(localized: "Scenarios"), glyph: "bolt", accent: Palette.orange, rows: scenarios, facet: .scenario,
                blind: String(localized: "scenarios come from /v1/alerts"), empty: String(localized: "nothing to rank in the retained window"),
                hero: String(alerts.count), unit: String(localized: "alerts"),
                subtitle: scenarios.first.map { top in
                    let events = alerts.reduce(0) { $0 + $1.eventsCount }
                    return String(localized: "worst \(top.label)") + " · " + String(localized: "\(events) events rolled up")
                }
            )
            let onPaths = !paths.isEmpty || accounts.isEmpty
            let targeting = onPaths ? paths : accounts
            rankCard(
                title: onPaths ? String(localized: "Targeted paths") : String(localized: "Targeted accounts"),
                glyph: "scope", accent: Palette.blue, rows: targeting, facet: onPaths ? .uri : .user,
                blind: onPaths ? String(localized: "paths live in alert.meta[]") : String(localized: "accounts live in alert.meta[]"),
                empty: String(localized: "no meta reported"),
                hero: String(targeting.count), unit: onPaths ? String(localized: "paths") : String(localized: "accounts"),
                subtitle: targeting.first.map { String(localized: "most wanted \($0.label)") }
            )
            let named = model.snapshot.alertList.contains { !$0.routers.isEmpty }
            rankCard(
                title: String(localized: "Targeted routes"), glyph: "arrow.triangle.branch", accent: Palette.purple, rows: routes, facet: .router,
                blind: String(localized: "routers live in alert.meta[]"),
                empty: model.snapshot.alertList.isEmpty ? String(localized: "nothing was aimed at")
                    : named ? String(localized: "no router matches") : String(localized: "no router in the evidence"),
                hero: String(routes.count), unit: String(localized: "routers"),
                subtitle: routes.first.map { String(localized: "most wanted \($0.label)") },
                hostChips: Array(hosts.prefix(3))
            )
            pair {
                rankCard(
                    title: String(localized: "Tooling"), glyph: "cpu", accent: Palette.teal, rows: tooling, facet: .agent,
                    blind: String(localized: "user agents live in alert.meta[]"), empty: String(localized: "no HTTP scenario fired here"),
                    subtitle: tooling.first.map { String(localized: "worst \($0.label)") }
                )
            } and: { bansCard }
        }
    }

    @ViewBuilder
    private func pair<A: View, B: View>(@ViewBuilder _ first: () -> A, @ViewBuilder and second: () -> B) -> some View {
        if wide {
            HStack(alignment: .top, spacing: Spacing.sm) { first(); second() }.fixedSize(horizontal: false, vertical: true)
        } else {
            first()
            second()
        }
    }

    private var sourcesCard: some View {
        let loose = sources.filter { $0.open > 0 }
        let repeats = sources.filter { $0.count > 1 }
        return DeskCard(
            title: String(localized: "Attacking sources"),
            glyph: "scope",
            accent: Palette.red,
            health: loose.contains { $0.count > 1 } ? Palette.red : loose.isEmpty ? nil : Palette.yellow,
            hero: model.alertsOK ? LogParser.formatCount(sources.count) : "-",
            sub: !model.alertsOK ? String(localized: "sources are only listed on /v1/alerts")
                : sources.first.map { String(localized: "worst \($0.key) \($0.weight) events") } ?? String(localized: "no sources")
        ) {
            if !model.alertsOK || !model.decisionsOK {
                Text("ban state unknown").font(.caption2).foregroundStyle(Palette.muted)
            } else if !loose.isEmpty {
                HStack(spacing: Spacing.sm) {
                    Flag(icon: "lock.open", count: loose.count, label: String(localized: "loose"), color: loose.contains { $0.count > 1 } ? Palette.red : Palette.yellow) {
                        onFacet(.outcome, "loose")
                    }
                    Flag(icon: "nosign", count: sources.count - loose.count, label: String(localized: "banned"), color: Palette.muted) {
                        onFacet(.outcome, "banned")
                    }
                }
            } else if !sources.isEmpty {
                OkPill(text: String(localized: "every source banned"))
            }
        } content: {
            Cells(colors: sources.map { $0.open > 0 && $0.count > 1 ? Palette.red : $0.open > 0 ? Palette.yellow : Palette.muted.opacity(0.3) })
        } footer: {
            if !sources.isEmpty {
                Text(String(localized: "\(repeats.count) repeat")).font(.caption2).foregroundStyle(Palette.muted)
                Text(String(localized: "\(sources.count - repeats.count) one-shot")).font(.caption2).foregroundStyle(Palette.muted)
            }
        }
    }

    private var bansCard: some View {
        let snapshot = model.snapshot
        let total = snapshot.decisionTotal
        let perCell = max((total + 239) / 240, 1)
        let stale = snapshot.decisionsStale
        let ownCells = (snapshot.ownBans + perCell - 1) / perCell
        let cellCount = (total + perCell - 1) / perCell
        return DeskCard(
            title: stale != nil ? String(localized: "Bans in force (stale)") : String(localized: "Bans in force"),
            glyph: "shield",
            accent: stale != nil ? Palette.yellow : Palette.green,
            health: !model.decisionsOK ? Palette.red : stale != nil ? Palette.yellow : nil,
            hero: model.decisionsOK ? LogParser.formatCount(total) : "-",
            sub: !model.decisionsOK ? String(localized: "nothing was read from /v1/decisions")
                : stale ?? (String(localized: "\(LogParser.formatCount(snapshot.ownBans)) from this host") + " · " + String(localized: "\(LogParser.formatCount(snapshot.subscribedBans)) subscribed"))
        ) {
            if model.decisionsOK {
                Flag(icon: "nosign", count: snapshot.typeCount("ban"), label: "ban", color: Palette.muted) { onFacet(.type, "ban") }
            }
        } content: {
            Cells(colors: (0..<min(cellCount, 240)).map { $0 < ownCells ? Palette.text.opacity(0.42) : Palette.muted.opacity(0.3) })
            if perCell > 1 {
                Text(String(localized: "1 cell = \(LogParser.formatCount(perCell))")).font(.caption2).foregroundStyle(Palette.muted)
            }
        } footer: {
            ForEach(snapshot.origins, id: \.0) { origin, count in
                Button { onFacet(.origin, origin) } label: {
                    Text(verbatim: "\(LogParser.formatCount(count)) \(origin)")
                        .font(.caption2)
                        .foregroundStyle(origin == "cscli" ? Palette.yellow : Palette.muted)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func rankCard(
        title: String, glyph: String, accent: Color, rows: [CsRanked], facet: CsFacet, blind: String, empty: String,
        hero: String? = nil, unit: String? = nil, subtitle: String?, rowFlag: ((CsRanked) -> String?)? = nil, hostChips: [CsRanked] = []
    ) -> some View {
        let isBlind = !model.alertsOK
        let shown = rows.prefix(4)
        return DeskCard(
            title: title,
            glyph: glyph,
            accent: accent,
            hero: isBlind ? "-" : rows.isEmpty ? "0" : hero ?? String(rows.count),
            heroUnit: !isBlind && !rows.isEmpty ? unit : nil,
            sub: isBlind ? blind : rows.isEmpty ? empty : subtitle ?? String(localized: "worst \(rows[0].key)")
        ) {
            if !isBlind && !rows.isEmpty && !hostChips.isEmpty {
                HStack(spacing: Spacing.sm) {
                    ForEach(hostChips) { host in
                        Button { onFacet(.host, host.key) } label: {
                            Text(verbatim: "\(host.count) \(host.key)").font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        } content: {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(shown)) { row in
                    let active = model.facets[facet] == row.key
                    Button { onFacet(facet, row.key) } label: {
                        HStack(spacing: Spacing.xs) {
                            if let flag = rowFlag?(row) { Text(flag).font(.caption2) }
                            Text(row.label).font(.caption2.monospaced()).foregroundStyle(active ? Palette.blue : Palette.text).lineLimit(1)
                            Spacer(minLength: Spacing.xs)
                            if row.open > 0 {
                                Image(systemName: "lock.open").font(.system(size: 9)).foregroundStyle(Palette.yellow)
                                Text(verbatim: "\(row.open)").font(.caption2.monospaced()).foregroundStyle(Palette.yellow)
                            }
                            Text(LogParser.formatCount(row.count)).font(.caption2.monospaced().weight(.bold))
                        }
                        .padding(.vertical, 3)
                        .padding(.horizontal, 4)
                        .background(active ? Palette.blue.opacity(0.1) : .clear, in: RoundedRectangle(cornerRadius: 4))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                if rows.count > 4 {
                    let rest = rows.dropFirst(4)
                    Text(String(localized: "+\(rest.reduce(0) { $0 + $1.count }) alerts across \(rest.count) more"))
                        .font(.caption2)
                        .foregroundStyle(Palette.muted)
                }
            }
        }
    }
}

private struct AlertRow: View {
    let alert: CsAlert
    let country: String
    let handled: Bool
    let decisionsOK: Bool
    let onBan: () -> Void
    let onFacet: (CsFacet, String) -> Void

    var body: some View {
        Card(accent: handled ? Palette.green : Palette.yellow, spacing: 3) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 4) {
                        Button { onFacet(.ip, alert.ip) } label: {
                            Text(alert.ip.ifEmpty(String(localized: "unknown"))).font(.caption.monospaced().weight(.bold))
                        }
                        Button { onFacet(.scenario, alert.scenarioName) } label: {
                            Text(alert.scenarioName.components(separatedBy: "/").last ?? alert.scenarioName)
                                .font(.caption2)
                                .foregroundStyle(Palette.muted)
                                .lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                    Text(evidence).font(.caption2.monospaced()).foregroundStyle(Palette.muted).lineLimit(1)
                    let router = alert.routers.first
                    let host = alert.hosts.first
                    if router != nil || host != nil {
                        HStack(spacing: Spacing.sm) {
                            if let router {
                                Button { onFacet(.router, router) } label: {
                                    Label(RouteDetailView.shortName(router).ifEmpty(router), systemImage: "arrow.triangle.branch")
                                }
                                .accessibilityHint("Filter by route")
                            }
                            if let host {
                                Button { onFacet(.host, host) } label: { Label(host, systemImage: "globe") }
                                    .accessibilityHint("Filter by host")
                            }
                        }
                        .labelStyle(TightLabelStyle())
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.muted)
                        .buttonStyle(.plain)
                        .lineLimit(1)
                    }
                    HStack(spacing: 4) {
                        if !country.isEmpty {
                            Text(Countries.flag(country))
                            Text(country).foregroundStyle(Palette.muted)
                        }
                        if !alert.source.asName.isEmpty {
                            Text("· \(alert.source.asName)").foregroundStyle(Palette.muted).lineLimit(1)
                        }
                    }
                    .font(.caption2)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 3) {
                    Text(verbatim: "\(alert.eventsCount)").font(.caption.monospaced().weight(.bold)).foregroundStyle(Palette.muted)
                    Text(!decisionsOK ? "?" : handled ? String(localized: "banned") : String(localized: "open"))
                        .font(.caption2)
                        .foregroundStyle(!decisionsOK ? Palette.muted : handled ? Palette.green : Palette.yellow)
                    if !handled && !alert.ip.isEmpty {
                        Button(action: onBan) { Image(systemName: "nosign").foregroundStyle(Palette.red) }
                            .buttonStyle(.borderless)
                            .accessibilityLabel(String(localized: "Ban \(alert.ip)"))
                    }
                }
            }
        }
    }

    private var evidence: String {
        var parts: [String] = []
        if let uri = alert.uris.first { parts.append(uri) }
        if let user = alert.users.first { parts.append(user) }
        return parts.isEmpty ? alert.scenarioName : parts.joined(separator: " · ")
    }
}

private struct DecisionRow: View {
    let decision: CsDecision
    let onDelete: () -> Void

    var body: some View {
        Card(accent: decision.own ? Palette.blue : Palette.muted, spacing: 2) {
            HStack(spacing: Spacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(decision.value).font(.caption.monospaced().weight(.bold)).lineLimit(1)
                    Text([decision.type, decision.scope, decision.originKey].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.caption2)
                        .foregroundStyle(Palette.muted)
                    if !decision.scenario.isEmpty {
                        Text(decision.scenario).font(.caption2.monospaced()).foregroundStyle(Palette.muted).lineLimit(1)
                    }
                }
                Spacer()
                Text(decision.duration).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
                if decision.id != 0 {
                    Button(action: onDelete) { Image(systemName: "trash").foregroundStyle(Palette.red) }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(String(localized: "Unban \(decision.value)"))
                }
            }
        }
    }
}

/// Ban, captcha or bypass an IP or range. Port of `AddDecisionSheet`.
private struct AddDecisionSheet: View {
    @Environment(\.dismiss) private var dismiss
    let initialValue: String
    let ownDecisions: [CsDecision]
    let onAdd: (String, String, String, String) async throws -> Void
    let onDelete: (CsDecision) -> Void

    @State private var value = ""
    @State private var type = "ban"
    @State private var duration = "24h"
    @State private var reason = ""
    @State private var saving = false
    @State private var error: String?

    private let durations: [(String, String)] = [
        ("1h", String(localized: "\(1) hours")), ("4h", String(localized: "\(4) hours")), ("24h", String(localized: "\(24) hours")),
        ("168h", String(localized: "\(7) days")), ("720h", String(localized: "\(30) days")), ("8760h", String(localized: "\(1) years")),
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("IP or range", text: $value, prompt: Text(verbatim: "1.2.3.4 or 1.2.3.0/24"))
                        .font(.body.monospaced())
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } footer: {
                    Text("Single IP or CIDR range.")
                }
                Section {
                    Picker("Type", selection: $type) {
                        ForEach(["ban", "captcha", "bypass"], id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Picker("Duration", selection: $duration) {
                        ForEach(durations, id: \.0) { Text($0.1).tag($0.0) }
                    }
                }
                Section {
                    TextField("Reason (optional)", text: $reason, prompt: Text(verbatim: "manual ban from Traefik Manager"))
                } footer: {
                    Text("Stored as the decision scenario.")
                }
                if let error {
                    Section { Text(error).foregroundStyle(Palette.red).font(.footnote) }
                }
                if !ownDecisions.isEmpty {
                    Section(String(localized: "Your decisions \(ownDecisions.count)")) {
                        ForEach(ownDecisions) { decision in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(decision.value).font(.caption.monospaced())
                                    Text(decision.duration).font(.caption2).foregroundStyle(Palette.muted)
                                }
                                Spacer()
                                if decision.id != 0 {
                                    Button { onDelete(decision) } label: { Image(systemName: "trash").foregroundStyle(Palette.red) }
                                        .buttonStyle(.borderless)
                                        .accessibilityLabel(String(localized: "Unban \(decision.value)"))
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Add decision")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        ProgressView()
                    } else {
                        Button("Add") { add() }.disabled(value.isBlank)
                    }
                }
            }
        }
        .onAppear { value = initialValue }
    }

    private func add() {
        saving = true
        error = nil
        Task {
            defer { saving = false }
            do {
                try await onAdd(value, type, duration, reason)
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

import SwiftUI

/// Access log with analytics and tap-to-filter. Port of `LogsScreen`.
struct LogsView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var model = LogsModel()
    @State private var selectedLine: LogLine?

    var body: some View {
        @Bindable var model = model
        content
            .background(Palette.bg)
            .navigationTitle("Logs")
            .searchable(text: $model.query, prompt: Text("Filter logs"))
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    Button { model.toggleAutoRefresh(app: app) } label: {
                        Image(systemName: model.autoRefresh ? "pause.circle.fill" : "play.circle")
                    }
                    .accessibilityLabel(model.autoRefresh ? "Stop auto refresh" : "Start auto refresh")
                    Button { Task { await model.load(app: app, initial: false) } } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityLabel("Refresh logs")
                }
            }
            .navigationDestination(item: $selectedLine) { line in
                LogDetailView(line: line, country: line.entry.flatMap { model.countryByIP[$0.ip] })
            }
            .task(id: app.generation) {
                model.reset()
                await model.load(app: app, initial: true)
            }
            .onDisappear { model.stop() }
            .refreshable { await model.load(app: app, initial: false) }
    }

    @ViewBuilder
    private var content: some View {
        if model.loading {
            ProgressView("Loading logs").frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let error = model.loadError {
            ContentUnavailableView {
                Label("Could not load the access log", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Retry") { Task { await model.load(app: app, initial: false) } }.buttonStyle(.borderedProminent)
            }
        } else if let error = model.serverError, model.lines.isEmpty {
            ContentUnavailableView {
                Label("Access log not available", systemImage: "doc.text.magnifyingglass")
            } description: {
                Text(error)
            }
        } else if model.lines.isEmpty {
            ContentUnavailableView(
                "No traffic yet",
                systemImage: "doc.text",
                description: Text("The access log is empty, Traefik has not served a request since it was last rotated.")
            )
        } else {
            list
        }
    }

    private var list: some View {
        let window = model.window
        let visible = model.visible
        return List {
            Group {
                LogVerdictCard(model: model, window: window)
                WindowKeyRow(model: model, window: window, visibleCount: visible.count)
                Picker("Lines", selection: Binding(get: { model.lineCount }, set: { model.setLineCount($0, app: app) })) {
                    ForEach(logLineSteps, id: \.self) { Text(verbatim: "\($0)").tag($0) }
                }
                .pickerStyle(.segmented)
                LogSignalCards(window: window, facets: model.facets, compact: sizeClass != .regular) { model.toggleFacet($0, $1) }
                RuntimeChips(model: model)
                if model.geoEnabled && !model.countries.isEmpty {
                    GeographyCard(countries: model.countries, selected: model.country, limit: sizeClass == .regular ? 8 : 5) { model.toggleCountry($0) }
                }
                HStack {
                    SectionLabel(text: String(localized: "Access log"))
                    Spacer()
                    Text(String(localized: "\(visible.count) of \(model.fetched) lines"))
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.muted)
                }
                .padding(.top, Spacing.sm)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.lg, bottom: Spacing.xs, trailing: Spacing.lg))

            if visible.isEmpty {
                Text(String(localized: "0 of the last \(model.fetched) lines match the current filters"))
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
                    .listRowBackground(Color.clear)
            }
            ForEach(visible.reversed()) { line in
                Button { selectedLine = line } label: {
                    LogRow(line: line, country: line.entry.flatMap { model.countryByIP[$0.ip] })
                }
                .buttonStyle(.plain)
                .listRowBackground(rowBackground(line))
                .listRowInsets(EdgeInsets(top: Spacing.sm, leading: Spacing.lg, bottom: Spacing.sm, trailing: Spacing.lg))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func rowBackground(_ line: LogLine) -> Color {
        guard let status = line.entry?.status else { return .clear }
        if status >= 500 { return Palette.red.opacity(0.06) }
        if status >= 400 { return Palette.yellow.opacity(0.06) }
        return .clear
    }
}

// MARK: - Header pieces

private struct LogVerdictCard: View {
    let model: LogsModel
    let window: LogWindow

    var body: some View {
        let latency = window.latency
        let accent: Color = window.health == .down ? Palette.red : window.health == .warn ? Palette.yellow : Palette.green
        Card(accent: accent, spacing: Spacing.xs) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: window.health == .up ? "checkmark.circle" : "exclamationmark.triangle")
                    .foregroundStyle(accent)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(headline).font(.subheadline.weight(.semibold))
                    FlowLayout(spacing: Spacing.sm) {
                        ForEach(window.codeRank.prefix(3), id: \.0) { code, count in
                            verdictItem(
                                "\(count) \(code) " + (LogParser.statusName(code)?.lowercased() ?? String(localized: "response")),
                                color: code >= 500 ? Palette.red : Palette.yellow
                            ) { model.toggleFacet(.status, String(code)) }
                        }
                        if latency.slow > 0 {
                            verdictItem(String(localized: "\(latency.slow) over 500ms"), color: Palette.yellow) {
                                model.toggleFacet(.duration, "slow")
                            }
                        }
                        if window.retries > 0 {
                            verdictItem(String(localized: "\(window.retries) retries"), color: Palette.yellow, action: nil)
                        }
                        if !calm.isEmpty {
                            Text(calm.joined(separator: ", ")).font(.caption2).foregroundStyle(Palette.muted)
                        }
                    }
                }
                Spacer(minLength: 0)
                Text(window.spanMillis.map { String(localized: "\(LogParser.spanText($0)) window") } ?? String(localized: "\(model.fetched) lines"))
                    .font(.caption2.monospaced())
                    .foregroundStyle(Palette.muted)
            }
        }
    }

    private var headline: String {
        if window.serverErrors > 0 { return String(localized: "\(window.serverErrors) server errors") }
        if window.clientErrors > 0 { return String(localized: "\(window.clientErrors) client errors") }
        if window.latency.slow > 0 { return String(localized: "\(window.latency.slow) slow requests") }
        if window.parsed == 0 { return String(localized: "Nothing matches") }
        return String(localized: "All clean")
    }

    private var calm: [String] {
        var parts: [String] = []
        if window.serverErrors == 0 { parts.append(String(localized: "no server errors")) }
        if window.clientErrors == 0 && window.serverErrors > 0 { parts.append(String(localized: "no client errors")) }
        if window.latency.slow == 0 {
            parts.append(window.latency.max.map { String(localized: "nothing slower than \(LogParser.formatMs($0))") } ?? String(localized: "no timing recorded"))
        }
        return parts
    }

    @ViewBuilder
    private func verdictItem(_ text: String, color: Color, action: (() -> Void)?) -> some View {
        let label = Label(text, systemImage: "exclamationmark.triangle").font(.caption2).foregroundStyle(color).labelStyle(TightLabelStyle())
        if let action {
            Button(action: action) { label }.buttonStyle(.plain)
        } else {
            label
        }
    }
}

struct TightLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 3) {
            configuration.icon.imageScale(.small)
            configuration.title
        }
    }
}

private struct WindowKeyRow: View {
    let model: LogsModel
    let window: LogWindow
    let visibleCount: Int

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            FlowLayout(spacing: Spacing.md) {
                SectionLabel(text: String(localized: "Window"))
                keyItem("list.bullet.rectangle", String(localized: "last \(model.fetched) lines"))
                if let span = window.spanMillis, span > 0 {
                    keyItem("clock", String(localized: "span \(LogParser.spanText(span))"))
                }
                if let rate = window.requestsPerMinute {
                    keyItem("bolt", String(localized: "\(String(rate)) req/min"))
                }
                keyItem("list.bullet.rectangle", String(localized: "\(model.unparsed) unparsed"))
            }
            if !model.facets.isEmpty {
                let dead = model.deadFacets
                FlowLayout(spacing: Spacing.sm) {
                    SectionLabel(text: String(localized: "Filters"))
                    ForEach(LogFacet.allCases.filter { model.facets[$0] != nil }) { facet in
                        let tint = dead.contains(facet) ? Palette.muted : Palette.blue
                        Button { model.facets[facet] = nil } label: {
                            HStack(spacing: 3) {
                                Image(systemName: "line.3.horizontal.decrease").imageScale(.small)
                                Text(facet.label)
                                Text(facet.display(model.facets[facet] ?? "")).font(.caption2.monospaced().weight(.bold)).lineLimit(1)
                                Image(systemName: "xmark").imageScale(.small)
                            }
                            .font(.caption2)
                            .foregroundStyle(tint)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(tint.opacity(0.1), in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(String(localized: "Clear \(facet.label) filter"))
                    }
                    Button("clear") { model.facets = [:] }.font(.caption2).buttonStyle(.plain).foregroundStyle(Palette.blue)
                }
            }
            HStack {
                if let span = model.selectionSpan, span > 0 {
                    keyItem("clock", String(localized: "selection spans \(LogParser.spanText(span))"))
                }
                Spacer()
                let text = model.selected
                    ? String(localized: "\(visibleCount) of the last \(model.fetched) lines")
                    : String(localized: "sample, not all traffic")
                Text(text).font(.caption2).foregroundStyle(Palette.yellow)
            }
        }
    }

    private func keyItem(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: 9))
            Text(text).font(.caption2.monospaced())
        }
        .foregroundStyle(Palette.muted)
    }
}

private struct RuntimeChips: View {
    let model: LogsModel

    var body: some View {
        FlowLayout(spacing: Spacing.md) {
            chip("curlybraces", model.formatLabel, !model.parsedEntries.isEmpty)
            chip("list.bullet.rectangle", String(localized: "\(model.parsedEntries.count) of \(model.fetched) lines parsed"), model.unparsed == 0)
            chip("timer", model.nanoPrecision ? String(localized: "ns precision") : String(localized: "ms precision"), model.nanoPrecision)
            chip("checkmark.shield", model.tlsFields ? String(localized: "tls fields") : String(localized: "no tls fields"), model.tlsFields)
            chip("globe", model.geoEnabled ? String(localized: "geoip on") : String(localized: "geoip off"), model.geoEnabled)
            chip("arrow.clockwise", model.autoRefresh ? String(localized: "auto refresh on") : String(localized: "auto refresh off"), model.autoRefresh)
        }
    }

    private func chip(_ icon: String, _ text: String, _ on: Bool) -> some View {
        HStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: 9))
            Text(text).font(.caption2.monospaced())
        }
        .foregroundStyle(on ? Palette.green : Palette.muted)
    }
}

/// Country chips from GeoIP, tap to filter. Port of `CountryStrip`.
struct CountryStrip: View {
    let countries: [CountryCount]
    let selected: String?
    let onSelect: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(countries) { country in
                    let active = country.code == selected
                    Button { onSelect(country.code) } label: {
                        HStack(spacing: 4) {
                            Text(country.flag)
                            Text(country.code).font(.caption2.monospaced())
                            Text(verbatim: "\(country.count)").font(.caption2.monospaced().weight(.bold))
                        }
                        .foregroundStyle(active ? Palette.blue : Palette.text)
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, 4)
                        .background(active ? Palette.blue.opacity(0.14) : Palette.card, in: Capsule())
                        .overlay(Capsule().strokeBorder(Palette.border))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(country.name) \(country.count)")
                }
            }
        }
    }
}

/// One access log line. Port of `LogRow`.
private struct LogRow: View {
    let line: LogLine
    let country: String?

    var body: some View {
        if let entry = line.entry {
            let color: Color = entry.status >= 500 ? Palette.red : entry.status >= 400 ? Palette.yellow : entry.status > 0 ? Palette.green : Palette.muted
            HStack(spacing: Spacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(entry.path.ifEmpty("/")).font(.caption.monospaced()).lineLimit(1)
                        Text(entry.method).font(.caption2).foregroundStyle(Palette.muted)
                    }
                    HStack(spacing: 4) {
                        if let country { Text(Countries.flag(country)).font(.caption2) }
                        Text(entry.ip).font(.caption2.monospaced()).foregroundStyle(Palette.muted).lineLimit(1)
                        let service = entry.service.ifEmpty(entry.router)
                        if !service.isEmpty {
                            Text("· \(LogParser.shortName(service))").font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
                        }
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(entry.status == 0 ? "-" : String(entry.status)).font(.caption.monospaced().weight(.bold)).foregroundStyle(color)
                    Text(entry.duration.ifEmpty(LogParser.formatMs(entry.durMs))).font(.caption2.monospaced()).foregroundStyle(Palette.muted)
                }
            }
            .contentShape(Rectangle())
        } else {
            Text(line.raw).font(.caption2.monospaced()).foregroundStyle(Palette.muted).lineLimit(2)
        }
    }
}

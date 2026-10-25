import SwiftUI

private let writeMethods: Set<String> = ["POST", "PUT", "PATCH", "DELETE"]

/// The analytics desk: status, latency, methods, domains, paths, clients, services, failures. Port of `LogSignalCards`.
struct LogSignalCards: View {
    let window: LogWindow
    let facets: [LogFacet: String]
    let compact: Bool
    let onFacet: (LogFacet, String) -> Void

    var body: some View {
        VStack(spacing: Spacing.sm) {
            pair { statusCard } and: { latencyCard }
            pair { methodsCard } and: { domainsCard }
            pathsCard
            pair { clientsCard } and: { servicesCard }
            if !window.failures.isEmpty { failuresCard }
        }
    }

    /// Two cards side by side on wide screens, stacked on phones.
    @ViewBuilder
    private func pair<A: View, B: View>(@ViewBuilder _ first: () -> A, @ViewBuilder and second: () -> B) -> some View {
        if compact {
            first()
            second()
        } else {
            HStack(alignment: .top, spacing: Spacing.sm) {
                first()
                second()
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    // MARK: Cards

    private var statusCard: some View {
        let b = window.statuses
        let top = window.codeRank.first
        let sub: String = if let (code, count) = top {
            [
                "\(code) \(LogParser.statusName(code)?.lowercased() ?? String(localized: "response")) x\(count)",
                window.codeRank.count > 1 ? String(localized: "+\(window.codeRank.count - 1) codes") : nil,
            ].compactMap { $0 }.joined(separator: " · ")
        } else {
            [String(localized: "\(b.twoXx) ok"), String(localized: "\(b.threeXx) redirects"), String(localized: "clean")].joined(separator: " · ")
        }
        return DeskCard(
            title: String(localized: "Status codes"),
            glyph: "waveform.path.ecg",
            accent: Palette.blue,
            health: b.fiveXx > 0 ? Palette.red : b.fourXx > 0 ? Palette.yellow : nil,
            hero: LogParser.formatCount(window.parsed),
            sub: sub
        ) {
            if b.fiveXx > 0 {
                Flag(icon: "xmark.circle", count: b.fiveXx, label: "5xx", color: Palette.red) { onFacet(.status, "5xx") }
            } else if b.fourXx > 0 {
                Flag(icon: "exclamationmark.triangle", count: b.fourXx, label: "4xx", color: Palette.yellow) { onFacet(.status, "4xx") }
            } else {
                OkPill(text: String(localized: "all 2xx"))
            }
        } content: {
            Cells(colors: Array(repeating: Palette.red, count: b.fiveXx)
                + Array(repeating: Palette.yellow, count: b.fourXx)
                + Array(repeating: Palette.muted.opacity(0.3), count: b.threeXx + b.oneXx + b.other)
                + Array(repeating: Palette.muted.opacity(0.55), count: b.twoXx))
        } footer: {
            counter("2xx", b.twoXx, "checkmark.circle", Palette.muted, .status, "2xx")
            counter("3xx", b.threeXx, "arrow.up.right", Palette.muted, .status, "3xx")
            counter("4xx", b.fourXx, "exclamationmark.triangle", b.fourXx > 0 ? Palette.yellow : Palette.muted, .status, "4xx")
            counter("5xx", b.fiveXx, "xmark.circle", b.fiveXx > 0 ? Palette.red : Palette.muted, .status, "5xx")
            if b.oneXx > 0 { counter("1xx", b.oneXx, "arrow.left.arrow.right", Palette.muted, .status, "1xx") }
            if b.other > 0 { counter(String(localized: "other"), b.other, "questionmark.circle", Palette.muted, .status, "other") }
        }
    }

    private var latencyCard: some View {
        let l = window.latency
        let hero = LogParser.heroMs(l.average)
        let sub: String = if l.timed == 0 {
            l.held > 0 ? String(localized: "nothing here is a completed response") : String(localized: "no duration in this format")
        } else {
            "p50 \(LogParser.formatMs(l.p50)) · p95 \(LogParser.formatMs(l.p95)) · max \(LogParser.formatMs(l.max))"
                + (l.slowest.map { $0.path.isEmpty ? "" : " \($0.path)" } ?? "")
        }
        return DeskCard(
            title: String(localized: "Response time"),
            glyph: "timer",
            accent: Palette.teal,
            health: l.verySlow > 0 ? Palette.red : l.slow > 0 ? Palette.yellow : nil,
            hero: hero.0,
            heroUnit: hero.1,
            sub: sub
        ) {
            if l.verySlow > 0 {
                Flag(icon: "hourglass.bottomhalf.filled", count: l.verySlow, label: String(localized: "over 2s"), color: Palette.red) { onFacet(.duration, "slow") }
            } else if l.slow > 0 {
                Flag(icon: "hourglass.bottomhalf.filled", count: l.slow, label: String(localized: "over 500ms"), color: Palette.yellow) { onFacet(.duration, "slow") }
            } else if window.retries > 0 {
                Flag(icon: "arrow.left.arrow.right", count: window.retries, label: String(localized: "retries"), color: Palette.yellow, action: nil)
            } else {
                OkPill(text: String(localized: "all under 100ms"))
            }
        } content: {
            Cells(colors: Array(repeating: Palette.red, count: l.verySlow)
                + Array(repeating: Palette.orange, count: max(0, l.slow - l.verySlow))
                + Array(repeating: Palette.yellow, count: l.medium)
                + Array(repeating: Palette.muted.opacity(0.55), count: l.fast))
        } footer: {
            counter(String(localized: "under 100ms"), l.fast, "bolt", Palette.muted, .duration, "fast")
            counter("100-500ms", l.medium, "hourglass", l.medium > 0 ? Palette.yellow : Palette.muted, .duration, "med")
            counter(String(localized: "over 500ms"), l.slow, "hourglass.bottomhalf.filled", l.slow > 0 ? Palette.red : Palette.muted, .duration, "slow")
            if l.held > 0 { counter(String(localized: "upgrades"), l.held, "arrow.left.arrow.right", Palette.muted, .duration, "held") }
        }
    }

    private var methodsCard: some View {
        let writes = window.methods.filter { writeMethods.contains($0.key) }
        let risky = window.methods.contains { $0.key == "PUT" || $0.key == "DELETE" }
        let sub: String = window.methods.max(by: { $0.count < $1.count }).map { top in
            let pct = top.count * 100 / max(1, window.parsed)
            return risky ? String(localized: "\(top.key) \(pct)%, PUT or DELETE present") : String(localized: "\(top.key) \(pct)%, no PUT or DELETE")
        } ?? String(localized: "no method recorded")
        return DeskCard(title: String(localized: "Methods"), glyph: "arrow.left.arrow.right", accent: Palette.orange, hero: String(window.methods.count), sub: sub) {
            if let first = writes.first {
                Flag(
                    icon: "arrow.left.arrow.right",
                    count: writes.reduce(0) { $0 + $1.count },
                    label: String(localized: "writes"),
                    color: writes.contains { $0.key == "DELETE" || $0.key == "PUT" } ? Palette.yellow : Palette.muted
                ) { onFacet(.method, first.key) }
            } else {
                OkPill(text: String(localized: "reads only"))
            }
        } content: {
            RankedRows(groups: window.methods, facet: .method, facets: facets, limit: 3, onFacet: onFacet)
        }
    }

    private var domainsCard: some View {
        rankedCard(
            title: String(localized: "Domains"), glyph: "globe", accent: Palette.purple,
            groups: window.domains, facet: .domain, empty: String(localized: "the access log has no Host field"), limit: 3
        )
    }

    private var pathsCard: some View {
        let failingCount = window.paths.filter { $0.errors > 0 }.count
        let sub: String = window.paths.first.map { top in
            [
                String(localized: "\(top.key) \(top.count) requests"),
                top.worstCode.map { "\(top.worstCount) x \($0)" },
                failingCount - 1 > 0 ? String(localized: "+\(failingCount - 1) more failing") : nil,
            ].compactMap { $0 }.joined(separator: ", ")
        } ?? String(localized: "no path recorded")
        return DeskCard(title: String(localized: "Paths"), glyph: "point.topleft.down.to.point.bottomright.curvepath", accent: Palette.teal, hero: String(window.paths.count), sub: sub) {
            failingFlag(window.paths)
        } content: {
            RankedRows(groups: window.paths, facet: .path, facets: facets, limit: 4, prefix: "~", onFacet: onFacet)
        }
    }

    private var clientsCard: some View {
        let byClass = Dictionary(grouping: window.clients) { LogParser.ipClass($0.key) }
            .map { ($0.key, $0.value.reduce(0) { $0 + $1.count }) }
            .sorted { $0.1 > $1.1 }
            .prefix(4)
        return DeskCard(
            title: String(localized: "Clients"), glyph: "person.2", accent: Palette.blue,
            hero: String(window.clients.count),
            sub: window.clients.first.map { String(localized: "\($0.key) \($0.count) requests") } ?? String(localized: "no client recorded")
        ) {
            failingFlag(window.clients)
        } content: {
            RankedRows(groups: window.clients, facet: .ip, facets: facets, limit: 3, onFacet: onFacet)
            if byClass.count > 1 {
                Divider().overlay(Palette.border).padding(.vertical, Spacing.xs)
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(Array(byClass), id: \.0) { kind, count in
                        counter(ipClassLabel(kind), count, kind == "public" ? "globe" : "wifi.router", Palette.muted, .ipClass, kind)
                    }
                }
            }
        }
    }

    private var servicesCard: some View {
        let providers = Dictionary(grouping: window.services) { LogParser.providerOf($0.key) }.filter { !$0.key.isEmpty }
        return DeskCard(
            title: String(localized: "Services"), glyph: "server.rack", accent: Palette.green,
            hero: window.services.isEmpty ? "-" : String(window.services.count),
            sub: window.services.first.map {
                String(localized: "\(LogParser.shortName($0.key)) \($0.count) requests")
            } ?? String(localized: "this format names no service")
        ) {
            if window.services.contains(where: { $0.errors > 0 }) {
                failingFlag(window.services)
            } else if !window.services.isEmpty {
                OkPill(text: String(localized: "all healthy"))
            }
        } content: {
            RankedRows(groups: window.services, facet: .service, facets: facets, limit: 3, label: { LogParser.shortName($0.key) }, onFacet: onFacet)
        } footer: {
            ForEach(providers.keys.sorted(), id: \.self) { provider in
                counter(provider, providers[provider]!.reduce(0) { $0 + $1.count }, "server.rack", Palette.muted, .provider, provider)
            }
        }
    }

    private var failuresCard: some View {
        Card(accent: Palette.yellow, spacing: Spacing.xs) {
            HStack {
                Image(systemName: "exclamationmark.triangle").font(.system(size: 12)).foregroundStyle(Palette.yellow)
                SectionLabel(text: String(localized: "Where it fails"))
                Spacer()
                Text(String(localized: "\(window.clientErrors + window.serverErrors) of \(window.parsed) requests"))
                    .font(.caption2.monospaced())
                    .foregroundStyle(Palette.muted)
            }
            ForEach(window.failures.prefix(5)) { failure in
                Button {
                    onFacet(.status, String(failure.status))
                    onFacet(.path, "~\(failure.path)")
                } label: {
                    HStack(spacing: Spacing.sm) {
                        Text(verbatim: "\(failure.status)")
                            .font(.caption2.monospaced().weight(.bold))
                            .foregroundStyle(failure.status >= 500 ? Palette.red : Palette.yellow)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(failure.path).font(.caption2.monospaced()).lineLimit(1)
                            Text(String(localized: "\(LogParser.statusName(failure.status) ?? String(localized: "failed")) on \(failure.path), \(String(failure.share))% of that path"))
                                .font(.system(size: 10))
                                .foregroundStyle(Palette.muted)
                                .lineLimit(1)
                        }
                        Spacer()
                        Text(LogParser.formatCount(failure.count)).font(.caption2.monospaced().weight(.bold))
                        Text(verbatim: "\(failure.share)%").font(.caption2).foregroundStyle(Palette.yellow)
                    }
                    .foregroundStyle(Palette.text)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: Helpers

    private func rankedCard(title: String, glyph: String, accent: Color, groups: [RankedGroup], facet: LogFacet, empty: String, limit: Int) -> some View {
        DeskCard(
            title: title, glyph: glyph, accent: accent,
            hero: groups.isEmpty ? "-" : String(groups.count),
            sub: groups.first.map { String(localized: "\($0.key) \($0.count) requests") } ?? empty
        ) {
            failingFlag(groups)
        } content: {
            RankedRows(groups: groups, facet: facet, facets: facets, limit: limit, onFacet: onFacet)
        }
    }

    @ViewBuilder
    private func failingFlag(_ groups: [RankedGroup]) -> some View {
        let failing = groups.filter { $0.errors > 0 }.count
        if failing > 0 {
            Flag(icon: "exclamationmark.triangle", count: failing, label: String(localized: "failing"), color: Palette.yellow) { onFacet(.status, "errors") }
        } else if !groups.isEmpty {
            OkPill(text: String(localized: "all served"))
        }
    }

    private func counter(_ label: String, _ count: Int, _ icon: String, _ color: Color, _ facet: LogFacet, _ value: String) -> some View {
        let active = facets[facet] == value
        return Button { onFacet(facet, value) } label: {
            HStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 9))
                Text(verbatim: "\(count)").font(.caption2.monospaced().weight(.bold))
                Text(label).font(.caption2)
            }
            .foregroundStyle(active ? Palette.blue : color)
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .background(active ? Palette.blue.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 4))
        }
        .buttonStyle(.plain)
    }

    private func ipClassLabel(_ kind: String) -> String {
        switch kind {
        case "public": String(localized: "Public")
        case "private": String(localized: "Private")
        case "cgnat": "CGNAT"
        case "loopback": String(localized: "Loopback")
        case "link-local": String(localized: "Link-local")
        default: String(localized: "Unknown")
        }
    }
}

struct DeskCard<Flags: View, Content: View, Footer: View>: View {
    let title: String
    let glyph: String
    let accent: Color
    var health: Color?
    let hero: String
    var heroUnit: String?
    let sub: String
    @ViewBuilder var flags: Flags
    @ViewBuilder var content: Content
    @ViewBuilder var footer: Footer

    var body: some View {
        Card(accent: health, spacing: Spacing.xs) {
            HStack {
                SectionLabel(text: title)
                Spacer()
                Image(systemName: glyph).font(.system(size: 12)).foregroundStyle(accent)
            }
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(hero).font(.title2.weight(.semibold)).monospacedDigit()
                if let heroUnit { Text(heroUnit).font(.subheadline).foregroundStyle(Palette.muted) }
                Spacer(minLength: Spacing.sm)
                flags
            }
            Text(sub).font(.caption2).foregroundStyle(Palette.muted).lineLimit(2)
            Divider().overlay(Palette.border).padding(.vertical, 2)
            content
            let hasFooter = !(footer is EmptyView)
            if hasFooter {
                Divider().overlay(Palette.border).padding(.vertical, 2)
                FlowLayout(spacing: Spacing.sm) { footer }
            }
        }
    }
}

extension DeskCard where Footer == EmptyView {
    init(
        title: String, glyph: String, accent: Color, health: Color? = nil, hero: String, heroUnit: String? = nil, sub: String,
        @ViewBuilder flags: () -> Flags, @ViewBuilder content: () -> Content
    ) {
        self.init(
            title: title, glyph: glyph, accent: accent, health: health, hero: hero, heroUnit: heroUnit, sub: sub,
            flags: flags, content: content, footer: { EmptyView() }
        )
    }
}

struct Flag: View {
    let icon: String
    let count: Int
    let label: String
    let color: Color
    var action: (() -> Void)?

    var body: some View {
        let content = HStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: 10))
            Text(verbatim: "\(count)").font(.caption2.monospaced().weight(.bold))
            Text(label).font(.caption2)
        }
        .foregroundStyle(color)
        if let action {
            Button(action: action) { content }.buttonStyle(.plain)
        } else {
            content
        }
    }
}

struct OkPill: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(Palette.green)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(Palette.green.opacity(0.12), in: Capsule())
    }
}

struct Cells: View {
    let colors: [Color]

    var body: some View {
        if !colors.isEmpty {
            FlowLayout(spacing: 2) {
                ForEach(Array(colors.prefix(120).enumerated()), id: \.offset) { _, color in
                    RoundedRectangle(cornerRadius: 1.5).fill(color).frame(width: 6, height: 6)
                }
            }
            .accessibilityHidden(true)
        }
    }
}

private struct RankedRows: View {
    let groups: [RankedGroup]
    let facet: LogFacet
    let facets: [LogFacet: String]
    let limit: Int
    var prefix = ""
    var label: (RankedGroup) -> String = { $0.key }
    let onFacet: (LogFacet, String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(groups.prefix(limit)) { group in
                let value = prefix + group.key
                let active = facets[facet] == value
                Button { onFacet(facet, value) } label: {
                    HStack(spacing: Spacing.xs) {
                        Text(label(group)).font(.caption2.monospaced()).foregroundStyle(active ? Palette.blue : Palette.text).lineLimit(1)
                        Spacer(minLength: Spacing.xs)
                        if group.errors > 0 {
                            let tint = group.serverErrors > 0 ? Palette.red : Palette.yellow
                            Image(systemName: "exclamationmark.triangle").font(.system(size: 9)).foregroundStyle(tint)
                            Text(verbatim: "\(group.errors)").font(.caption2.monospaced()).foregroundStyle(tint)
                        }
                        Text(LogParser.formatCount(group.count)).font(.caption2.monospaced().weight(.bold))
                    }
                    .padding(.vertical, 3)
                    .padding(.horizontal, 4)
                    .background(active ? Palette.blue.opacity(0.1) : .clear, in: RoundedRectangle(cornerRadius: 4))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            if groups.count > limit {
                let rest = groups.dropFirst(limit)
                Text(String(localized: "+\(rest.reduce(0) { $0 + $1.count }) requests across \(rest.count) more"))
                    .font(.caption2)
                    .foregroundStyle(Palette.muted)
            }
        }
    }
}

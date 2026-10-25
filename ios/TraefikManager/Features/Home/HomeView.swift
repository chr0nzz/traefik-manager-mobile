import SwiftUI

/// The dashboard: verdict, providers, the four signal cards, entry points and runtime. Port of `DashboardScreen`.
struct HomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var providerFilter: String?

    private var store: DashboardStore { app.dashboard }
    private var compact: Bool { sizeClass != .regular }

    var body: some View {
        Group {
            if let raw = store.raw {
                content(DashboardBuilder.build(raw, providerFilter: providerFilter))
            } else if let error = store.error {
                ContentUnavailableView {
                    Label("Could not load the overview", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(error)
                } actions: {
                    Button("Retry") { Task { await app.refreshDashboard() } }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Palette.bg)
        .navigationTitle("Dashboard")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task { await app.refreshDashboard() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel("Refresh overview")
            }
        }
        .task(id: app.generation) { await app.refreshDashboard() }
    }

    private func content(_ snapshot: DashboardSnapshot) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                if app.isDemo { DemoBanner() }

                VerdictCard(verdict: snapshot.verdict)

                if !snapshot.providers.isEmpty {
                    ProvidersBar(providers: snapshot.providers, active: providerFilter) { name in
                        providerFilter = providerFilter == name ? nil : name
                    }
                }

                ForEach(Array(stride(from: 0, to: snapshot.cards.count, by: 2)), id: \.self) { start in
                    HStack(alignment: .top, spacing: Spacing.md) {
                        ForEach(snapshot.cards[start..<min(start + 2, snapshot.cards.count)]) { card in
                            SignalCardView(card: card, compact: compact)
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }

                if !snapshot.entrypoints.isEmpty {
                    EntrypointsCard(rows: snapshot.entrypoints)
                }

                RuntimeFooter(runtime: snapshot.runtime)
            }
            .padding(Spacing.lg)
            .frame(maxWidth: 1100)
            .frame(maxWidth: .infinity)
        }
        .refreshable { await app.refreshDashboard() }
    }
}

private struct DemoBanner: View {
    var body: some View {
        Label("Demo mode — nothing you change is saved", systemImage: "play.rectangle")
            .font(.footnote.weight(.medium))
            .foregroundStyle(Palette.onSecondaryContainer)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.secondaryContainer, in: RoundedRectangle(cornerRadius: Radius.md))
    }
}

private struct VerdictCard: View {
    let verdict: Verdict

    var body: some View {
        Card(accent: verdict.status.color, spacing: 2) {
            HStack(spacing: Spacing.sm) {
                StatusDot(status: verdict.status)
                Text(verdict.headline).font(.subheadline.weight(.semibold))
            }
            Text(verdict.detail)
                .font(.footnote)
                .foregroundStyle(Palette.muted)
        }
        .accessibilityElement(children: .combine)
    }
}

private struct ProvidersBar: View {
    let providers: [ProviderCount]
    let active: String?
    let onSelect: (String) -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            SectionLabel(text: String(localized: "Providers"))
            ProviderChips(providers: providers, active: active, onSelect: onSelect)
        }
    }
}

struct ProviderChips: View {
    let providers: [ProviderCount]
    var active: String?
    var onSelect: ((String) -> Void)?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(providers) { provider in
                    chip(provider)
                }
            }
        }
    }

    @ViewBuilder
    private func chip(_ provider: ProviderCount) -> some View {
        let isActive = provider.name == active
        let tint: Color = provider.worst == .error ? Palette.red
            : provider.worst == .warn ? Palette.yellow
            : isActive ? Palette.blue : Palette.muted
        let label = HStack(spacing: 4) {
            Image(systemName: Self.icon(for: provider.name))
                .font(.system(size: 11))
                .foregroundStyle(tint)
            Text(provider.name)
                .foregroundStyle(isActive ? Palette.blue : Palette.muted)
            Text(verbatim: "\(provider.count)")
                .fontWeight(.bold)
                .foregroundStyle(isActive ? Palette.blue : Palette.text)
        }
        .font(.caption2.monospaced())
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, 3)
        .background(isActive ? Palette.blue.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: Radius.sm))

        if let onSelect {
            Button { onSelect(provider.name) } label: { label }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isActive ? .isSelected : [])
        } else {
            label
        }
    }

    static func icon(for name: String) -> String {
        if name.hasPrefix("docker") || name.hasPrefix("swarm") { return "shippingbox" }
        if name.hasPrefix("kubernetes") { return "circle.hexagongrid" }
        if name.hasPrefix("file") { return "doc.text" }
        if name.hasPrefix("internal") { return "gearshape" }
        if name.hasPrefix("http") { return "link" }
        if ["consul", "redis", "etcd", "zookeeper"].contains(where: name.hasPrefix) { return "cylinder.split.1x2" }
        if name.hasPrefix("plugin") { return "puzzlepiece.extension" }
        return "powerplug"
    }
}

private struct SignalCardView: View {
    let card: SignalCard
    let compact: Bool

    private var glyph: (String, Color) {
        switch card.kind {
        case .http: ("arrow.triangle.branch", Palette.blue)
        case .stream: ("arrow.left.arrow.right", Palette.teal)
        case .services: ("server.rack", Palette.green)
        case .middlewares: ("puzzlepiece.extension", Palette.purple)
        }
    }

    var body: some View {
        Card(accent: card.health == .ok ? nil : card.health.color, spacing: Spacing.xs) {
            HStack {
                SectionLabel(text: card.title)
                Spacer(minLength: 4)
                Image(systemName: glyph.0)
                    .font(.system(size: 13))
                    .foregroundStyle(glyph.1)
            }
            HStack(alignment: .firstTextBaseline) {
                Text(card.total.map(String.init) ?? "-")
                    .font(compact ? .title2.weight(.semibold) : .title.weight(.semibold))
                    .monospacedDigit()
                Spacer(minLength: 4)
                if card.flags.isEmpty {
                    HealthLabel(status: card.health, text: card.healthLabel)
                } else {
                    HStack(spacing: Spacing.xs) {
                        ForEach(card.flags.prefix(compact ? 1 : 3), id: \.self) { flag in
                            CountChip(flag: flag, showLabel: !compact)
                        }
                    }
                }
            }
            Text(card.sub)
                .font(.caption.monospaced())
                .foregroundStyle(Palette.muted)
                .lineLimit(1)
            SignalStrip(
                cells: card.cells,
                cellSize: compact ? 4 : 6,
                maxCells: compact ? 60 : 150,
                emptyLabel: card.stripEmptyLabel
            )
            .padding(.top, compact ? 2 : Spacing.xs)
            if !card.providers.isEmpty {
                Divider().overlay(Palette.border).padding(.top, Spacing.xs)
                ProviderChips(providers: card.providers)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct CountChip: View {
    let flag: SignalFlag
    let showLabel: Bool

    var body: some View {
        HStack(spacing: 3) {
            Text(verbatim: "\(flag.count)").fontWeight(.bold)
            if showLabel { Text(flag.label) }
        }
        .font(.caption2.monospaced())
        .foregroundStyle(flag.status.color)
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(flag.status.color.opacity(0.14), in: Capsule())
    }
}

private struct EntrypointsCard: View {
    let rows: [EntrypointRow]

    var body: some View {
        Card(spacing: 0) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "door.left.hand.open")
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.muted)
                SectionLabel(text: String(localized: "Entry Points"))
                Text(verbatim: "\(rows.count)")
                    .font(.caption.monospaced())
                    .foregroundStyle(Palette.muted)
            }
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                if index > 0 { Divider().overlay(Palette.border).padding(.top, Spacing.sm) }
                EntrypointRowView(row: row)
            }
        }
    }
}

private struct EntrypointRowView: View {
    let row: EntrypointRow
    private let protoWidth: CGFloat = 50

    private var protoColor: Color {
        switch row.proto {
        case "UDP": Palette.orange
        case "TCP": Palette.teal
        default: Palette.blue
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(row.proto)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(protoColor)
                    .frame(width: protoWidth, alignment: .leading)
                Text(row.name)
                    .font(.subheadline.monospaced())
                    .lineLimit(1)
                Spacer()
                Text(row.routerCount.map(String.init) ?? "-")
                    .font(.caption.monospaced())
                    .foregroundStyle((row.routerCount ?? 0) == 0 ? Palette.muted : Palette.text)
                if row.idle {
                    Text("idle")
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.muted)
                } else {
                    StatusDot(status: row.health, size: 6)
                }
            }
            HStack(spacing: Spacing.sm) {
                Text(row.address)
                    .font(.caption2.monospaced())
                    .foregroundStyle(Palette.muted)
                SignalStrip(cells: row.cells, cellSize: 4)
            }
            .padding(.leading, protoWidth + Spacing.sm)
            Text(row.facts)
                .font(.caption2.monospaced())
                .foregroundStyle(Palette.muted)
                .lineLimit(1)
                .padding(.leading, protoWidth + Spacing.sm)
        }
        .padding(.top, Spacing.sm)
        .opacity(row.idle ? 0.55 : 1)
        .accessibilityElement(children: .combine)
    }
}

private struct RuntimeFooter: View {
    let runtime: RuntimeInfo

    var body: some View {
        Text(parts.joined(separator: "  ·  "))
            .font(.caption2.monospaced())
            .foregroundStyle(Palette.muted)
            .padding(.horizontal, Spacing.xs)
    }

    private var parts: [String] {
        var parts: [String] = []
        if let version = runtime.version {
            parts.append("v\(version)" + (runtime.codename.map { " \($0)" } ?? ""))
        }
        if let metrics = runtime.metrics, !metrics.isEmpty {
            parts.append(String(localized: "metrics \(metrics)"))
        } else {
            parts.append(String(localized: "metrics off"))
        }
        parts.append(runtime.accessLog == true ? String(localized: "access log on") : String(localized: "access log off"))
        if let tracing = runtime.tracing, !tracing.isEmpty {
            parts.append(String(localized: "tracing \(tracing)"))
        } else {
            parts.append(String(localized: "tracing off"))
        }
        return parts
    }
}

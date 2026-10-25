import SwiftUI
import WidgetKit

struct StatusEntry: TimelineEntry {
    var date: Date
    var card: WidgetCard?
    var serverName: String
    var updatedAt: Date?
    var kind: WidgetCardKind
}

struct ServersEntry: TimelineEntry {
    var date: Date
    var servers: [WidgetServerRow]
    var updatedAt: Date?
}

struct LauncherEntry: TimelineEntry {
    var date: Date
    var apps: [WidgetLauncherApp]
}

extension WidgetHealth {
    var color: Color {
        switch self {
        case .ok: Palette.green
        case .warn: Palette.yellow
        case .error: Palette.red
        case .idle, .unknown: Palette.muted
        }
    }
}

/// A run of small squares, one per object. Port of the widget cell strip.
struct CellStrip: View {
    var cells: [WidgetHealth]
    var size: CGFloat = 6
    var rows = 2

    var body: some View {
        GeometryReader { proxy in
            let perRow = max(Int((proxy.size.width + 2) / (size + 2)), 1)
            let shown = Array(cells.prefix(perRow * rows))
            VStack(alignment: .leading, spacing: 2) {
                ForEach(0..<max((shown.count + perRow - 1) / perRow, 1), id: \.self) { row in
                    HStack(spacing: 2) {
                        ForEach(Array(shown.dropFirst(row * perRow).prefix(perRow).enumerated()), id: \.offset) { _, cell in
                            RoundedRectangle(cornerRadius: 1.5).fill(cell.color.opacity(cell == .idle ? 0.35 : 1)).frame(width: size, height: size)
                        }
                    }
                }
            }
        }
        .frame(height: CGFloat(rows) * (size + 2))
    }
}

private struct ChipView: View {
    var chip: WidgetChip

    var body: some View {
        Text(chip.label)
            .font(.system(size: 10, weight: .medium))
            .lineLimit(1)
            .foregroundStyle(chip.health == .unknown ? Palette.muted : chip.health.color)
            .padding(.horizontal, 5)
            .padding(.vertical, 1.5)
            .background((chip.health == .unknown ? Palette.muted : chip.health.color).opacity(0.12), in: Capsule())
    }
}

private struct UpdatedLabel: View {
    var date: Date?

    var body: some View {
        if let date, date > .distantPast {
            Text(date, style: .relative).font(.system(size: 9)).foregroundStyle(Palette.muted)
        }
    }
}

struct StatusWidgetView: View {
    /// Set by the in-app debug gallery; the widget itself reads the environment.
    var familyOverride: WidgetFamily?
    var entry: StatusEntry
    @Environment(\.widgetFamily) private var environmentFamily
    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    var body: some View {
        if let card = entry.card {
            switch family {
            case .accessoryRectangular: accessory(card)
            case .systemSmall: small(card)
            case .systemLarge: large(card)
            default: medium(card)
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.kind.title).font(.caption.weight(.semibold)).foregroundStyle(Palette.text)
                Text(entry.serverName).font(.caption2).foregroundStyle(Palette.muted)
                Spacer()
                Text("Open Traefik Manager to load this card.").font(.caption2).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    private func header(_ card: WidgetCard) -> some View {
        HStack(spacing: 4) {
            Circle().fill(card.health.color).frame(width: 6, height: 6)
            Text(card.title.uppercased()).font(.system(size: 10, weight: .semibold)).kerning(0.4).foregroundStyle(Palette.muted).lineLimit(1)
            Spacer(minLength: 0)
            Text(entry.serverName).font(.system(size: 10)).foregroundStyle(Palette.muted).lineLimit(1)
        }
    }

    private func hero(_ card: WidgetCard, size: CGFloat) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(card.hero).font(.system(size: size, weight: .bold, design: .rounded)).foregroundStyle(Palette.text).minimumScaleFactor(0.6).lineLimit(1)
            Text(card.unit).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
            Spacer(minLength: 0)
            if !card.healthLabel.isEmpty {
                Text(card.healthLabel).font(.system(size: 10, weight: .medium)).foregroundStyle(card.health.color).lineLimit(1)
            }
        }
    }

    private func small(_ card: WidgetCard) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Circle().fill(card.health.color).frame(width: 6, height: 6)
                Text(card.title.uppercased()).font(.system(size: 9, weight: .semibold)).foregroundStyle(Palette.muted).lineLimit(1)
            }
            Text(card.hero).font(.system(size: 34, weight: .bold, design: .rounded)).foregroundStyle(Palette.text).minimumScaleFactor(0.5).lineLimit(1)
            Text(card.unit).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
            Spacer(minLength: 0)
            if let chip = card.chips.first { ChipView(chip: chip) }
            if !card.cells.isEmpty { CellStrip(cells: card.cells, size: 5, rows: 2) }
            Text(entry.serverName).font(.system(size: 9)).foregroundStyle(Palette.muted).lineLimit(1)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func medium(_ card: WidgetCard) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            header(card)
            hero(card, size: 30)
            if !card.chips.isEmpty {
                HStack(spacing: 4) { ForEach(Array(card.chips.prefix(3).enumerated()), id: \.offset) { ChipView(chip: $0.element) } }
            }
            Text(card.sub).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
            Spacer(minLength: 0)
            if !card.cells.isEmpty {
                CellStrip(cells: card.cells, size: 6, rows: 2)
            } else if let row = card.rows.first {
                rowView(row)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func large(_ card: WidgetCard) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            header(card)
            hero(card, size: 36)
            if !card.chips.isEmpty {
                HStack(spacing: 4) { ForEach(Array(card.chips.prefix(3).enumerated()), id: \.offset) { ChipView(chip: $0.element) } }
            }
            Text(card.sub).font(.caption).foregroundStyle(Palette.muted).lineLimit(2)
            if !card.cells.isEmpty { CellStrip(cells: card.cells, size: 7, rows: 3) }
            if !card.rows.isEmpty {
                Divider().overlay(Palette.border)
                ForEach(Array(card.rows.prefix(card.cells.isEmpty ? 8 : 5).enumerated()), id: \.offset) { rowView($0.element) }
            }
            Spacer(minLength: 0)
            HStack(spacing: 4) {
                ForEach(Array(card.footer.prefix(3).enumerated()), id: \.offset) { ChipView(chip: $0.element) }
                Spacer(minLength: 0)
                UpdatedLabel(date: entry.updatedAt)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func rowView(_ row: WidgetRow) -> some View {
        HStack(spacing: 6) {
            Circle().fill(row.health.color).frame(width: 5, height: 5)
            Text(row.name).font(.system(size: 11, design: .monospaced)).foregroundStyle(Palette.text).lineLimit(1)
            Spacer(minLength: 4)
            if !row.flag.isEmpty {
                Text(row.flag).font(.system(size: 10, weight: .semibold)).foregroundStyle(Palette.red)
            }
            Text(row.count).font(.system(size: 11, weight: .medium, design: .monospaced)).foregroundStyle(Palette.muted)
        }
    }

    private func accessory(_ card: WidgetCard) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(card.title).font(.caption2.weight(.semibold)).lineLimit(1)
            Text("\(card.hero) \(card.unit)").font(.headline).lineLimit(1)
            Text(card.healthLabel.isEmpty ? card.sub : card.healthLabel).font(.caption2).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ServersWidgetView: View {
    /// Set by the in-app debug gallery; the widget itself reads the environment.
    var familyOverride: WidgetFamily?
    var entry: ServersEntry
    @Environment(\.widgetFamily) private var environmentFamily
    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    private var limit: Int {
        switch family {
        case .systemSmall: 3
        case .systemLarge: 8
        default: 3
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("ALL SERVERS").font(.system(size: 10, weight: .semibold)).kerning(0.4).foregroundStyle(Palette.muted)
                Spacer()
                UpdatedLabel(date: entry.updatedAt)
            }
            if entry.servers.isEmpty {
                Spacer()
                Text("Open Traefik Manager to load your servers.").font(.caption2).foregroundStyle(Palette.muted)
                Spacer()
            } else {
                ForEach(entry.servers.prefix(limit)) { server in
                    family == .systemSmall ? AnyView(compactRow(server)) : AnyView(fullRow(server))
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func status(_ server: WidgetServerRow) -> WidgetHealth {
        !server.reachable ? .error : server.err > 0 ? .error : server.warn > 0 ? .warn : .ok
    }

    private func compactRow(_ server: WidgetServerRow) -> some View {
        HStack(spacing: 5) {
            Circle().fill(status(server).color).frame(width: 6, height: 6)
            Text(server.name).font(.caption2.weight(.semibold)).lineLimit(1)
            Spacer(minLength: 2)
            Text(server.reachable ? "\(server.routers)" : "-").font(.caption2.monospaced()).foregroundStyle(Palette.muted)
        }
    }

    private func fullRow(_ server: WidgetServerRow) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Circle().fill(status(server).color).frame(width: 6, height: 6)
                Text(server.name).font(.caption.weight(.semibold)).foregroundStyle(Palette.text).lineLimit(1)
                Spacer(minLength: 4)
                if !server.reachable {
                    Text("unreachable").font(.system(size: 10)).foregroundStyle(Palette.red)
                } else {
                    Text(summary(server)).font(.system(size: 10, design: .monospaced)).foregroundStyle(Palette.muted).lineLimit(1)
                }
            }
            if server.reachable && !server.cells.isEmpty && family == .systemLarge {
                CellStrip(cells: server.cells, size: 5, rows: 1)
            }
        }
    }

    private func summary(_ server: WidgetServerRow) -> String {
        var parts = [String(localized: "\(server.routers) routers"), String(localized: "\(server.services) svc")]
        if server.err > 0 { parts.append(String(localized: "\(server.err) down")) }
        if server.bans >= 0 { parts.append(String(localized: "\(server.bans) bans")) }
        return parts.joined(separator: " · ")
    }
}

struct LauncherWidgetView: View {
    /// Set by the in-app debug gallery; the widget itself reads the environment.
    var familyOverride: WidgetFamily?
    var entry: LauncherEntry
    @Environment(\.widgetFamily) private var environmentFamily
    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    private var columns: Int { 4 }
    private var limit: Int { family == .systemLarge ? 16 : 8 }

    var body: some View {
        if entry.apps.isEmpty {
            Text("Open Traefik Manager to load your launcher.")
                .font(.caption2)
                .foregroundStyle(Palette.muted)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            let apps = Array(entry.apps.prefix(limit))
            Grid(horizontalSpacing: 8, verticalSpacing: 8) {
                ForEach(0..<((apps.count + columns - 1) / columns), id: \.self) { row in
                    GridRow {
                        ForEach(0..<columns, id: \.self) { column in
                            let index = row * columns + column
                            if index < apps.count { tile(apps[index]) } else { Color.clear.gridCellUnsizedAxes([.horizontal, .vertical]) }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private func tile(_ app: WidgetLauncherApp) -> some View {
        let content = VStack(spacing: 3) {
            Group {
                if let data = app.icon, let image = UIImage(data: data) {
                    Image(uiImage: image).resizable().scaledToFit().padding(5)
                } else {
                    Text(String(app.name.prefix(1)).uppercased()).font(.headline).foregroundStyle(Palette.blue)
                }
            }
            .frame(width: 36, height: 36)
            .background(Palette.blue.opacity(0.1), in: RoundedRectangle(cornerRadius: 9))
            Text(app.name).font(.system(size: 9)).foregroundStyle(Palette.text).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        if let url = URL(string: app.url) { Link(destination: url) { content } } else { content }
    }
}

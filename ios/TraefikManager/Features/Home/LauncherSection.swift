import SwiftUI

/// App launcher grouped into cards under the dashboard. Port of `LauncherSection`.
struct LauncherSection: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL
    let columns: Int
    var onEdit: (LauncherApp) -> Void
    var onInfo: (LauncherApp) -> Void

    private var store: LauncherStore { app.launcher }

    var body: some View {
        let groups = store.groups
        VStack(spacing: Spacing.sm) {
            ForEach(Array(stride(from: 0, to: groups.count, by: columns)), id: \.self) { start in
                HStack(alignment: .top, spacing: Spacing.sm) {
                    ForEach(groups[start..<min(start + columns, groups.count)]) { group in
                        GroupCard(group: group, density: store.density, open: open, onEdit: onEdit, onInfo: onInfo)
                    }
                    ForEach(0..<(columns - min(columns, groups.count - start)), id: \.self) { _ in
                        Color.clear.frame(maxWidth: .infinity)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func open(_ launcherApp: LauncherApp) {
        guard let text = launcherApp.url, let url = URL(string: text) else { return }
        openURL(url)
    }
}

private struct GroupCard: View {
    let group: LauncherGroup
    let density: LauncherDensity
    let open: (LauncherApp) -> Void
    let onEdit: (LauncherApp) -> Void
    let onInfo: (LauncherApp) -> Void

    @State private var expanded = false
    @State private var width: CGFloat = 320

    private var tileColumns: Int { width >= 560 ? 5 : width >= 300 ? 4 : 3 }
    private var limit: Int { density == .icons ? tileColumns * 5 : 5 }
    private var shown: [LauncherApp] { expanded ? group.apps : Array(group.apps.prefix(limit)) }

    var body: some View {
        Card(spacing: 0) {
            HStack(spacing: Spacing.xs) {
                Image(systemName: LauncherBuilder.icon(for: group))
                    .font(.system(size: 12))
                    .foregroundStyle(Palette.muted)
                Text((group.custom ? group.name : LauncherBuilder.label(for: group.name)).uppercased())
                    .font(.caption2.monospaced().weight(.semibold))
                Text(verbatim: "\(group.apps.count)")
                    .font(.caption2.monospaced())
                    .foregroundStyle(Palette.muted)
                Rectangle().fill(Palette.border).frame(height: 1).padding(.leading, Spacing.xs)
            }
            .padding(.bottom, Spacing.md)

            switch density {
            case .list:
                ForEach(Array(shown.enumerated()), id: \.element.id) { index, item in
                    AppRow(item: item, open: open, onEdit: onEdit, onInfo: onInfo)
                    if index < shown.count - 1 { Divider().overlay(Palette.border) }
                }
            case .icons:
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.sm), count: tileColumns), spacing: Spacing.lg) {
                    ForEach(shown) { item in
                        Tile(item: item, open: open, onEdit: onEdit)
                    }
                }
                .padding(.bottom, Spacing.xs)
            }

            let hiddenCount = group.apps.count - shown.count
            if hiddenCount > 0 || expanded {
                Button {
                    withAnimation { expanded.toggle() }
                } label: {
                    Text(expanded ? String(localized: "show less") : String(localized: "\(hiddenCount) more"))
                        .font(.caption)
                }
                .buttonStyle(.plain)
                .foregroundStyle(Palette.blue)
                .padding(.top, Spacing.xs)
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }
}

private struct AppRow: View {
    let item: LauncherApp
    let open: (LauncherApp) -> Void
    let onEdit: (LauncherApp) -> Void
    let onInfo: (LauncherApp) -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Button { open(item) } label: {
                HStack(spacing: Spacing.sm) {
                    AppPlate(item: item, size: 34)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(item.name).font(.subheadline).foregroundStyle(Palette.text).lineLimit(1)
                        if let reason = item.reason {
                            Text(reason).font(.caption2).foregroundStyle(Palette.muted).lineLimit(1)
                        }
                    }
                    Spacer(minLength: Spacing.xs)
                    Text(item.host)
                        .font(.caption2.monospaced())
                        .foregroundStyle(Palette.muted)
                        .lineLimit(1)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(item.url == nil)
            Button { onInfo(item) } label: { Image(systemName: "info.circle") }
                .accessibilityLabel("About \(item.name)")
            Button { onEdit(item) } label: { Image(systemName: "pencil") }
                .accessibilityLabel("Edit \(item.name)")
        }
        .buttonStyle(.borderless)
        .font(.system(size: 14))
        .foregroundStyle(Palette.muted)
        .padding(.vertical, 5)
        .contextMenu { menu }
    }

    @ViewBuilder
    private var menu: some View {
        Button { onEdit(item) } label: { Label("Card settings", systemImage: "pencil") }
        Button { onInfo(item) } label: { Label("About \(item.name)", systemImage: "info.circle") }
    }
}

private struct Tile: View {
    let item: LauncherApp
    let open: (LauncherApp) -> Void
    let onEdit: (LauncherApp) -> Void

    var body: some View {
        Button { open(item) } label: {
            VStack(spacing: 7) {
                AppPlate(item: item, size: 52)
                Text(item.name)
                    .font(.caption2)
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button { onEdit(item) } label: { Label("Card settings", systemImage: "pencil") }
        }
    }
}

/// Rounded icon tile with a small status square, like `Plate`.
struct AppPlate: View {
    @Environment(AppModel.self) private var app
    let item: LauncherApp
    let size: CGFloat
    var config: DashboardConfig?

    var body: some View {
        let url = app.connection.flatMap { RouteIcons.url(for: item.route, config: config ?? app.launcher.config, baseURL: $0.baseURL) }
        ZStack(alignment: .bottomTrailing) {
            RemoteImage(url: url) {
                Text(item.name.prefix(1).uppercased())
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Palette.muted)
            }
            .padding(4)
            .frame(width: size, height: size)
            .background(Palette.bg, in: RoundedRectangle(cornerRadius: 10))
            RoundedRectangle(cornerRadius: 2)
                .fill(dotColor)
                .frame(width: 7, height: 7)
        }
        .accessibilityHidden(true)
    }

    private var dotColor: Color {
        if !item.route.enabled { return Palette.yellow }
        if item.url == nil { return Palette.muted.opacity(0.3) }
        if item.route.target.isEmpty && item.route.servers.isEmpty { return Palette.red }
        return Palette.muted.opacity(0.3)
    }
}

import SwiftUI
import WidgetKit

@main
struct TraefikManagerWidgets: WidgetBundle {
    var body: some Widget {
        StatusWidget()
        ServersWidget()
        LauncherWidget()
    }
}

// MARK: - Status card


struct StatusProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> StatusEntry { Self.sample }

    func snapshot(for configuration: StatusWidgetIntent, in context: Context) async -> StatusEntry {
        context.isPreview ? (entry(for: configuration).card == nil ? Self.sample : entry(for: configuration)) : entry(for: configuration)
    }

    func timeline(for configuration: StatusWidgetIntent, in context: Context) async -> Timeline<StatusEntry> {
        Timeline(entries: [entry(for: configuration)], policy: .after(Date().addingTimeInterval(15 * 60)))
    }

    private func entry(for configuration: StatusWidgetIntent) -> StatusEntry {
        let snapshot = SharedVault.read()
        let server = configuration.server?.id ?? ""
        let name = snapshot?.servers.first { $0.id == server }?.name ?? configuration.server?.name ?? String(localized: "Host")
        let kind = configuration.card.kind
        return StatusEntry(date: Date(), card: snapshot?.card(kind, server: server), serverName: name, updatedAt: snapshot?.updatedAt, kind: kind)
    }

    static let sample = StatusEntry(
        date: Date(),
        card: WidgetCard(
            key: "http", title: String(localized: "HTTP routers"), hero: "13", unit: String(localized: "routers"),
            health: .warn, healthLabel: String(localized: "degraded"), sub: "broken-app: disabled",
            cells: [.error, .warn] + Array(repeating: .ok, count: 11),
            chips: [WidgetChip(label: "2 down", health: .error)],
            rows: [WidgetRow(name: "media", count: "ok"), WidgetRow(name: "broken-app", count: "off", health: .error)],
            footer: [WidgetChip(label: "file 8"), WidgetChip(label: "docker 3")]
        ),
        serverName: "Host",
        updatedAt: Date(),
        kind: .http
    )
}

struct StatusWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetKinds.status, intent: StatusWidgetIntent.self, provider: StatusProvider()) { entry in
            StatusWidgetView(entry: entry)
                .containerBackground(Palette.card, for: .widget)
        }
        .configurationDisplayName("Status card")
        .description("A dashboard, certificate or CrowdSec card for one server.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .accessoryRectangular])
    }
}

// MARK: - All servers


struct ServersProvider: TimelineProvider {
    func placeholder(in context: Context) -> ServersEntry { Self.sample }

    func getSnapshot(in context: Context, completion: @escaping (ServersEntry) -> Void) {
        let entry = current()
        completion(context.isPreview && entry.servers.isEmpty ? Self.sample : entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ServersEntry>) -> Void) {
        completion(Timeline(entries: [current()], policy: .after(Date().addingTimeInterval(15 * 60))))
    }

    private func current() -> ServersEntry {
        let snapshot = SharedVault.read()
        return ServersEntry(date: Date(), servers: snapshot?.servers ?? [], updatedAt: snapshot?.updatedAt)
    }

    static let sample = ServersEntry(date: Date(), servers: [
        WidgetServerRow(id: "", name: "Host", routers: 13, warn: 1, err: 2, services: 10, cells: [.error, .warn] + Array(repeating: .ok, count: 8), bans: 41),
        WidgetServerRow(id: "a", name: "vps-frankfurt", routers: 6, services: 6, cells: Array(repeating: .ok, count: 6)),
    ], updatedAt: Date())
}

struct ServersWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.servers, provider: ServersProvider()) { entry in
            ServersWidgetView(entry: entry)
                .containerBackground(Palette.card, for: .widget)
        }
        .configurationDisplayName("All servers")
        .description("Routers, problems and bans on every server.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Launcher


struct LauncherProvider: TimelineProvider {
    func placeholder(in context: Context) -> LauncherEntry { Self.sample }

    func getSnapshot(in context: Context, completion: @escaping (LauncherEntry) -> Void) {
        let apps = SharedVault.read()?.launcher ?? []
        completion(LauncherEntry(date: Date(), apps: context.isPreview && apps.isEmpty ? Self.sample.apps : apps))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LauncherEntry>) -> Void) {
        let apps = SharedVault.read()?.launcher ?? []
        completion(Timeline(entries: [LauncherEntry(date: Date(), apps: apps)], policy: .after(Date().addingTimeInterval(60 * 60))))
    }

    static let sample = LauncherEntry(date: Date(), apps: ["Jellyfin", "Grafana", "Home Assistant", "Immich", "Vaultwarden", "Gitea", "Nextcloud", "Portainer"].map {
        WidgetLauncherApp(id: $0, name: $0, url: "https://example.com")
    })
}

struct LauncherWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.launcher, provider: LauncherProvider()) { entry in
            LauncherWidgetView(entry: entry)
                .containerBackground(Palette.card, for: .widget)
        }
        .configurationDisplayName("Launcher")
        .description("Open the apps behind your routes in one tap.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

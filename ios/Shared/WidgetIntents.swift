import AppIntents
import WidgetKit

/// A server the widgets can follow, listed from the last snapshot the app wrote.
struct WidgetServerEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Server"
    static let defaultQuery = WidgetServerQuery()

    /// "" is the host.
    var id: String
    var name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }

    static let host = WidgetServerEntity(id: "", name: String(localized: "Host"))
}

struct WidgetServerQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [WidgetServerEntity] {
        try await suggestedEntities().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [WidgetServerEntity] {
        let servers = SharedVault.read()?.servers ?? []
        let rows = servers.map { WidgetServerEntity(id: $0.id, name: $0.name) }
        return rows.isEmpty ? [.host] : rows
    }

    func defaultResult() async -> WidgetServerEntity? { .host }
}

enum WidgetCardOption: String, AppEnum {
    case http, stream, services, middlewares, entrypoints, certs, sources, scenarios, paths, bans

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Card"
    static let caseDisplayRepresentations: [WidgetCardOption: DisplayRepresentation] = [
        .http: "HTTP routers",
        .stream: "TCP / UDP routers",
        .services: "Services",
        .middlewares: "Middlewares",
        .entrypoints: "Entry points",
        .certs: "Certificates",
        .sources: "Attacking sources",
        .scenarios: "Scenarios",
        .paths: "Targeted paths",
        .bans: "Bans in force",
    ]

    var kind: WidgetCardKind { WidgetCardKind(rawValue: rawValue) ?? .http }
}

/// What a status widget shows and for which server. Port of the Android widget configuration screen.
struct StatusWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Status card"
    static let description = IntentDescription("Pick a dashboard, certificate or CrowdSec card for one server.")

    @Parameter(title: "Card", default: .http)
    var card: WidgetCardOption

    @Parameter(title: "Server")
    var server: WidgetServerEntity?

    init() {}

    init(card: WidgetCardOption, server: WidgetServerEntity?) {
        self.card = card
        self.server = server
    }
}

enum WidgetKinds {
    static let status = "dev.chr0nzz.traefikmanager.status"
    static let servers = "dev.chr0nzz.traefikmanager.servers"
    static let launcher = "dev.chr0nzz.traefikmanager.launcher"
}

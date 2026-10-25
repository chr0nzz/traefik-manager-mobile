import Foundation
import Security

/// Status wire values shared by the app and the widget (`TmStatus.wire()` on Android).
enum WidgetHealth: String, Codable, Sendable {
    case ok, warn, error, idle, unknown
}

struct WidgetChip: Codable, Hashable, Sendable {
    var label: String
    var health: WidgetHealth = .unknown
}

struct WidgetRow: Codable, Hashable, Sendable {
    var name: String
    var count: String
    var flag = ""
    var health: WidgetHealth = .ok
}

/// One card as the widget draws it; built in the app so the extension stays small. Port of `WidgetCard`.
struct WidgetCard: Codable, Hashable, Sendable {
    var key: String
    var title: String
    var hero = "-"
    var unit = ""
    var health: WidgetHealth = .ok
    var healthLabel = ""
    var sub = ""
    var cells: [WidgetHealth] = []
    var chips: [WidgetChip] = []
    var rows: [WidgetRow] = []
    var footer: [WidgetChip] = []
}

struct WidgetServerRow: Codable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String
    var routers = 0
    var warn = 0
    var err = 0
    var services = 0
    var cells: [WidgetHealth] = []
    var bans = -1
    var reachable = true
}

struct WidgetLauncherApp: Codable, Hashable, Sendable, Identifiable {
    var id: String
    var name: String
    var url: String
    var icon: Data?
}

/// Everything the widgets show, written by the app and read by the extension.
struct WidgetSnapshot: Codable, Sendable {
    /// Server id ("" for the host) to its name, in switcher order.
    var servers: [WidgetServerRow] = []
    /// Server id to card key to card.
    var cards: [String: [String: WidgetCard]] = [:]
    var launcher: [WidgetLauncherApp] = []
    var updatedAt = Date.distantPast
    var demo = false

    func card(_ kind: WidgetCardKind, server: String) -> WidgetCard? { cards[server]?[kind.rawValue] }
}

/// The cards a status widget can show. Port of `WidgetCardType` (the overview is its own widget here).
enum WidgetCardKind: String, CaseIterable, Codable, Sendable {
    case http, stream, services, middlewares, entrypoints, certs, sources, scenarios, paths, bans

    var title: String {
        switch self {
        case .http: String(localized: "HTTP routers")
        case .stream: String(localized: "TCP / UDP routers")
        case .services: String(localized: "Services")
        case .middlewares: String(localized: "Middlewares")
        case .entrypoints: String(localized: "Entry points")
        case .certs: String(localized: "Certificates")
        case .sources: String(localized: "Attack sources")
        case .scenarios: String(localized: "Scenarios")
        case .paths: String(localized: "Targeted paths")
        case .bans: String(localized: "Active bans")
        }
    }

    var crowdsec: Bool { [.sources, .scenarios, .paths, .bans].contains(self) }
}

/// A keychain item in an access group both targets list, so no App Group is needed.
enum SharedVault {
    static let accessGroup = "8QCK35QCDX.dev.chr0nzz.traefikmanager.shared"
    private static let service = "dev.chr0nzz.traefikmanager.widgets"
    private static let account = "snapshot"

    static func read() -> WidgetSnapshot? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    @discardableResult
    static func write(_ snapshot: WidgetSnapshot) -> Bool {
        guard let data = try? JSONEncoder().encode(snapshot) else { return false }
        let update = [kSecValueData as String: data] as CFDictionary
        if SecItemUpdate(baseQuery as CFDictionary, update) == errSecSuccess { return true }
        var add = baseQuery
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        return SecItemAdd(add as CFDictionary, nil) == errSecSuccess
    }

    static func clear() { SecItemDelete(baseQuery as CFDictionary) }

    private static var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: accessGroup,
        ]
    }
}

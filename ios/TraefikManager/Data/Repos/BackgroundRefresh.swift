import BackgroundTasks
import Foundation
import UserNotifications

/// Local alerts for new manager notifications, read during background refresh.
/// iOS stand-in for Android's UnifiedPush: no server changes, but iOS decides how often it runs.
enum NotificationAlerts {
    private static let enabledKey = "alerts.enabled"
    private static let severityKey = "alerts.min_severity"

    static var enabled: Bool {
        get { UserDefaults.standard.bool(forKey: enabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }

    /// info, warning or error.
    static var minSeverity: String {
        get { UserDefaults.standard.string(forKey: severityKey) ?? "info" }
        set { UserDefaults.standard.set(newValue, forKey: severityKey) }
    }

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    static func authorization() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    private static func markerKey(_ connection: Connection) -> String { "alerts.seen.\(connection.baseURL.absoluteString)" }

    /// Remembers the newest item so only later ones alert; called whenever the app shows the history.
    static func markSeen(_ items: [TmNotification], connection: Connection?) {
        guard let connection else { return }
        let (id, at) = newest(items)
        let key = markerKey(connection)
        let stored = UserDefaults.standard.dictionary(forKey: key) as? [String: Int64] ?? [:]
        UserDefaults.standard.set(["id": max(Int64(id), stored["id"] ?? 0), "at": max(at, stored["at"] ?? 0)], forKey: key)
    }

    private static func newest(_ items: [TmNotification]) -> (Int, Int64) {
        (items.map(\.id).max() ?? 0, items.map(\.at).max() ?? 0)
    }

    /// Items newer than the stored marker, at or above the chosen severity. Pure, for tests.
    static func fresh(_ items: [TmNotification], seenId: Int64, seenAt: Int64, minSeverity: String) -> [TmNotification] {
        let rank = ["info": 0, "success": 0, "warning": 1, "error": 2]
        let floor = rank[minSeverity] ?? 0
        return items.filter { item in
            let isNew = item.id > 0 ? Int64(item.id) > seenId : item.at > seenAt
            return isNew && (rank[item.type.lowercased()] ?? 0) >= floor
        }
    }

    /// Fetches the history and posts anything new. The first run only sets the marker.
    static func check(service: TMService, connection: Connection) async {
        guard enabled, await authorization() == .authorized,
              let items = try? await service.notifications() else { return }
        let key = markerKey(connection)
        guard let stored = UserDefaults.standard.dictionary(forKey: key) as? [String: Int64] else {
            markSeen(items, connection: connection)
            return
        }
        let new = fresh(items, seenId: stored["id"] ?? 0, seenAt: stored["at"] ?? 0, minSeverity: minSeverity)
        markSeen(items, connection: connection)
        guard !new.isEmpty else { return }
        let center = UNUserNotificationCenter.current()
        for item in new.prefix(4) {
            let content = UNMutableNotificationContent()
            content.title = item.category.isBlank ? "Traefik Manager" : ChannelKinds.categoryLabel(item.category)
            content.body = item.msg
            content.threadIdentifier = connection.baseURL.absoluteString
            content.sound = item.severity == .error ? .default : nil
            try? await center.add(UNNotificationRequest(identifier: "tm-\(item.id)-\(item.at)", content: content, trigger: nil))
        }
        if new.count > 4 {
            let content = UNMutableNotificationContent()
            content.title = "Traefik Manager"
            content.body = String(localized: "\(new.count - 4) more notifications")
            content.threadIdentifier = connection.baseURL.absoluteString
            try? await center.add(UNNotificationRequest(identifier: "tm-more-\(Date().timeIntervalSince1970)", content: content, trigger: nil))
        }
    }

    static func clearDelivered() {
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }
}

/// The app refresh task: alerts first, then the widget snapshot.
enum BackgroundRefresh {
    static let identifier = "dev.chr0nzz.traefikmanager.refresh"

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func run() async {
        schedule()
        guard let connection = ConnectionStore().load() else { return }
        let host: TMService = connection.demo
            ? DemoService()
            : LiveService(client: APIClient(baseURL: connection.baseURL, apiKey: connection.apiKey, agentId: nil))
        if !connection.demo { await NotificationAlerts.check(service: host, connection: connection) }
        await WidgetPublisher.publish(connection: connection, minimumGap: 0)
    }
}

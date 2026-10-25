import Foundation
import Observation

/// The manager's notification history and unread count, shared by the bell and the history screen.
/// Port of `NotificationsRepository`; servers without a read marker fall back to a local count.
@Observable @MainActor
final class NotificationsStore {
    private(set) var items: [TmNotification]?
    private(set) var readUntil: Int?
    private(set) var error: String?
    private var localRead = 0
    private var key = "host"

    var unread: Int {
        let list = items ?? []
        if let readUntil { return list.filter { $0.id > readUntil }.count }
        return max(list.count - localRead, 0)
    }

    func reset(for agentId: String?) {
        key = agentId ?? "host"
        items = nil
        readUntil = nil
        error = nil
        localRead = UserDefaults.standard.integer(forKey: localKey)
    }

    private var localKey: String { "notifications.read.\(key)" }

    func refresh(_ service: TMService) async {
        do {
            async let state = try? service.notificationState()
            items = try await service.notifications()
            readUntil = await state?.readUntil
            error = nil
            NotificationAlerts.markSeen(items ?? [], connection: ConnectionStore().load())
        } catch {
            self.error = error.localizedDescription
        }
    }

    func markRead(_ service: TMService) async {
        NotificationAlerts.clearDelivered()
        let list = items ?? []
        let highest = list.map(\.id).max() ?? 0
        if readUntil != nil && highest > 0 {
            if (try? await service.markNotificationsRead(upTo: highest)) != nil { readUntil = highest }
        } else {
            setLocalRead(list.count)
        }
    }

    func delete(_ notification: TmNotification, service: TMService) async throws {
        _ = try await service.deleteNotification(notification)
        items = items?.filter { notification.id > 0 ? $0.id != notification.id : $0.ts != notification.ts }
        if readUntil == nil { setLocalRead(items?.count ?? 0) }
    }

    func clear(_ service: TMService) async throws {
        _ = try await service.clearNotifications()
        items = []
        if readUntil == nil { setLocalRead(0) }
    }

    private func setLocalRead(_ count: Int) {
        localRead = count
        UserDefaults.standard.set(count, forKey: localKey)
    }
}

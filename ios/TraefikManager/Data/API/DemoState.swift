import Foundation

/// Mutable demo-mode state shared by every `DemoService` copy, so toggles stick for the session.
final class DemoState: @unchecked Sendable {
    static let shared = DemoState()

    private let lock = NSLock()
    private var disabled: Set<String> = []

    var disabledRoutes: Set<String> {
        lock.withLock { disabled }
    }

    func setDisabled(_ routeId: String, _ isDisabled: Bool) {
        lock.withLock {
            if isDisabled { disabled.insert(routeId) } else { disabled.remove(routeId) }
        }
    }
}

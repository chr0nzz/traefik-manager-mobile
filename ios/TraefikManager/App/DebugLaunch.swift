import Foundation

/// Launch arguments for simulator screenshots (`-tmTab routes -tmRoute <id>`). Always nil in release builds.
enum DebugLaunch {
    static var tab: String? { value("tmTab") }
    static var route: String? { value("tmRoute") }
    static var form: String? { value("tmForm") }
    static var template: String? { value("tmTemplate") }
    static var scrollToBottom: Bool { value("tmScroll") == "bottom" }
    /// Store screenshots: hides the demo banner.
    static var cleanShots: Bool { value("tmShots") == "clean" }

    private static func value(_ key: String) -> String? {
        #if DEBUG
        UserDefaults.standard.string(forKey: key)
        #else
        nil
        #endif
    }
}

extension String: @retroactive Identifiable {
    public var id: String { self }
}

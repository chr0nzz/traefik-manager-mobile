import SwiftUI

enum ThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var label: LocalizedStringKey {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}

/// App-level preferences, the iOS side of `PreferencesStore`.
enum PreferenceKey {
    static let themeMode = "theme_mode"
    static let activeAgent = "active_agent"
}

import SwiftUI

@main
struct TraefikManagerApp: App {
    @State private var app = AppModel()
    @AppStorage(PreferenceKey.themeMode) private var themeMode: ThemeMode = .system

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .preferredColorScheme(themeMode.colorScheme)
        }
    }
}

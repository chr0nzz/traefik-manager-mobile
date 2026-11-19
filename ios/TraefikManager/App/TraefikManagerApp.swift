import SwiftUI

@main
struct TraefikManagerApp: App {
    @State private var app = AppModel()
    @AppStorage(PreferenceKey.themeMode) private var themeMode: ThemeMode = .system
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            AppLockGate {
                RootView()
            }
            .environment(app)
            .preferredColorScheme(themeMode.colorScheme)
            // Launcher widget tiles on iOS 17 can only open this app; pass the app's page on to the browser.
            .onOpenURL { url in
                guard url.scheme == "http" || url.scheme == "https" else { return }
                UIApplication.shared.open(url)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                Task { await WidgetPublisher.publish(connection: app.connection) }
            case .background:
                BackgroundRefresh.schedule()
            default:
                break
            }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await BackgroundRefresh.run()
        }
    }
}

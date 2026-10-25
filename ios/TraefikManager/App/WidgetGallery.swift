#if DEBUG
import SwiftUI
import WidgetKit

/// Debug-only preview of the widgets, drawn from the snapshot the app wrote to `SharedVault`.
/// Lets simulator screenshots check the widget layouts and the keychain hand-off without a home screen.
struct WidgetGallery: View {
    @Environment(AppModel.self) private var app
    @State private var snapshot: WidgetSnapshot?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let snapshot {
                    Text("Snapshot \(snapshot.updatedAt.formatted(date: .omitted, time: .standard)) · \(snapshot.servers.count) servers · \(snapshot.launcher.count) apps")
                        .font(.caption)
                        .foregroundStyle(Palette.muted)
                    HStack(alignment: .top, spacing: 16) {
                        frame(.systemSmall) { StatusWidgetView(familyOverride: .systemSmall, entry: entry(.http, snapshot)) }
                        frame(.systemSmall) { ServersWidgetView(familyOverride: .systemSmall, entry: ServersEntry(date: Date(), servers: snapshot.servers, updatedAt: snapshot.updatedAt)) }
                    }
                    frame(.systemMedium) { StatusWidgetView(familyOverride: .systemMedium, entry: entry(.certs, snapshot)) }
                    frame(.systemMedium) { LauncherWidgetView(familyOverride: .systemMedium, entry: LauncherEntry(date: Date(), apps: snapshot.launcher)) }
                    frame(.systemLarge) { StatusWidgetView(familyOverride: .systemLarge, entry: entry(.scenarios, snapshot)) }
                    frame(.systemLarge) { ServersWidgetView(familyOverride: .systemLarge, entry: ServersEntry(date: Date(), servers: snapshot.servers, updatedAt: snapshot.updatedAt)) }
                } else {
                    ProgressView("Building the widget snapshot")
                }
            }
            .padding()
        }
        .background(Palette.bg)
        .navigationTitle("Widgets")
        .task {
            await WidgetPublisher.publish(connection: app.connection, minimumGap: 0)
            snapshot = SharedVault.read()
        }
    }

    private func entry(_ kind: WidgetCardKind, _ snapshot: WidgetSnapshot) -> StatusEntry {
        StatusEntry(date: Date(), card: snapshot.card(kind, server: ""), serverName: "Host", updatedAt: snapshot.updatedAt, kind: kind)
    }

    private func frame(_ family: WidgetFamily, @ViewBuilder content: () -> some View) -> some View {
        let size: CGSize = switch family {
        case .systemSmall: CGSize(width: 170, height: 170)
        case .systemMedium: CGSize(width: 364, height: 170)
        default: CGSize(width: 364, height: 382)
        }
        return content()
            .padding(16)
            .frame(width: size.width, height: size.height)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 22))
    }
}
#endif

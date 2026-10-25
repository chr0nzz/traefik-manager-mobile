import SwiftUI

/// Temporary dashboard: confirms the connection and versions. Replaced by the full Overview in phase 2.
struct HomeView: View {
    @Environment(AppModel.self) private var app
    @State private var manager: ManagerVersion?
    @State private var traefik: TraefikVersion?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                if app.isDemo {
                    Label("Demo mode — nothing you change is saved", systemImage: "play.rectangle")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Palette.onSecondaryContainer)
                        .padding(.horizontal, Spacing.md)
                        .padding(.vertical, Spacing.sm)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Palette.secondaryContainer, in: RoundedRectangle(cornerRadius: Radius.md))
                }

                Card {
                    Text("Connected").font(.headline)
                    row("Server", app.activeServer.label)
                    row("Instance", app.isDemo ? String(localized: "Demo") : app.connection?.baseURL.host() ?? "")
                    row("Traefik Manager", manager.map { "v\($0.version)" } ?? "…")
                    row("Traefik", traefik?.version.map { "v\($0)" } ?? "…")
                }

                if let error {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(Palette.red)
                }
            }
            .padding(Spacing.lg)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.bg)
        .navigationTitle("Dashboard")
        .task(id: app.generation) { await load() }
        .refreshable { await load() }
    }

    private func row(_ title: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(title).foregroundStyle(Palette.muted)
            Spacer()
            Text(value).font(.body.monospaced()).lineLimit(1)
        }
        .font(.subheadline)
    }

    private func load() async {
        guard let service = app.service else { return }
        error = nil
        do {
            async let managerVersion = service.managerVersion()
            async let traefikVersion = service.traefikVersion()
            (manager, traefik) = try await (managerVersion, traefikVersion)
        } catch {
            self.error = error.localizedDescription
        }
    }
}

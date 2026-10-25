import SwiftUI

/// Active server picker: the host plus every agent with health. Port of `ServerSwitcherHeader`.
struct ServerSwitcher: View {
    @Environment(AppModel.self) private var app
    var compact = false

    var body: some View {
        Menu {
            Picker(selection: selection) {
                ForEach(app.servers) { server in
                    Label {
                        Text(server.label)
                        Text(server.detail)
                    } icon: {
                        Image(systemName: server.isHost ? "house" : (server.reachable ? "server.rack" : "exclamationmark.triangle"))
                    }
                    .tag(server.agentId)
                }
            } label: {
                EmptyView()
            }
            .pickerStyle(.inline)
        } label: {
            label
        }
        .disabled(app.servers.count <= 1)
        .accessibilityLabel(Text("Server: \(app.activeServer.label)"))
    }

    private var selection: Binding<String?> {
        Binding(
            get: { app.activeAgentId },
            set: { id in Task { await app.switchServer(to: id) } }
        )
    }

    @ViewBuilder
    private var label: some View {
        let active = app.activeServer
        if compact {
            HStack(spacing: 6) {
                HealthDot(reachable: active.reachable)
                Text(active.label).font(.subheadline.weight(.medium)).lineLimit(1)
                if app.servers.count > 1 {
                    Image(systemName: "chevron.up.chevron.down").font(.caption2)
                }
            }
            .foregroundStyle(Palette.text)
        } else {
            HStack(spacing: Spacing.sm) {
                HealthDot(reachable: active.reachable)
                VStack(alignment: .leading, spacing: 2) {
                    Text(active.label)
                        .font(.body.weight(.medium))
                        .foregroundStyle(Palette.text)
                        .lineLimit(1)
                    Text(app.switching ? String(localized: "Switching…") : active.detail)
                        .font(.caption.monospaced())
                        .foregroundStyle(Palette.muted)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if app.servers.count > 1 {
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption)
                        .foregroundStyle(Palette.muted)
                }
            }
            .contentShape(Rectangle())
        }
    }
}

import SwiftUI

/// What the manager has reported: mark read, delete one, clear all. Port of `NotificationHistoryScreen`.
struct NotificationHistoryView: View {
    @Environment(AppModel.self) private var app
    @State private var pendingDelete: TmNotification?
    @State private var clearOpen = false
    @State private var message: String?

    private var store: NotificationsStore { app.notifications }

    var body: some View {
        content
            .background(Palette.bg)
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .primaryAction) {
                    if store.unread > 0 {
                        Button { Task { if let service = app.hostService { await store.markRead(service) } } } label: {
                            Image(systemName: "checkmark.circle")
                        }
                        .accessibilityLabel("Mark read")
                    }
                    if !(store.items ?? []).isEmpty {
                        Button { clearOpen = true } label: { Image(systemName: "trash") }
                            .accessibilityLabel("Clear the history")
                    }
                }
            }
            .alert("Delete this notification?", isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }), presenting: pendingDelete) { item in
                Button("Delete", role: .destructive) { delete(item) }
                Button("Cancel", role: .cancel) {}
            } message: { item in
                Text(item.msg)
            }
            .alert("Clear the whole history?", isPresented: $clearOpen) {
                Button("Clear all", role: .destructive) { clearAll() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Every notification on the server is removed. This cannot be undone.")
            }
            .task { await refresh() }
            .refreshable { await refresh() }
            .toast($message)
    }

    @ViewBuilder
    private var content: some View {
        if let items = store.items {
            if items.isEmpty {
                ContentUnavailableView("Nothing yet", systemImage: "bell", description: Text("Saves, pings and CrowdSec events show up here."))
            } else {
                List {
                    Section {
                        ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                            row(item)
                                .swipeActions {
                                    Button(role: .destructive) { pendingDelete = item } label: { Label("Delete", systemImage: "trash") }
                                }
                        }
                    } footer: {
                        Text("Times are the server's clock.")
                    }
                }
            }
        } else if let error = store.error {
            ContentUnavailableView {
                Label("Could not load notifications", systemImage: "exclamationmark.triangle")
            } description: { Text(error) } actions: {
                Button("Retry") { Task { await refresh() } }.buttonStyle(.borderedProminent)
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func row(_ item: TmNotification) -> some View {
        let unread = store.readUntil.map { item.id > $0 } ?? false
        let (icon, tint): (String, Color) = switch item.severity {
        case .success: ("checkmark.circle.fill", Palette.green)
        case .info: ("info.circle.fill", Palette.blue)
        case .warning: ("exclamationmark.triangle.fill", Palette.yellow)
        case .error: ("xmark.octagon.fill", Palette.red)
        }
        let meta = [item.since() ?? item.stamp, item.category.isBlank ? nil : ChannelKinds.categoryLabel(item.category)].compactMap { $0 }
        return HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: icon).foregroundStyle(tint).font(.system(size: 15)).padding(.top, 2)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.msg).font(.subheadline.weight(unread ? .semibold : .regular))
                Text(meta.joined(separator: "  ")).font(.caption).foregroundStyle(Palette.muted)
            }
            Spacer(minLength: 0)
            if unread { Circle().fill(Palette.blue).frame(width: 7, height: 7).padding(.top, 6) }
        }
        .accessibilityElement(children: .combine)
    }

    private func refresh() async {
        guard let service = app.hostService else { return }
        await store.refresh(service)
    }

    private func delete(_ item: TmNotification) {
        guard let service = app.hostService else { return }
        Task {
            do {
                try await store.delete(item, service: service)
                message = String(localized: "Notification deleted")
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Could not delete the notification"))
            }
        }
    }

    private func clearAll() {
        guard let service = app.hostService else { return }
        Task {
            do {
                try await store.clear(service)
                message = String(localized: "History cleared")
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Could not clear the history"))
            }
        }
    }
}

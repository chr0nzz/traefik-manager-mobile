import SwiftUI

private let webhookTypes = ["discord", "slack", "ntfy", "gotify", "generic"]
private let routeCheckIntervals = [60, 300, 900, 1800]

/// Route checks, notification channels (or the single legacy webhook) and a link to the history.
/// Port of `NotificationsScreen`; Android's UnifiedPush section has no iOS equivalent.
struct NotificationsSettingsView: View {
    @Environment(AppModel.self) private var app
    @State private var loading = true
    @State private var channelsSupported: Bool?
    @State private var channels: [NotificationChannel] = []
    @State private var channelsError: String?
    @State private var routeCheckSupported: Bool?
    @State private var routeCheckEnabled = true
    @State private var routeCheckInterval = 300
    @State private var savingRouteCheck = false
    @State private var webhookUrl = ""
    @State private var webhookType = "discord"
    @State private var webhookUsername = ""
    @State private var webhookPassword = ""
    @State private var webhookTest: TestState = .idle
    @State private var savingWebhook = false
    @State private var editing: NotificationChannel?
    @State private var pendingDelete: NotificationChannel?
    @State private var message: String?

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                form
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if channelsSupported == true {
                    Button { editing = NotificationChannel.draft } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add a channel")
                } else if channelsSupported == false {
                    if savingWebhook { ProgressView() } else { Button("Save", action: saveWebhook) }
                }
            }
        }
        .sheet(item: $editing) { draft in
            NavigationStack {
                ChannelEditorView(draft: draft) { saved in
                    replace(saved)
                }
            }
        }
        .alert("Remove channel", isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }), presenting: pendingDelete) { channel in
            Button("Remove", role: .destructive) { delete(channel) }
            Button("Cancel", role: .cancel) {}
        } message: { channel in
            Text(channel.kind == "unifiedpush"
                 ? String(localized: "Remove \"\(channel.name)\"? Push to that phone stops until the app registers again.")
                 : String(localized: "Remove channel \"\(channel.name)\"? Events will stop being delivered to it."))
        }
        .task { await load() }
        .refreshable { await load() }
        .toast($message)
    }

    private var form: some View {
        Form {
            Section {
                NavigationLink(value: SettingsPage.history) {
                    HStack {
                        Label("History", systemImage: "clock.arrow.circlepath")
                        Spacer()
                        if app.notifications.unread > 0 {
                            Text("\(app.notifications.unread)")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Palette.red, in: Capsule())
                        }
                    }
                }
            } footer: {
                Text("The bell on the Dashboard shows what the manager has reported.")
            }

            if routeCheckSupported == true {
                Section {
                    Toggle(isOn: Binding(get: { routeCheckEnabled }, set: { saveRouteChecks(enabled: $0) })) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Check route reachability")
                            Text("Colours the status dot on Routes, and alerts under the Traefik category when a backend stops answering.")
                                .font(.caption2)
                                .foregroundStyle(Palette.muted)
                        }
                    }
                    .disabled(savingRouteCheck)
                    if routeCheckEnabled {
                        Picker("Every", selection: Binding(get: { routeCheckInterval }, set: { saveRouteChecks(interval: $0) })) {
                            ForEach(intervalOptions, id: \.self) { Text(intervalLabel($0)).tag($0) }
                        }
                        .disabled(savingRouteCheck)
                    }
                } header: {
                    Text("Route checks")
                }
            }

            if channelsSupported != false {
                Section {
                    if channels.isEmpty {
                        ContentUnavailableView("No channels configured", systemImage: "bell.slash",
                                               description: Text("Add a channel to get a message when routes change, backups run or certificates expire."))
                    }
                    ForEach(channels) { channel in channelRow(channel) }
                    if let channelsError {
                        Text(channelsError).font(.footnote).foregroundStyle(Palette.red)
                    }
                } header: {
                    Text("Channels \(channels.count)")
                } footer: {
                    Text("Every enabled channel gets its own copy of an event, filtered by the categories, severity, digest and quiet hours set on it.")
                }
            } else {
                webhookSection
            }
        }
    }

    private var intervalOptions: [Int] {
        routeCheckIntervals.contains(routeCheckInterval) ? routeCheckIntervals : routeCheckIntervals + [routeCheckInterval]
    }

    private func intervalLabel(_ seconds: Int) -> String {
        routeCheckIntervals.contains(seconds) ? String(localized: "\(seconds / 60) minutes") : String(localized: "\(seconds) seconds")
    }

    private func channelRow(_ channel: NotificationChannel) -> some View {
        let missing = channel.missingFields
        return VStack(alignment: .leading, spacing: Spacing.xs) {
            Toggle(isOn: Binding(get: { channel.enabled }, set: { _ in toggle(channel) })) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: Spacing.xs) {
                        Text(channel.name).font(.subheadline.weight(.semibold))
                        Text(ChannelKinds.label(channel.kind)).font(.caption2).foregroundStyle(Palette.purple)
                    }
                    Text(missing.isEmpty ? channel.summary : String(localized: "Needs \(missing.joined(separator: ", "))"))
                        .font(.caption)
                        .foregroundStyle(missing.isEmpty ? Palette.muted : Palette.yellow)
                }
            }
            HStack(spacing: Spacing.lg) {
                Button { test(channel) } label: { Image(systemName: "paperplane") }
                    .accessibilityLabel(String(localized: "Send a test to \(channel.name)"))
                Button { editing = channel } label: { Image(systemName: "pencil") }
                    .accessibilityLabel(String(localized: "Edit \(channel.name)"))
                Spacer()
                Button { pendingDelete = channel } label: { Image(systemName: "trash").foregroundStyle(Palette.red) }
                    .accessibilityLabel(String(localized: "Remove \(channel.name)"))
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 2)
    }

    private var webhookSection: some View {
        Section {
            TextField("https://discord.com/api/webhooks/…", text: $webhookUrl)
                .keyboardType(.URL)
                .onChange(of: webhookUrl) { webhookTest = .idle }
            Picker("Provider", selection: $webhookType) {
                ForEach(webhookTypes, id: \.self) { Text($0).tag($0) }
            }
            .onChange(of: webhookType) { webhookTest = .idle }
            TextField("Username (optional)", text: $webhookUsername)
            SecureField("Password or token (optional)", text: $webhookPassword)
            Button(action: testWebhook) {
                HStack {
                    Text("Send a test")
                    Spacer()
                    switch webhookTest {
                    case .running: ProgressView()
                    case .ok: Text("Request accepted").font(.footnote).foregroundStyle(Palette.green)
                    case let .failed(text): Text(text).font(.footnote).foregroundStyle(Palette.red).lineLimit(2)
                    case .idle: EmptyView()
                    }
                }
            }
            .disabled(webhookTest == .running || webhookUrl.isBlank)
        } header: {
            Text("Webhook")
        } footer: {
            VStack(alignment: .leading, spacing: 6) {
                Text("Channels arrived in Traefik Manager 1.12.0. This server is older, so it still takes the one webhook.")
                Text("The server never returns the password, so it always looks empty.")
                if case .ok = webhookTest {
                    Text("The endpoint received the request. Check the destination to confirm it arrived.")
                }
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
    }

    // MARK: Loading

    private func load() async {
        guard let service = app.hostService else { return }
        async let health = loadRouteChecks()
        async let settings = try? service.settingsRaw()
        async let history: Void = app.notifications.refresh(service)
        do {
            channels = try await service.notificationChannels()
            channelsSupported = true
            channelsError = nil
        } catch APIError.http(404, _) {
            channelsSupported = false
        } catch {
            channelsSupported = channelsSupported ?? true
            channelsError = error.localizedDescription.ifEmpty(String(localized: "Failed to load channels"))
        }
        if let raw = await settings {
            webhookUrl = raw.text("webhook_url")
            webhookType = raw.text("webhook_type").ifEmpty("discord")
            webhookUsername = raw.text("webhook_username")
        }
        _ = await (health, history)
        loading = false
    }

    private func loadRouteChecks() async {
        guard let service = app.service else { return }
        do {
            let snapshot = try await service.routeHealth()
            routeCheckSupported = true
            routeCheckEnabled = snapshot.enabled
            routeCheckInterval = snapshot.interval
        } catch APIError.http(404, _) {
            routeCheckSupported = false
        } catch {}
    }

    // MARK: Actions

    private func saveRouteChecks(enabled: Bool? = nil, interval: Int? = nil) {
        guard let service = app.hostService else { return }
        let nextEnabled = enabled ?? routeCheckEnabled
        let nextInterval = interval ?? routeCheckInterval
        savingRouteCheck = true
        Task {
            do {
                let response = try await service.saveRouteHealth(enabled: nextEnabled, interval: nextInterval)
                guard response.ok else { throw APIError.server(response.error ?? String(localized: "Could not save the route checks")) }
                routeCheckEnabled = response.enabled
                routeCheckInterval = response.interval
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Could not save the route checks"))
            }
            savingRouteCheck = false
        }
    }

    private func replace(_ channel: NotificationChannel) {
        if let index = channels.firstIndex(where: { $0.id == channel.id }) { channels[index] = channel } else { channels.append(channel) }
    }

    private func toggle(_ channel: NotificationChannel) {
        guard let service = app.hostService else { return }
        var next = channel
        next.enabled.toggle()
        Task {
            do {
                let response = try await service.updateChannel(channel.id, next)
                guard let saved = response.channel else { throw APIError.server(response.error ?? String(localized: "Failed to update channel")) }
                replace(saved)
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Failed to update channel"))
            }
        }
    }

    private func test(_ channel: NotificationChannel) {
        guard let service = app.hostService else { return }
        Task {
            let result = try? await service.testChannel(channel.id)
            if let result, result.ok, result.error == nil {
                message = String(localized: "Test message delivered")
            } else {
                message = (result?.error ?? result?.detail).flatMap { $0.isBlank ? nil : $0 } ?? String(localized: "Test failed")
            }
        }
    }

    private func delete(_ channel: NotificationChannel) {
        guard let service = app.hostService else { return }
        Task {
            do {
                _ = try await service.deleteChannel(channel.id)
                channels.removeAll { $0.id == channel.id }
                message = String(localized: "Channel removed")
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Failed to remove channel"))
            }
        }
    }

    private func testWebhook() {
        guard let service = app.hostService else { return }
        webhookTest = .running
        let request = WebhookTestRequest(url: webhookUrl.trimmed, webhookType: webhookType, username: webhookUsername.trimmed, password: webhookPassword)
        Task {
            do {
                let result = try await service.testWebhook(request)
                webhookTest = result.ok ? .ok("sent") : .failed(result.error ?? String(localized: "The webhook was not delivered"))
            } catch {
                webhookTest = .failed(error.localizedDescription.ifEmpty(String(localized: "Delivery failed")))
            }
        }
    }

    private func saveWebhook() {
        guard let service = app.hostService else { return }
        savingWebhook = true
        var changes: JSONObject = [
            "webhook_url": .string(webhookUrl.trimmed),
            "webhook_type": .string(webhookType),
            "webhook_username": .string(webhookUsername.trimmed),
        ]
        if !webhookPassword.isEmpty { changes["webhook_password"] = .string(webhookPassword) }
        Task {
            do {
                try await service.patchSettings(changes)
                webhookPassword = ""
                message = String(localized: "Webhook saved")
            } catch {
                message = error.localizedDescription.ifEmpty(String(localized: "Could not save the webhook"))
            }
            savingWebhook = false
        }
    }
}

extension NotificationChannel {
    /// A new, unsaved channel.
    static var draft: NotificationChannel {
        var channel = NotificationChannel()
        channel.id = "new-\(UUID().uuidString)"
        channel.kind = "discord"
        return channel
    }

    var isDraft: Bool { id.hasPrefix("new-") }
}

import SwiftUI

/// Create or edit one notification channel, with a save-then-test button. Port of `ChannelEditorSheet`.
struct ChannelEditorView: View {
    let onSaved: (NotificationChannel) -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var draft: NotificationChannel
    @State private var quietStart: Date?
    @State private var quietEnd: Date?
    @State private var saving = false
    @State private var test: TestState = .idle
    @State private var error: String?

    init(draft: NotificationChannel, onSaved: @escaping (NotificationChannel) -> Void) {
        self.onSaved = onSaved
        _draft = State(initialValue: draft)
        let window = draft.quietHours.split(separator: "-", maxSplits: 1).map(String.init)
        _quietStart = State(initialValue: window.count == 2 ? Self.time(window[0]) : nil)
        _quietEnd = State(initialValue: window.count == 2 ? Self.time(window[1]) : nil)
    }

    private var kind: ChannelKind? { ChannelKinds.of(draft.kind) }

    var body: some View {
        Form {
            Section {
                TextField("e.g. Ops Discord", text: $draft.name)
                Picker("Type", selection: $draft.kind) {
                    ForEach(ChannelKinds.all) { Text($0.pickerLabel).tag($0.key) }
                }
            } footer: {
                Text("Label for this destination in the channel list. The type sets the payload format for the target service.")
            }

            if let kind {
                Section {
                    ForEach(kind.fields, id: \.key) { field in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(field.label).font(.caption).foregroundStyle(Palette.muted)
                            let value = Binding(get: { draft.value(field.key) }, set: { draft.setValue(field.key, $0) })
                            if field.secret {
                                SecureField(field.placeholder, text: value)
                            } else {
                                TextField(field.placeholder, text: value).keyboardType(field.key == "url" ? .URL : .default)
                            }
                            Text(field.help).font(.caption2).foregroundStyle(Palette.muted)
                        }
                    }
                }
                if kind.basicAuth {
                    Section {
                        TextField("Username", text: $draft.username)
                        SecureField("Password", text: $draft.password)
                    } header: {
                        Text("Basic auth")
                    } footer: {
                        Text("Optional credentials for endpoints that require authentication.")
                    }
                }
            }

            Section {
                Toggle(isOn: $draft.enabled) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Enabled")
                        Text("Deliver events to this channel.").font(.caption2).foregroundStyle(Palette.muted)
                    }
                }
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    label("Categories", "Which kinds of event reach this channel. Select none to send every category.")
                    FlowLayout(spacing: Spacing.xs) {
                        ForEach(ChannelKinds.categories, id: \.key) { option in
                            chip(option.label, selected: draft.categories.contains(option.key)) {
                                if draft.categories.contains(option.key) {
                                    draft.categories.removeAll { $0 == option.key }
                                } else {
                                    draft.categories.append(option.key)
                                }
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    label("Minimum severity", "Anything below this level is dropped for this channel.")
                    Picker("Minimum severity", selection: $draft.minSeverity) {
                        ForEach(ChannelKinds.severities, id: \.key) { Text($0.label).tag($0.key) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    label("Digest", "Send each event as it happens, or batch them into one message.")
                    Picker("Digest", selection: $draft.digest) {
                        ForEach(ChannelKinds.digests, id: \.key) { Text($0.label).tag($0.key) }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }
            } header: {
                Text("Delivery")
            }

            Section {
                if let start = quietStart, let end = quietEnd {
                    DatePicker("From", selection: Binding(get: { start }, set: { quietStart = $0 }), displayedComponents: .hourAndMinute)
                    DatePicker("To", selection: Binding(get: { end }, set: { quietEnd = $0 }), displayedComponents: .hourAndMinute)
                    Button("Clear quiet hours", role: .destructive) {
                        quietStart = nil
                        quietEnd = nil
                    }
                    Toggle(isOn: $draft.breakThrough) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Break through quiet hours")
                            Text("Deliver errors right away even inside the quiet window.").font(.caption2).foregroundStyle(Palette.muted)
                        }
                    }
                } else {
                    Button("Set quiet hours") {
                        quietStart = Self.time("22:00")
                        quietEnd = Self.time("07:00")
                    }
                }
            } header: {
                Text("Quiet hours")
            } footer: {
                Text("Hold messages inside this window and send them once it ends. Leave empty for none.")
            }

            Section {
                Button(action: runTest) {
                    HStack {
                        Text("Test")
                        Spacer()
                        switch test {
                        case .running: ProgressView()
                        case .ok: Text("Delivered.").foregroundStyle(Palette.green)
                        case let .failed(text): Text(text).foregroundStyle(Palette.red).lineLimit(2)
                        case .idle: EmptyView()
                        }
                    }
                }
                .disabled(saving || test == .running)
                if let error {
                    Text(error).font(.footnote).foregroundStyle(Palette.red)
                }
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .navigationTitle(draft.isDraft ? "Add channel" : "Edit channel")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if saving { ProgressView() } else { Button("Save", action: save) }
            }
        }
        .onChange(of: draft) { if test != .running { test = .idle } }
        .interactiveDismissDisabled(saving)
    }

    private func label(_ title: LocalizedStringKey, _ help: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title)
            Text(help).font(.caption2).foregroundStyle(Palette.muted)
        }
    }

    private func chip(_ text: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .foregroundStyle(selected ? Color.white : Palette.text)
                .background(selected ? Palette.blue : Palette.card, in: Capsule())
                .overlay(Capsule().strokeBorder(selected ? Color.clear : Palette.border))
        }
        .buttonStyle(.plain)
    }

    private static func time(_ text: String) -> Date? {
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return Calendar.current.date(bySettingHour: min(max(parts[0], 0), 23), minute: min(max(parts[1], 0), 59), second: 0, of: Date())
    }

    private static func format(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }

    private func prepared() -> NotificationChannel? {
        var channel = draft
        if let start = quietStart, let end = quietEnd {
            channel.quietHours = "\(Self.format(start))-\(Self.format(end))"
        } else {
            channel.quietHours = ""
        }
        let missing = channel.missingFields
        if !missing.isEmpty {
            error = String(localized: "Fill in \(missing.joined(separator: ", ")) first.")
            return nil
        }
        error = nil
        return channel
    }

    private func persist(_ channel: NotificationChannel) async throws -> NotificationChannel {
        guard let service = app.hostService else { throw APIError.server(String(localized: "Failed to save channel")) }
        let response = channel.isDraft ? try await service.createChannel(channel) : try await service.updateChannel(channel.id, channel)
        guard let saved = response.channel else { throw APIError.server(response.error ?? String(localized: "Failed to save channel")) }
        return saved
    }

    private func save() {
        guard let channel = prepared() else { return }
        saving = true
        Task {
            do {
                let saved = try await persist(channel)
                onSaved(saved)
                dismiss()
            } catch {
                self.error = error.localizedDescription.ifEmpty(String(localized: "Failed to save channel"))
            }
            saving = false
        }
    }

    private func runTest() {
        guard let channel = prepared(), let service = app.hostService else { return }
        test = .running
        Task {
            do {
                let saved = try await persist(channel)
                onSaved(saved)
                draft.id = saved.id
                let result = try? await service.testChannel(saved.id)
                test = result?.ok == true && result?.error == nil
                    ? .ok("")
                    : .failed((result?.error ?? result?.detail).flatMap { $0.isBlank ? nil : $0 } ?? String(localized: "Failed."))
            } catch {
                test = .idle
                self.error = error.localizedDescription.ifEmpty(String(localized: "Failed to save channel"))
            }
        }
    }
}

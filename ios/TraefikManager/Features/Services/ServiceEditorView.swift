import SwiftUI

/// Create or edit a load balancer, weighted, mirroring or failover service. Port of `ServiceEditorSheet`.
struct ServiceEditorView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State var draft: ServiceDraft
    var onSaved: (String) -> Void

    @State private var saving = false
    @State private var error: String?

    private var serviceOptions: [String] {
        app.services.rows.map(\.shortName).filter { $0 != draft.originalName }
    }

    var body: some View {
        Form {
            if draft.readOnly {
                Section {
                    Text("Highest random weight is not authorable in Traefik Manager. It is read here and edited in the config file.")
                        .font(.footnote)
                        .foregroundStyle(Palette.yellow)
                }
            }
            if let error {
                Section { Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Palette.red).font(.footnote) }
            }

            Section {
                LabeledContent("Name") {
                    TextField("Name", text: $draft.name, prompt: Text(verbatim: "api-pool"))
                        .multilineTextAlignment(.trailing)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Picker("Type", selection: $draft.type) {
                    ForEach(ServiceDraft.authorableTypes, id: \.self) { Text(ServiceRows.typeLabel($0)).tag($0) }
                    if draft.readOnly { Text(ServiceRows.typeLabel("highestRandomWeight")).tag("highestRandomWeight") }
                }
                if draft.adding {
                    LabeledContent("Config file") {
                        TextField("Config file", text: $draft.configFile, prompt: Text(verbatim: "dynamic.yml"))
                            .multilineTextAlignment(.trailing)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }
            } footer: {
                if draft.adding { Text("Leave empty for the default file.") }
            }

            Section {
                ForEach(Array($draft.children.enumerated()), id: \.element.id) { index, $child in
                    ChildRow(
                        child: $child,
                        hint: draft.rowHint(index),
                        usesShare: draft.usesShare,
                        shareLabel: draft.shareLabel,
                        services: serviceOptions
                    )
                    .swipeActions {
                        if draft.children.count > 1 {
                            Button("Remove this backend", systemImage: "trash", role: .destructive) {
                                draft.children.removeAll { $0.id == child.id }
                            }
                        }
                    }
                }
                if draft.children.count < draft.maxRows {
                    Button { draft.children.append(ServiceChildDraft()) } label: { Label("Add backend", systemImage: "plus.circle") }
                }
            } header: {
                Text("Backends")
            } footer: {
                Text(backendHelp)
            }

            if draft.type == "loadBalancer" {
                healthSection
            }
        }
        .disabled(saving)
        .navigationTitle(draft.adding ? String(localized: "New service") : String(localized: "Edit service"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if saving { ProgressView() } else { Button("Save", action: save).disabled(draft.readOnly) }
            }
        }
        .interactiveDismissDisabled(saving)
    }

    private var backendHelp: String {
        switch draft.type {
        case "mirroring": String(localized: "The first row serves traffic. The rest mirror it by percent.")
        case "failover": String(localized: "The first row is primary, the second is the fallback.")
        case "loadBalancer": String(localized: "Addresses to balance across.")
        default: String(localized: "Weights decide the share each backend takes.")
        }
    }

    private var healthSection: some View {
        Section {
            Toggle(isOn: $draft.healthCheck.enabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Poll the servers")
                    Text("Traefik stops sending traffic to servers that fail").font(.caption).foregroundStyle(Palette.muted)
                }
            }
            if draft.healthCheck.enabled {
                field("Path", $draft.healthCheck.path, String(localized: "/ (server root)"), mono: true)
                HStack {
                    field("Interval", $draft.healthCheck.interval, "30s")
                    field("Timeout", $draft.healthCheck.timeout, "5s")
                }
                HStack {
                    field("Method", $draft.healthCheck.method, "GET")
                    field("Expected status", $draft.healthCheck.status, String(localized: "any 2xx or 3xx"), numeric: true)
                }
                field("Interval when down", $draft.healthCheck.unhealthyInterval, String(localized: "same as interval"))
                Picker("Scheme", selection: $draft.healthCheck.scheme) {
                    Text("same as server").tag("")
                    Text(verbatim: "http").tag("http")
                    Text(verbatim: "https").tag("https")
                }
                field("Port", $draft.healthCheck.port, String(localized: "same as server"), numeric: true)
                field("Host header", $draft.healthCheck.hostname, "optional")
                Picker("Mode", selection: $draft.healthCheck.mode) {
                    Text(verbatim: "http").tag("")
                    Text(verbatim: "grpc").tag("grpc")
                }
                Toggle("Follow redirects", isOn: $draft.healthCheck.followRedirects)
                ForEach($draft.healthCheck.headers) { $header in
                    HStack {
                        TextField("Header", text: $header.name).font(.body.monospaced()).textInputAutocapitalization(.never)
                        TextField("Value", text: $header.value).textInputAutocapitalization(.never)
                    }
                    .swipeActions {
                        Button("Remove this header", systemImage: "trash", role: .destructive) {
                            draft.healthCheck.headers.removeAll { $0.id == header.id }
                        }
                    }
                }
                Button { draft.healthCheck.headers.append(.init()) } label: { Label("Add header", systemImage: "plus.circle") }
            }
        } header: {
            Text("Health check")
        }
    }

    private func field(_ label: LocalizedStringKey, _ text: Binding<String>, _ placeholder: String, mono: Bool = false, numeric: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(Palette.muted)
            TextField(label, text: text, prompt: Text(verbatim: placeholder))
                .font(mono ? .body.monospaced() : .body)
                .keyboardType(numeric ? .numberPad : .default)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
    }

    private func save() {
        if let problem = draft.problem {
            error = problem
            return
        }
        guard let service = app.service else { return }
        saving = true
        error = nil
        Task {
            defer { saving = false }
            do {
                let name = try await app.services.save(draft, service: service)
                await app.refreshServices()
                app.routes.noteExternalChange()
                onSaved(name)
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}

private struct ChildRow: View {
    @Binding var child: ServiceChildDraft
    let hint: String?
    let usesShare: Bool
    let shareLabel: String
    let services: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack {
                if let hint { Text(hint).font(.caption).foregroundStyle(Palette.muted) }
                Picker("Kind", selection: $child.kind) {
                    Text("Address").tag(ServiceChildDraft.manual)
                    Text("Service").tag(ServiceChildDraft.service)
                }
                .pickerStyle(.segmented)
            }
            if child.kind == ServiceChildDraft.service {
                Picker("Service", selection: $child.name) {
                    Text("Select a service").tag("")
                    ForEach(services + (child.name.isEmpty || services.contains(child.name) ? [] : [child.name]), id: \.self) {
                        Text($0).tag($0)
                    }
                }
            } else {
                HStack {
                    Picker("Scheme", selection: $child.scheme) {
                        Text(verbatim: "http").tag("http")
                        Text(verbatim: "https").tag("https")
                        Text(verbatim: "h2c").tag("h2c")
                    }
                    .labelsHidden()
                    .fixedSize()
                    TextField("Address", text: $child.address, prompt: Text(verbatim: "10.0.0.10:80"))
                        .font(.body.monospaced())
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                }
            }
            if usesShare {
                HStack {
                    Text(shareLabel).foregroundStyle(Palette.muted)
                    TextField(shareLabel, text: $child.share)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                }
            }
        }
    }
}

import SwiftUI

/// Add or edit a route. Port of `RouteFormScreen`, laid out as a native iOS form.
struct RouteFormView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    /// nil creates a new route.
    let routeId: String?
    var onSaved: (String) -> Void = { _ in }

    @State private var model = RouteFormModel()
    @State private var showConfigPicker = false
    @State private var newDomain = ""
    @State private var showLoadBalancing = false
    @State private var showPreset = false

    var body: some View {
        @Bindable var model = model
        Form {
            if model.loading {
                Section { HStack { Spacer(); ProgressView(); Spacer() } }
            } else {
                if let error = model.error {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(Palette.red)
                            .font(.footnote)
                    }
                }
                basicsSection
                ruleSection
                backendsSection
                if model.form.protocol == .http && !model.form.usesSharedService && model.form.isManagedService {
                    loadBalancingSection
                }
                entryPointsSection
                if model.form.protocol != .udp { middlewaresSection }
                if model.form.protocol != .udp { tlsSection }
                if model.form.protocol == .http { presetSection }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(routeId == nil ? String(localized: "New route") : String(localized: "Edit route"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                if model.saving {
                    ProgressView()
                } else {
                    Button(routeId == nil ? "Create" : "Save") { save() }
                        .disabled(model.loading)
                }
            }
        }
        .interactiveDismissDisabled(model.saving)
        .sheet(isPresented: $showConfigPicker) {
            ConfigFilePicker(
                files: model.configFiles,
                selected: model.form.configFile,
                canCreate: model.canCreateConfigFile
            ) { model.form.configFile = $0 }
        }
        .task {
            guard let service = app.service else { return }
            await model.load(service: service, editing: routeId)
        }
    }

    // MARK: - Sections

    private var basicsSection: some View {
        @Bindable var model = model
        return Section {
            if !model.configFiles.isEmpty || model.canCreateConfigFile {
                Button { showConfigPicker = true } label: {
                    LabeledContent("Config file") {
                        Text(model.form.configFile.ifEmpty(String(localized: "Select a file")))
                            .foregroundStyle(model.form.configFile.isEmpty ? Palette.muted : Palette.text)
                    }
                }
                .tint(Palette.text)
            }
            Picker("Protocol", selection: Binding(get: { model.form.protocol }, set: { model.setProtocol($0) })) {
                ForEach(RouteProtocol.allCases) { Text($0.rawValue.uppercased()).tag($0) }
            }
            .pickerStyle(.segmented)
            .disabled(model.form.isEdit)
            TextField("Route / service name", text: $model.form.name)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } footer: {
            if model.form.isEdit { Text("Renaming moves the router, its service and any owned middleware") }
        }
    }

    @ViewBuilder
    private var ruleSection: some View {
        @Bindable var model = model
        switch model.form.protocol {
        case .http:
            Section {
                Picker("Rule", selection: Binding(get: { model.form.advancedRule }, set: { model.setAdvancedRule($0) })) {
                    Text("Simple").tag(false)
                    Text("Advanced rule").tag(true)
                }
                .pickerStyle(.segmented)
                if model.form.advancedRule {
                    TextField("Rule", text: $model.form.httpRule, axis: .vertical)
                        .font(.body.monospaced())
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } else {
                    TextField("Subdomain", text: $model.form.subdomain)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    ForEach(model.form.domains, id: \.self) { domain in
                        Text(domain).font(.body.monospaced())
                    }
                    .onDelete { model.form.domains.remove(atOffsets: $0) }
                    let available = model.domainOptions.filter { !model.form.domains.contains($0) }
                    if !available.isEmpty {
                        Menu {
                            ForEach(available, id: \.self) { domain in
                                Button(domain) { model.form.domains.append(domain) }
                            }
                        } label: {
                            Label("Add a domain", systemImage: "plus.circle")
                        }
                    }
                    HStack {
                        TextField("Add a domain", text: $newDomain, prompt: Text(verbatim: "other.example.com"))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .onSubmit(addDomain)
                        Button("Add", action: addDomain).disabled(newDomain.isBlank)
                    }
                }
            } header: {
                Text("Domains")
            } footer: {
                if !model.form.advancedRule {
                    Text("Leave empty to use the domain itself")
                }
            }
        case .tcp:
            Section {
                TextField("HostSNI(`*`)", text: $model.form.tcpRule, axis: .vertical)
                    .font(.body.monospaced())
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } header: {
                Text("SNI rule")
            } footer: {
                Text("Defaults to HostSNI(`*`) - matches any SNI")
            }
        case .udp:
            Section {
                Text("UDP routers have no rule - traffic is matched by entry point only.")
                    .font(.footnote)
                    .foregroundStyle(Palette.muted)
            }
        }
    }

    @ViewBuilder
    private var backendsSection: some View {
        @Bindable var model = model
        Section {
            Picker("Backends", selection: $model.form.backendMode) {
                Text("Build backends").tag(BackendMode.manual)
                Text("Use a service").tag(BackendMode.existingService)
            }
            .pickerStyle(.segmented)

            if model.form.usesSharedService {
                let current = model.form.serviceRef
                let options = model.serviceOptions + (current.isEmpty || model.serviceOptions.contains(current) ? [] : [current])
                Picker("Service", selection: $model.form.serviceRef) {
                    Text("Select a service").tag("")
                    ForEach(options, id: \.self) { Text($0).tag($0) }
                }
            } else if !model.form.isManagedService {
                Text(model.form.serviceOwned
                    ? String(localized: "This route uses a \(model.form.serviceType) service that Traefik Manager manages. Edit its backends from the Services tab.")
                    : String(localized: "This route uses a \(model.form.serviceType) service. Backends are managed in the config file."))
                    .font(.footnote)
                    .foregroundStyle(Palette.yellow)
            } else {
                if model.form.protocol == .http {
                    Picker("Combine backends as", selection: $model.form.compositeType) {
                        ForEach(RouteForm.compositeTypes, id: \.self) { Text(RouteForm.combineLabel($0)).tag($0) }
                    }
                }
                ForEach($model.form.backends) { $backend in
                    BackendRow(
                        backend: $backend,
                        composite: model.form.protocol == .http,
                        compositeType: model.form.compositeType,
                        serviceOptions: model.serviceOptions,
                        canRemove: model.form.backends.count > 1
                    ) { model.removeBackend(backend.id) }
                }
                if !model.form.backendsAtCap {
                    Button { model.addBackend() } label: { Label("Add backend", systemImage: "plus.circle") }
                }
            }
        } header: {
            Text("Backends")
        } footer: {
            if model.form.protocol == .http && model.form.isManagedService && !model.form.usesSharedService {
                VStack(alignment: .leading, spacing: 4) {
                    Text(RouteForm.combineHint(model.form.compositeType))
                    if model.form.compositeType == "failover" {
                        Text("Failover takes two backends: the one that serves and the one that takes over.")
                    }
                    if model.form.wasComposite && model.form.compositeType == RouteForm.loadBalancer {
                        Text("Saving replaces the composite service with a plain load balancer.").foregroundStyle(Palette.yellow)
                    }
                }
            }
        }
    }

    private var loadBalancingSection: some View {
        @Bindable var model = model
        return Section {
            DisclosureGroup("Load balancing", isExpanded: $showLoadBalancing) {
                Toggle("Sticky sessions", isOn: $model.form.sticky.enabled)
                if model.form.sticky.enabled {
                    TextField("Cookie name", text: $model.form.sticky.cookieName)
                        .textInputAutocapitalization(.never)
                    Toggle("Secure cookie", isOn: $model.form.sticky.secure)
                    Toggle("HttpOnly cookie", isOn: $model.form.sticky.httpOnly)
                }
                Toggle("Health check", isOn: $model.form.healthCheck.enabled)
                if model.form.healthCheck.enabled {
                    TextField("Path", text: $model.form.healthCheck.path, prompt: Text(verbatim: "/health"))
                        .textInputAutocapitalization(.never)
                    TextField("Interval", text: $model.form.healthCheck.interval, prompt: Text(verbatim: "10s"))
                    TextField("Timeout", text: $model.form.healthCheck.timeout, prompt: Text(verbatim: "3s"))
                }
                TextField("Router priority", value: $model.form.priority, format: .number, prompt: Text("Leave empty for Traefik's default"))
                    .keyboardType(.numberPad)
            }
        }
    }

    @ViewBuilder
    private var entryPointsSection: some View {
        @Bindable var model = model
        Section {
            if model.entryPointsUnavailable {
                TextField(
                    "Entry points",
                    text: Binding(
                        get: { model.form.entryPoints.joined(separator: ", ") },
                        set: { model.form.entryPoints = $0.split(separator: ",").map { String($0).trimmed }.filter { !$0.isEmpty } }
                    )
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            } else if model.form.protocol == .udp {
                Picker("Entry point", selection: Binding(
                    get: { model.form.entryPoints.first ?? "" },
                    set: { model.form.entryPoints = $0.isEmpty ? [] : [$0] }
                )) {
                    Text("none").tag("")
                    ForEach(model.entryPointOptions, id: \.self) { Text($0).tag($0) }
                }
            } else {
                ForEach(model.entryPointOptions, id: \.self) { name in
                    Toggle(name, isOn: membership(name, in: $model.form.entryPoints))
                        .font(.body.monospaced())
                }
            }
        } header: {
            Text("Entry points")
        } footer: {
            if model.entryPointsUnavailable { Text("Traefik API unavailable - enter names separated by commas") }
        }
    }

    @ViewBuilder
    private var middlewaresSection: some View {
        @Bindable var model = model
        let known = model.protocolMiddlewares.map(\.name)
        let extra = model.form.middlewares.filter { !known.contains($0) }
        if !(known + extra).isEmpty {
            Section("Middlewares") {
                ForEach(known + extra, id: \.self) { name in
                    Toggle(name, isOn: membership(name, in: $model.form.middlewares))
                        .font(.body.monospaced())
                }
            }
        }
    }

    @ViewBuilder
    private var tlsSection: some View {
        @Bindable var model = model
        let tlsActive = model.form.protocol == .http ? model.form.tlsEnabled : model.form.tcpTLSMode == .terminate
        Section("TLS and options") {
            if model.form.protocol == .tcp {
                Picker("TLS", selection: $model.form.tcpTLSMode) {
                    Text("No TLS").tag(TCPTLSMode.none)
                    Text(verbatim: "TLS").tag(TCPTLSMode.terminate)
                    Text("Passthrough").tag(TCPTLSMode.passthrough)
                }
                .pickerStyle(.segmented)
            } else {
                Toggle("Enable TLS", isOn: $model.form.tlsEnabled)
            }
            if tlsActive {
                Picker("Certificate resolver", selection: $model.form.certResolver) {
                    Text("None (external certificate)").tag("")
                    ForEach(resolverChoices, id: \.self) { Text($0).tag($0) }
                }
            }
            if model.form.protocol == .http {
                Picker("TLS options profile", selection: $model.form.tlsOptionsProfile) {
                    Text("None (default)").tag("")
                    ForEach(profileChoices, id: \.self) { Text($0).tag($0) }
                }
                Toggle("Pass host header", isOn: Binding(
                    get: { model.form.effectivePassHostHeader },
                    set: { model.form.passHostHeader = $0 }
                ))
                .disabled(model.form.streamingEnabled)
                Toggle("Skip TLS verification", isOn: $model.form.insecureSkipVerify)
                Toggle("Optimize for streaming (Jellyfin / Emby / Plex)", isOn: Binding(
                    get: { model.form.streamingEnabled },
                    set: {
                        model.form.streamingPresent = true
                        model.form.streamingEnabled = $0
                    }
                ))
                Toggle("Request wildcard certificate", isOn: Binding(
                    get: { model.form.wildcardEnabled },
                    set: { model.setWildcard($0) }
                ))
                if model.form.wildcardEnabled {
                    VStack(alignment: .leading, spacing: 4) {
                        TextField("Certificate domain", text: $model.form.tlsMainDomain)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Text("Traefik asks your resolver for a certificate covering this name")
                            .font(.caption)
                            .foregroundStyle(Palette.muted)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        TextField("Extra domains", text: Binding(
                            get: { model.form.tlsSans.joined(separator: "\n") },
                            set: { model.form.tlsSans = $0.split(separator: "\n").map { String($0).trimmed }.filter { !$0.isEmpty } }
                        ), axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        Text("One per line. A wildcard needs a DNS challenge resolver")
                            .font(.caption)
                            .foregroundStyle(Palette.muted)
                    }
                }
            }
        }
    }

    private var presetSection: some View {
        @Bindable var model = model
        return Section {
            Toggle("Security headers preset", isOn: Binding(
                get: { model.form.headersPreset.enabled },
                set: { model.setPresetEnabled($0) }
            ))
            if model.form.headersPreset.custom {
                Text("This middleware was hand-edited. It is kept as-is unless you change a toggle below.")
                    .font(.caption)
                    .foregroundStyle(Palette.yellow)
            }
            if model.form.headersPreset.enabled {
                DisclosureGroup("Options", isExpanded: $showPreset) {
                    Toggle(isOn: presetBinding(\.hsts)) { Text(verbatim: "HSTS") }
                    Toggle("Content-Type nosniff", isOn: presetBinding(\.nosniff))
                    Toggle("Frame deny", isOn: presetBinding(\.frameDeny))
                    Picker("Referrer policy", selection: presetBinding(\.referrer)) {
                        ForEach(HeadersPresetDefaults.referrerValues, id: \.self) { Text($0).tag($0) }
                    }
                    ForEach(HeadersPresetDefaults.features, id: \.self) { feature in
                        Picker(feature, selection: Binding(
                            get: { model.form.headersPreset.perms[feature] ?? "block" },
                            set: { value in
                                model.form.headersPreset.perms[feature] = value
                                model.form.headersPreset.custom = false
                            }
                        )) {
                            ForEach(HeadersPresetDefaults.permValues, id: \.self) { Text($0).tag($0) }
                        }
                        .font(.body.monospaced())
                    }
                }
            }
        }
    }

    // MARK: - Helpers

    private var resolverChoices: [String] {
        let current = model.form.certResolver
        return model.certResolverOptions + (current.isEmpty || model.certResolverOptions.contains(current) ? [] : [current])
    }

    private var profileChoices: [String] {
        let names = model.tlsProfiles.map(\.name)
        let current = model.form.tlsOptionsProfile
        return names + (current.isEmpty || names.contains(current) ? [] : [current])
    }

    private func presetBinding<T>(_ keyPath: WritableKeyPath<HeadersPresetForm, T>) -> Binding<T> {
        Binding(
            get: { model.form.headersPreset[keyPath: keyPath] },
            set: { value in
                model.form.headersPreset[keyPath: keyPath] = value
                model.form.headersPreset.custom = false
            }
        )
    }

    private func membership(_ name: String, in list: Binding<[String]>) -> Binding<Bool> {
        Binding(
            get: { list.wrappedValue.contains(name) },
            set: { isOn in
                if isOn {
                    if !list.wrappedValue.contains(name) { list.wrappedValue.append(name) }
                } else {
                    list.wrappedValue.removeAll { $0 == name }
                }
            }
        )
    }

    private func addDomain() {
        let domain = newDomain.trimmed
        guard !domain.isEmpty, !model.form.domains.contains(domain) else { return }
        model.form.domains.append(domain)
        newDomain = ""
    }

    private func save() {
        guard let service = app.service else { return }
        Task {
            if await model.save(service: service) {
                app.routes.noteExternalChange()
                await app.refreshRoutes()
                onSaved(model.form.name)
                dismiss()
            }
        }
    }
}

/// One backend: address or service, with a weight/percent for composite services.
private struct BackendRow: View {
    @Binding var backend: BackendServer
    let composite: Bool
    let compositeType: String
    let serviceOptions: [String]
    let canRemove: Bool
    let onRemove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if composite {
                HStack {
                    Picker("Kind", selection: $backend.kind) {
                        Text(verbatim: "Address").tag(BackendKind.address)
                        Text("Service").tag(BackendKind.service)
                    }
                    .pickerStyle(.segmented)
                    if compositeType != RouteForm.loadBalancer {
                        TextField(compositeType == "mirroring" ? "Percent" : "Weight", text: $backend.share)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 64)
                    }
                }
            }
            if backend.isService {
                Picker("Service", selection: $backend.serviceName) {
                    Text("Select a service").tag("")
                    ForEach(serviceOptions, id: \.self) { Text($0).tag(RouteDetailView.shortName($0)) }
                }
            } else {
                HStack(spacing: Spacing.sm) {
                    if composite || backend.scheme != "http" {
                        Picker("Scheme", selection: $backend.scheme) {
                            Text(verbatim: "http").tag("http")
                            Text(verbatim: "https").tag("https")
                            Text(verbatim: "h2c").tag("h2c")
                        }
                        .labelsHidden()
                        .fixedSize()
                    }
                    TextField("Host", text: $backend.host, prompt: Text(verbatim: "10.0.0.5"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    TextField("Port", text: $backend.port, prompt: Text(verbatim: "8080"))
                        .keyboardType(.numberPad)
                        .frame(width: 70)
                }
                .font(.body.monospaced())
            }
        }
        .swipeActions {
            if canRemove {
                Button("Remove backend", systemImage: "trash", role: .destructive, action: onRemove)
            }
        }
    }
}

/// Pick an existing dynamic config file or name a new one. Port of `ConfigFileSheet`.
struct ConfigFilePicker: View {
    @Environment(\.dismiss) private var dismiss
    let files: [ConfigFile]
    let selected: String
    let canCreate: Bool
    let onSelect: (String) -> Void

    @State private var query = ""
    @State private var newName = ""

    private var matches: [ConfigFile] {
        let needle = query.trimmed.lowercased()
        return needle.isEmpty ? files : files.filter { $0.label.lowercased().contains(needle) }
    }

    var body: some View {
        NavigationStack {
            List {
                if canCreate {
                    Section {
                        HStack {
                            TextField("New file name", text: $newName, prompt: Text(verbatim: "media.yml"))
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                            Button("Create") {
                                let name = newName.trimmed
                                choose(name.hasSuffix(".yml") || name.hasSuffix(".yaml") ? name : "\(name).yml")
                            }
                            .disabled(newName.isBlank)
                        }
                    } header: {
                        Text("Create a new file")
                    } footer: {
                        Text(".yml is added if you leave it off")
                    }
                }
                Section {
                    if matches.isEmpty {
                        Text(files.isEmpty ? "No config files reported" : "No files match")
                            .foregroundStyle(Palette.muted)
                    }
                    ForEach(matches) { file in
                        Button { choose(file.label) } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(file.label).foregroundStyle(Palette.text)
                                    if file.path != file.label {
                                        Text(file.path).font(.caption.monospaced()).foregroundStyle(Palette.muted)
                                    }
                                }
                                Spacer()
                                if file.label == selected {
                                    Image(systemName: "checkmark").foregroundStyle(Palette.blue)
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $query, prompt: Text("Search files"))
            .navigationTitle("Config file")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func choose(_ name: String) {
        onSelect(name)
        dismiss()
    }
}

import SwiftUI

/// Add or edit a middleware with a template wizard or raw YAML. Port of `MiddlewareFormScreen`.
struct MiddlewareFormView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    /// nil creates a new middleware.
    let name: String?
    var onSaved: (String) -> Void = { _ in }

    @State private var model = MiddlewareFormModel()
    @State private var showConfigPicker = false
    @State private var showTemplatePicker = false
    @State private var genUser = ""
    @State private var genPassword = ""
    @State private var genRealm = "traefik"

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
                basics
                if !model.isTCP { templateSection }
                if model.mode == .wizard, let wizard = model.wizard {
                    wizardSection(wizard)
                    if wizard.id == "basicAuth" || wizard.id == "digestAuth" {
                        generatorSection(digest: wizard.id == "digestAuth")
                    }
                }
                yamlSection
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle(name == nil ? String(localized: "Add middleware") : String(localized: "Edit middleware"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            ToolbarItem(placement: .confirmationAction) {
                if model.saving {
                    ProgressView()
                } else {
                    Button(name == nil ? "Create" : "Save", action: save).disabled(model.loading)
                }
            }
        }
        .interactiveDismissDisabled(model.saving)
        .sheet(isPresented: $showConfigPicker) {
            ConfigFilePicker(files: model.configFiles, selected: model.form.configFile, canCreate: model.canCreateConfigFile) {
                model.form.configFile = $0
            }
        }
        .sheet(isPresented: $showTemplatePicker) {
            TemplatePicker(customTemplates: model.customTemplates, selected: model.templateId) { model.selectTemplate($0) }
        }
        .task {
            guard let service = app.service else { return }
            await model.load(service: service, editing: name, middlewares: app.routes.snapshot?.middlewares ?? [])
            if name == nil, let template = DebugLaunch.template { model.selectTemplate(template) }
        }
    }

    // MARK: - Sections

    private var basics: some View {
        @Bindable var model = model
        return Section {
            if model.showConfigFile {
                Button { showConfigPicker = true } label: {
                    LabeledContent("Config file") {
                        Text(model.form.configFile.ifEmpty(String(localized: "Select a file")))
                            .foregroundStyle(model.form.configFile.isEmpty ? Palette.muted : Palette.text)
                    }
                }
                .tint(Palette.text)
            }
            Picker("Protocol", selection: Binding(get: { model.form.protocol }, set: { model.setProtocol($0) })) {
                ForEach(MiddlewareProtocol.allCases) { Text($0.rawValue.uppercased()).tag($0) }
            }
            .pickerStyle(.segmented)
            TextField("Name", text: $model.form.name)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } footer: {
            VStack(alignment: .leading, spacing: 4) {
                if model.form.isEdit { Text("Renaming updates every route that references it") }
                if model.isTCP { Text("TCP middlewares support only ipAllowList and inFlightConn, written as YAML.") }
            }
        }
    }

    private var templateSection: some View {
        Section {
            Button { showTemplatePicker = true } label: {
                LabeledContent("Template") {
                    Text(model.templateLabel ?? String(localized: "Custom"))
                        .foregroundStyle(Palette.text)
                }
            }
            .tint(Palette.text)
            if model.wizard != nil {
                Picker("Mode", selection: Binding(get: { model.mode }, set: { model.setMode($0) })) {
                    Text("Wizard").tag(MiddlewareMode.wizard)
                    Text(verbatim: "YAML").tag(MiddlewareMode.yaml)
                }
                .pickerStyle(.segmented)
            }
        }
    }

    private func wizardSection(_ wizard: MiddlewareWizard) -> some View {
        Section(wizard.label) {
            ForEach(visibleFields(wizard)) { field in
                fieldView(field)
            }
        }
    }

    /// The IP allow list wizard only shows the input for the chosen client IP strategy.
    private func visibleFields(_ wizard: MiddlewareWizard) -> [WizardField] {
        guard wizard.id.hasPrefix("ipAllowList") else { return wizard.fields }
        let strategy = model.wizardTextValue("strategy").ifEmpty("direct")
        return wizard.fields.filter { field in
            switch field.key {
            case "depth": strategy == "depth"
            case "excluded": strategy == "excluded"
            default: true
            }
        }
    }

    @ViewBuilder
    private func fieldView(_ field: WizardField) -> some View {
        switch field {
        case let .text(key, label, placeholder, _, help, numeric, secret):
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.caption).foregroundStyle(Palette.muted)
                Group {
                    if secret {
                        SecureField(label, text: text(key), prompt: Text(verbatim: placeholder))
                    } else {
                        TextField(label, text: text(key), prompt: Text(verbatim: placeholder))
                    }
                }
                .keyboardType(numeric ? .numberPad : .default)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.body.monospaced())
                if let help { Text(help).font(.caption2).foregroundStyle(Palette.muted) }
            }
        case let .lines(key, label, placeholder, help, _):
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.caption).foregroundStyle(Palette.muted)
                TextField(label, text: text(key), prompt: Text(verbatim: placeholder), axis: .vertical)
                    .lineLimit(2...8)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.body.monospaced())
                if let help { Text(help).font(.caption2).foregroundStyle(Palette.muted) }
            }
        case let .toggle(key, label, _):
            Toggle(label, isOn: Binding(get: { model.wizardFlag(key) }, set: { model.setWizardToggle(key, $0) }))
        case let .choice(key, label, options, defaultValue):
            Picker(label, selection: Binding(
                get: { model.wizardTextValue(key).ifEmpty(defaultValue) },
                set: { model.setWizardText(key, $0) }
            )) {
                ForEach(options, id: \.0) { Text($0.1).tag($0.0) }
            }
        }
    }

    private func generatorSection(digest: Bool) -> some View {
        Section {
            TextField("Username", text: $genUser)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            SecureField("Password", text: $genPassword)
            if digest {
                TextField("Realm", text: $genRealm)
                    .textInputAutocapitalization(.never)
            }
            Button {
                generate(digest: digest)
            } label: {
                HStack {
                    Text(model.generating ? String(localized: "Generating") : String(localized: "Generate entry"))
                    if model.generating { Spacer(); ProgressView() }
                }
            }
            .disabled(genUser.isBlank || genPassword.isEmpty || model.generating)
            if let error = model.generatorError {
                Text(error).font(.footnote).foregroundStyle(Palette.red)
            }
        } footer: {
            Text(digest
                ? "The server hashes the password and appends the user:realm:md5 line above."
                : "The server hashes the password and appends the user:hash line above.")
        }
    }

    private var yamlSection: some View {
        @Bindable var model = model
        return Section {
            YamlEditor(text: $model.yaml, editable: model.mode == .yaml)
                .frame(minHeight: 220)
                .listRowInsets(EdgeInsets())
                .opacity(model.mode == .wizard ? 0.75 : 1)
        } header: {
            Text("Configuration (YAML)")
        } footer: {
            Text("The middleware body only, without the name above it.")
        }
    }

    // MARK: - Helpers

    private func text(_ key: String) -> Binding<String> {
        Binding(get: { model.wizardTextValue(key) }, set: { model.setWizardText(key, $0) })
    }

    private func generate(digest: Bool) {
        guard let service = app.service else { return }
        Task {
            await model.generateEntry(service: service, username: genUser.trimmed, password: genPassword, realm: digest ? genRealm.trimmed : nil)
            if model.generatorError == nil {
                genUser = ""
                genPassword = ""
            }
        }
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

/// Built-in wizards by category plus the user's saved templates.
private struct TemplatePicker: View {
    @Environment(\.dismiss) private var dismiss
    let customTemplates: [MiddlewareTemplate]
    let selected: String
    let onSelect: (String) -> Void

    @State private var query = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    row(id: "", title: String(localized: "Custom"), subtitle: String(localized: "Write the YAML yourself"))
                }
                if !customTemplates.isEmpty {
                    Section("My templates") {
                        ForEach(customTemplates.filter(matches)) { template in
                            row(id: "custom:\(template.id)", title: template.name, subtitle: MiddlewareTemplates.kind(of: template.yaml))
                        }
                    }
                }
                ForEach(MiddlewareTemplates.categories, id: \.self) { category in
                    let wizards = MiddlewareTemplates.all.filter { $0.category == category && matches($0.label) }
                    if !wizards.isEmpty {
                        Section(MiddlewareTemplates.categoryLabel(category)) {
                            ForEach(wizards) { wizard in
                                row(id: wizard.id, title: wizard.label, subtitle: nil)
                            }
                        }
                    }
                }
            }
            .searchable(text: $query)
            .navigationTitle("Template")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }

    private func matches(_ template: MiddlewareTemplate) -> Bool { matches(template.name) }

    private func matches(_ text: String) -> Bool {
        query.isBlank || text.localizedCaseInsensitiveContains(query.trimmed)
    }

    private func row(id: String, title: String, subtitle: String?) -> some View {
        Button {
            onSelect(id)
            dismiss()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(Palette.text)
                    if let subtitle { Text(subtitle).font(.caption).foregroundStyle(Palette.muted) }
                }
                Spacer()
                if id == selected { Image(systemName: "checkmark").foregroundStyle(Palette.blue) }
            }
        }
    }
}

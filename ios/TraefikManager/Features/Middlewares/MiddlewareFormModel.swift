import Foundation
import Observation

enum MiddlewareMode: Hashable { case wizard, yaml }

/// State for the add/edit middleware screen. Port of `MiddlewareFormViewModel`.
@MainActor
@Observable
final class MiddlewareFormModel {
    var form = MiddlewareForm()
    var yaml = ""
    private(set) var loading = false
    private(set) var mode: MiddlewareMode = .yaml
    private(set) var templateId = ""
    private(set) var templateLabel: String?
    private(set) var wizardText: [String: String] = [:]
    private(set) var wizardToggles: [String: Bool] = [:]
    private(set) var customTemplates: [MiddlewareTemplate] = []
    private(set) var configFiles: [ConfigFile] = []
    private(set) var canCreateConfigFile = false
    private(set) var generating = false
    private(set) var generatorError: String?
    private(set) var saving = false
    var error: String?

    var wizard: MiddlewareWizard? { MiddlewareTemplates.byId(templateId) }
    var isTCP: Bool { form.protocol == .tcp }
    var showConfigFile: Bool { !configFiles.isEmpty || canCreateConfigFile }

    func load(service: TMService, editing name: String?, middlewares: [MiddlewareDef]) async {
        loading = name != nil
        async let configs = (try? service.configs()) ?? ConfigsResponse()
        async let templates = (try? service.middlewareTemplates().templates) ?? []
        let normalized = await configs.normalized(onAgent: service.agentId != nil)
        configFiles = normalized.files
        canCreateConfigFile = normalized.configDirSet
        customTemplates = await templates

        if let name {
            if let existing = middlewares.first(where: { $0.name == name }) {
                let proto = existing.proto
                yaml = existing.yaml.trimmingTrailingWhitespace
                form = MiddlewareForm(
                    name: existing.name, protocol: proto, yaml: existing.yaml, configFile: existing.configFile,
                    isEdit: true, originalName: existing.name, originalProtocol: proto
                )
            } else {
                error = String(localized: "Could not load \(name)")
            }
        } else if form.configFile.isEmpty, configFiles.count == 1 {
            form.configFile = configFiles[0].label
        }
        loading = false
    }

    func setProtocol(_ proto: MiddlewareProtocol) {
        form.protocol = proto
        error = nil
        if proto == .tcp {
            mode = .yaml
            templateId = ""
            templateLabel = nil
        }
    }

    func setMode(_ value: MiddlewareMode) {
        if value == .yaml { regenerate() }
        mode = value
    }

    /// `""` clears; `custom:<id>` loads a saved template; anything else is a built-in wizard id.
    func selectTemplate(_ id: String) {
        error = nil
        if id.isEmpty {
            templateId = ""
            templateLabel = nil
            mode = .yaml
            return
        }
        if let custom = customTemplates.first(where: { "custom:\($0.id)" == id }) {
            yaml = custom.yaml
            templateId = id
            templateLabel = custom.name
            mode = .yaml
            return
        }
        guard let wizard = MiddlewareTemplates.byId(id) else { return }
        let defaults = wizard.defaultValues
        wizardText = defaults.text
        wizardToggles = defaults.toggles
        yaml = wizard.build(defaults)
        templateId = id
        templateLabel = wizard.label
        mode = .wizard
    }

    func wizardTextValue(_ key: String) -> String { wizardText[key] ?? "" }

    func wizardFlag(_ key: String) -> Bool {
        WizardValues(text: wizardText, toggles: wizardToggles, fields: wizard?.fields ?? []).flag(key)
    }

    func setWizardText(_ key: String, _ value: String) {
        wizardText[key] = value
        error = nil
        regenerate()
    }

    func setWizardToggle(_ key: String, _ value: Bool) {
        wizardToggles[key] = value
        error = nil
        regenerate()
    }

    func generateEntry(service: TMService, username: String, password: String, realm: String?) async {
        generating = true
        generatorError = nil
        defer { generating = false }
        do {
            let result: HtpasswdResponse
            if let realm {
                result = try await service.digestAuth(username: username, password: password, realm: realm)
            } else {
                result = try await service.htpasswd(username: username, password: password)
            }
            guard result.ok, !result.entry.isEmpty else {
                generatorError = result.error ?? String(localized: "Could not generate the entry")
                return
            }
            let existing = wizardTextValue("users").trimmingTrailingWhitespace
            setWizardText("users", existing.isBlank ? result.entry : existing + "\n" + result.entry)
        } catch {
            generatorError = error.localizedDescription
        }
    }

    private func regenerate() {
        guard !isTCP, let wizard else { return }
        yaml = wizard.build(WizardValues(text: wizardText, toggles: wizardToggles, fields: wizard.fields))
    }

    func save(service: TMService) async -> Bool {
        if mode == .wizard { regenerate() }
        form.yaml = yaml
        if let problem = validate() {
            error = problem
            return false
        }
        saving = true
        error = nil
        defer { saving = false }
        do {
            let result = try await service.saveMiddleware(form)
            guard result.worked else {
                error = result.message ?? result.error ?? String(localized: "Could not save the middleware")
                return false
            }
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    private func validate() -> String? {
        if showConfigFile && form.configFile.isBlank { return String(localized: "Select a config file for this middleware") }
        if mode == .wizard {
            let missing: (String) -> Bool = { (self.wizardText[$0] ?? "").isBlank }
            if ["basicAuth", "digestAuth"].contains(templateId), missing("users") {
                return String(localized: "Add at least one user before saving")
            }
            if ["forwardAuth", "forwardAuthAuthentik", "forwardAuthAuthelia"].contains(templateId), missing("address") {
                return String(localized: "Forward auth address is required")
            }
            if templateId == "forwardAuthGatekeeper", missing("url") {
                return String(localized: "Gatekeeper URL is required")
            }
        }
        return form.validationError
    }
}

extension String {
    var trimmingTrailingWhitespace: String {
        var copy = self
        while copy.last?.isWhitespace == true { copy.removeLast() }
        return copy
    }
}

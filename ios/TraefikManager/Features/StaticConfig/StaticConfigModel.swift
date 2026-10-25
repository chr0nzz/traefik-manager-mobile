import Foundation
import Observation

/// State for the static config editor. Port of `StaticConfigViewModel`.
@Observable @MainActor
final class StaticConfigModel {
    var loading = true
    var saving = false
    var restarting = false
    var busy = false
    var path = ""
    var loadError: String?
    var saveError: String?
    var message: String?
    var restartPending = false
    /// The YAML being edited, raw editor and section sheets share it.
    var content = ""
    var parsed: JSONObject?
    /// A section change was staged through the server and is not saved yet.
    var pending = false

    private var original = ""

    var dirty: Bool { content != original }
    var hasChanges: Bool { pending || dirty }

    var entrypoints: [StaticNamed] { StaticEntries.entrypoints(parsed) }
    var resolvers: [StaticNamed] { StaticEntries.resolvers(parsed) }
    var plugins: [StaticPluginRow] { StaticEntries.plugins(parsed) }
    var providers: ProvidersForm { ProvidersForm.read(parsed?["providers"]?.objectValue) }
    var otherProviders: [String] { ProvidersForm.others(parsed?["providers"]?.objectValue) }
    var api: ApiForm { ApiForm.read(parsed) }
    var log: LogForm { LogForm.read(parsed) }
    var observability: ObservabilityForm { ObservabilityForm.read(parsed) }
    var system: SystemForm { SystemForm.read(parsed) }

    func load(_ service: TMService) async {
        loading = parsed == nil && original.isEmpty
        loadError = nil
        do {
            let doc = try await service.staticConfig()
            original = doc.raw
            content = doc.raw
            path = doc.path
            parsed = doc.parsed
            pending = false
        } catch {
            loadError = error.localizedDescription
        }
        loading = false
    }

    func discard(_ service: TMService) async {
        content = original
        saveError = nil
        await load(service)
    }

    /// Stages a section change; returns an error message or nil on success.
    func apply(_ service: TMService, section: String, action: String, name: String = "", oldName: String = "", data: JSONObject = [:]) async -> String? {
        busy = true
        saveError = nil
        defer { busy = false }
        let request = StaticSectionRequest(section: section, action: action, name: name, oldName: oldName, data: data, currentRaw: content.isBlank ? original : content)
        do {
            let response = try await service.staticSection(request)
            guard response.ok, !response.raw.isBlank else {
                return response.error.flatMap { $0.isBlank ? nil : $0 } ?? String(localized: "Could not apply the change")
            }
            content = response.raw
            parsed = response.parsed
            pending = true
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    func save(_ service: TMService) async {
        let body = content
        saving = true
        saveError = nil
        do {
            try await service.saveStaticConfig(body)
            original = body
            pending = false
            restartPending = true
            message = String(localized: "Static config saved")
            saving = false
            await load(service)
        } catch {
            saving = false
            saveError = error.localizedDescription.ifEmpty(String(localized: "Save failed"))
        }
    }

    func restart(_ service: TMService) async {
        restarting = true
        do {
            try await service.restartTraefik()
            restartPending = false
            message = String(localized: "Traefik restarted")
        } catch {
            message = error.localizedDescription.ifEmpty(String(localized: "Restart failed"))
        }
        restarting = false
    }

    func reset() {
        loading = true
        parsed = nil
        content = ""
        original = ""
        pending = false
        restartPending = false
        loadError = nil
        saveError = nil
    }
}

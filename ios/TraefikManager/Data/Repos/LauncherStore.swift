import Foundation
import Observation

enum LauncherDensity: String { case list, icons }

/// Dashboard app launcher for the active server. Port of `LauncherRepository` + `LauncherViewModel`.
@MainActor
@Observable
final class LauncherStore {
    private(set) var groups: [LauncherGroup] = []
    private(set) var hidden: [LauncherApp] = []
    private(set) var config = DashboardConfig()
    private(set) var density: LauncherDensity = .list
    private(set) var saving = false

    private var generation = 0

    func reset() {
        generation += 1
        groups = []
        hidden = []
        config = DashboardConfig()
    }

    /// Builds groups from the shared route list plus the server's dashboard config.
    func load(service: TMService, routes: [Route]) async {
        let started = generation
        async let fetchedConfig = (try? service.dashboardConfig()) ?? DashboardConfig()
        async let prefs = try? service.uiPrefs()
        let loaded = await fetchedConfig
        let loadedPrefs = await prefs
        guard generation == started else { return }
        apply(config: loaded, routes: routes)
        if let value = loadedPrefs?.uiPrefs.dashPodDensity, let parsed = LauncherDensity(rawValue: value) {
            density = parsed
        }
    }

    private func apply(config: DashboardConfig, routes: [Route]) {
        self.config = config
        groups = LauncherBuilder.build(routes: routes, config: config, includeHidden: false)
        hidden = routes.filter { config.routeOverrides[$0.id]?.hidden == true }.map { LauncherBuilder.app($0, config: config) }
    }

    // MARK: - Writes

    func saveOverride(_ override: RouteOverride, for routeId: String, service: TMService, routes: [Route]) async throws {
        var next = config
        if override == RouteOverride() {
            next.routeOverrides.removeValue(forKey: routeId)
        } else {
            next.routeOverrides[routeId] = override
        }
        try await save(next, service: service, routes: routes)
    }

    func setHidden(_ isHidden: Bool, routeId: String, service: TMService, routes: [Route]) async throws {
        var override = config.routeOverrides[routeId] ?? RouteOverride()
        override.hidden = isHidden
        try await saveOverride(override, for: routeId, service: service, routes: routes)
    }

    func addGroup(_ name: String, service: TMService, routes: [Route]) async throws {
        let trimmed = name.trimmed
        guard !trimmed.isEmpty,
              !config.customGroups.contains(where: { $0.name.caseInsensitiveCompare(trimmed) == .orderedSame })
        else { return }
        var next = config
        next.customGroups.append(CustomGroup(name: trimmed))
        try await save(next, service: service, routes: routes)
    }

    func removeGroup(_ name: String, service: TMService, routes: [Route]) async throws {
        var next = config
        next.customGroups.removeAll { $0.name == name }
        for (id, override) in next.routeOverrides where override.group == name {
            next.routeOverrides[id]?.group = ""
        }
        try await save(next, service: service, routes: routes)
    }

    func setDensity(_ value: LauncherDensity, service: TMService) async {
        density = value
        var prefs = (try? await service.uiPrefs().uiPrefs) ?? UIPrefs()
        prefs.dashPodDensity = value.rawValue
        _ = try? await service.saveUIPrefs(prefs)
    }

    private func save(_ next: DashboardConfig, service: TMService, routes: [Route]) async throws {
        saving = true
        defer { saving = false }
        let response = try await service.saveDashboardConfig(next)
        guard response.worked else {
            throw APIError.server(response.error ?? response.message ?? String(localized: "Could not save the dashboard"))
        }
        apply(config: next, routes: routes)
    }
}

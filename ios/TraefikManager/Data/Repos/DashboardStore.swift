import Foundation
import Observation

/// Shared dashboard data for the active server, port of `DashboardRepository`.
/// Restores the last snapshot from disk, then refreshes all six endpoints in parallel.
@MainActor
@Observable
final class DashboardStore {
    private(set) var raw: RawDashboard?
    private(set) var loading = false
    private(set) var error: String?

    private var inFlight: Task<Void, Never>?
    private var generation = 0

    func reset() {
        generation += 1
        inFlight?.cancel()
        inFlight = nil
        raw = nil
        error = nil
        loading = false
    }

    /// Fills `raw` from the on-disk snapshot when nothing is loaded yet. Demo mode never caches.
    func restore(cacheKey: String?) {
        guard raw == nil, let cacheKey else { return }
        raw = SnapshotStore.read(RawDashboard.self, name: "dashboard", key: cacheKey)
    }

    /// Coalesces concurrent refreshes into one fetch, like the Android in-flight `Deferred`.
    func refresh(service: TMService, cacheKey: String?) async {
        if let inFlight {
            await inFlight.value
            return
        }
        let started = generation
        let task = Task { await fetch(service: service, cacheKey: cacheKey, started: started) }
        inFlight = task
        await task.value
        if generation == started { inFlight = nil }
    }

    private func fetch(service: TMService, cacheKey: String?, started: Int) async {
        loading = raw == nil
        error = nil
        async let overview = try? service.overview()
        async let entrypoints = try? service.entrypoints()
        async let routers = try? service.routers()
        async let services = try? service.services()
        async let middlewares = try? service.middlewares()
        async let version = try? service.traefikVersion()
        let fetched = await RawDashboard(
            overview: overview,
            entrypoints: entrypoints,
            routers: routers,
            services: services,
            middlewares: middlewares,
            version: version
        )
        guard generation == started, !Task.isCancelled else { return }
        loading = false
        let nothing = fetched.overview == nil && fetched.routers == nil && fetched.services == nil
            && fetched.middlewares == nil && fetched.entrypoints == nil
        if nothing {
            error = String(localized: "Could not load the overview")
            return
        }
        raw = fetched
        if let cacheKey { SnapshotStore.write(fetched, name: "dashboard", key: cacheKey) }
    }
}

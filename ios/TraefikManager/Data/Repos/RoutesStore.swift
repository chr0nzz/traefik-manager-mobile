import Foundation
import Observation

struct RoutesSnapshot: Codable, Sendable {
    var routes: [Route]
    var middlewares: [MiddlewareDef]
    var configErrors: [ConfigError]
    var services = ServicesByProtocol()
}

/// Shared route list for the active server, port of `RoutesRepository` + the list half of `RoutesViewModel`.
@MainActor
@Observable
final class RoutesStore {
    private(set) var snapshot: RoutesSnapshot?
    private(set) var health: [String: RouteHealth] = [:]
    private(set) var loading = false
    private(set) var error: String?
    /// Bumped after any write so dependent screens (dashboard, launcher) reload.
    private(set) var changes = 0

    private var generation = 0

    var routes: [Route] { snapshot?.routes ?? [] }

    func reset() {
        generation += 1
        snapshot = nil
        health = [:]
        error = nil
        loading = false
    }

    func load(service: TMService, cacheKey: String?) async {
        let started = generation
        if snapshot == nil, let cacheKey {
            snapshot = SnapshotStore.read(RoutesSnapshot.self, name: "routes", key: cacheKey)
        }
        loading = snapshot == nil
        error = nil
        do {
            let fetched = try await Self.fetch(service: service)
            guard generation == started else { return }
            snapshot = fetched
            if let cacheKey { SnapshotStore.write(fetched, name: "routes", key: cacheKey) }
        } catch {
            guard generation == started else { return }
            if case let APIError.decoding(detail) = error {
                self.error = String(localized: "The server sent a route the app could not read: \(detail)")
            } else {
                self.error = error.localizedDescription
            }
        }
        loading = false
        if let fetched = try? await service.routeHealth(), generation == started {
            health = fetched.routes
        }
    }

    /// Agents only expose their file routes through the hub; the host lists everything.
    static func fetch(service: TMService) async throws -> RoutesSnapshot {
        let response: RoutesResponse
        if let agentId = service.agentId {
            response = try await service.scoped(to: nil).agentRoutes(agentId)
        } else {
            response = try await service.routes()
        }
        let apps = service.agentId != nil ? response.apps.filter(\.isFileRoute) : response.apps
        return RoutesSnapshot(
            routes: apps,
            middlewares: response.middlewares,
            configErrors: response.configErrors,
            services: response.services
        )
    }

    // MARK: - Writes

    func toggle(_ route: Route, service: TMService) async throws {
        let result = try await service.toggleRoute(route.id, enable: !route.enabled)
        guard result.worked else {
            throw APIError.server(result.message ?? result.error ?? String(localized: "Could not toggle the route"))
        }
        if var current = snapshot, let index = current.routes.firstIndex(where: { $0.id == route.id }) {
            current.routes[index].enabled.toggle()
            snapshot = current
        }
        changes += 1
    }

    func delete(_ route: Route, service: TMService) async throws {
        let result = try await service.deleteRoute(route)
        guard result.worked else {
            throw APIError.server(result.message ?? result.error ?? String(localized: "Could not delete the route"))
        }
        snapshot?.routes.removeAll { $0.id == route.id }
        changes += 1
    }

    func saveRaw(routeId: String, content: String, service: TMService) async throws {
        let result = try await service.saveRouteRaw(routeId, content: content)
        guard result.worked else {
            throw APIError.server(result.error ?? result.message ?? String(localized: "Could not save the YAML"))
        }
        changes += 1
    }

    func noteExternalChange() { changes += 1 }
}

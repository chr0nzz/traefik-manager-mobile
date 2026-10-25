import Foundation
import Observation

/// Services for the active server, port of `ServicesRepository` + the load half of `ServicesViewModel`.
@MainActor
@Observable
final class ServicesStore {
    private(set) var rows: [ServiceRow] = []
    private(set) var raw: [String: TraefikService] = [:]
    private(set) var reachable = true
    private(set) var loaded = false
    private(set) var loading = false
    private(set) var error: String?

    private var generation = 0

    func reset() {
        generation += 1
        rows = []
        raw = [:]
        reachable = true
        loaded = false
        error = nil
    }

    /// Rows keyed the way route details look them up.
    var byBackendKey: [String: ServiceRow] {
        Dictionary(rows.map { (BackendHealth.key($0.proto.rawValue, $0.shortName), $0) }, uniquingKeysWith: { first, _ in first })
    }

    func load(service: TMService) async {
        let started = generation
        loading = !loaded
        error = nil
        do {
            let envelope = try await service.serviceEnvelope()
            guard generation == started else { return }
            rows = ServiceRows.from(envelope)
            raw = Dictionary((envelope.http + envelope.tcp + envelope.udp).map { ($0.shortName, $0) }, uniquingKeysWith: { first, _ in first })
            reachable = envelope.reachable
            loaded = true
        } catch {
            guard generation == started else { return }
            if case let APIError.decoding(detail) = error {
                self.error = String(localized: "The server sent a service the app could not read: \(detail)")
            } else {
                self.error = error.localizedDescription
            }
        }
        loading = false
    }

    func save(_ draft: ServiceDraft, service: TMService) async throws -> String {
        let response = try await service.saveService(draft)
        guard response.ok else { throw APIError.server(response.error ?? String(localized: "Could not save the service")) }
        return response.name.ifEmpty(draft.name.trimmed)
    }

    func delete(_ name: String, force: Bool, service: TMService) async throws {
        _ = try await service.deleteService(name, force: force)
    }

    func setOwnership(_ name: String, adopt: Bool, service: TMService) async throws -> Bool {
        let response = try await service.setServiceOwnership(name, adopt: adopt)
        guard response.ok else { throw APIError.server(response.error ?? String(localized: "Could not change the ownership")) }
        return response.owned
    }
}

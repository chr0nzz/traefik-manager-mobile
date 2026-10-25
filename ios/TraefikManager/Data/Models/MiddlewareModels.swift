import Foundation

struct MiddlewareTemplate: Codable, Hashable, Identifiable, Sendable {
    var id = ""
    var name = ""
    var yaml = ""

    init(id: String, name: String, yaml: String) {
        self.id = id
        self.name = name
        self.yaml = yaml
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.lenientString(.id)
        name = c.value(.name, default: "")
        yaml = c.value(.yaml, default: "")
    }
}

struct MiddlewareTemplatesResponse: Decodable, Sendable {
    var templates: [MiddlewareTemplate] = []

    private enum CodingKeys: String, CodingKey { case templates }

    init(templates: [MiddlewareTemplate]) { self.templates = templates }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        templates = c.value(.templates, default: [])
    }
}

struct TemplateBody: Encodable {
    var name: String
    var yaml: String
}

struct HtpasswdRequest: Encodable {
    var username: String
    var password: String
}

struct DigestRequest: Encodable {
    var username: String
    var password: String
    var realm: String
}

struct HtpasswdResponse: Decodable, Sendable {
    var ok = false
    var entry = ""
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, entry, error }

    init(ok: Bool, entry: String) {
        self.ok = ok
        self.entry = entry
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        entry = c.value(.entry, default: "")
        error = c.optional(.error)
    }
}

enum MiddlewareProtocol: String, CaseIterable, Identifiable, Sendable {
    case http, tcp

    var id: String { rawValue }

    init(wire: String) { self = MiddlewareProtocol(rawValue: wire.lowercased().ifEmpty("http")) ?? .http }
}

extension MiddlewareDef {
    var proto: MiddlewareProtocol { MiddlewareProtocol(wire: type) }
    var kind: String { MiddlewareTemplates.kind(of: yaml) }
}

/// The add/edit middleware form. Port of `MiddlewareForm`.
struct MiddlewareForm: Hashable, Sendable {
    var name = ""
    var `protocol`: MiddlewareProtocol = .http
    var yaml = ""
    var configFile = ""
    var isEdit = false
    var originalName = ""
    var originalProtocol: MiddlewareProtocol = .http

    var validationError: String? {
        if name.isBlank { return String(localized: "A middleware name is required.") }
        if let problem = EntityNames.problem(name) { return problem }
        if yaml.isBlank { return String(localized: "Middleware content cannot be empty.") }
        return nil
    }

    /// Fields for the legacy `/save-middleware` endpoint.
    func fields(agentId: String?) -> [(String, String)] {
        [
            ("middlewareName", name.trimmed),
            ("middlewareContent", yaml.trimmed),
            ("mwProtocol", self.protocol.rawValue),
            ("isMwEdit", String(isEdit)),
            ("originalMwId", originalName),
            ("originalMwProtocol", originalProtocol.rawValue),
            ("configFile", configFile),
            ("agent_id", agentId ?? ""),
        ]
    }
}

/// How many routes use each middleware, counting chains. Port of `MiddlewareUsage`.
enum MiddlewareUsage {
    static func counts(routes: [Route], middlewares: [MiddlewareDef]) -> [String: Int] {
        var referenced: [String: Int] = [:]
        for route in routes {
            for name in Set((route.middlewareNames + route.entrypointMiddlewares).map(RouteDetailView.shortName)) {
                referenced[name, default: 0] += 1
            }
        }
        let chainMembers = middlewares.flatMap { middleware in
            chainRefs(middleware.yaml).map { (RouteDetailView.shortName($0), middleware.name) }
        }
        for (member, owner) in chainMembers {
            let ownerUses = referenced[owner] ?? 0
            if ownerUses > 0 { referenced[member, default: 0] += ownerUses }
        }
        return Dictionary(uniqueKeysWithValues: middlewares.map { ($0.name, referenced[$0.name] ?? 0) })
    }

    private static func chainRefs(_ yaml: String) -> [String] {
        guard let range = yaml.range(of: "chain:") else { return [] }
        return yaml[range.upperBound...]
            .components(separatedBy: "\n")
            .compactMap { line -> String? in
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                guard trimmed.hasPrefix("-") else { return nil }
                let value = trimmed.dropFirst().trimmingCharacters(in: CharacterSet(charactersIn: "\"' "))
                return value.isEmpty ? nil : value
            }
    }
}

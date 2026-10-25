import Foundation

struct ManagerVersion: Decodable, Sendable {
    var version = ""
    var repo = ""
    var staticConfigConfigured = false

    private enum CodingKeys: String, CodingKey {
        case version, repo
        case staticConfigConfigured = "static_config_configured"
    }

    init(version: String = "", repo: String = "", staticConfigConfigured: Bool = false) {
        self.version = version
        self.repo = repo
        self.staticConfigConfigured = staticConfigConfigured
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = c.lenientString(.version)
        repo = c.value(.repo, default: "")
        staticConfigConfigured = c.value(.staticConfigConfigured, default: false)
    }
}

struct ApiKeyEntry: Decodable, Hashable, Sendable {
    var name = ""
    var preview = ""
    var createdAt = ""

    var revocable: Bool { !preview.isEmpty }

    private enum CodingKeys: String, CodingKey {
        case name, preview
        case createdAt = "created_at"
    }

    init(name: String, preview: String, createdAt: String) {
        self.name = name
        self.preview = preview
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        preview = c.value(.preview, default: "")
        createdAt = c.value(.createdAt, default: "")
    }
}

struct ApiKeyStatus: Decodable, Sendable {
    var enabled = false
    var count = 0
    var keys: [ApiKeyEntry] = []

    private enum CodingKeys: String, CodingKey { case enabled, count, keys }

    init(enabled: Bool, count: Int = 0, keys: [ApiKeyEntry] = []) {
        self.enabled = enabled
        self.count = count
        self.keys = keys
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = c.value(.enabled, default: false)
        count = c.lenientInt(.count)
        keys = c.value(.keys, default: [])
    }
}

struct TraefikVersion: Codable, Hashable, Sendable {
    var version: String?
    var codename: String?

    private enum CodingKeys: String, CodingKey {
        case version = "Version"
        case codename = "Codename"
    }
}

struct OkResponse: Decodable, Sendable {
    var ok = false
    var success = false
    var message: String?
    var error: String?

    var worked: Bool { ok || success }

    private enum CodingKeys: String, CodingKey { case ok, success, message, error }

    init(ok: Bool = false, success: Bool = false, message: String? = nil, error: String? = nil) {
        self.ok = ok
        self.success = success
        self.message = message
        self.error = error
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        success = c.value(.success, default: false)
        message = c.optional(.message)
        error = c.optional(.error)
    }
}

import Foundation

struct PluginEntry: Codable, Hashable, Identifiable, Sendable {
    var name = ""
    var moduleName = ""
    var version = ""
    var settings: JSONValue?

    var id: String { name }

    private enum CodingKeys: String, CodingKey { case name, moduleName, version, settings }

    init(name: String, moduleName: String, version: String = "", settings: JSONValue? = nil) {
        self.name = name
        self.moduleName = moduleName
        self.version = version
        self.settings = settings
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.lenientString(.name)
        moduleName = c.lenientString(.moduleName)
        version = c.lenientString(.version)
        settings = try? c.decodeIfPresent(JSONValue.self, forKey: .settings)
    }

    var repoURL: URL? { moduleName.hasPrefix("github.com/") ? URL(string: "https://\(moduleName)") : nil }
    var displayVersion: String { version.isBlank ? "-" : version }
    var displayModule: String { moduleName.isBlank ? "-" : moduleName }

    /// Pretty JSON of the plugin settings, or nil when there are none.
    var settingsText: String? {
        guard let settings, settings != .null else { return nil }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return (try? encoder.encode(settings)).flatMap { String(data: $0, encoding: .utf8) }
    }
}

struct PluginsResponse: Decodable, Sendable {
    var plugins: [PluginEntry] = []
    var error: String?

    private enum CodingKeys: String, CodingKey { case plugins, error }

    init(plugins: [PluginEntry]) { self.plugins = plugins }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        plugins = c.value(.plugins, default: [])
        error = c.optional(.error)
    }
}

/// Which middlewares reference a plugin, port of `PluginUsage`.
enum PluginUsage {
    static func counts(_ plugins: [PluginEntry], _ middlewares: [MiddlewareDef]) -> [String: Int] {
        Dictionary(plugins.map { ($0.name, usersOf($0.name, middlewares).count) }, uniquingKeysWith: { a, _ in a })
    }

    static func usersOf(_ plugin: String, _ middlewares: [MiddlewareDef]) -> [MiddlewareDef] {
        guard !plugin.isEmpty,
              let alias = try? NSRegularExpression(pattern: "^\\s*\(NSRegularExpression.escapedPattern(for: plugin))\\s*:", options: .anchorsMatchLines)
        else { return [] }
        return middlewares.filter { middleware in
            let yaml = middleware.yaml
            let range = NSRange(yaml.startIndex..., in: yaml)
            return pluginKey.firstMatch(in: yaml, range: range) != nil && alias.firstMatch(in: yaml, range: range) != nil
        }
    }

    private static let pluginKey = try! NSRegularExpression(pattern: "(^|\\n)\\s*plugin\\s*:")
}

/// Port of `PluginVersions`.
enum PluginVersions {
    static func updates(_ plugins: [PluginEntry], catalog: [String: String]) -> [String: String] {
        var result: [String: String] = [:]
        for plugin in plugins {
            if let latest = catalog[plugin.moduleName.lowercased()], isNewer(latest, plugin.version) {
                result[plugin.name] = latest
            }
        }
        return result
    }

    static func isNewer(_ latest: String, _ installed: String) -> Bool {
        guard let newer = parts(latest), let current = parts(installed) else { return false }
        for index in 0..<max(newer.count, current.count) {
            let a = index < newer.count ? newer[index] : 0
            let b = index < current.count ? current[index] : 0
            if a != b { return a > b }
        }
        return false
    }

    private static func parts(_ version: String) -> [Int]? {
        var trimmed = version.trimmed
        if trimmed.hasPrefix("v") || trimmed.hasPrefix("V") { trimmed.removeFirst() }
        guard !trimmed.isEmpty else { return nil }
        var result: [Int] = []
        for piece in trimmed.split(separator: ".", omittingEmptySubsequences: false) {
            guard let value = Int(piece) else { return nil }
            result.append(value)
        }
        return result
    }
}

struct PluginCatalog: Decodable, Sendable {
    var plugins: [String: String] = [:]

    private enum CodingKeys: String, CodingKey { case plugins }

    init(plugins: [String: String]) { self.plugins = plugins }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        plugins = c.value(.plugins, default: [:])
    }
}

struct StaticAvailable: Decodable, Sendable {
    var available = false

    private enum CodingKeys: String, CodingKey { case available }

    init(available: Bool) { self.available = available }

    init(from decoder: Decoder) throws {
        available = (try? decoder.container(keyedBy: CodingKeys.self))?.value(.available, default: false) ?? false
    }
}

struct AgentStaticStatus: Decodable, Sendable {
    var configured = false
    var path = ""
    var restartMethod = ""

    private enum CodingKeys: String, CodingKey {
        case configured, path
        case restartMethod = "restart_method"
    }

    init(configured: Bool, path: String, restartMethod: String) {
        self.configured = configured
        self.path = path
        self.restartMethod = restartMethod
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        configured = c.value(.configured, default: false)
        path = c.value(.path, default: "")
        restartMethod = c.value(.restartMethod, default: "")
    }
}

struct PluginInstallRequest: Encodable {
    var staticYaml: String
    var middlewareYaml = ""
    var middlewareFile = ""
    var server = ""

    private enum CodingKeys: String, CodingKey {
        case server
        case staticYaml = "static_yaml"
        case middlewareYaml = "middleware_yaml"
        case middlewareFile = "middleware_file"
    }
}

struct PluginInstallResponse: Decodable, Sendable {
    var ok = false
    var plugins: [String] = []
    var middlewareFile: String?
    var warning: String?
    var error: String?

    private enum CodingKeys: String, CodingKey {
        case ok, plugins, warning, error
        case middlewareFile = "middleware_file"
    }

    init(ok: Bool, plugins: [String] = [], error: String? = nil) {
        self.ok = ok
        self.plugins = plugins
        self.error = error
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        plugins = c.value(.plugins, default: [])
        middlewareFile = c.optional(.middlewareFile)
        warning = c.optional(.warning)
        error = c.optional(.error)
    }
}

typealias JSONObject = [String: JSONValue]

struct StaticSectionRequest: Encodable {
    var section: String
    var action: String
    var name: String
    var oldName = ""
    var data: JSONObject = [:]
    var currentRaw: String?

    private enum CodingKeys: String, CodingKey {
        case section, action, name, data
        case oldName = "old_name"
        case currentRaw = "current_raw"
    }
}

struct StaticSectionResponse: Decodable, Sendable {
    var ok = false
    var raw = ""
    var parsed: JSONObject?
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, raw, parsed, error }

    init(ok: Bool, raw: String, parsed: JSONObject? = nil) {
        self.ok = ok
        self.raw = raw
        self.parsed = parsed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        raw = c.value(.raw, default: "")
        parsed = try? c.decodeIfPresent(JSONObject.self, forKey: .parsed)
        error = c.optional(.error)
    }
}

struct StaticSaveRequest: Encodable { var content: String }

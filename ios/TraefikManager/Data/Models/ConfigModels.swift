import Foundation

struct ConfigFile: Codable, Hashable, Identifiable, Sendable {
    var label = ""
    var path = ""
    var name = ""

    var id: String { path }

    init(label: String, path: String) {
        self.label = label
        self.path = path
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        label = c.value(.label, default: "")
        path = c.value(.path, default: "")
        name = c.value(.name, default: "")
    }
}

struct ConfigsResponse: Codable, Sendable {
    var files: [ConfigFile] = []
    var configDirSet = false

    init(files: [ConfigFile] = [], configDirSet: Bool = false) {
        self.files = files
        self.configDirSet = configDirSet
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        files = c.value(.files, default: [])
        configDirSet = c.value(.configDirSet, default: false)
    }

    /// Labels fall back to names, duplicates drop out; agents always allow creating files.
    func normalized(onAgent: Bool) -> ConfigsResponse {
        var seen = Set<String>()
        let named = files
            .map { file -> ConfigFile in
                let label = file.label.ifEmpty(file.name)
                return ConfigFile(label: label, path: file.path.ifEmpty(label))
            }
            .filter { !$0.label.isEmpty && seen.insert($0.path).inserted }
        return onAgent
            ? ConfigsResponse(files: named.sorted { $0.label < $1.label }, configDirSet: true)
            : ConfigsResponse(files: named, configDirSet: configDirSet)
    }
}

struct TLSOptionProfile: Codable, Hashable, Sendable {
    var name = ""
    var configFile = ""
    var minVersion = ""
    var maxVersion = ""

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.value(.name, default: "")
        configFile = c.value(.configFile, default: "")
        minVersion = c.value(.minVersion, default: "")
        maxVersion = c.value(.maxVersion, default: "")
    }
}

struct CertResolversResponse: Decodable, Sendable {
    var resolvers: [String] = []

    private enum CodingKeys: String, CodingKey { case resolvers }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        resolvers = c.value(.resolvers, default: [])
    }
}

struct StaticConfigResponse: Decodable, Sendable {
    var parsed: [String: JSONValue]?
    var path = ""
    var raw = ""
    var error: String?

    private enum CodingKeys: String, CodingKey { case parsed, path, raw, error }

    init(path: String, raw: String, parsed: [String: JSONValue]? = nil) {
        self.raw = raw
        self.path = path
        self.parsed = parsed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        parsed = c.optional(.parsed)
        path = c.value(.path, default: "")
        raw = c.value(.raw, default: "")
        error = c.optional(.error)
    }
}

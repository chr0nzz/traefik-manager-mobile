import Foundation

extension Dictionary where Key == String, Value == JSONValue {
    func text(_ key: String) -> String {
        if case let .string(value)? = self[key] { return value }
        return ""
    }

    func lenientText(_ key: String) -> String { self[key]?.stringValue ?? "" }

    func flag(_ key: String, _ fallback: Bool = false) -> Bool { self[key]?.boolValue ?? fallback }

    func strings(_ key: String) -> [String] { self[key]?.arrayValue?.compactMap(\.stringValue) ?? [] }
}

struct SaveSettingsResponse: Decodable, Sendable {
    var success = false
    var error: String?

    private enum CodingKeys: String, CodingKey { case success, error }

    init(success: Bool) { self.success = success }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        success = c.value(.success, default: false)
        error = c.optional(.error)
    }
}

struct TestConnectionRequest: Encodable {
    var url: String
    var user = ""
    var password = ""
}

struct TestConnectionResult: Decodable, Sendable {
    var ok = false
    var version: String?
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, version, error }

    init(ok: Bool, version: String? = nil) {
        self.ok = ok
        self.version = version
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        version = c.optional(.version)
        error = c.optional(.error)
    }
}

/// An agent as `GET /api/agents` returns it, kept raw so edits only send what changed. Port of `AgentConfig`.
struct AgentConfig: Decodable, Identifiable, Sendable {
    var raw: JSONObject

    init(raw: JSONObject) { self.raw = raw }

    init(from decoder: Decoder) throws {
        raw = try decoder.singleValueContainer().decode(JSONObject.self)
    }

    var id: String { raw.lenientText("id") }
    var name: String { raw.text("name") }
    var url: String { raw.text("url") }
    var apiKeyRaw: String? { raw.text("api_key_raw").isEmpty ? nil : raw.text("api_key_raw") }
    func text(_ key: String) -> String { raw.lenientText(key) }
    func flag(_ key: String) -> Bool { raw.flag(key) }
    var domains: [String] { raw.strings("domains") }
    var visibleTabs: [String: Bool] { (raw["visible_tabs"]?.objectValue ?? [:]).compactMapValues(\.boolValue) }
}

struct AgentConfigsResponse: Decodable, Sendable {
    var agents: [AgentConfig] = []

    private enum CodingKeys: String, CodingKey { case agents }

    init(agents: [AgentConfig]) { self.agents = agents }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        agents = c.value(.agents, default: [])
    }
}

struct CreateAgentRequest: Encodable {
    var name: String
    var url: String
}

struct AgentMutationResponse: Decodable, Sendable {
    var ok = false
    var agent: AgentConfig?
    var error: String?

    private enum CodingKeys: String, CodingKey { case ok, agent, error }

    init(ok: Bool, agent: AgentConfig?) {
        self.ok = ok
        self.agent = agent
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        agent = try? c.decodeIfPresent(AgentConfig.self, forKey: .agent)
        error = c.optional(.error)
    }

    var rawKey: String? { agent?.apiKeyRaw }
}

/// docker-compose snippet for installing an agent. Port of `AgentCompose`.
enum AgentCompose {
    private static let keyPlaceholder = "<your-api-key>"
    private static let defaultPort = "8090"

    static func envLines(_ agent: AgentConfig, apiKey: String?) -> [String] {
        let t = agent.text
        var lines = [
            "TMA_API_KEY=\(apiKey ?? keyPlaceholder)",
            "TRAEFIK_API_URL=\(t("traefik_api_url").ifEmpty("http://traefik:8080"))",
            "CONFIG_PATH=\(t("config_path").ifEmpty("/app/config"))",
        ]
        func add(_ key: String, _ value: String) { if !value.isEmpty { lines.append("\(key)=\(value)") } }
        if !t("tma_port").isEmpty && t("tma_port") != defaultPort { add("TMA_PORT", t("tma_port")) }
        if !t("tma_rate_limit").isEmpty && t("tma_rate_limit") != "300" { add("TMA_RATE_LIMIT", t("tma_rate_limit")) }
        if agent.flag("traefik_insecure_skip_verify") { add("TRAEFIK_INSECURE_SKIP_VERIFY", "true") }
        add("STATIC_CONFIG_PATH", t("static_config_path"))
        let restart = t("restart_method")
        if !restart.isEmpty {
            add("RESTART_METHOD", restart)
            add("TRAEFIK_CONTAINER", t("traefik_container"))
            if restart == "proxy" { add("DOCKER_HOST", t("docker_host")) }
            if restart == "poison-pill" { add("SIGNAL_FILE_PATH", t("signal_file_path")) }
        }
        add("ACME_JSON_PATH", t("acme_json_path"))
        add("ACCESS_LOG_PATH", t("access_log_path"))
        add("PLUGINS_DIR", t("plugins_dir"))
        add("BACKUP_DIR", t("backup_dir"))
        if t("backup_keep_count") != "0" { add("BACKUP_KEEP_COUNT", t("backup_keep_count")) }
        add("CROWDSEC_LAPI_URL", t("crowdsec_lapi_url"))
        add("CROWDSEC_MACHINE_ID", t("crowdsec_machine_id"))
        add("CROWDSEC_CLIENT_CERT", t("crowdsec_client_cert"))
        add("CROWDSEC_CLIENT_KEY", t("crowdsec_client_key"))
        add("CROWDSEC_CA_CERT", t("crowdsec_ca_cert"))
        if agent.flag("git_backup_enabled") {
            add("GIT_BACKUP_ENABLED", "true")
            add("GIT_BACKUP_REPO", t("git_backup_repo"))
            add("GIT_BACKUP_BRANCH", t("git_backup_branch").ifEmpty("main"))
            add("GIT_BACKUP_USERNAME", t("git_backup_username"))
            add("GIT_BACKUP_AUTO_PUSH", String(agent.raw.flag("git_backup_auto_push", true)))
        }
        return lines
    }

    static func volumeLines(_ agent: AgentConfig) -> [String] {
        let t = agent.text
        let configPath = t("config_path").ifEmpty("/app/config")
        var lines = ["\(configPath):\(configPath)"]
        if !t("static_config_path").isEmpty { lines.append("\(t("static_config_path")):\(t("static_config_path"))") }
        lines.append(t("backup_dir").isEmpty ? "tma_backups:/app/backups" : "\(t("backup_dir")):/app/backups")
        for path in ["acme_json_path", "access_log_path", "plugins_dir", "crowdsec_client_cert", "crowdsec_client_key", "crowdsec_ca_cert"].map(t) where !path.isEmpty {
            lines.append("\(path):\(path):ro")
        }
        if t("restart_method") == "socket" { lines.append("/var/run/docker.sock:/var/run/docker.sock:ro") }
        if t("restart_method") == "poison-pill" { lines.append("traefik-signals:/signals") }
        return lines
    }

    static func compose(_ agent: AgentConfig, apiKey: String?) -> String {
        let port = agent.text("tma_port").ifEmpty(defaultPort)
        var out = [
            "services:",
            "  tma:",
            "    image: ghcr.io/chr0nzz/traefik-manager-agent:latest",
            "    container_name: tma",
            "    restart: unless-stopped",
            "    ports:",
            "      - \"\(port):\(port)\"",
            "    environment:",
        ]
        out += envLines(agent, apiKey: apiKey).map { "      - \($0)" }
        out.append("    volumes:")
        out += volumeLines(agent).map { "      - \($0)" }
        var named: [String] = []
        if agent.text("backup_dir").isEmpty { named.append("tma_backups:") }
        if agent.text("restart_method") == "poison-pill" { named.append("traefik-signals:") }
        if !named.isEmpty {
            out.append("volumes:")
            out += named.map { "  \($0)" }
        }
        return out.joined(separator: "\n")
    }
}

/// Compares two API URLs by scheme, host, port and path. Port of `ApiOrigin`.
enum ApiOrigin {
    static func same(_ a: String, _ b: String) -> Bool {
        guard let left = parts(a), let right = parts(b) else { return false }
        return left == right
    }

    private struct Parts: Equatable {
        var scheme: String
        var host: String
        var port: Int?
        var path: String
    }

    private static func parts(_ raw: String) -> Parts? {
        let text = raw.trimmed
        guard let range = text.range(of: "://") else { return nil }
        let scheme = text[..<range.lowerBound].lowercased()
        guard !scheme.isEmpty else { return nil }
        let rest = String(text[range.upperBound...])
        let end = rest.firstIndex { "/?#".contains($0) } ?? rest.endIndex
        var authority = String(rest[..<end])
        if let at = authority.lastIndex(of: "@") { authority = String(authority[authority.index(after: at)...]) }
        var path = String(rest[end...])
        if let cut = path.firstIndex(where: { "?#".contains($0) }) { path = String(path[..<cut]) }
        while path.hasSuffix("/") { path.removeLast() }
        var host: String
        var portText: String
        if authority.hasPrefix("[") {
            let close = authority.firstIndex(of: "]") ?? authority.endIndex
            host = String(authority[authority.index(after: authority.startIndex)..<close])
            portText = close < authority.endIndex ? String(authority[authority.index(after: close)...]) : ""
            if portText.hasPrefix(":") { portText.removeFirst() }
        } else {
            let pieces = authority.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
            host = String(pieces.first ?? "")
            portText = pieces.count > 1 ? String(pieces[1]) : ""
        }
        guard !host.isEmpty else { return nil }
        let port: Int?
        if portText.isEmpty {
            port = ["http": 80, "https": 443][scheme]
        } else {
            guard let value = Int(portText) else { return nil }
            port = value
        }
        return Parts(scheme: scheme, host: host.lowercased(), port: port, path: path)
    }
}

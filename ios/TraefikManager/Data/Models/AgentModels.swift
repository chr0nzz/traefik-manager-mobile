import Foundation

struct Agent: Decodable, Hashable, Identifiable, Sendable {
    var id = ""
    var name = ""
    var url = ""
    var certResolver = ""
    var domains: [String] = []
    var createdAt = ""
    var visibleTabs: [String: Bool] = [:]
    var crowdsecLapiUrl = ""
    var gitHostBackup = false

    func tabVisible(_ tab: String) -> Bool { visibleTabs[tab] ?? true }

    var crowdsecConfigured: Bool { !crowdsecLapiUrl.isEmpty }

    private enum CodingKeys: String, CodingKey {
        case id, name, url, domains
        case certResolver = "cert_resolver"
        case createdAt = "created_at"
        case visibleTabs = "visible_tabs"
        case crowdsecLapiUrl = "crowdsec_lapi_url"
        case gitHostBackup = "git_host_backup"
    }

    init(id: String, name: String, url: String, visibleTabs: [String: Bool] = [:], crowdsecLapiUrl: String = "") {
        self.id = id
        self.name = name
        self.url = url
        self.visibleTabs = visibleTabs
        self.crowdsecLapiUrl = crowdsecLapiUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.lenientString(.id)
        name = c.value(.name, default: "")
        url = c.value(.url, default: "")
        certResolver = c.value(.certResolver, default: "")
        domains = c.value(.domains, default: [])
        createdAt = c.value(.createdAt, default: "")
        visibleTabs = c.value(.visibleTabs, default: [:])
        crowdsecLapiUrl = c.value(.crowdsecLapiUrl, default: "")
        gitHostBackup = c.value(.gitHostBackup, default: false)
    }
}

struct AgentsResponse: Decodable, Sendable {
    var agents: [Agent] = []

    private enum CodingKeys: String, CodingKey { case agents }

    init(agents: [Agent]) { self.agents = agents }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        agents = c.value(.agents, default: [])
    }
}

struct AgentHealth: Decodable, Hashable, Sendable {
    var ok = false
    var version: String?
    var latencyMs: Int?
    var error: String?

    private enum CodingKeys: String, CodingKey {
        case ok, version, error
        case latencyMs = "latency_ms"
    }

    init(ok: Bool, version: String? = nil, latencyMs: Int? = nil, error: String? = nil) {
        self.ok = ok
        self.version = version
        self.latencyMs = latencyMs
        self.error = error
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ok = c.value(.ok, default: false)
        version = c.optional(.version)
        latencyMs = c.contains(.latencyMs) ? c.lenientInt(.latencyMs) : nil
        error = c.optional(.error)
    }
}

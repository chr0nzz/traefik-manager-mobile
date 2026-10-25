import Foundation

struct ServerSettings: Decodable, Sendable {
    var domains: [String] = []
    var certResolver = ""
    var visibleTabs: [String: Bool] = [:]
    var crowdsecEnabled = false
    var crowdsecLapiUrl = ""

    var certResolvers: [String] {
        certResolver.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    func tabVisible(_ tab: String) -> Bool { visibleTabs[tab] ?? true }

    private enum CodingKeys: String, CodingKey {
        case domains
        case certResolver = "cert_resolver"
        case visibleTabs = "visible_tabs"
        case crowdsecEnabled = "crowdsec_enabled"
        case crowdsecLapiUrl = "crowdsec_lapi_url"
    }

    init(
        domains: [String] = [],
        certResolver: String = "",
        visibleTabs: [String: Bool] = [:],
        crowdsecEnabled: Bool = false
    ) {
        self.domains = domains
        self.certResolver = certResolver
        self.visibleTabs = visibleTabs
        self.crowdsecEnabled = crowdsecEnabled
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        domains = c.value(.domains, default: [])
        certResolver = c.lenientString(.certResolver)
        visibleTabs = c.value(.visibleTabs, default: [:])
        crowdsecEnabled = c.value(.crowdsecEnabled, default: false)
        crowdsecLapiUrl = c.value(.crowdsecLapiUrl, default: "")
    }
}

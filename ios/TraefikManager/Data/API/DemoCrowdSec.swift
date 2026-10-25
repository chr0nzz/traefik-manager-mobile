import Foundation

/// CrowdSec fixtures for demo mode, ported from `DemoApi.kt`.
enum DemoCrowdSec {
    static let decisions: [CsDecision] = [
        CsDecision(id: 1, value: "213.209.159.154", type: "ban", scope: "Ip", origin: "cscli", scenario: "manual ban from Traefik Manager", duration: "590h47m11s"),
        CsDecision(id: 2, value: "45.148.10.238", type: "ban", scope: "Ip", origin: "crowdsec", scenario: "crowdsecurity/http-probing", duration: "3h57m11s"),
        CsDecision(id: 3, value: "62.210.142.174", type: "ban", scope: "Ip", origin: "CAPI", scenario: "community blocklist", duration: "167h2m4s"),
        CsDecision(id: 4, value: "185.220.101.0/24", type: "ban", scope: "Range", origin: "lists", scenario: "firehol_level1", duration: "23h11m9s"),
    ]

    static func alerts() -> [CsAlert] {
        let now = Date()
        return [
            alert(1, "45.148.10.125", "crowdsecurity/http-sensitive-files", "NL", "Techoff Srv Limited", "AS48090", 5, now.addingTimeInterval(-120), ["/.env"], router: "dashboard@file", host: "dash.example.com"),
            alert(2, "62.210.142.174", "crowdsecurity/http-technology-probing", "FR", "Scaleway S.a.s.", "12876", 1, now.addingTimeInterval(-1440), ["/remote/login"]),
            alert(3, "212.87.212.246", "crowdsecurity/http-probing", "DE", "ITP-Solutions GmbH", "41913", 11, now.addingTimeInterval(-3600), ["/"], router: "dashboard@file", host: "dash.example.com"),
            alert(4, "45.142.193.221", "crowdsecurity/http-cve-probing", "RO", "Skynet Network", "212238", 1, now.addingTimeInterval(-25200), ["/global-protect/login.esp"], router: "api@file", host: "api.example.com"),
        ]
    }

    private static func alert(
        _ id: Int64, _ ip: String, _ scenario: String, _ country: String, _ asName: String, _ asNumber: String,
        _ events: Int, _ at: Date, _ uris: [String], router: String? = nil, host: String? = nil
    ) -> CsAlert {
        var alert = CsAlert()
        alert.uuid = "demo-\(id)"
        alert.id = id
        alert.scenario = scenario
        alert.eventsCount = events
        alert.machineId = "traefik-host"
        alert.startAt = ISO8601DateFormatter().string(from: at)
        alert.source.ip = ip
        alert.source.value = ip
        alert.source.cn = country
        alert.source.asName = asName
        alert.source.asNumber = asNumber
        var meta = [CsMetaEntry(key: "target_uri", value: json(uris)), CsMetaEntry(key: "user_agent", value: json(["Mozilla/5.0 zgrab/0.x"]))]
        if let router { meta.append(CsMetaEntry(key: "traefik_router_name", value: json([router]))) }
        if let host { meta.append(CsMetaEntry(key: "target_fqdn", value: json([host]))) }
        alert.meta = meta
        return alert
    }

    private static func json(_ values: [String]) -> String {
        String(decoding: (try? JSONSerialization.data(withJSONObject: values)) ?? Data("[]".utf8), as: UTF8.self)
    }
}

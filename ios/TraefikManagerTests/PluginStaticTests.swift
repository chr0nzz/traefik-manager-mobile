import XCTest
@testable import TraefikManager

final class PluginStaticTests: XCTestCase {
    func testCertRowsSortByExpiryAndClassifyHealth() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let iso = { (days: Double) in ISO8601DateFormatter().string(from: now.addingTimeInterval(days * 86_400)) }
        let rows = CertRows.from([
            CertEntry(resolver: "le", main: "b.example.com", sans: ["b.example.com", "www.b.example.com"], notAfter: iso(40)),
            CertEntry(resolver: "file", main: "chain.pem"),
            CertEntry(resolver: "le", main: "a.example.com", notAfter: iso(2.5)),
            CertEntry(resolver: "le", main: "c.example.com", notAfter: iso(20)),
        ], now: now)
        XCTAssertEqual(rows.map(\.main), ["a.example.com", "c.example.com", "b.example.com", "chain.pem"])
        XCTAssertEqual(rows.map(\.health), [.critical, .expiring, .healthy, .unknown])
        XCTAssertEqual(rows[0].daysLeft, 3)
        XCTAssertEqual(rows[2].extraDomains, ["www.b.example.com"])
        XCTAssertEqual(rows[2].domainCount, 2)
    }

    func testCertDeleteOutcome() throws {
        let partial = try JSONDecoder().decode(CertDeleteResponse.self, from: Data(#"{"ok":false,"partial":true,"error":"stopped"}"#.utf8))
        XCTAssertEqual(partial.outcome(asked: 2), "stopped")
        let done = try JSONDecoder().decode(CertDeleteResponse.self, from: Data(#"{"ok":true,"removed":"2","restarted":true}"#.utf8))
        XCTAssertEqual(done.removed, 2)
        XCTAssertTrue(done.outcome(asked: 2).contains("2"))
    }

    func testPluginVersionComparison() {
        XCTAssertTrue(PluginVersions.isNewer("v1.4.6", "v1.3.5"))
        XCTAssertTrue(PluginVersions.isNewer("1.10", "v1.9.9"))
        XCTAssertFalse(PluginVersions.isNewer("v1.3.5", "v1.3.5"))
        XCTAssertFalse(PluginVersions.isNewer("v1.4.0-rc1", "v1.3.0"))
        XCTAssertFalse(PluginVersions.isNewer("v2.0.0", ""))
        let plugins = [
            PluginEntry(name: "cs", moduleName: "github.com/Max/Bouncer", version: "v1.0.0"),
            PluginEntry(name: "geo", moduleName: "plugins-local/geo"),
        ]
        XCTAssertEqual(PluginVersions.updates(plugins, catalog: ["github.com/max/bouncer": "v1.1.0"]), ["cs": "v1.1.0"])
    }

    func testPluginUsageNeedsPluginKeyAndAlias() throws {
        let decode = { (name: String, yaml: String) in
            try JSONDecoder().decode(MiddlewareDef.self, from: JSONSerialization.data(withJSONObject: ["name": name, "yaml": yaml]))
        }
        let middlewares = try [
            decode("crowdsec", "plugin:\n  crowdsec-bouncer:\n    enabled: true\n"),
            decode("headers", "headers:\n  crowdsec-bouncer: x\n"),
            decode("other", "plugin:\n  geoblock:\n    allow: [CA]\n"),
        ]
        XCTAssertEqual(PluginUsage.usersOf("crowdsec-bouncer", middlewares).map(\.name), ["crowdsec"])
        XCTAssertEqual(PluginUsage.counts([PluginEntry(name: "geoblock", moduleName: "x")], middlewares), ["geoblock": 1])
    }

    func testStaticFormsReadParsedConfig() throws {
        let json = #"""
        {"entryPoints":{"websecure":{"address":":443","http3":{},"forwardedHeaders":{"trustedIPs":["10.0.0.0/8","172.16.0.0/12"]},
          "http":{"middlewares":["a@file","b@file"],"tls":{"certResolver":"le"},"redirections":{"entryPoint":{"to":"x"}}},
          "transport":{"respondingTimeouts":{"readTimeout":"60s"}}}},
         "certificatesResolvers":{"le":{"acme":{"email":"me@x.io","httpChallenge":{"entryPoint":"web"}}}},
         "providers":{"docker":{"exposedByDefault":false},"kubernetesCRD":{},"providersThrottleDuration":"2s"},
         "log":{"level":"debug","format":"JSON","maxSize":10},
         "accessLog":{"filters":{"statusCodes":["400-499","500"]}},
         "metrics":{"prometheus":{"addRoutersLabels":true}},
         "core":{"defaultRuleSyntax":"v2"},
         "experimental":{"plugins":{"b":{"moduleName":"github.com/b"}},"localPlugins":{"a":{"moduleName":"plugins-local/a"}}}}
        """#
        let root = try JSONDecoder().decode(JSONObject.self, from: Data(json.utf8))

        let entry = StaticEntries.entrypoints(root)[0]
        let ep = EntrypointForm.read(entry.name, entry.entry)
        XCTAssertEqual(ep.address, ":443")
        XCTAssertTrue(ep.http3)
        XCTAssertEqual(ep.trustedIps, "10.0.0.0/8\n172.16.0.0/12")
        XCTAssertEqual(ep.middlewares, "a@file, b@file")
        XCTAssertTrue(ep.tlsEnabled)
        XCTAssertEqual(ep.tlsCertResolver, "le")
        XCTAssertEqual(ep.redirectTo, "x")
        XCTAssertEqual(ep.readTimeout, "60s")

        let resolver = StaticEntries.resolvers(root)[0]
        let form = ResolverForm.read(resolver.name, resolver.entry)
        XCTAssertEqual(form.challengeType, "httpChallenge")
        XCTAssertEqual(form.storage, "/acme.json")
        XCTAssertEqual(form.httpEntrypoint, "web")

        let providers = ProvidersForm.read(root["providers"]?.objectValue)
        XCTAssertTrue(providers.docker)
        XCTAssertFalse(providers.dockerExposedByDefault)
        XCTAssertFalse(providers.file)
        XCTAssertEqual(providers.throttle, "2s")
        XCTAssertEqual(ProvidersForm.others(root["providers"]?.objectValue), ["kubernetesCRD"])

        let log = LogForm.read(root)
        XCTAssertEqual(log.level, "DEBUG")
        XCTAssertEqual(log.logFormat, "json")
        XCTAssertEqual(log.maxSize, "10")
        XCTAssertTrue(log.accessLog)
        XCTAssertEqual(log.alStatusCodes, "400-499, 500")

        XCTAssertFalse(ApiForm.read(root).enabled)
        XCTAssertTrue(ApiForm.read(root).dashboard)
        let obs = ObservabilityForm.read(root)
        XCTAssertTrue(obs.prometheus)
        XCTAssertTrue(obs.promRouterLabels)
        XCTAssertTrue(obs.promEpLabels)
        XCTAssertEqual(SystemForm.read(root).ruleSyntax, "v2")
        XCTAssertEqual(StaticEntries.plugins(root).map(\.name), ["b", "a"])
        XCTAssertEqual(StaticEntries.plugins(root).map(\.local), [false, true])
    }

    func testSectionRequestEncodesSnakeCase() throws {
        let request = StaticSectionRequest(section: "plugins", action: "edit", name: "n", oldName: "o",
                                           data: StaticPluginForm(name: "n", moduleName: " m ", version: "v1", local: false).data, currentRaw: nil)
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as? [String: Any]
        XCTAssertEqual(object?["old_name"] as? String, "o")
        XCTAssertEqual((object?["data"] as? [String: Any])?["moduleName"] as? String, "m")
        XCTAssertNil(object?["current_raw"])
    }
}

final class BackupTests: XCTestCase {
    func testHubListDecodesArrayWithKinds() throws {
        let list = try JSONDecoder().decode(BackupsList.self, from: Data(#"""
        [{"name":"a.bak","size":2048,"modified":"2026-08-12 14:30:01","kind":"routes"},
         {"name":"b.bak","size":"10","modified":"x","kind":"STATIC"},{"name":"c.bak","kind":"certs"}]
        """#.utf8))
        XCTAssertEqual(list.entries.map(\.kind), [.routes, .static, .certs])
        XCTAssertEqual(list.entries[1].size, 10)
        XCTAssertNil(list.staticConfigured)
    }

    func testAgentListReadsDateAndOldAgents() throws {
        let list = try JSONDecoder().decode(BackupsList.self, from: Data(#"""
        {"backups":[{"name":"a.bak","size":1,"date":"2026-08-12T14:30:01Z"}],"static_configured":false}
        """#.utf8))
        XCTAssertEqual(list.entries.first?.stamp, "2026-08-12T14:30:01Z")
        XCTAssertEqual(list.staticConfigured, false)
        XCTAssertFalse(list.kindKnown)
    }

    func testLineDiffMarksChangedLines() {
        let lines = LineDiff.lines(old: "a\nb\nc", new: "a\nB\nc\nd")
        XCTAssertEqual(lines.map(\.kind), [.same, .removed, .added, .same, .added])
        XCTAssertEqual(lines.filter { $0.kind == .added }.map(\.text), ["B", "d"])
        XCTAssertEqual(LineDiff.lines(old: "", new: "x").map(\.kind), [.added])
    }

    func testCreateResponseNames() throws {
        let one = try JSONDecoder().decode(CreateBackupResponse.self, from: Data(#"{"success":true,"name":"x.bak"}"#.utf8))
        XCTAssertTrue(one.worked)
        XCTAssertEqual(one.created, ["x.bak"])
        XCTAssertEqual(ByteSize.label(2048), "2 KB")
        XCTAssertEqual(ByteSize.label(3_145_728), "3 MB")
    }
}

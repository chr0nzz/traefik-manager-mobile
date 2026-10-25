import XCTest
@testable import TraefikManager

final class RouteModelTests: XCTestCase {
    private func route(_ json: String) throws -> Route {
        try JSONDecoder().decode(Route.self, from: Data(json.utf8))
    }

    func testHostsIgnoreNegatedMatchers() throws {
        let r = try route(#"{"id":"a","name":"a","rule":"Host(`a.example.com`) || !Host(`b.example.com`)"}"#)
        XCTAssertEqual(r.hosts, ["a.example.com"])
        XCTAssertFalse(r.isPlainHostRule)
    }

    func testMiddlewaresAcceptStringOrArray() throws {
        XCTAssertEqual(try route(#"{"middlewares":"auth"}"#).middlewareNames, ["auth"])
        XCTAssertEqual(try route(#"{"middlewares":["a","b"]}"#).middlewareNames, ["a", "b"])
    }

    func testTLSVariants() throws {
        XCTAssertTrue(try route(#"{"tls":true}"#).tlsEnabled)
        XCTAssertTrue(try route(#"{"tls":{"passthrough":true}}"#).tlsPassthrough)
        XCTAssertFalse(try route(#"{"tls":null}"#).tlsEnabled)
    }

    func testStatusPrefersDisabledThenMissingBackend() throws {
        XCTAssertEqual(try route(#"{"enabled":false,"target":"x"}"#).status(), .disabled)
        XCTAssertEqual(try route(#"{"target":"N/A"}"#).status(), .error)
        XCTAssertEqual(try route(#"{"target":"http://x"}"#).status(health: RouteHealth(state: "degraded")), .warn)
    }
}

final class RouteFormTests: XCTestCase {
    func testSplitHostsFindsLongestDomain() {
        let split = RouteFormParsing.splitHosts(["app.home.example.com"], knownDomains: ["example.com", "home.example.com"])
        XCTAssertEqual(split, .init(subdomain: "app", domains: ["home.example.com"]))
    }

    func testSplitHostsRejectsMixedSubdomains() {
        XCTAssertNil(RouteFormParsing.splitHosts(["a.example.com", "b.example.com"], knownDomains: ["example.com"]))
    }

    func testParseBackend() {
        let backend = RouteFormParsing.parseBackend("https://10.0.0.5:8443")
        XCTAssertEqual(backend?.scheme, "https")
        XCTAssertEqual(backend?.host, "10.0.0.5")
        XCTAssertEqual(backend?.port, "8443")
        XCTAssertNil(RouteFormParsing.parseBackend("N/A"))
        XCTAssertEqual(RouteFormParsing.parseBackend("http://svc/path")?.host, "svc/path")
    }

    func testEncoderHTTPFields() {
        var form = RouteForm()
        form.name = " media "
        form.subdomain = "media"
        form.domains = ["example.com"]
        form.entryPoints = ["websecure"]
        form.certResolver = "le"
        form.backends = [BackendServer(scheme: "http", host: "10.0.0.9", port: "8096")]
        let fields = RouteFormEncoder.fields(form, agentId: nil)
        let lookup = Dictionary(fields.map { ($0.0, $0.1) }, uniquingKeysWith: { first, _ in first })
        XCTAssertEqual(lookup["serviceName"], "media")
        XCTAssertEqual(lookup["certResolver"], "le")
        XCTAssertEqual(lookup["domains"], "example.com")
        XCTAssertEqual(fields.filter { $0.0 == "targetIp" }.map(\.1), ["10.0.0.9", "", ""])
        XCTAssertTrue(lookup["backendsJsonHttp"]?.contains("10.0.0.9") == true)
    }

    func testEncoderDisabledTLSSendsSentinel() {
        var form = RouteForm()
        form.name = "a"
        form.tlsEnabled = false
        let fields = RouteFormEncoder.fields(form, agentId: "edge")
        XCTAssertEqual(fields.first { $0.0 == "certResolver" }?.1, RouteForm.certResolverDisabled)
        XCTAssertEqual(fields.first { $0.0 == "agent_id" }?.1, "edge")
    }

    func testValidation() {
        var form = RouteForm()
        XCTAssertNotNil(form.validationError)
        form.name = "bad/name"
        XCTAssertNotNil(form.validationError)
        form.name = "ok"
        form.backends = [BackendServer(host: "10.0.0.1")]
        XCTAssertNil(form.validationError)
        form.protocol = .tcp
        XCTAssertNotNil(form.validationError, "TCP backends need a port")
    }
}

final class LauncherTests: XCTestCase {
    func testGroupsByKeyword() {
        var route = Route(id: "r", name: "jellyfin-app")
        route.rule = "Host(`tv.example.com`)"
        XCTAssertEqual(LauncherBuilder.groupFor(route, config: DashboardConfig()), "Media")
        let app = LauncherBuilder.app(route, config: DashboardConfig())
        XCTAssertEqual(app.url, "https://tv.example.com")
        XCTAssertEqual(app.host, "tv.example.com")
    }

    func testWildcardHasNoLaunchURL() {
        var route = Route(id: "r", name: "x")
        route.rule = "Host(`*.example.com`)"
        XCTAssertNil(LauncherBuilder.app(route, config: DashboardConfig()).url)
    }

    func testIconSlug() {
        var route = Route(id: "r", name: "x")
        route.serviceName = "Grafana-Service@file"
        XCTAssertEqual(RouteIcons.slug(for: route), "grafana")
    }
}

final class DashboardBuilderTests: XCTestCase {
    func testVerdictCountsDownObjects() {
        let routers = ProtoEnvelope(http: [
            TraefikObject("a@file", "file", "enabled", using: ["web"]),
            TraefikObject("b@file", "file", "disabled", using: ["web"]),
        ])
        let snapshot = DashboardBuilder.build(overview: nil, entrypoints: nil, routers: routers, services: nil, middlewares: nil)
        XCTAssertEqual(snapshot.verdict.status, .error)
        XCTAssertEqual(snapshot.cards.first?.total, 2)
        XCTAssertEqual(snapshot.cards.first?.flags.first?.count, 1)
    }

    func testUnreachableWhenNothingListed() {
        let snapshot = DashboardBuilder.build(overview: nil, entrypoints: nil, routers: nil, services: nil, middlewares: nil)
        XCTAssertFalse(snapshot.traefikReachable)
        XCTAssertEqual(snapshot.verdict.status, .warn)
    }
}

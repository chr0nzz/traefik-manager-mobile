import XCTest
@testable import TraefikManager

final class URLNormalizationTests: XCTestCase {
    func testAddsHttpsToHostnames() {
        XCTAssertEqual(ConnectView.normalize("manager.example.com/")?.absoluteString, "https://manager.example.com")
    }

    func testAddsHttpToIPv4() {
        XCTAssertEqual(ConnectView.normalize(" 192.168.1.10:5000 ")?.absoluteString, "http://192.168.1.10:5000")
    }

    func testKeepsExplicitScheme() {
        XCTAssertEqual(ConnectView.normalize("HTTP://tm.lan")?.absoluteString, "HTTP://tm.lan")
    }

    func testRejectsEmpty() {
        XCTAssertNil(ConnectView.normalize("   "))
    }
}

final class AgentProxyTests: XCTestCase {
    func testHostRequestsAreUntouched() {
        XCTAssertEqual(APIClient.proxiedPath("api/traefik/overview", agentId: nil, query: []), "api/traefik/overview")
    }

    func testTraefikCallsGoThroughProxy() {
        XCTAssertEqual(
            APIClient.proxiedPath("api/traefik/overview", agentId: "edge", query: []),
            "api/agents/proxy/edge/traefik/overview"
        )
    }

    func testRawRouteIsProxied() {
        XCTAssertEqual(
            APIClient.proxiedPath("api/routes/abc/raw", agentId: "edge", query: []),
            "api/agents/proxy/edge/routes/abc/raw"
        )
    }

    func testHubEndpointsStayOnHub() {
        XCTAssertEqual(APIClient.proxiedPath("api/routes", agentId: "edge", query: []), "api/routes")
        XCTAssertEqual(APIClient.proxiedPath("api/agents", agentId: "edge", query: []), "api/agents")
    }

    func testExplicitAgentQuerySkipsProxy() {
        let query = [URLQueryItem(name: "agent_id", value: "edge")]
        XCTAssertEqual(APIClient.proxiedPath("api/traefik/services", agentId: "edge", query: query), "api/traefik/services")
    }

    func testRequestHeadersAndLanguage() throws {
        var client = APIClient(baseURL: URL(string: "https://tm.example.com/sub/")!, apiKey: "k", agentId: nil)
        client.languageTag = { "de" }
        let request = try client.makeRequest(.get, "api/settings", query: [])
        XCTAssertEqual(request.url?.absoluteString, "https://tm.example.com/sub/api/settings?lang=de")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Api-Key"), "k")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Requested-With"), "fetch")
    }
}

final class LenientDecodingTests: XCTestCase {
    func testAgentHealthAcceptsStringLatencyAndNulls() throws {
        let json = #"{"ok": true, "version": null, "latency_ms": "12"}"#
        let health = try JSONDecoder().decode(AgentHealth.self, from: Data(json.utf8))
        XCTAssertTrue(health.ok)
        XCTAssertNil(health.version)
        XCTAssertEqual(health.latencyMs, 12)
    }

    func testSettingsDefaultsMissingFields() throws {
        let settings = try JSONDecoder().decode(ServerSettings.self, from: Data(#"{"cert_resolver": "le, cf"}"#.utf8))
        XCTAssertEqual(settings.certResolvers, ["le", "cf"])
        XCTAssertTrue(settings.tabVisible("logs"))
    }

    func testAgentIdMayBeNumeric() throws {
        let agent = try JSONDecoder().decode(Agent.self, from: Data(#"{"id": 7, "name": "edge"}"#.utf8))
        XCTAssertEqual(agent.id, "7")
    }
}

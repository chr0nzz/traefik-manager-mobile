import XCTest
@testable import TraefikManager

final class CrowdSecTests: XCTestCase {
    func testMetaDecodesJSONArrays() throws {
        let alert = try JSONDecoder().decode(CsAlert.self, from: Data(#"""
        {"id":7,"scenario":"crowdsecurity/http-probing","events_count":"3","source":{"ip":"1.2.3.4","cn":"de","as_number":123},
         "meta":[{"key":"target_uri","value":"[\"/a\",\"/b\"]"},{"key":"traefik_router_name","value":"[\"app@file\"]"},{"key":"user_agent","value":"curl"}]}
        """#.utf8))
        XCTAssertEqual(alert.uris, ["/a", "/b"])
        XCTAssertEqual(alert.routers, ["app@file"])
        XCTAssertEqual(alert.userAgents, ["curl"])
        XCTAssertEqual(alert.countryCode, "DE")
        XCTAssertEqual(alert.source.asNumber, "123")
        XCTAssertEqual(alert.eventsCount, 3)
    }

    func testRankingPutsOpenSourcesFirst() {
        let alerts = DemoCrowdSec.alerts()
        let banned: Set<String> = ["62.210.142.174"]
        let snapshot = CrowdSecSnapshot(decisions: .loaded([]), alerts: .loaded(alerts))
        let ranked = CrowdSecAnalytics.sources(alerts) { snapshot.handled($0, banned: banned) }
        XCTAssertEqual(ranked.count, 4)
        XCTAssertEqual(ranked.last?.key, "62.210.142.174")
    }

    func testFacetsMatchDecisionsByOrigin() {
        var facets = CsFacets()
        facets.toggle(.origin, "subscribed")
        XCTAssertEqual(DemoCrowdSec.decisions.filter(facets.matches).map(\.id), [3, 4])
        facets.toggle(.origin, "subscribed")
        XCTAssertTrue(facets.isEmpty)
    }

    func testWorldMapLoads() {
        XCTAssertGreaterThan(WorldMapShapes.shapes.count, 150)
        XCTAssertTrue(WorldMapShapes.shapes.contains { $0.code == "DE" })
    }
}

final class ProviderTests: XCTestCase {
    func testProviderRoutesFilterByProvider() {
        var docker = TraefikObject("app@docker", "docker", "enabled")
        docker.rule = "Host(`app.example.com`)"
        let file = TraefikObject("x@file", "file", "enabled")
        let routes = ProviderRows.routes(page: .docker, routers: ProtoEnvelope(http: [docker, file]), services: nil, managed: [])
        XCTAssertEqual(routes.map(\.shortName), ["app"])
        XCTAssertEqual(routes.first?.plainHost, "app.example.com")
        let external = ProviderRows.routes(page: .fileExternal, routers: ProtoEnvelope(http: [file]), services: nil, managed: ["x"])
        XCTAssertTrue(external.isEmpty, "managed file routes stay on the Routes tab")
    }
}

import XCTest
@testable import TraefikManager

final class ServiceRowTests: XCTestCase {
    private func service(_ json: String) throws -> TraefikService {
        try JSONDecoder().decode(TraefikService.self, from: Data(json.utf8))
    }

    func testLoadBalancerRowCountsBackends() throws {
        let row = ServiceRows.row(try service("""
        {"name":"api@docker","status":"enabled","serverStatus":{"http://a":"UP","http://b":"DOWN"},
         "loadBalancer":{"servers":[{"url":"http://a"},{"url":"http://b"}],"healthCheck":{"path":"/"}}}
        """), proto: .http)
        XCTAssertEqual(row.provider, "docker")
        XCTAssertEqual(row.health, .warning)
        XCTAssertEqual(row.backendSummary, "1/2 active")
        XCTAssertEqual(row.servers.map(\.health), [.up, .down])
        XCTAssertEqual(row.passHostHeader, true)
        XCTAssertEqual(BackendHealth.summary(row), "1 of 2 servers down")
    }

    func testCompositeTargets() throws {
        let row = ServiceRows.row(try service("""
        {"name":"m@file","status":"enabled","mirroring":{"service":"main","mirrors":[{"name":"copy","percent":10}]}}
        """), proto: .http)
        XCTAssertEqual(row.kind, "mirroring")
        XCTAssertEqual(row.composite, ["main", "copy mirror (10%)"])
    }

    func testOwnedChildrenAreHidden() throws {
        var envelope = ServiceEnvelope()
        envelope.http = [try service(#"{"name":"pool@file"}"#), try service(#"{"name":"pool-child@file"}"#)]
        envelope.ownedServices = ["pool"]
        envelope.ownedChildren = ["pool-child"]
        let rows = ServiceRows.from(envelope)
        XCTAssertEqual(rows.map(\.shortName), ["pool"])
        XCTAssertTrue(rows[0].owned)
    }

    func testDraftRoundTrip() throws {
        let draft = ServiceDraft.from(name: "w@file", service: try service("""
        {"name":"w@file","weighted":{"services":[{"name":"a@file","weight":3},{"name":"b@file"}]}}
        """))
        XCTAssertEqual(draft.type, "weighted")
        let payload = draft.payload(agentId: "edge")
        XCTAssertEqual(payload.children.map(\.name), ["a", "b"])
        XCTAssertEqual(payload.children.map(\.weight), [3, 1])
        XCTAssertNil(payload.healthCheck)
        XCTAssertEqual(payload.agentId, "edge")
    }

    func testLoadBalancerNeedsAddress() {
        var draft = ServiceDraft()
        draft.name = "x"
        draft.children = [ServiceChildDraft(kind: ServiceChildDraft.service, name: "other")]
        XCTAssertNotNil(draft.problem)
    }

    func testShortDuration() {
        XCTAssertEqual(ServiceHealthDraft.shortDuration("1m30s"), "1m30s")
        XCTAssertEqual(ServiceHealthDraft.shortDuration("0h0m10s"), "10s")
        XCTAssertEqual(ServiceHealthDraft.shortDuration("weird"), "weird")
    }
}

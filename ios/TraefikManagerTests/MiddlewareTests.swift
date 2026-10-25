import XCTest
@testable import TraefikManager

final class MiddlewareWizardTests: XCTestCase {
    private func build(_ id: String, text: [String: String] = [:], toggles: [String: Bool] = [:]) -> String {
        let wizard = MiddlewareTemplates.byId(id)!
        var values = wizard.defaultValues
        values.text.merge(text) { $1 }
        values.toggles.merge(toggles) { $1 }
        return wizard.build(values)
    }

    func testThirtyWizards() {
        XCTAssertEqual(MiddlewareTemplates.all.count, 30)
        XCTAssertEqual(Set(MiddlewareTemplates.all.map(\.id)).count, 30)
    }

    func testBasicAuthQuotesUsersAndRealm() {
        let yaml = build("basicAuth", text: ["users": "a:hash\nb:hash2", "realm": "Home \"lab\""])
        XCTAssertEqual(yaml, "basicAuth:\n  users:\n    - \"a:hash\"\n    - \"b:hash2\"\n  realm: \"Home \\\"lab\\\"\"")
    }

    func testForwardAuthAuthentikDefaults() {
        let yaml = build("forwardAuthAuthentik")
        XCTAssertTrue(yaml.contains("address: \"http://authentik-server:9000/outpost.goauthentik.io/auth/traefik\""))
        XCTAssertTrue(yaml.contains("trustForwardHeader: true"))
        XCTAssertTrue(yaml.contains("- \"X-authentik-uid\""))
        XCTAssertTrue(yaml.hasSuffix("maxResponseBodySize: 4096"))
    }

    func testIPAllowListDepthStrategy() {
        let yaml = build("ipAllowList", text: ["cidrs": "10.0.0.0/8", "strategy": "depth", "depth": "2"])
        XCTAssertEqual(yaml, "ipAllowList:\n  sourceRange:\n    - \"10.0.0.0/8\"\n  ipStrategy:\n    depth: 2")
    }

    func testEncodedCharactersEmpty() {
        XCTAssertEqual(build("encodedCharacters"), "encodedCharacters: {}")
    }

    func testGatekeeperAddressAndAuthorization() {
        let yaml = build("forwardAuthGatekeeper", text: ["url": "https://auth.example.com/", "policy": "admin"], toggles: ["authorization": true])
        XCTAssertTrue(yaml.contains("address: \"https://auth.example.com/auth/verify?policy=admin\""))
        XCTAssertTrue(yaml.contains("- \"Authorization\""))
    }

    func testKindOf() {
        XCTAssertEqual(MiddlewareTemplates.kind(of: "# note\nrateLimit:\n  average: 1"), "rateLimit")
        XCTAssertEqual(MiddlewareTemplates.kind(of: ""), "middleware")
    }
}

final class MiddlewareUsageTests: XCTestCase {
    private func middleware(_ name: String, _ yaml: String) throws -> MiddlewareDef {
        let data = try JSONSerialization.data(withJSONObject: ["name": name, "yaml": yaml])
        return try JSONDecoder().decode(MiddlewareDef.self, from: data)
    }

    func testChainMembersInheritOwnerUsage() throws {
        var route = Route(id: "r", name: "r")
        route.middlewares = .array([.string("secure@file")])
        let counts = MiddlewareUsage.counts(routes: [route], middlewares: [
            try middleware("secure", "chain:\n  middlewares:\n    - headers\n    - limit"),
            try middleware("headers", "headers: {}"),
            try middleware("limit", "rateLimit: {}"),
            try middleware("lonely", "compress: {}"),
        ])
        XCTAssertEqual(counts, ["secure": 1, "headers": 1, "limit": 1, "lonely": 0])
    }

    func testFormFields() {
        let form = MiddlewareForm(name: " auth ", protocol: .tcp, yaml: "ipAllowList: {}\n", configFile: "a.yml")
        let fields = Dictionary(form.fields(agentId: "edge").map { ($0.0, $0.1) }, uniquingKeysWith: { a, _ in a })
        XCTAssertEqual(fields["middlewareName"], "auth")
        XCTAssertEqual(fields["mwProtocol"], "tcp")
        XCTAssertEqual(fields["middlewareContent"], "ipAllowList: {}")
        XCTAssertEqual(fields["agent_id"], "edge")
    }
}

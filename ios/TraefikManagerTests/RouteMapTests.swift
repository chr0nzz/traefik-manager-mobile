import XCTest
@testable import TraefikManager

final class RouteMapTests: XCTestCase {
    private func route(_ name: String, provider: String = "file", middlewares: [String] = [], entry: String = "websecure") -> Route {
        var route = Route(id: "\(provider)::\(name)", name: name)
        route.provider = provider
        route.serviceName = name
        route.target = "http://10.0.0.1"
        route.entryPoints = .array([.string(entry)])
        route.middlewares = .array(middlewares.map(JSONValue.string))
        return route
    }

    func testChainColumnsAndEdges() {
        let graph = RouteMapBuilder.build(routes: [route("app", middlewares: ["auth"])])
        XCTAssertEqual(Set(graph.nodes.map(\.id)), ["ep:websecure", "route:file::app", "mw:auth", "svc:app"])
        XCTAssertEqual(graph.edges.count, 3)
        let columns = Dictionary(uniqueKeysWithValues: graph.nodes.map { ($0.id, $0.column) })
        XCTAssertEqual(columns["ep:websecure"], 0)
        XCTAssertEqual(columns["svc:app"], 3)
        XCTAssertLessThan(graph.byId["ep:websecure"]!.x, graph.byId["svc:app"]!.x)
    }

    func testLargeProvidersCollapseIntoAGroup() {
        let docker = (0..<6).map { route("c\($0)", provider: "docker") }
        let graph = RouteMapBuilder.build(routes: docker + [route("solo")])
        XCTAssertNotNil(graph.byId["group:docker"])
        XCTAssertEqual(graph.byId["group:docker"]?.members.count, 6)
        XCTAssertNil(graph.byId["route:docker::c0"])
    }

    func testConnectedAndFilters() {
        let graph = RouteMapBuilder.build(routes: [route("a", middlewares: ["m"]), route("b", entry: "web")])
        XCTAssertEqual(graph.connected("mw:m"), ["ep:websecure", "route:file::a", "mw:m", "svc:a"])
        XCTAssertEqual(RouteMapBuilder.build(routes: [route("a"), route("b", entry: "web")], entryPoint: "web").nodes.count, 3)
        XCTAssertTrue(RouteMapBuilder.build(routes: [route("a")], query: "zzz").nodes.isEmpty)
    }
}

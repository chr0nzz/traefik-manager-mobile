import XCTest
@testable import TraefikManager

final class LogParserTests: XCTestCase {
    func testJSONLine() throws {
        let raw = #"{"ClientAddr":"203.0.113.9:5555","RequestMethod":"GET","RequestPath":"/api/items/123?x=1","DownstreamStatus":502,"OriginStatus":0,"Duration":2500000,"RequestHost":"app.example.com","ServiceName":"app@docker","StartUTC":"2026-10-24T10:00:00Z","TLSVersion":"1.3"}"#
        let entry = try XCTUnwrap(LogParser.parse(raw))
        XCTAssertEqual(entry.format, .json)
        XCTAssertEqual(entry.ip, "203.0.113.9")
        XCTAssertEqual(entry.status, 502)
        XCTAssertEqual(entry.origin, 0)
        XCTAssertEqual(entry.durMs, 2.5)
        XCTAssertEqual(entry.duration, "3ms")
        XCTAssertEqual(entry.pattern, "/api/items/<_>")
        XCTAssertEqual(entry.statusName, "Bad Gateway")
        XCTAssertNotNil(entry.timestamp)
    }

    func testTraefikCLFLine() throws {
        let raw = #"10.0.0.5 - - [24/Oct/2026:10:00:00 -0400] "POST /login HTTP/1.1" 401 12 "-" "curl/8" 42 "auth@file" "http://10.0.0.9:80" 3ms"#
        let entry = try XCTUnwrap(LogParser.parse(raw))
        XCTAssertEqual(entry.format, .clf)
        XCTAssertEqual(entry.method, "POST")
        XCTAssertEqual(entry.status, 401)
        XCTAssertEqual(entry.router, "auth@file")
        XCTAssertEqual(entry.durMs, 3)
        let stamp = try XCTUnwrap(entry.timestamp)
        XCTAssertEqual(stamp, 1_792_850_400_000, accuracy: 1)
    }

    func testUnparsedLine() {
        XCTAssertNil(LogParser.parse("time=... level=info msg=hello"))
    }

    func testDurations() {
        XCTAssertEqual(LogParser.parseDuration("1m30s"), 90_000)
        XCTAssertEqual(LogParser.parseDuration("250µs"), 0.25)
        XCTAssertNil(LogParser.parseDuration("-"))
        XCTAssertEqual(LogParser.formatMs(1234), "1.23s")
        XCTAssertEqual(LogParser.spanText(3_725_000), "1h 02m")
    }

    func testIPClass() {
        XCTAssertEqual(LogParser.ipClass("100.87.28.37"), "cgnat")
        XCTAssertEqual(LogParser.ipClass("192.168.1.4"), "private")
        XCTAssertEqual(LogParser.ipClass("::1"), "loopback")
        XCTAssertEqual(LogParser.ipClass("8.8.8.8"), "public")
    }

    func testFlag() {
        XCTAssertEqual(Countries.flag("de"), "🇩🇪")
        XCTAssertEqual(Countries.flag("xyz"), "")
    }
}

final class LogAnalyticsTests: XCTestCase {
    private func entry(_ status: Int, _ path: String, _ ms: Double) -> LogEntry {
        LogEntry(format: .json, ip: "1.1.1.1", method: "GET", path: path, status: status, durMs: ms)
    }

    func testWindow() {
        let window = LogAnalytics.build([
            entry(200, "/a", 10), entry(200, "/a", 600), entry(404, "/b", 20), entry(500, "/b", 30),
        ], fetched: 5, unparsed: 1)
        XCTAssertEqual(window.health, .down)
        XCTAssertEqual(window.statuses.twoXx, 2)
        XCTAssertEqual(window.latency.slow, 1)
        XCTAssertEqual(window.paths.first?.key, "/b", "paths with server errors rank first")
        XCTAssertEqual(window.failures.count, 2)
        XCTAssertEqual(window.failures.first?.share, 50)
    }

    func testFacetStatusSpecs() {
        XCTAssertTrue(LogFacet.statusMatch(404, "4xx"))
        XCTAssertTrue(LogFacet.statusMatch(503, "errors"))
        XCTAssertFalse(LogFacet.statusMatch(200, "errors"))
        XCTAssertTrue(LogFacet.path.hit("~/a/<_>", LogEntry(format: .json, path: "/a/42")))
    }
}

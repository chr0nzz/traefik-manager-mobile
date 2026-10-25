import XCTest
@testable import TraefikManager

final class SettingsTests: XCTestCase {
    func testApiOriginIgnoresDefaultPortsAndTrailingSlash() {
        XCTAssertTrue(ApiOrigin.same("http://traefik:8080/", "HTTP://Traefik:8080"))
        XCTAssertTrue(ApiOrigin.same("https://a.example.com", "https://a.example.com:443/"))
        XCTAssertTrue(ApiOrigin.same("http://user:pw@[::1]:80/api", "http://[::1]/api?x=1"))
        XCTAssertFalse(ApiOrigin.same("http://traefik:8080", "http://traefik:8081"))
        XCTAssertFalse(ApiOrigin.same("http://traefik", "https://traefik"))
        XCTAssertFalse(ApiOrigin.same("traefik:8080", "traefik:8080"))
    }

    func testComposeIncludesOnlyConfiguredOptions() {
        let agent = AgentConfig(raw: [
            "config_path": .string("/srv/dynamic"),
            "restart_method": .string("poison-pill"),
            "signal_file_path": .string("/signals/restart"),
            "traefik_container": .string("traefik"),
            "acme_json_path": .string("/letsencrypt/acme.json"),
            "tma_port": .string("9000"),
            "backup_keep_count": .string("0"),
        ])
        let env = AgentCompose.envLines(agent, apiKey: nil)
        XCTAssertEqual(env.first, "TMA_API_KEY=<your-api-key>")
        XCTAssertTrue(env.contains("CONFIG_PATH=/srv/dynamic"))
        XCTAssertTrue(env.contains("TMA_PORT=9000"))
        XCTAssertTrue(env.contains("SIGNAL_FILE_PATH=/signals/restart"))
        XCTAssertFalse(env.contains { $0.hasPrefix("DOCKER_HOST") })
        XCTAssertFalse(env.contains { $0.hasPrefix("BACKUP_KEEP_COUNT") })
        let volumes = AgentCompose.volumeLines(agent)
        XCTAssertEqual(volumes.first, "/srv/dynamic:/srv/dynamic")
        XCTAssertTrue(volumes.contains("tma_backups:/app/backups"))
        XCTAssertTrue(volumes.contains("/letsencrypt/acme.json:/letsencrypt/acme.json:ro"))
        XCTAssertTrue(volumes.contains("traefik-signals:/signals"))
        let compose = AgentCompose.compose(agent, apiKey: "k")
        XCTAssertTrue(compose.contains("\"9000:9000\""))
        XCTAssertTrue(compose.hasSuffix("volumes:\n  tma_backups:\n  traefik-signals:"))
    }

    func testChannelPayloadDropsUnusedFieldsAndSummarises() throws {
        var channel = NotificationChannel()
        channel.name = "  Phone "
        channel.kind = "telegram"
        channel.url = "https://leftover"
        channel.token = " bot "
        channel.username = "u"
        XCTAssertEqual(channel.missingFields, [String(localized: "Chat ID")])
        let payload = channel.payload
        XCTAssertEqual(payload.name, "Phone")
        XCTAssertEqual(payload.url, "")
        XCTAssertEqual(payload.token, "bot")
        XCTAssertEqual(payload.username, "")
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(payload)) as? [String: Any]
        XCTAssertNil(json?["id"])
        XCTAssertEqual(json?["min_severity"] as? String, "info")

        channel.categories = ["certs", "crowdsec"]
        channel.minSeverity = "warning"
        channel.digest = "daily"
        channel.quietHours = "23:00-07:00"
        channel.breakThrough = true
        XCTAssertEqual(channel.summary.components(separatedBy: " · ").count, 4)
        XCTAssertTrue(channel.summary.hasPrefix("Certificates, CrowdSec"))
    }

    @MainActor
    func testUnreadUsesServerMarkerWhenPresent() async {
        let store = NotificationsStore()
        store.reset(for: "test-\(UUID().uuidString)")
        await store.refresh(DemoService())
        XCTAssertEqual(store.items?.count, 4)
        XCTAssertEqual(store.readUntil, 2)
        XCTAssertEqual(store.unread, 2)
        await store.markRead(DemoService())
        XCTAssertEqual(store.unread, 0)
    }

    func testNotificationSinceAndDecoding() throws {
        let item = try JSONDecoder().decode(TmNotification.self, from: Data(#"{"ts":"2026-08-11 21:58:12","type":"ERROR","msg":"x","id":"7","at":0}"#.utf8))
        XCTAssertEqual(item.id, 7)
        XCTAssertEqual(item.severity, .error)
        XCTAssertNil(item.since())
        let recent = TmNotification(ts: "", type: "info", msg: "y", category: "", id: 1, at: Int64(Date().timeIntervalSince1970) - 125)
        XCTAssertEqual(recent.since(), String(localized: "2 min ago"))
    }
}

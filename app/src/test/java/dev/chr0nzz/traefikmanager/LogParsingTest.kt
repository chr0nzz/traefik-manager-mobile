package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.IpClass
import dev.chr0nzz.traefikmanager.data.model.LogAnalytics
import dev.chr0nzz.traefikmanager.data.model.LogFormat
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.LogsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogParsingTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val clfFull =
        """1.2.3.4 - - [11/Aug/2026:17:45:02 +0000] "GET /api/health HTTP/1.1" 200 15 "-" "curl/8.5.0" 42 """ +
            """"web-router@file" "http://10.0.0.5:8080" 3ms"""

    @Test
    fun `agent null lines decode to an empty list`() {
        val response = json.decodeFromString<LogsResponse>("""{"lines":null}""")
        assertNull(response.lines)
        assertTrue(response.lines.orEmpty().isEmpty())
    }

    @Test
    fun `the not-configured envelope arrives with HTTP 200`() {
        val response = json.decodeFromString<LogsResponse>(
            """{"error":"Access log not found. Set ACCESS_LOG_PATH env var or configure the path in Settings.","lines":[]}""",
        )
        assertEquals(
            "Access log not found. Set ACCESS_LOG_PATH env var or configure the path in Settings.",
            response.error,
        )
    }

    @Test
    fun `the full CLF line maps every group`() {
        val entry = LogParser.parse(clfFull)
        assertNotNull(entry)
        requireNotNull(entry)
        assertEquals(LogFormat.Clf, entry.format)
        assertEquals("1.2.3.4", entry.ip)
        assertEquals("11/Aug/2026:17:45:02 +0000", entry.date)
        assertEquals("GET", entry.method)
        assertEquals("/api/health", entry.path)
        assertEquals(200, entry.status)
        assertEquals("15", entry.size)
        assertEquals("web-router@file", entry.router)
        assertEquals("http://10.0.0.5:8080", entry.serviceUrl)
        assertEquals(3.0, entry.durMs!!, 0.001)
        assertEquals("", entry.service)
        assertNull(entry.origin)
    }

    @Test
    fun `a dash router and url become empty strings`() {
        val line = """1.2.3.4 - - [11/Aug/2026:17:45:02 +0000] "GET / HTTP/1.1" 200 15 "-" "curl" 42 "-" "-" 0ms"""
        val entry = LogParser.parse(line)!!
        assertEquals("", entry.router)
        assertEquals("", entry.serviceUrl)
    }

    @Test
    fun `a dash status parses as zero and counts as held open`() {
        val line = """1.2.3.4 - - [11/Aug/2026:17:45:02 +0000] "POST /x HTTP/2.0" - - "-" "-" 1 "-" "-" 5s"""
        val entry = LogParser.parse(line)!!
        assertEquals(0, entry.status)
        assertTrue(entry.heldOpen)
        assertEquals("other", entry.statusClass)
        assertEquals("tunnel", entry.statusName)
    }

    @Test
    fun `a malformed request line does not parse`() {
        assertNull(LogParser.parse("""1.2.3.4 - - [11/Aug/2026:17:45:02 +0000] "-" - - "-" "-" 1"""))
        assertNull(LogParser.parse("not a log line at all"))
        assertNull(LogParser.parse(""))
    }

    @Test
    fun `the basic CLF branch fills only the first six fields`() {
        val line = """1.2.3.4 - - [11/Aug/2026:17:45:02 +0000] "GET /x HTTP/1.1" 404 120"""
        val entry = LogParser.parse(line)!!
        assertEquals(LogFormat.GenericClf, entry.format)
        assertEquals(404, entry.status)
        assertEquals("120", entry.size)
        assertEquals("", entry.router)
        assertNull(entry.durMs)
    }

    @Test
    fun `the JSON branch maps traefik field names`() {
        val line = """{"ClientHost":"9.9.9.9","RequestMethod":"GET","RequestPath":"/api/x","DownstreamStatus":404,
            "OriginStatus":0,"Duration":2400000,"DownstreamContentSize":19,"RequestHost":"a.example.com",
            "RequestScheme":"https","entryPointName":"websecure","RouterName":"r@file","ServiceName":"s@docker",
            "ServiceURL":"http://172.18.0.3:8090","TLSVersion":"1.3","RetryAttempts":2,
            "StartUTC":"2026-08-12T05:41:39.077135755Z"}"""
        val entry = LogParser.parse(line)!!
        assertEquals(LogFormat.Json, entry.format)
        assertEquals("9.9.9.9", entry.ip)
        assertEquals(404, entry.status)
        assertEquals(0, entry.origin)
        assertEquals("19", entry.size)
        assertEquals("websecure", entry.entryPoint)
        assertEquals("s@docker", entry.service)
        assertEquals(2, entry.retries)
        assertEquals(2.4, entry.durMs!!, 0.0001)
        assertEquals("2ms", entry.duration)
        assertNotNull(entry.timestamp)
    }

    @Test
    fun `a JSON line without method, path or status falls through`() {
        assertNull(LogParser.parse("""{"DownstreamStatus":0,"Duration":5}"""))
    }

    @Test
    fun `client addr falls back to the host part`() {
        val entry = LogParser.parse("""{"ClientAddr":"1.2.3.4:5678","RequestMethod":"GET","RequestPath":"/"}""")!!
        assertEquals("1.2.3.4", entry.ip)
    }

    @Test
    fun `host only strips ports without breaking ipv6`() {
        assertEquals("", LogParser.hostOnly(""))
        assertEquals("2001:db8::1", LogParser.hostOnly("[2001:db8::1]:443"))
        assertEquals("2001:db8::1", LogParser.hostOnly("[2001:db8::1]"))
        assertEquals("1.2.3.4", LogParser.hostOnly("1.2.3.4:5678"))
        assertEquals("2001:db8::1", LogParser.hostOnly("2001:db8::1"))
        assertEquals("host", LogParser.hostOnly("host"))
    }

    @Test
    fun `go durations sum every unit`() {
        assertEquals(3.0, LogParser.parseDuration("3ms")!!, 0.0001)
        assertEquals(0.0, LogParser.parseDuration("0ms")!!, 0.0001)
        assertEquals(1500.0, LogParser.parseDuration("1.5s")!!, 0.0001)
        assertEquals(0.25, LogParser.parseDuration("250µs")!!, 0.0001)
        assertEquals(0.25, LogParser.parseDuration("250us")!!, 0.0001)
        assertEquals(0.0009, LogParser.parseDuration("900ns")!!, 0.000001)
        assertEquals(300_000.0, LogParser.parseDuration("5m")!!, 0.0001)
        assertEquals(90_000.0, LogParser.parseDuration("1m30s")!!, 0.0001)
        assertEquals(2_462_000.0, LogParser.parseDuration("41m2s")!!, 0.0001)
        assertEquals(3_723_000.0, LogParser.parseDuration("1h2m3s")!!, 0.0001)
        assertEquals(12.0, LogParser.parseDuration("12")!!, 0.0001)
        assertNull(LogParser.parseDuration("-"))
        assertNull(LogParser.parseDuration("abc"))
        assertNull(LogParser.parseDuration(null))
    }

    @Test
    fun `duration formatting matches the web thresholds`() {
        assertEquals("", LogParser.formatDuration(0))
        assertEquals("500ns", LogParser.formatDuration(500))
        assertEquals("2µs", LogParser.formatDuration(1500))
        assertEquals("2ms", LogParser.formatDuration(1_500_000))
        assertEquals("2.50s", LogParser.formatDuration(2_500_000_000))
    }

    @Test
    fun `millisecond display matches the web helper`() {
        assertEquals("-", LogParser.formatMs(null))
        assertEquals("10s", LogParser.formatMs(10_000.0))
        assertEquals("1.23s", LogParser.formatMs(1234.0))
        assertEquals("999ms", LogParser.formatMs(999.0))
        assertEquals("1ms", LogParser.formatMs(1.4))
        assertEquals("0.5ms", LogParser.formatMs(0.5))
        assertEquals("0ms", LogParser.formatMs(0.004))
        assertEquals("0ms", LogParser.formatMs(0.0))
    }

    @Test
    fun `CLF timestamps honour the offset`() {
        assertEquals(
            java.time.Instant.parse("2026-08-11T17:45:02Z").toEpochMilli(),
            LogParser.parseTimestamp("11/Aug/2026:17:45:02 +0000"),
        )
        assertEquals(
            java.time.Instant.parse("2026-08-11T15:45:02Z").toEpochMilli(),
            LogParser.parseTimestamp("11/Aug/2026:17:45:02 +0200"),
        )
        assertEquals(
            java.time.Instant.parse("2026-08-11T22:45:02Z").toEpochMilli(),
            LogParser.parseTimestamp("11/Aug/2026:17:45:02 -0500"),
        )
        assertNull(LogParser.parseTimestamp("11/Foo/2026:17:45:02 +0000"))
        assertNull(LogParser.parseTimestamp("garbage"))
        assertNull(LogParser.parseTimestamp(""))
    }

    @Test
    fun `path folding replaces ids, dates, uuids and long hex`() {
        assertEquals("/api/timeline/bucket/<_>", LogParser.pattern("/api/timeline/bucket/12345?x=1"))
        assertEquals("/a/<_>/b", LogParser.pattern("/a/2026-08-11/b"))
        assertEquals("/u/<_>", LogParser.pattern("/u/3f2b1c9d8e7a6b5c"))
        assertEquals("/s/<_>", LogParser.pattern("/s/3f2b1c9d-8e7a-4b5c-9d8e-7a6b5c4d3e2f"))
        assertEquals("/abc", LogParser.pattern("/abc"))
        assertEquals("favicon.ico", LogParser.pattern("favicon.ico"))
        assertEquals("/a//b", LogParser.pattern("/a//b"))
    }

    @Test
    fun `status classes and names follow the fixed map`() {
        assertEquals("2xx", LogParser.statusClass(204))
        assertEquals("5xx", LogParser.statusClass(503))
        assertEquals("other", LogParser.statusClass(0))
        assertEquals("I am a teapot", LogParser.statusName(418))
        assertEquals("", LogParser.statusName(599))
        assertEquals("tunnel", LogParser.statusName(0))
    }

    @Test
    fun `span text formats like the web`() {
        assertEquals("1m 05s", LogParser.spanText(65_000))
        assertEquals("1h 00m", LogParser.spanText(3_600_000))
        assertEquals("1d 1h", LogParser.spanText(90_000_000))
        assertEquals("42s", LogParser.spanText(42_000))
    }

    @Test
    fun `held-open rows are excluded from latency but counted separately`() {
        val entries = listOf(
            LogParser.parse("""{"RequestMethod":"GET","RequestPath":"/a","DownstreamStatus":200,"Duration":50000000}""")!!,
            LogParser.parse("""{"RequestMethod":"GET","RequestPath":"/b","DownstreamStatus":200,"Duration":600000000}""")!!,
            LogParser.parse("""{"RequestMethod":"GET","RequestPath":"/ws","DownstreamStatus":101,"Duration":2400000000000}""")!!,
        )
        val latency = LogAnalytics.latency(entries)
        assertEquals(2, latency.timed)
        assertEquals(1, latency.held)
        assertEquals(600.0, latency.max!!, 0.001)
        assertEquals(1, latency.slow)
        assertEquals(1, latency.fast)
    }

    @Test
    fun `percentiles use nearest rank by floor`() {
        val entries = (1..10).map { index ->
            LogParser.parse(
                """{"RequestMethod":"GET","RequestPath":"/$index","DownstreamStatus":200,"Duration":${index * 1_000_000}}""",
            )!!
        }
        val latency = LogAnalytics.latency(entries)
        assertEquals(6.0, latency.p50!!, 0.001)
        assertEquals(10.0, latency.p95!!, 0.001)
        assertEquals(5.5, latency.average!!, 0.001)
    }

    @Test
    fun `ranking puts server errors first then client errors`() {
        val entries = listOf(
            """{"RequestMethod":"GET","RequestPath":"/a","RequestHost":"a.com","DownstreamStatus":200}""",
            """{"RequestMethod":"GET","RequestPath":"/a","RequestHost":"a.com","DownstreamStatus":200}""",
            """{"RequestMethod":"GET","RequestPath":"/b","RequestHost":"b.com","DownstreamStatus":404}""",
            """{"RequestMethod":"GET","RequestPath":"/c","RequestHost":"c.com","DownstreamStatus":502}""",
        ).map { LogParser.parse(it)!! }
        val window = LogAnalytics.build(entries, fetched = 4, unparsed = 0)
        assertEquals(listOf("c.com", "b.com", "a.com"), window.domains.map { it.key })
        assertEquals(1, window.serverErrors)
        assertEquals(1, window.clientErrors)
        assertEquals(2, window.statuses.twoXx)
    }

    @Test
    fun `code ranking breaks ties on the lower status`() {
        val entries = listOf(404, 403, 404, 403, 500).map { status ->
            LogParser.parse("""{"RequestMethod":"GET","RequestPath":"/x","DownstreamStatus":$status}""")!!
        }
        val window = LogAnalytics.build(entries, fetched = 5, unparsed = 0)
        assertEquals(listOf(403 to 2, 404 to 2, 500 to 1), window.codeRank)
    }

    @Test
    fun `ip classification matches the web rules`() {
        assertEquals(IpClass.Loopback, Countries.classify("127.0.0.1"))
        assertEquals(IpClass.LinkLocal, Countries.classify("169.254.1.1"))
        assertEquals(IpClass.Private, Countries.classify("10.0.0.1"))
        assertEquals(IpClass.Private, Countries.classify("172.20.1.1"))
        assertEquals(IpClass.Public, Countries.classify("172.15.1.1"))
        assertEquals(IpClass.Private, Countries.classify("192.168.1.1"))
        assertEquals(IpClass.Cgnat, Countries.classify("100.87.28.37"))
        assertEquals(IpClass.Public, Countries.classify("8.8.8.8"))
        assertEquals(IpClass.Unknown, Countries.classify("999.1.1.1"))
        assertEquals(IpClass.Loopback, Countries.classify("::1"))
        assertEquals(IpClass.LinkLocal, Countries.classify("fe80::1"))
        assertEquals(IpClass.Private, Countries.classify("fd00::1"))
        assertEquals(IpClass.Private, Countries.classify("::ffff:192.168.1.1"))
        assertEquals(IpClass.Public, Countries.classify("2001:db8::1"))
    }

    @Test
    fun `only public and unknown addresses are worth a geo lookup`() {
        assertTrue(Countries.worthLookingUp("8.8.8.8"))
        assertTrue(!Countries.worthLookingUp("192.168.1.5"))
        assertTrue(!Countries.worthLookingUp("127.0.0.1"))
        assertTrue(!Countries.worthLookingUp("100.87.28.37"))
    }

    @Test
    fun `flags need exactly two letters`() {
        assertEquals("🇺🇸", Countries.flag("US"))
        assertEquals("🇺🇸", Countries.flag("us"))
        assertEquals("", Countries.flag("12"))
        assertEquals("", Countries.flag("USA"))
        assertEquals("", Countries.flag(""))
    }
}

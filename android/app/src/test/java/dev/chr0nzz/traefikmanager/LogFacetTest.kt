package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.LogLine
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.ui.logs.LogFacet
import dev.chr0nzz.traefikmanager.ui.logs.LogFacets
import dev.chr0nzz.traefikmanager.ui.logs.LogsUiState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFacetTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun line(index: Int, raw: String) = LogLine(index, raw, LogParser.parse(raw))

    private fun jsonLine(
        index: Int,
        path: String,
        status: Int,
        method: String = "GET",
        ip: String = "1.1.1.1",
        service: String = "api@docker",
        domain: String = "a.example.com",
        durationNs: Long = 5_000_000,
    ) = line(
        index,
        """{"ClientHost":"$ip","RequestMethod":"$method","RequestPath":"$path","DownstreamStatus":$status,
           "Duration":$durationNs,"RequestHost":"$domain","ServiceName":"$service"}""",
    )

    private val lines = listOf(
        jsonLine(0, "/", 200),
        jsonLine(1, "/api/users/42", 404, ip = "2.2.2.2"),
        jsonLine(2, "/api/users/7", 404, ip = "2.2.2.2"),
        jsonLine(3, "/health", 500, method = "POST", service = "web@file", durationNs = 900_000_000),
        jsonLine(4, "/health", 200, domain = "b.example.com"),
        LogLine(5, "not parseable", null),
    )

    private fun state(vararg facets: Pair<LogFacet, String>) =
        LogsUiState(lines = lines, facets = facets.toMap())

    @Test
    fun `status facets accept classes, codes and the errors alias`() {
        assertTrue(LogFacets.statusMatch(404, "errors"))
        assertTrue(LogFacets.statusMatch(500, "5xx"))
        assertFalse(LogFacets.statusMatch(404, "5xx"))
        assertTrue(LogFacets.statusMatch(404, "404"))
        assertTrue(LogFacets.statusMatch(0, "other"))
        assertFalse(LogFacets.statusMatch(200, "errors"))
    }

    @Test
    fun `a status facet narrows the visible list`() {
        assertEquals(2, state(LogFacet.Status to "404").visible.size)
        assertEquals(3, state(LogFacet.Status to "errors").visible.size)
        assertEquals(1, state(LogFacet.Status to "5xx").visible.size)
    }

    @Test
    fun `facets combine with AND`() {
        val narrowed = state(LogFacet.Status to "errors", LogFacet.Ip to "2.2.2.2")
        assertEquals(2, narrowed.visible.size)

        val impossible = state(LogFacet.Status to "5xx", LogFacet.Ip to "2.2.2.2")
        assertTrue(impossible.visible.isEmpty())
    }

    @Test
    fun `a path facet can target a folded pattern or an exact path`() {
        assertEquals(2, state(LogFacet.Path to "~/api/users/<_>").visible.size)
        assertEquals(1, state(LogFacet.Path to "/api/users/42").visible.size)
    }

    @Test
    fun `the duration facet honours bands and held-open connections`() {
        assertEquals(4, state(LogFacet.Duration to "fast").visible.size)
        assertEquals(1, state(LogFacet.Duration to "slow").visible.size)
        assertTrue(state(LogFacet.Duration to "held").visible.isEmpty())
    }

    @Test
    fun `service and method facets match the parsed fields`() {
        assertEquals(1, state(LogFacet.Service to "web@file").visible.size)
        assertEquals(1, state(LogFacet.Method to "POST").visible.size)
        assertEquals(1, state(LogFacet.Domain to "b.example.com").visible.size)
    }

    @Test
    fun `unparsed lines are dropped as soon as any facet is on`() {
        assertEquals(6, LogsUiState(lines = lines).visible.size)
        assertEquals(4, state(LogFacet.Method to "GET").visible.size)
    }

    @Test
    fun `analytics describe the current selection, not the whole window`() {
        val all = LogsUiState(lines = lines)
        assertEquals(5, all.window.parsed)
        assertEquals(1, all.window.serverErrors)

        val narrowed = state(LogFacet.Status to "404")
        assertEquals(2, narrowed.window.parsed)
        assertEquals(0, narrowed.window.serverErrors)
        assertEquals(2, narrowed.window.clientErrors)
        assertEquals(6, narrowed.fetched)
    }

    @Test
    fun `the country list stays scoped to the facets but ignores the country filter`() {
        val geo = LogsUiState(
            lines = lines,
            countryByIp = mapOf("1.1.1.1" to "US", "2.2.2.2" to "DE"),
            country = "DE",
        )
        assertEquals(setOf("US", "DE"), geo.countries.map { it.code }.toSet())
        assertEquals(2, geo.visible.size)
    }

    @Test
    fun `a facet that matches nothing is reported as dead`() {
        val dead = LogsUiState(lines = lines, facets = mapOf(LogFacet.Ip to "9.9.9.9"))
        assertEquals(setOf(LogFacet.Ip), dead.deadFacets)

        val live = state(LogFacet.Ip to "2.2.2.2")
        assertTrue(live.deadFacets.isEmpty())
    }

    @Test
    fun `facet chips label paths without the pattern marker`() {
        assertEquals("/api/users/<_>", LogFacets.label(LogFacet.Path, "~/api/users/<_>"))
        assertEquals("api", LogFacets.label(LogFacet.Service, "api@docker"))
        assertEquals("404", LogFacets.label(LogFacet.Status, "404"))
    }

    @Test
    fun `the widen step is the next line count up`() {
        assertEquals(200, LogsUiState(lineCount = 100).nextLineStep)
        assertEquals(1000, LogsUiState(lineCount = 500).nextLineStep)
        assertEquals(null, LogsUiState(lineCount = 1000).nextLineStep)
    }

    @Test
    fun `optional tabs follow the server, and an unreported tab stays visible`() {
        val settings = json.decodeFromString<ServerSettings>(
            """{"visible_tabs":{"crowdsec":false,"logs":true,"certs":false},"crowdsec_enabled":false}""",
        )
        assertFalse(settings.tabVisible("crowdsec"))
        assertTrue(settings.tabVisible("logs"))
        assertFalse(settings.tabVisible("certs"))
        assertTrue(settings.tabVisible("plugins"))
        assertFalse(settings.crowdsecEnabled)
    }

    @Test
    fun `a server that reports no tabs at all leaves everything visible`() {
        val settings = json.decodeFromString<ServerSettings>("""{"domains":["a.com"]}""")
        assertTrue(settings.tabVisible("crowdsec"))
        assertTrue(settings.tabVisible("logs"))
    }
}

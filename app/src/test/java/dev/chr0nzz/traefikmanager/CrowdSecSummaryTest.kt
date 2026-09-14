package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSummary
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsDecisionFeed
import dev.chr0nzz.traefikmanager.data.model.CsDecisionPage
import dev.chr0nzz.traefikmanager.data.model.CsDecisionQuery
import dev.chr0nzz.traefikmanager.data.model.CsDecisionsSummary
import dev.chr0nzz.traefikmanager.data.model.CsFacet
import dev.chr0nzz.traefikmanager.data.model.CsFacets
import dev.chr0nzz.traefikmanager.data.model.CsMetaEntry
import dev.chr0nzz.traefikmanager.data.model.CsRead
import dev.chr0nzz.traefikmanager.data.model.CsReads
import dev.chr0nzz.traefikmanager.data.model.CsSource
import dev.chr0nzz.traefikmanager.data.model.CsSummary
import dev.chr0nzz.traefikmanager.data.model.CsSummaryStep
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdSecSummaryTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val summaryJson = """
        {
          "version": "a1b2c3d4e5f60718",
          "decisions": {
            "ok": true, "error": "", "stale": "", "total": 41250, "own": 12, "subscribed": 41238, "wide": 3,
            "origins": {"capi": 41000, "lists": 238, "crowdsec": 10, "cscli": 2, "": 0},
            "types": {"ban": 41249, "captcha": 1},
            "rows": [{"id": 99, "value": "9.9.9.9", "origin": "cscli", "scope": "Ip", "type": "ban", "until": "x"}],
            "rows_more": 0
          },
          "alerts": {
            "ok": true, "error": "", "status": 200, "limit": 500, "capped": true,
            "rows": [
              {"id": 2, "scenario": "crowdsecurity/http-probing", "handled": true,
               "source": {"ip": "1.1.1.1", "scope": "Ip", "as_number": 64500}},
              {"id": 1, "scenario": "crowdsecurity/http-probing", "handled": false,
               "source": {"ip": "9.9.9.9", "scope": "Ip"}},
              {"id": 3, "scenario": "blocklist", "source": {"ip": "2.2.2.2", "scope": "capi"}}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `the summary decodes counts, rows and the handled flag`() {
        val summary = json.decodeFromString<CsSummary>(summaryJson)
        assertEquals("a1b2c3d4e5f60718", summary.version)
        assertFalse(summary.unchanged)
        val decisions = summary.decisions!!
        assertEquals(41250, decisions.total)
        assertEquals(41238, decisions.subscribed)
        assertEquals(1, decisions.rows.size)
        assertEquals(1, decisions.types["captcha"])
        val alerts = summary.alerts!!
        assertTrue(alerts.capped)
        assertEquals(true, alerts.rows.first().handled)
        assertNull(alerts.rows.last().handled)
        assertEquals("64500", alerts.rows.first().source.asNumber)
    }

    @Test
    fun `an unchanged answer decodes without blocks`() {
        val summary = json.decodeFromString<CsSummary>("""{"version":"abc","unchanged":true}""")
        assertTrue(summary.unchanged)
        assertNull(summary.decisions)
        assertNull(summary.alerts)
        assertEquals(CsSummaryStep.Unchanged, CrowdSecSummary.step(200, summary, "abc", null))
        assertTrue(CrowdSecSummary.step(200, summary, null, null) is CsSummaryStep.Failed)
    }

    @Test
    fun `an old server without the summary falls back to the legacy reads`() {
        assertEquals(CsSummaryStep.Legacy, CrowdSecSummary.step(404, null, null, null))
        assertEquals(CsSummaryStep.Legacy, CrowdSecSummary.step(404, null, "abc", "not found"))
    }

    @Test
    fun `a 503 that says not configured is not configured, other failures stay failures`() {
        assertEquals(
            CsSummaryStep.NotConfigured,
            CrowdSecSummary.step(503, null, null, "CrowdSec not configured"),
        )
        assertTrue(CrowdSecSummary.step(503, null, null, null) is CsSummaryStep.Failed)
        val failed = CrowdSecSummary.step(500, null, null, "boom") as CsSummaryStep.Failed
        assertEquals("boom", failed.message)
        assertEquals(500, failed.status)
    }

    @Test
    fun `a fresh summary becomes a snapshot that counts from the server`() {
        val summary = json.decodeFromString<CsSummary>(summaryJson)
        val step = CrowdSecSummary.step(200, summary, "older", null)
        assertTrue(step is CsSummaryStep.Fresh)
        val snapshot = CrowdSecSummary.snapshot(summary)
        assertTrue(snapshot.serverSearch)
        assertEquals("a1b2c3d4e5f60718", snapshot.version)
        assertEquals(41250, snapshot.decisionTotal)
        assertEquals(12, snapshot.ownBans)
        assertEquals(41238, snapshot.subscribedBans)
        assertEquals(41249, snapshot.typeCount("ban"))
        assertEquals("capi", snapshot.origins.first().origin)
        assertEquals(1, snapshot.ownRows.size)
        assertEquals(2, snapshot.alertList.size)
        assertEquals(500, snapshot.alertLimit)
        assertEquals(true, snapshot.alertsCapped)
    }

    @Test
    fun `handled comes from the server flag, not the banned rows`() {
        val snapshot = CrowdSecSummary.snapshot(json.decodeFromString<CsSummary>(summaryJson))
        val open = snapshot.alertList.first { it.ip == "9.9.9.9" }
        val banned = snapshot.alertList.first { it.ip == "1.1.1.1" }
        assertFalse(snapshot.handled(open))
        assertTrue(snapshot.handled(banned))
        val sources = CrowdSecAnalytics.sources(snapshot.alertList, snapshot::handled)
        assertEquals(1, sources.first { it.key == "9.9.9.9" }.open)
        assertEquals(0, sources.first { it.key == "1.1.1.1" }.open)
    }

    @Test
    fun `without a handled flag the banned ip set still decides`() {
        val snapshot = CrowdSecSnapshot(
            decisions = CsRead.Loaded(listOf(CsDecision(id = 1, value = "1.1.1.1", scope = "Ip"))),
        )
        assertTrue(snapshot.handled(CsAlert(source = CsSource(ip = "1.1.1.1"))))
        assertFalse(snapshot.handled(CsAlert(handled = true, simulated = true, source = CsSource(ip = "1.1.1.1"))))
        assertFalse(snapshot.serverSearch)
        assertEquals(1, snapshot.decisionTotal)
    }

    @Test
    fun `a failed decisions block never reports handled`() {
        val summary = CsSummary(
            version = "v",
            decisions = CsDecisionsSummary(ok = false, error = "LAPI 403"),
            alerts = json.decodeFromString<CsSummary>(summaryJson).alerts,
        )
        val snapshot = CrowdSecSummary.snapshot(summary)
        assertTrue(snapshot.decisions is CsRead.Failed)
        assertNull(snapshot.counts)
        assertFalse(snapshot.alertList.any { snapshot.handled(it) })
    }

    @Test
    fun `stored data from before the summary still decodes`() {
        val counts = json.decodeFromString<CsDecisionsSummary>("{}")
        assertEquals(0, counts.total)
        assertTrue(counts.origins.isEmpty())
        val alert = json.decodeFromString<CsAlert>("""{"scenario":"x","source":{"ip":"1.1.1.1"}}""")
        assertNull(alert.handled)
    }

    @Test
    fun `the search page decodes`() {
        val page = json.decodeFromString<CsDecisionPage>(
            """{"rows":[{"id":5,"value":"1.2.3.4","origin":"cscli"}],"total":61,"page":2,"pages":4,"per":20,
               "facet_totals":{"origin":61,"type":70}}""",
        )
        assertEquals(61, page.total)
        assertEquals(2, page.page)
        assertEquals(4, page.pages)
        assertEquals(70, page.facetTotals["type"])
        assertEquals("1.2.3.4", page.rows.single().value)
    }

    @Test
    fun `decision facets map to search params`() {
        val facets = CsFacets()
            .toggle(CsFacet.Origin, "subscribed")
            .toggle(CsFacet.Type, "ban")
            .toggle(CsFacet.Ip, "1.2.3.4")
            .toggle(CsFacet.Scenario, "crowdsecurity/ssh-bf")
            .toggle(CsFacet.Uri, "/admin")
        assertEquals(
            CsDecisionQuery(q = "capi", origin = "subscribed", type = "ban", ip = "1.2.3.4", scenario = "crowdsecurity/ssh-bf"),
            facets.decisionQuery("  CAPI "),
        )
        val bare = CsFacets().decisionQuery("")
        assertEquals(CsDecisionQuery(), bare)
        assertFalse(bare.filtered)
    }

    @Test
    fun `the feed appends the next page and restarts on page one`() {
        val feed = CsDecisionFeed(CsDecisionQuery(), "v", loading = true)
        val first = feed.accept(CsDecisionPage(rows = listOf(CsDecision(id = 1)), total = 3, page = 1, pages = 2))
        assertTrue(first.more)
        assertFalse(first.loading)
        val second = first.accept(CsDecisionPage(rows = listOf(CsDecision(id = 2)), total = 3, page = 2, pages = 2))
        assertEquals(listOf(1L, 2L), second.rows.map { it.id })
        assertFalse(second.more)
        val again = second.accept(CsDecisionPage(rows = listOf(CsDecision(id = 9)), total = 1, page = 1, pages = 1))
        assertEquals(listOf(9L), again.rows.map { it.id })
    }

    @Test
    fun `origin counts merge case and blanks`() {
        val origins = CrowdSecAnalytics.origins(mapOf("CAPI" to 2, "capi" to 3, "" to 1, "cscli" to 1))
        assertEquals("capi", origins.first().origin)
        assertEquals(5, origins.first().count)
        assertEquals(1, origins.first { it.origin == "other" }.count)
    }
}

class CrowdSecRoutesTest {

    private fun alert(ip: String, vararg meta: Pair<String, String>, handled: Boolean? = null) = CsAlert(
        scenario = "crowdsecurity/http-probing",
        handled = handled,
        source = CsSource(ip = ip),
        meta = meta.map { CsMetaEntry(it.first, it.second) },
    )

    @Test
    fun `the leaf router name wins over the chain name`() {
        val both = alert(
            "1.1.1.1",
            "traefik_router_name" to "websecure-chain@file",
            "traefik_router_name_leaf" to "[\"dashboard@file\"]",
        )
        assertEquals(listOf("dashboard@file"), both.routers)
        val plain = alert("1.1.1.1", "traefik_router_name" to "blog@docker")
        assertEquals(listOf("blog@docker"), plain.routers)
        assertTrue(alert("1.1.1.1").routers.isEmpty())
    }

    @Test
    fun `hosts come from target_fqdn`() {
        val hit = alert("1.1.1.1", "target_fqdn" to "[\"a.example.com\",\"b.example.com\"]")
        assertEquals(listOf("a.example.com", "b.example.com"), hit.hosts)
    }

    @Test
    fun `route and host facets filter the evidence`() {
        val hit = alert("1.1.1.1", "traefik_router_name" to "blog@docker", "target_fqdn" to "blog.example.com")
        fun match(facets: CsFacets) = facets.matches(hit, countryOf = { "" }, handled = { false })
        assertTrue(match(CsFacets().toggle(CsFacet.Router, "blog@docker")))
        assertFalse(match(CsFacets().toggle(CsFacet.Router, "api@docker")))
        assertTrue(match(CsFacets().toggle(CsFacet.Host, "blog.example.com")))
        assertFalse(match(CsFacets().toggle(CsFacet.Host, "api.example.com")))
    }

    @Test
    fun `route and host facets are alert only and labelled for people`() {
        assertEquals("route", CsFacet.Router.label)
        assertEquals("host", CsFacet.Host.label)
        assertEquals(CsReads.Alerts, CsFacet.Router.reads)
        assertEquals(CsReads.Alerts, CsFacet.Host.reads)
        assertEquals(CsDecisionQuery(), CsFacets().toggle(CsFacet.Router, "x").toggle(CsFacet.Host, "y").decisionQuery(""))
    }

    @Test
    fun `targeted routes rank open alerts first and drop the provider`() {
        val alerts = listOf(
            alert("1.1.1.1", "traefik_router_name" to "blog@docker", handled = true),
            alert("1.1.1.2", "traefik_router_name" to "blog@docker", handled = true),
            alert("2.2.2.2", "traefik_router_name" to "api@file", "target_fqdn" to "api.example.com", handled = false),
        )
        val snapshot = CrowdSecSnapshot(decisions = CsRead.Loaded(emptyList()), alerts = CsRead.Loaded(alerts))
        val routes = CrowdSecAnalytics.routes(alerts, snapshot::handled)
        assertEquals("api@file", routes.first().key)
        assertEquals("api", routes.first().label)
        assertEquals("api.example.com", routes.first().extra)
        assertEquals(1, routes.first().open)
        assertEquals(0, routes.last().open)
        assertEquals(2, routes.last().count)
        assertEquals(listOf("api.example.com"), CrowdSecAnalytics.hosts(alerts, snapshot::handled).map { it.key })
    }
}

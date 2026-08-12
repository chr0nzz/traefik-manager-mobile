package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.CertEntry
import dev.chr0nzz.traefikmanager.data.model.CertHealth
import dev.chr0nzz.traefikmanager.data.model.CertRows
import dev.chr0nzz.traefikmanager.data.model.CertsResponse
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.PluginUsage
import dev.chr0nzz.traefikmanager.data.model.PluginsResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertAndPluginTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val now = Instant.parse("2026-08-12T00:00:00Z").toEpochMilli()

    private fun daysOut(days: Long, hours: Long = 0): String =
        Instant.ofEpochMilli(now).plusSeconds(days * 86_400 + hours * 3_600).toString().replace(".000Z", "Z")

    @Test
    fun `expiry buckets match the web thresholds`() {
        assertEquals(CertHealth.Healthy, CertRows.healthOf(30))
        assertEquals(CertHealth.Expiring, CertRows.healthOf(29))
        assertEquals(CertHealth.Expiring, CertRows.healthOf(7))
        assertEquals(CertHealth.Critical, CertRows.healthOf(6))
        assertEquals(CertHealth.Critical, CertRows.healthOf(0))
        assertEquals(CertHealth.Critical, CertRows.healthOf(-4))
        assertEquals(CertHealth.Unknown, CertRows.healthOf(null))
    }

    @Test
    fun `days left rounds up like Math ceil`() {
        val partial = CertRows.row(CertEntry(main = "a", notAfter = daysOut(6, 5)), now)
        assertEquals(7, partial.daysLeft)

        val almostGone = CertRows.row(CertEntry(main = "a", notAfter = daysOut(0, 2)), now)
        assertEquals(1, almostGone.daysLeft)

        val justExpired = CertRows.row(CertEntry(main = "a", notAfter = daysOut(-1, 12)), now)
        assertEquals(0, justExpired.daysLeft)
        assertEquals("expired", justExpired.daysLeftLabel)
    }

    @Test
    fun `a missing or unparseable not_after is unknown, not expired`() {
        val missing = CertRows.row(CertEntry(main = "a"), now)
        assertNull(missing.daysLeft)
        assertNull(missing.daysLeftLabel)
        assertEquals(CertHealth.Unknown, missing.health)

        val broken = CertRows.row(CertEntry(main = "a", notAfter = "not-a-date"), now)
        assertNull(broken.daysLeft)
        assertEquals(CertHealth.Unknown, broken.health)
    }

    @Test
    fun `the main domain is removed from the SAN list and counted once`() {
        val row = CertRows.row(
            CertEntry(
                main = "example.com",
                sans = listOf("example.com", "www.example.com", "api.example.com"),
                notAfter = daysOut(40),
            ),
            now,
        )
        assertEquals(listOf("www.example.com", "api.example.com"), row.extraDomains)
        assertEquals(3, row.domainCount)
    }

    @Test
    fun `a single domain reports one domain`() {
        val row = CertRows.row(CertEntry(main = "example.com", notAfter = daysOut(40)), now)
        assertTrue(row.extraDomains.isEmpty())
        assertEquals(1, row.domainCount)
    }

    @Test
    fun `null main and non-string sans do not break decoding`() {
        val response = json.decodeFromString<CertsResponse>(
            """{"certs":[{"resolver":"le","main":null,"sans":["a.com",7,null],"not_after":null}]}""",
        )
        val entry = response.certs.single()
        assertEquals("", entry.main)
        assertEquals(listOf("a.com", "7", ""), entry.sans)

        val row = CertRows.row(entry, now)
        assertEquals("Unknown", row.title)
        assertEquals(listOf("a.com", "7"), row.extraDomains)
    }

    @Test
    fun `origin prefers source and falls back to the certFile basename`() {
        assertEquals("ovh.json", CertRows.row(CertEntry(main = "a", source = "ovh.json"), now).origin)
        assertEquals(
            "chain.pem",
            CertRows.row(CertEntry(main = "a", certFile = "/etc/traefik/certs/chain.pem"), now).origin,
        )
        assertNull(CertRows.row(CertEntry(main = "a"), now).origin)
    }

    @Test
    fun `rows sort by days left with unknowns last`() {
        val rows = CertRows.from(
            listOf(
                CertEntry(main = "far.com", notAfter = daysOut(90)),
                CertEntry(main = "unknown.com"),
                CertEntry(main = "soon.com", notAfter = daysOut(3)),
                CertEntry(main = "mid.com", notAfter = daysOut(20)),
            ),
            now,
        )
        assertEquals(listOf("soon.com", "mid.com", "far.com", "unknown.com"), rows.map { it.main })
    }

    @Test
    fun `the resolver falls back to a dash`() {
        assertEquals("-", CertRows.row(CertEntry(main = "a"), now).resolverLabel)
        assertEquals("letsencrypt", CertRows.row(CertEntry(main = "a", resolver = "letsencrypt"), now).resolverLabel)
    }

    @Test
    fun `plugin usage needs both a plugin key and the alias line`() {
        val middlewares = listOf(
            MiddlewareDef("bouncer", "http", "plugin:\n  crowdsec-bouncer:\n    enabled: true\n", "mw.yml"),
            MiddlewareDef("headers", "http", "headers:\n  frameDeny: true\n", "mw.yml"),
            MiddlewareDef("decoy", "http", "headers:\n  customRequestHeaders:\n    crowdsec-bouncer: yes\n", "mw.yml"),
        )
        assertEquals(listOf("bouncer"), PluginUsage.usersOf("crowdsec-bouncer", middlewares).map { it.name })
        assertEquals(0, PluginUsage.usersOf("geoblock", middlewares).size)
    }

    @Test
    fun `plugin names with regex characters are escaped`() {
        val middlewares = listOf(
            MiddlewareDef("a", "http", "plugin:\n  my.plugin:\n    x: 1\n", "mw.yml"),
            MiddlewareDef("b", "http", "plugin:\n  myXplugin:\n    x: 1\n", "mw.yml"),
        )
        assertEquals(listOf("a"), PluginUsage.usersOf("my.plugin", middlewares).map { it.name })
    }

    @Test
    fun `plugin version and module tolerate yaml numbers`() {
        val response = json.decodeFromString<PluginsResponse>(
            """{"plugins":[{"name":"p","moduleName":"github.com/x/y","version":1.2},
                           {"name":"q","moduleName":null,"version":null,"settings":null}]}""",
        )
        assertEquals("1.2", response.plugins[0].version)
        assertEquals("https://github.com/x/y", response.plugins[0].repoUrl)
        assertEquals("", response.plugins[1].moduleName)
        assertEquals("-", response.plugins[1].displayVersion)
        assertNull(response.plugins[1].repoUrl)
    }

    @Test
    fun `a non github module has no repository link`() {
        val response = json.decodeFromString<PluginsResponse>(
            """{"plugins":[{"name":"p","moduleName":"plugins-local/geoblock","version":"v1"}]}""",
        )
        assertNull(response.plugins.single().repoUrl)
    }

    @Test
    fun `the server error field is surfaced and plugins default to empty`() {
        val response = json.decodeFromString<PluginsResponse>(
            """{"plugins":[],"error":"STATIC_CONFIG_PATH not configured on this agent"}""",
        )
        assertTrue(response.plugins.isEmpty())
        assertEquals("STATIC_CONFIG_PATH not configured on this agent", response.error)
    }
}

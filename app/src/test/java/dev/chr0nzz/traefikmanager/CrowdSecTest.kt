package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CrowdSecMeta
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsMetaEntry
import dev.chr0nzz.traefikmanager.data.model.CsRead
import dev.chr0nzz.traefikmanager.data.model.CsSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdSecTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `meta values decode both the plain and the json-array encoding`() {
        val meta = listOf(
            CsMetaEntry("target_uri", """["/wp-login.php","/.env"]"""),
            CsMetaEntry("method", "GET"),
            CsMetaEntry("", "dropped"),
            CsMetaEntry("status", ""),
        )
        val parsed = CrowdSecMeta.parse(meta)
        assertEquals(listOf("/wp-login.php", "/.env"), parsed["target_uri"])
        assertEquals(listOf("GET"), parsed["method"])
        assertFalse(parsed.containsKey(""))
        assertFalse(parsed.containsKey("status"))
    }

    @Test
    fun `repeated meta keys accumulate`() {
        val parsed = CrowdSecMeta.parse(
            listOf(CsMetaEntry("target_uri", "/a"), CsMetaEntry("target_uri", "/b")),
        )
        assertEquals(listOf("/a", "/b"), parsed["target_uri"])
    }

    @Test
    fun `events_count survives being sent as a string`() {
        val alert = json.decodeFromString<CsAlert>("""{"scenario":"x","events_count":"12"}""")
        assertEquals(12, alert.eventsCount)

        val numeric = json.decodeFromString<CsAlert>("""{"scenario":"x","events_count":7}""")
        assertEquals(7, numeric.eventsCount)

        val missing = json.decodeFromString<CsAlert>("""{"scenario":"x"}""")
        assertEquals(0, missing.eventsCount)
    }

    @Test
    fun `as_number survives being sent as a number or null`() {
        val numeric = json.decodeFromString<CsAlert>("""{"source":{"as_number":12876}}""")
        assertEquals("12876", numeric.source.asNumber)

        val nulled = json.decodeFromString<CsAlert>("""{"source":{"as_number":null}}""")
        assertEquals("", nulled.source.asNumber)
    }

    @Test
    fun `only two-letter country codes are accepted`() {
        assertEquals("DE", CsAlert(source = CsSource(cn = "de")).countryCode)
        assertEquals("", CsAlert(source = CsSource(cn = "DEU")).countryCode)
        assertEquals("", CsAlert(source = CsSource()).countryCode)
    }

    @Test
    fun `origin decides whether a decision is ours`() {
        assertTrue(CsDecision(origin = "cscli").own)
        assertTrue(CsDecision(origin = "crowdsec").own)
        assertTrue(CsDecision(origin = "manual").own)
        assertFalse(CsDecision(origin = "CAPI").own)
        assertFalse(CsDecision(origin = "lists").own)
        assertEquals("capi", CsDecision(origin = "CAPI").originKey)
    }

    @Test
    fun `capi and lists scoped alerts are dropped before ranking`() {
        val alerts = listOf(
            CsAlert(scenario = "a", source = CsSource(ip = "1.1.1.1", scope = "Ip")),
            CsAlert(scenario = "b", source = CsSource(ip = "2.2.2.2", scope = "capi")),
            CsAlert(scenario = "c", source = CsSource(ip = "3.3.3.3", scope = "Lists")),
        )
        assertEquals(1, CrowdSecAnalytics.filterAlerts(alerts).size)
    }

    @Test
    fun `the ip-to-decision join is an exact string match`() {
        val snapshot = CrowdSecSnapshot(
            decisions = CsRead.Loaded(
                listOf(
                    CsDecision(id = 1, value = "1.1.1.1", scope = "Ip"),
                    CsDecision(id = 2, value = "10.0.0.0/8", scope = "Range"),
                    CsDecision(id = 3, value = "FR", scope = "Country"),
                ),
            ),
        )
        assertEquals(setOf("1.1.1.1", "10.0.0.0/8"), snapshot.bannedIps)
        assertTrue(snapshot.handled(CsAlert(source = CsSource(ip = "1.1.1.1"))))
        assertFalse(snapshot.handled(CsAlert(source = CsSource(ip = "10.0.0.5"))))
        assertFalse(
            snapshot.handled(CsAlert(simulated = true, source = CsSource(ip = "1.1.1.1"))),
        )
    }

    @Test
    fun `a failed decisions read never reports handled`() {
        val snapshot = CrowdSecSnapshot(decisions = CsRead.Failed("LAPI 502", 502))
        assertFalse(snapshot.handled(CsAlert(source = CsSource(ip = "1.1.1.1"))))
        assertFalse(snapshot.decisions.ok)
        assertTrue(snapshot.decisionList.isEmpty())
    }

    @Test
    fun `ranking is worst first - open sources before loud ones`() {
        val alerts = listOf(
            CsAlert(scenario = "s", eventsCount = 50, source = CsSource(ip = "banned.host")),
            CsAlert(scenario = "s", eventsCount = 50, source = CsSource(ip = "banned.host")),
            CsAlert(scenario = "s", eventsCount = 1, source = CsSource(ip = "open.host")),
        )
        val ranked = CrowdSecAnalytics.sources(alerts, banned = setOf("banned.host"))
        assertEquals("open.host", ranked.first().key)
        assertEquals(1, ranked.first().open)
        assertEquals(0, ranked.last().open)
        assertEquals(100, ranked.last().weight)
    }

    @Test
    fun `one alert naming several paths contributes a hit to each`() {
        val alert = CsAlert(
            scenario = "probing",
            source = CsSource(ip = "1.1.1.1"),
            meta = listOf(CsMetaEntry("target_uri", """["/a","/b","/a"]""")),
        )
        val ranked = CrowdSecAnalytics.paths(listOf(alert), emptySet())
        assertEquals(setOf("/a", "/b"), ranked.map { it.key }.toSet())
        assertEquals(1, ranked.first { it.key == "/a" }.count)
    }

    @Test
    fun `origin breakdown counts every decision source`() {
        val breakdown = CrowdSecAnalytics.origins(
            listOf(
                CsDecision(origin = "CAPI"),
                CsDecision(origin = "capi"),
                CsDecision(origin = "cscli"),
                CsDecision(origin = ""),
            ),
        )
        assertEquals("capi", breakdown.first().origin)
        assertEquals(2, breakdown.first().count)
        assertTrue(breakdown.any { it.origin == "other" })
    }

    @Test
    fun `percentages round like the web`() {
        assertEquals("50%", CrowdSecAnalytics.percent(1, 2))
        assertEquals("9.1%", CrowdSecAnalytics.percent(1, 11))
        assertEquals("0%", CrowdSecAnalytics.percent(1, 0))
    }

    @Test
    fun `decisions decode with lapi defaults`() {
        val decision = json.decodeFromString<CsDecision>(
            """{"id":42,"value":"1.2.3.4","duration":"3h57m11.351069171s","origin":"CAPI","scenario":"blocklist"}""",
        )
        assertEquals(42L, decision.id)
        assertEquals("ban", decision.type)
        assertEquals("Ip", decision.scope)
        assertEquals("3h57m11.351069171s", decision.duration)
    }

    @Test
    fun `alert keys fall back through uuid, id and index`() {
        assertEquals("abc", CsAlert(uuid = "abc").key(3))
        assertEquals("7", CsAlert(id = 7).key(3))
        assertEquals("cs3", CsAlert().key(3))
    }
}

class BannedIpsCostTest {

    private fun snapshot(decisions: Int): CrowdSecSnapshot = CrowdSecSnapshot(
        decisions = CsRead.Loaded(
            List(decisions) { CsDecision(id = it.toLong(), scope = "Ip", value = "10.0.0.$it") },
        ),
    )

    @Test
    fun `the banned set is built once, not once per alert`() {
        val snap = snapshot(500)
        val first = snap.bannedIps
        val second = snap.bannedIps
        assertSame(first, second)
    }

    @Test
    fun `it still contains what it should`() {
        val snap = CrowdSecSnapshot(
            decisions = CsRead.Loaded(
                listOf(
                    CsDecision(id = 1, scope = "Ip", value = "1.2.3.4"),
                    CsDecision(id = 2, scope = "Range", value = "10.0.0.0/8"),
                    CsDecision(id = 3, scope = "Country", value = "RU"),
                ),
            ),
        )
        assertEquals(setOf("1.2.3.4", "10.0.0.0/8"), snap.bannedIps)
    }

    @Test
    fun `an alert is handled when its ip is banned`() {
        val snap = CrowdSecSnapshot(
            decisions = CsRead.Loaded(listOf(CsDecision(id = 1, scope = "Ip", value = "1.2.3.4"))),
        )
        assertTrue(snap.handled(CsAlert(source = CsSource(ip = "1.2.3.4"))))
        assertFalse(snap.handled(CsAlert(source = CsSource(ip = "5.6.7.8"))))
    }
}

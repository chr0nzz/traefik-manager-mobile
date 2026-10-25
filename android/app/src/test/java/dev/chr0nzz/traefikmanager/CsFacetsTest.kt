package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsFacet
import dev.chr0nzz.traefikmanager.data.model.CsFacets
import dev.chr0nzz.traefikmanager.data.model.CsMetaEntry
import dev.chr0nzz.traefikmanager.data.model.CsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsFacetsTest {

    private val alert = CsAlert(
        scenario = "crowdsecurity/http-probing",
        source = CsSource(ip = "1.2.3.4", cn = "DE", asName = "Example AS", asNumber = "64500"),
        meta = listOf(
            CsMetaEntry("target_uri", "[\"/admin.php\"]"),
            CsMetaEntry("method", "[\"GET\"]"),
        ),
    )

    private fun match(facets: CsFacets, handled: Boolean = false) =
        facets.matches(alert, countryOf = { it.countryCode }, handled = { handled })

    @Test
    fun `a facet keeps only what carries its value`() {
        assertTrue(match(CsFacets().toggle(CsFacet.Ip, "1.2.3.4")))
        assertFalse(match(CsFacets().toggle(CsFacet.Ip, "9.9.9.9")))
        assertTrue(match(CsFacets().toggle(CsFacet.Asn, "64500")))
        assertFalse(match(CsFacets().toggle(CsFacet.Asn, "Example AS")))
    }

    @Test
    fun `facets apply together`() {
        val both = CsFacets().toggle(CsFacet.Country, "DE").toggle(CsFacet.Uri, "/admin.php")
        assertTrue(match(both))
        assertFalse(match(both.toggle(CsFacet.Verb, "POST")))
    }

    @Test
    fun `clicking the same value again lets it go`() {
        val once = CsFacets().toggle(CsFacet.Scenario, "crowdsecurity/http-probing")
        assertEquals("crowdsecurity/http-probing", once[CsFacet.Scenario])
        assertTrue(once.toggle(CsFacet.Scenario, "crowdsecurity/http-probing").isEmpty)
    }

    @Test
    fun `outcome reads the ban state, not the alert`() {
        val loose = CsFacets().toggle(CsFacet.Outcome, "loose")
        assertTrue(match(loose, handled = false))
        assertFalse(match(loose, handled = true))
    }

    @Test
    fun `a card leaves its own facet out so it does not collapse to one row`() {
        val facets = CsFacets().toggle(CsFacet.Country, "FR")
        assertFalse(match(facets))
        assertTrue(
            facets.matches(alert, countryOf = { it.countryCode }, handled = { false }, skip = CsFacet.Country),
        )
    }

    @Test
    fun `origin understands own and subscribed`() {
        val capi = CsDecision(value = "1.2.3.4", origin = "capi")
        val local = CsDecision(value = "1.2.3.4", origin = "crowdsec")
        assertTrue(CsFacets().toggle(CsFacet.Origin, "subscribed").matches(capi))
        assertFalse(CsFacets().toggle(CsFacet.Origin, "subscribed").matches(local))
        assertTrue(CsFacets().toggle(CsFacet.Origin, "own").matches(local))
        assertTrue(CsFacets().toggle(CsFacet.Origin, "capi").matches(capi))
    }

    @Test
    fun `ip and scenario read decisions as well as alerts`() {
        assertEquals(setOf(CsFacet.Ip, CsFacet.Scenario), CsFacet.entries.filter { it.reads.name == "Both" }.toSet())
    }
}

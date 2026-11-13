package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.TraefikAdvisories
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraefikAdvisoriesTest {

    @Test
    fun `the first matching advisory is reported, as on the web`() {
        assertEquals("CVE-2026-88007", TraefikAdvisories.firstAffecting("3.7.12")?.id)
        assertEquals("GHSA-qm9f-w54v-q2qh", TraefikAdvisories.firstAffecting("v3.7.13")?.id)
        assertEquals("GHSA-hrpp-p4fc-wwf8", TraefikAdvisories.firstAffecting("3.7.15")?.id)
        assertEquals("GHSA-hrpp-p4fc-wwf8", TraefikAdvisories.firstAffecting("2.11.59")?.id)
    }

    @Test
    fun `patched and unparseable versions report nothing`() {
        assertNull(TraefikAdvisories.firstAffecting("3.7.16"))
        assertNull(TraefikAdvisories.firstAffecting("2.11.60"))
        assertNull(TraefikAdvisories.firstAffecting("4.0.0"))
        assertNull(TraefikAdvisories.firstAffecting("dev"))
        assertNull(TraefikAdvisories.firstAffecting(null))
    }

    @Test
    fun `a dismissal hides the notice only for that version`() {
        val dismissed = mapOf("GHSA-hrpp-p4fc-wwf8" to "3.7.15")
        assertNull(TraefikAdvisories.notice("v3.7.15", emptyList(), dismissed))
        assertEquals("2.11.59", TraefikAdvisories.notice("2.11.59", emptyList(), dismissed)?.version)
    }

    @Test
    fun `forwardAuth raises the priority only for advisories that involve it`() {
        val auth = listOf(TraefikObject(name = "sso@file", type = "forwardauth"))
        assertTrue(TraefikAdvisories.notice("3.7.15", auth, emptyMap())!!.forwardAuthInUse)
        assertFalse(TraefikAdvisories.notice("3.7.13", auth, emptyMap())!!.forwardAuthInUse)
        assertFalse(TraefikAdvisories.notice("3.7.15", emptyList(), emptyMap())!!.forwardAuthInUse)
    }
}

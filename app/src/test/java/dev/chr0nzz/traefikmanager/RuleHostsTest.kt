package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleHostsTest {

    @Test
    fun `a negated host is not a host the route serves`() {
        val route = Route(rule = "Host(`app.example.com`) && !Host(`admin.example.com`)")
        assertEquals(listOf("app.example.com"), route.hosts)
    }

    @Test
    fun `space between the bang and Host still negates`() {
        assertTrue(Route(rule = "PathPrefix(`/`) && ! Host(`internal.example.com`)").hosts.isEmpty())
    }

    @Test
    fun `plain host lists are unchanged`() {
        val route = Route(rule = "Host(`a.example.com`) || Host(`b.example.com`)")
        assertEquals(listOf("a.example.com", "b.example.com"), route.hosts)
        assertTrue(route.isPlainHostRule)
    }
}

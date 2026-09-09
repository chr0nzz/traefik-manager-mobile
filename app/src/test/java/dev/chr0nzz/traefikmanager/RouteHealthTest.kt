package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteHealth
import dev.chr0nzz.traefikmanager.data.model.RouteHealthSnapshot
import dev.chr0nzz.traefikmanager.data.model.RouteServerTally
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.settings.RouteCheckIntervals
import dev.chr0nzz.traefikmanager.ui.routes.status
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteHealthTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun route(enabled: Boolean = true) =
        Route(id = "app", name = "app", target = "http://10.0.0.5:80", enabled = enabled)

    @Test
    fun `the server snapshot decodes with the keys it actually sends`() {
        val snapshot = json.decodeFromString<RouteHealthSnapshot>(
            """
            {"enabled":true,"interval":300,"checked_at":1788000000.5,
             "routes":{"app":{"state":"degraded","pending":false,"source":"servers",
                              "servers":{"up":1,"total":2},
                              "down_servers":["http://10.0.0.12:8080"],"at":1788000000.0}}}
            """.trimIndent(),
        )
        assertEquals(300, snapshot.interval)
        val health = snapshot.routes.getValue("app")
        assertEquals("degraded", health.state)
        assertEquals(RouteServerTally(up = 1, total = 2), health.servers)
        assertEquals(listOf("http://10.0.0.12:8080"), health.downServers)
    }

    @Test
    fun `a down route reads as an error, a degraded one as a warning`() {
        assertEquals(TmStatus.Error, route().status(RouteHealth(state = "down")))
        assertEquals(TmStatus.Warn, route().status(RouteHealth(state = "degraded")))
        assertEquals(TmStatus.Ok, route().status(RouteHealth(state = "up")))
    }

    @Test
    fun `a route not checked yet keeps the status it had before`() {
        assertEquals(TmStatus.Ok, route().status(RouteHealth(state = "pending")))
        assertEquals(TmStatus.Ok, route().status(null))
    }

    @Test
    fun `disabled still wins over any health the server reports`() {
        assertEquals(TmStatus.Disabled, route(enabled = false).status(RouteHealth(state = "up")))
        assertEquals(TmStatus.Disabled, route(enabled = false).status(RouteHealth(state = "down")))
    }

    @Test
    fun `a route with no backends is an error whatever the checker says`() {
        val empty = Route(id = "app", name = "app", target = "N/A")
        assertEquals(TmStatus.Error, empty.status(RouteHealth(state = "up")))
    }

    @Test
    fun `only checked states count as known`() {
        assertTrue(RouteHealth(state = "up").known)
        assertTrue(RouteHealth(state = "down").known)
        assertFalse(RouteHealth(state = "pending").known)
    }

    @Test
    fun `the summary says what the check found`() {
        assertEquals(
            "Degraded, 1 of 2 servers up",
            RouteHealth(state = "degraded", servers = RouteServerTally(1, 2)).summary,
        )
        assertEquals(
            "Down: Connection refused",
            RouteHealth(state = "down", error = "Connection refused").summary,
        )
        assertEquals("Reachable in 42ms", RouteHealth(state = "up", latencyMs = 42).summary)
        assertEquals("Reachable, this app", RouteHealth(state = "up", self = true).summary)
        assertEquals("Not checked yet", RouteHealth().summary)
    }
}

class RouteCheckIntervalTest {

    @Test
    fun `the intervals are the ones the server accepts`() {
        assertEquals(
            listOf(60, 300, 900, 1800),
            RouteCheckIntervals.options.map { it.first },
        )
    }

    @Test
    fun `each one reads as a duration`() {
        assertEquals("1 minute", RouteCheckIntervals.label(60))
        assertEquals("5 minutes", RouteCheckIntervals.label(300))
        assertEquals("30 minutes", RouteCheckIntervals.label(1800))
    }

    @Test
    fun `an interval the server invents still renders`() {
        assertEquals("45 seconds", RouteCheckIntervals.label(45))
    }
}

package dev.chr0nzz.traefikmanager

import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import dev.chr0nzz.traefikmanager.data.model.BackendHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceLoadBalancer
import dev.chr0nzz.traefikmanager.data.model.ServiceProtocol
import dev.chr0nzz.traefikmanager.data.model.ServiceRows
import dev.chr0nzz.traefikmanager.data.model.ServiceServer
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class BackendHealthTest {

    private fun row(statuses: Map<String, String>, checked: Boolean = true) = ServiceRows.row(
        TraefikService(
            name = "app-service@file",
            serverStatus = statuses,
            loadBalancer = ServiceLoadBalancer(
                servers = statuses.keys.map { ServiceServer(url = it) },
                healthCheck = if (checked) JsonObject(emptyMap()) else null,
            ),
        ),
        ServiceProtocol.Http,
    )

    @Test
    fun `the route info says how many backend servers answer`() {
        assertEquals("2 of 2 servers up", BackendHealth.summary(row(mapOf("http://a:80" to "UP", "http://b:80" to "UP"))).english())
        assertEquals("1 of 2 servers down", BackendHealth.summary(row(mapOf("http://a:80" to "UP", "http://b:80" to "DOWN"))).english())
        assertEquals("all 2 servers down", BackendHealth.summary(row(mapOf("http://a:80" to "DOWN", "http://b:80" to "DOWN"))).english())
    }

    @Test
    fun `nothing is claimed without a health check or a status`() {
        assertNull(BackendHealth.summary(row(mapOf("http://a:80" to "UP"), checked = false)).english())
        assertNull(BackendHealth.summary(row(emptyMap())).english())
        assertNull(BackendHealth.summary(null).english())
    }

    @Test
    fun `routes find their service by protocol and bare name`() {
        assertEquals("http:app-service", BackendHealth.key("http", "app-service@file"))
        assertEquals("tcp:db", BackendHealth.key("Tcp", "db"))
    }
}

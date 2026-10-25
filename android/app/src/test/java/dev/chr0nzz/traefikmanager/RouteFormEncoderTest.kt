package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.BackendServer
import dev.chr0nzz.traefikmanager.data.model.HealthCheckConfig
import dev.chr0nzz.traefikmanager.data.model.RouteForm
import dev.chr0nzz.traefikmanager.data.model.RouteFormEncoder
import dev.chr0nzz.traefikmanager.data.model.RouteProtocol
import dev.chr0nzz.traefikmanager.data.model.StickyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteFormEncoderTest {

    private fun List<Pair<String, String>>.valuesOf(name: String) =
        filter { it.first == name }.map { it.second }

    private fun List<Pair<String, String>>.valueOf(name: String) =
        firstOrNull { it.first == name }?.second

    private val httpForm = RouteForm(
        name = "app",
        protocol = RouteProtocol.Http,
        subdomain = "app",
        entryPoints = listOf("websecure"),
        middlewares = listOf("crowdsec", "secure-headers"),
        certResolver = "letsencrypt",
        backends = listOf(BackendServer(scheme = "http", host = "10.0.0.5", port = "8080")),
        configFile = "routes.yml",
    )

    @Test
    fun `http backend lands in repeated index 0`() {
        val fields = RouteFormEncoder.fields(httpForm, agentId = null)
        assertEquals(listOf("10.0.0.5", "", ""), fields.valuesOf("targetIp"))
        assertEquals(listOf("8080", "", ""), fields.valuesOf("targetPort"))
    }

    @Test
    fun `tcp backend lands in repeated index 1 with an empty slot before it`() {
        val fields = RouteFormEncoder.fields(
            httpForm.copy(protocol = RouteProtocol.Tcp, backends = listOf(BackendServer(host = "10.0.0.9", port = "5432"))),
            agentId = null,
        )
        assertEquals(listOf("", "10.0.0.9", ""), fields.valuesOf("targetIp"))
        assertEquals(listOf("", "5432", ""), fields.valuesOf("targetPort"))
    }

    @Test
    fun `udp backend lands in repeated index 2`() {
        val fields = RouteFormEncoder.fields(
            httpForm.copy(protocol = RouteProtocol.Udp, backends = listOf(BackendServer(host = "10.0.0.9", port = "51820"))),
            agentId = null,
        )
        assertEquals(listOf("", "", "10.0.0.9"), fields.valuesOf("targetIp"))
        assertEquals(listOf("", "", "51820"), fields.valuesOf("targetPort"))
    }

    @Test
    fun `entry points are sent as two slots, http first then tcp`() {
        val http = RouteFormEncoder.fields(httpForm, agentId = null).valuesOf("entryPoints")
        assertEquals(listOf("websecure", ""), http)

        val tcp = RouteFormEncoder.fields(
            httpForm.copy(protocol = RouteProtocol.Tcp, entryPoints = listOf("postgres")),
            agentId = null,
        ).valuesOf("entryPoints")
        assertEquals(listOf("", "postgres"), tcp)
    }

    @Test
    fun `udp uses its own entry point field`() {
        val fields = RouteFormEncoder.fields(
            httpForm.copy(protocol = RouteProtocol.Udp, entryPoints = listOf("wireguard")),
            agentId = null,
        )
        assertEquals("wireguard", fields.valueOf("udpEntryPoint"))
    }

    @Test
    fun `disabling TLS sends the disabled sentinel, not an empty resolver`() {
        val fields = RouteFormEncoder.fields(httpForm.copy(tlsEnabled = false), agentId = null)
        assertEquals(RouteForm.CERT_RESOLVER_DISABLED, fields.valuesOf("certResolver").first())
    }

    @Test
    fun `an empty resolver with TLS on sends the none sentinel`() {
        val fields = RouteFormEncoder.fields(httpForm.copy(certResolver = ""), agentId = null)
        assertEquals(RouteForm.CERT_RESOLVER_NONE, fields.valuesOf("certResolver").first())
    }

    @Test
    fun `the agent id travels in the body, not the proxy path`() {
        val fields = RouteFormEncoder.fields(httpForm, agentId = "agent-7")
        assertEquals("agent-7", fields.valueOf("agent_id"))
    }

    @Test
    fun `multi-backend, sticky and health check serialise into the backends json`() {
        val fields = RouteFormEncoder.fields(
            httpForm.copy(
                backends = listOf(
                    BackendServer(host = "10.0.0.5", port = "8080"),
                    BackendServer(host = "10.0.0.6", port = "8080"),
                ),
                sticky = StickyConfig(enabled = true, cookieName = "srv", secure = true),
                healthCheck = HealthCheckConfig(enabled = true, path = "/health", interval = "10s"),
                priority = 25,
            ),
            agentId = null,
        )
        val json = fields.valueOf("backendsJsonHttp").orEmpty()

        assertTrue(json.contains("10.0.0.5"))
        assertTrue(json.contains("10.0.0.6"))
        assertTrue(json.contains("\"cookieName\":\"srv\""))
        assertTrue(json.contains("\"path\":\"/health\""))
        assertTrue(json.contains("\"priority\":25"))
    }

    @Test
    fun `sticky and health check are omitted when disabled`() {
        val json = RouteFormEncoder.fields(httpForm, agentId = null).valueOf("backendsJsonHttp").orEmpty()
        assertTrue(json, !json.contains("\"sticky\""))
        assertTrue(json, !json.contains("\"healthCheck\""))
    }

    @Test
    fun `editing sends isEdit and the original id so a rename is safe`() {
        val fields = RouteFormEncoder.fields(
            httpForm.copy(isEdit = true, originalId = "routes.yml::app"),
            agentId = null,
        )
        assertEquals("true", fields.valueOf("isEdit"))
        assertEquals("routes.yml::app", fields.valueOf("originalId"))
    }

    @Test
    fun `udp never sends http-only fields`() {
        val fields = RouteFormEncoder.fields(httpForm.copy(protocol = RouteProtocol.Udp), agentId = null)
        assertFalse(fields.any { it.first == "middlewares" })
        assertFalse(fields.any { it.first == "passHostHeader" })
        assertFalse(fields.any { it.first == "backendsJsonHttp" })
    }

    @Test
    fun `validation requires a name and a backend host`() {
        assertFalse(RouteForm().isValid)
        assertFalse(httpForm.copy(name = "").isValid)
        assertFalse(httpForm.copy(backends = listOf(BackendServer())).isValid)
        assertTrue(httpForm.isValid)
    }

    @Test
    fun `tcp requires a port on every backend`() {
        val noPort = httpForm.copy(
            protocol = RouteProtocol.Tcp,
            backends = listOf(BackendServer(host = "10.0.0.9", port = "")),
        )
        assertFalse(noPort.isValid)
        assertTrue(noPort.copy(backends = listOf(BackendServer(host = "10.0.0.9", port = "5432"))).isValid)
    }
}

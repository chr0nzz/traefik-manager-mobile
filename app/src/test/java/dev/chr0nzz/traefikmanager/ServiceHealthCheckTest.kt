package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ServiceHealthDraft
import dev.chr0nzz.traefikmanager.data.model.ServiceLoadBalancer
import dev.chr0nzz.traefikmanager.data.model.ServicePayload
import dev.chr0nzz.traefikmanager.data.model.ServiceServer
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import dev.chr0nzz.traefikmanager.ui.services.ServiceChildDraft
import dev.chr0nzz.traefikmanager.ui.services.ServiceDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceHealthCheckTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `an existing health check reads back with every field`() {
        val hc = json.parseToJsonElement(
            """{"path":"/ready","interval":"10s","timeout":"3s","unhealthyInterval":"30s","method":"HEAD",
               "status":204,"scheme":"https","port":8443,"hostname":"app.internal","mode":"grpc",
               "followRedirects":false,"headers":{"X-Probe":"tm"}}""",
        )
        val service = TraefikService(
            name = "api@file",
            loadBalancer = ServiceLoadBalancer(servers = listOf(ServiceServer(url = "http://a:80")), healthCheck = hc),
        )
        val draft = ServiceDraft.of("api@file", service).healthCheck
        assertTrue(draft.enabled)
        assertEquals("204", draft.status)
        assertEquals("8443", draft.port)
        assertFalse(draft.followRedirects)
        assertEquals(listOf("X-Probe" to "tm"), draft.headers)
        assertFalse(ServiceHealthDraft.of(JsonObject(emptyMap())).enabled)
    }

    @Test
    fun `go durations read back in their short form like the web`() {
        assertEquals("1m", ServiceHealthDraft.shortDuration("1m0s"))
        assertEquals("1h30m", ServiceHealthDraft.shortDuration("1h30m0s"))
        assertEquals("2.5s", ServiceHealthDraft.shortDuration("2.5s"))
        assertEquals("500ms", ServiceHealthDraft.shortDuration("500ms"))
        assertEquals("0s", ServiceHealthDraft.shortDuration("0s"))
        assertEquals("soon", ServiceHealthDraft.shortDuration("soon"))
        val hc = json.parseToJsonElement("""{"path":"/","interval":"1m0s"}""")
        assertEquals("1m", ServiceHealthDraft.of(hc).interval)
    }

    @Test
    fun `only a load balancer sends its health check, and defaults stay off the wire`() {
        val draft = ServiceDraft(
            name = "api",
            children = listOf(ServiceChildDraft(address = "10.0.0.5:80")),
            healthCheck = ServiceHealthDraft(enabled = true, path = " /health ", headers = listOf("" to "x", "X-A" to "1")),
        )
        val payload = draft.payload()
        assertEquals("/health", payload.healthCheck?.path)
        assertEquals(mapOf("X-A" to "1"), payload.healthCheck?.headers)

        val body = json.encodeToJsonElement(ServicePayload.serializer(), payload).jsonObject["healthCheck"]!!.jsonObject
        assertEquals(setOf("enabled", "path", "headers"), body.keys)

        assertNull(draft.copy(type = "weighted").payload().healthCheck)
    }
}

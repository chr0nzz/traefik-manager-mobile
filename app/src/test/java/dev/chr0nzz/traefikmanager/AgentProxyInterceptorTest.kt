package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.api.AgentProxyInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AgentProxyInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun requestPath(agentId: String?, path: String): String {
        server.enqueue(MockResponse().setBody("{}"))
        val client = OkHttpClient.Builder().addInterceptor(AgentProxyInterceptor(agentId)).build()
        val url = server.url(path)
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return server.takeRequest().path.orEmpty()
    }

    @Test
    fun `no agent leaves the path untouched`() {
        assertEquals("/api/traefik/overview", requestPath(null, "/api/traefik/overview"))
    }

    @Test
    fun `traefik calls are proxied through the agent`() {
        assertEquals(
            "/api/agents/proxy/abc123/traefik/overview",
            requestPath("abc123", "/api/traefik/overview"),
        )
    }

    @Test
    fun `crowdsec and backups are proxied`() {
        assertEquals("/api/agents/proxy/a1/crowdsec/decisions", requestPath("a1", "/api/crowdsec/decisions"))
        assertEquals("/api/agents/proxy/a1/backups", requestPath("a1", "/api/backups"))
    }

    @Test
    fun `hub-only endpoints are never proxied`() {
        assertEquals("/api/agents", requestPath("a1", "/api/agents"))
        assertEquals("/api/routes", requestPath("a1", "/api/routes"))
        assertEquals("/api/settings", requestPath("a1", "/api/settings"))
    }

    @Test
    fun `subpath installs keep their prefix`() {
        assertEquals(
            "/tm/api/agents/proxy/a1/traefik/routers",
            requestPath("a1", "/tm/api/traefik/routers"),
        )
    }

    @Test
    fun `query parameters survive the rewrite`() {
        assertEquals(
            "/api/agents/proxy/a1/traefik/logs?lines=200",
            requestPath("a1", "/api/traefik/logs?lines=200"),
        )
    }

    @Test
    fun `the raw yaml editor is proxied so it never writes the host config`() {
        assertEquals(
            "/api/agents/proxy/a1/routes/routes.yml::media/raw",
            requestPath("a1", "/api/routes/routes.yml::media/raw"),
        )
    }

    @Test
    fun `the route list itself is not proxied - it has its own agent endpoint`() {
        assertEquals("/api/routes", requestPath("a1", "/api/routes"))
        assertEquals(
            "/api/routes/routes.yml::media/toggle",
            requestPath("a1", "/api/routes/routes.yml::media/toggle"),
        )
    }
}

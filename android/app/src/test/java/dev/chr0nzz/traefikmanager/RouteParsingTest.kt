package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RoutesResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun parse(body: String) = json.decodeFromString<RoutesResponse>(body)

    @Test
    fun `http route with boolean tls`() {
        val route = parse(
            """
            {"apps":[{"id":"routes.yml::app","name":"app","rule":"Host(`a.example.com`)",
            "service_name":"app","target":"http://10.0.0.5:8080","servers":["http://10.0.0.5:8080"],
            "middlewares":["crowdsec"],"entryPoints":["websecure"],"protocol":"http","tls":true,
            "enabled":true,"certResolver":"letsencrypt","serviceType":"loadBalancer","configFile":"routes.yml",
            "provider":"file","priority":null}],"middlewares":[],"configErrors":[]}
            """.trimIndent(),
        ).apps.first()

        assertTrue(route.tlsEnabled)
        assertEquals(listOf("crowdsec"), route.middlewareNames)
        assertEquals(listOf("websecure"), route.entryPointNames)
        assertEquals(listOf("a.example.com"), route.hosts)
        assertEquals(1, route.backendCount)
        assertEquals(null, route.priority)
    }

    @Test
    fun `tcp route with object tls counts as enabled`() {
        val route = parse(
            """
            {"apps":[{"id":"db","name":"db","rule":"HostSNI(`*`)","service_name":"db",
            "target":"10.0.0.9:5432","protocol":"tcp","tls":{"passthrough":true},"enabled":true}],
            "middlewares":[],"configErrors":[]}
            """.trimIndent(),
        ).apps.first()

        assertTrue(route.tlsEnabled)
    }

    @Test
    fun `udp route has false tls and no hosts`() {
        val route = parse(
            """
            {"apps":[{"id":"wg","name":"wg","rule":"","service_name":"wg","target":"10.0.0.9:51820",
            "protocol":"udp","tls":false,"enabled":true}],"middlewares":[],"configErrors":[]}
            """.trimIndent(),
        ).apps.first()

        assertFalse(route.tlsEnabled)
        assertTrue(route.hosts.isEmpty())
    }

    @Test
    fun `disabled route with unnormalized string lists`() {
        val route = parse(
            """
            {"apps":[{"id":"old","name":"old","rule":"Host(`old.example.com`)","service_name":"old",
            "target":"N/A","middlewares":"legacy-mw","entryPoints":"websecure","protocol":"http",
            "tls":true,"enabled":false}],"middlewares":[],"configErrors":[]}
            """.trimIndent(),
        ).apps.first()

        assertEquals(listOf("legacy-mw"), route.middlewareNames)
        assertEquals(listOf("websecure"), route.entryPointNames)
        assertEquals(0, route.backendCount)
        assertFalse(route.enabled)
    }

    @Test
    fun `multi host rule yields every host`() {
        val route = parse(
            """
            {"apps":[{"id":"multi","name":"multi",
            "rule":"Host(`a.example.com`) || Host(`b.example.com`)","service_name":"multi",
            "target":"http://10.0.0.5:80","protocol":"http","tls":true,"enabled":true}],
            "middlewares":[],"configErrors":[]}
            """.trimIndent(),
        ).apps.first()

        assertEquals(listOf("a.example.com", "b.example.com"), route.hosts)
    }

    @Test
    fun `missing optional keys fall back to defaults`() {
        val route = parse(
            """{"apps":[{"id":"bare","name":"bare"}],"middlewares":[],"configErrors":[]}"""
        ).apps.first()

        assertFalse(route.tlsEnabled)
        assertTrue(route.middlewareNames.isEmpty())
        assertEquals("http", route.protocol)
        assertTrue(route.enabled)
    }

    @Test
    fun `a plain host rule is recognised so the simple editor stays available`() {
        val single = Route(id = "a", name = "a", rule = "Host(`app.example.com`)")
        assertTrue(single.isPlainHostRule)

        val multi = Route(id = "b", name = "b", rule = "Host(`a.example.com`) || Host(`b.example.com`)")
        assertTrue(multi.isPlainHostRule)

        val advanced = Route(id = "c", name = "c", rule = "Host(`a.example.com`) && PathPrefix(`/api`)")
        assertFalse(advanced.isPlainHostRule)

        val ruleless = Route(id = "d", name = "d", rule = "")
        assertFalse(ruleless.isPlainHostRule)
    }
}

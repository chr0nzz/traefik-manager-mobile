package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.BackendKind
import dev.chr0nzz.traefikmanager.data.model.BackendMode
import dev.chr0nzz.traefikmanager.data.model.BackendServer
import dev.chr0nzz.traefikmanager.data.model.RouteForm
import dev.chr0nzz.traefikmanager.data.model.RouteFormEncoder
import dev.chr0nzz.traefikmanager.data.model.RouteProtocol
import dev.chr0nzz.traefikmanager.data.model.TcpTlsMode
import dev.chr0nzz.traefikmanager.ui.routes.RouteFormViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRoundTripTest {

    private val domains = listOf("xyzlab.app", "xyzlab.dev", "example.com")

    private fun encode(form: RouteForm, agentId: String? = null) =
        RouteFormEncoder.fields(form, agentId)

    private fun List<Pair<String, String>>.valuesOf(key: String) =
        filter { it.first == key }.map { it.second }

    private fun List<Pair<String, String>>.valueOf(key: String) =
        firstOrNull { it.first == key }?.second

    @Test
    fun `a host splits into subdomain and base domain, not the full host`() {
        val split = RouteFormViewModel.splitHosts(listOf("anchor.xyzlab.app"), domains)
        assertEquals("anchor", split?.subdomain)
        assertEquals(listOf("xyzlab.app"), split?.domains)
    }

    @Test
    fun `the same subdomain across several domains keeps one subdomain and many domains`() {
        val split = RouteFormViewModel.splitHosts(
            listOf("bin.xyzlab.dev", "bin.xyzlab.app"),
            domains,
        )
        assertEquals("bin", split?.subdomain)
        assertEquals(listOf("xyzlab.dev", "xyzlab.app"), split?.domains)
    }

    @Test
    fun `a bare domain host has an empty subdomain`() {
        val split = RouteFormViewModel.splitHosts(listOf("example.com"), domains)
        assertEquals("", split?.subdomain)
        assertEquals(listOf("example.com"), split?.domains)
    }

    @Test
    fun `an unknown domain refuses to split so the rule stays verbatim`() {
        assertNull(RouteFormViewModel.splitHosts(listOf("app.somewhere-else.net"), domains))
    }

    @Test
    fun `differing subdomains refuse to split`() {
        assertNull(RouteFormViewModel.splitHosts(listOf("a.xyzlab.app", "b.xyzlab.app"), domains))
    }

    @Test
    fun `the longest matching domain wins`() {
        val split = RouteFormViewModel.splitHosts(
            listOf("app.internal.example.com"),
            listOf("example.com", "internal.example.com"),
        )
        assertEquals("app", split?.subdomain)
        assertEquals(listOf("internal.example.com"), split?.domains)
    }

    @Test
    fun `encoding a simple http route posts subdomain plus base domain`() {
        val fields = encode(
            RouteForm(
                name = "anchor",
                subdomain = "anchor",
                domains = listOf("xyzlab.app"),
                backends = listOf(dev.chr0nzz.traefikmanager.data.model.BackendServer(host = "10.0.0.5", port = "3000")),
            ),
        )
        assertEquals("anchor", fields.valueOf("subdomain"))
        assertEquals(listOf("xyzlab.app"), fields.valuesOf("domains"))
        assertEquals("", fields.valueOf("httpRule"))
    }

    @Test
    fun `an advanced rule suppresses subdomain and domains`() {
        val fields = encode(
            RouteForm(
                name = "custom",
                advancedRule = true,
                httpRule = "Host(`a.example.com`) && PathPrefix(`/api`)",
                subdomain = "leftover",
                domains = listOf("example.com"),
                backends = listOf(dev.chr0nzz.traefikmanager.data.model.BackendServer(host = "10.0.0.5", port = "80")),
            ),
        )
        assertEquals("", fields.valueOf("subdomain"))
        assertTrue(fields.valuesOf("domains").isEmpty())
        assertEquals("Host(`a.example.com`) && PathPrefix(`/api`)", fields.valueOf("httpRule"))
    }

    @Test
    fun `positional target fields always occupy all three slots`() {
        val tcp = encode(
            RouteForm(
                name = "db",
                protocol = RouteProtocol.Tcp,
                backends = listOf(dev.chr0nzz.traefikmanager.data.model.BackendServer(host = "10.0.0.9", port = "5432")),
            ),
        )
        assertEquals(listOf("", "10.0.0.9", ""), tcp.valuesOf("targetIp"))
        assertEquals(listOf("", "5432", ""), tcp.valuesOf("targetPort"))

        val udp = encode(
            RouteForm(
                name = "wg",
                protocol = RouteProtocol.Udp,
                backends = listOf(dev.chr0nzz.traefikmanager.data.model.BackendServer(host = "10.0.0.9", port = "51820")),
            ),
        )
        assertEquals(listOf("", "", "10.0.0.9"), udp.valuesOf("targetIp"))
    }

    @Test
    fun `disabling TLS sends the disabled sentinel, enabling with no resolver sends none`() {
        val off = encode(RouteForm(name = "x", tlsEnabled = false, backends = listOf(backend())))
        assertEquals(RouteForm.CERT_RESOLVER_DISABLED, off.valuesOf("certResolver").first())

        val noResolver = encode(RouteForm(name = "x", tlsEnabled = true, backends = listOf(backend())))
        assertEquals(RouteForm.CERT_RESOLVER_NONE, noResolver.valuesOf("certResolver").first())

        val withResolver = encode(
            RouteForm(name = "x", tlsEnabled = true, certResolver = "cloudflare", backends = listOf(backend())),
        )
        assertEquals("cloudflare", withResolver.valuesOf("certResolver").first())
    }

    @Test
    fun `tcp passthrough posts passthrough rather than termination`() {
        val fields = encode(
            RouteForm(
                name = "db",
                protocol = RouteProtocol.Tcp,
                tcpTlsMode = TcpTlsMode.Passthrough,
                backends = listOf(backend(port = "5432")),
            ),
        )
        assertEquals("true", fields.valueOf("useTls"))
        assertEquals("true", fields.valueOf("tlsPassthrough"))
    }

    @Test
    fun `sticky and health check survive the encode when enabled`() {
        val fields = encode(
            RouteForm(
                name = "app",
                backends = listOf(backend()),
                sticky = dev.chr0nzz.traefikmanager.data.model.StickyConfig(
                    enabled = true,
                    cookieName = "tm_session",
                ),
                healthCheck = dev.chr0nzz.traefikmanager.data.model.HealthCheckConfig(
                    enabled = true,
                    path = "/health",
                    interval = "30s",
                ),
            ),
        )
        val payload = fields.valueOf("backendsJsonHttp").orEmpty()
        assertTrue(payload.contains("tm_session"))
        assertTrue(payload.contains("/health"))
        assertTrue(payload.contains("30s"))
    }

    @Test
    fun `an untouched form omits sticky and health check instead of nulling them`() {
        val payload = encode(RouteForm(name = "app", backends = listOf(backend())))
            .valueOf("backendsJsonHttp").orEmpty()
        assertTrue(payload, !payload.contains("\"sticky\""))
        assertTrue(payload, !payload.contains("\"healthCheck\""))
    }

    @Test
    fun `the backend scheme comes from the first backend row`() {
        val fields = encode(
            RouteForm(
                name = "app",
                backends = listOf(backend().copy(scheme = "https")),
            ),
        )
        assertEquals("https", fields.valueOf("scheme"))
    }

    @Test
    fun `a shared service posts serviceRef and no backend payload`() {
        val fields = encode(
            RouteForm(
                name = "app",
                backendMode = BackendMode.ExistingService,
                serviceRef = "shared-service",
            ),
        )
        assertEquals("shared-service", fields.valueOf("serviceRef"))
        assertNull(fields.valueOf("backendsJsonHttp"))
        assertEquals(listOf("", "", ""), fields.valuesOf("targetIp"))
    }

    @Test
    fun `streaming forces pass host header on`() {
        val fields = encode(
            RouteForm(
                name = "media",
                backends = listOf(backend()),
                passHostHeader = false,
                streamingPresent = true,
                streamingEnabled = true,
            ),
        )
        assertEquals("true", fields.valueOf("passHostHeader"))
        assertEquals("true", fields.valueOf("streamingPresetEnabled"))
    }

    @Test
    fun `a hand-edited headers preset is not overwritten`() {
        val fields = encode(
            RouteForm(
                name = "app",
                backends = listOf(backend()),
                headersPreset = dev.chr0nzz.traefikmanager.data.model.HeadersPresetForm(
                    present = true,
                    enabled = true,
                    custom = true,
                ),
            ),
        )
        assertEquals("true", fields.valueOf("headersPresetCustom"))
        assertNull(fields.valueOf("hp_hsts"))
    }

    @Test
    fun `a route with no preset posts present=false so the server leaves it alone`() {
        val fields = encode(RouteForm(name = "app", backends = listOf(backend())))
        assertEquals("false", fields.valueOf("headersPresetPresent"))
        assertNull(fields.valueOf("headersPresetEnabled"))
    }

    @Test
    fun `wildcard fields only go out when the wildcard is on`() {
        val off = encode(RouteForm(name = "app", backends = listOf(backend()), tlsMainDomain = "example.com"))
        assertNull(off.valueOf("tlsWildcardMain"))

        val on = encode(
            RouteForm(
                name = "app",
                backends = listOf(backend()),
                wildcardEnabled = true,
                tlsMainDomain = "example.com",
                tlsSans = listOf("*.example.com"),
            ),
        )
        assertEquals("example.com", on.valueOf("tlsWildcardMain"))
        assertEquals("*.example.com", on.valueOf("tlsWildcardSans"))
    }

    @Test
    fun `backend urls keep their port and path`() {
        val plain = RouteFormViewModel.parseBackend("http://10.0.0.5:8080")
        assertEquals("10.0.0.5", plain?.host)
        assertEquals("8080", plain?.port)

        val withPath = RouteFormViewModel.parseBackend("http://10.0.0.5:8080/base")
        assertEquals("10.0.0.5:8080/base", withPath?.host)
        assertEquals("", withPath?.port)

        val https = RouteFormViewModel.parseBackend("https://backend.internal:8443")
        assertEquals("https", https?.scheme)
        assertEquals("8443", https?.port)

        assertNull(RouteFormViewModel.parseBackend("N/A"))
    }

    @Test
    fun `validation explains what is missing`() {
        assertEquals("A route name is required.", RouteForm().validationError)
        assertEquals(
            "A backend host is required.",
            RouteForm(name = "app").validationError,
        )
        assertEquals(
            "TCP routes need a backend port.",
            RouteForm(
                name = "db",
                protocol = RouteProtocol.Tcp,
                backends = listOf(backend(port = "")),
            ).validationError,
        )
        assertEquals(
            "Pick the service this route points at.",
            RouteForm(name = "app", backendMode = BackendMode.ExistingService).validationError,
        )
        assertEquals(
            "Pick a service for every service backend.",
            RouteForm(
                name = "app",
                backends = listOf(BackendServer(kind = BackendKind.SERVICE)),
            ).validationError,
        )
        assertNull(
            RouteForm(name = "app", backends = listOf(backend())).validationError,
        )
    }

    @Test
    fun `a non-loadBalancer service is savable without backends`() {
        val form = RouteForm(name = "app", serviceType = "weighted", backends = emptyList())
        assertNull(form.validationError)
        assertNull(encode(form).valueOf("backendsJsonHttp"))
    }

    private fun backend(host: String = "10.0.0.5", port: String = "8080") =
        dev.chr0nzz.traefikmanager.data.model.BackendServer(host = host, port = port)
}

class CompositeRoutePayloadTest {

    private fun encode(form: RouteForm) = RouteFormEncoder.fields(form, null)

    private fun valueOf(form: RouteForm, key: String) =
        encode(form).firstOrNull { it.first == key }?.second

    private fun backendsJson(form: RouteForm) =
        valueOf(form, "backendsJsonHttp")?.let { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun `a plain load balancer sends no children key at all`() {
        val form = RouteForm(
            name = "app",
            backends = listOf(BackendServer(host = "10.0.0.10", port = "80")),
        )
        val payload = backendsJson(form)
        assertNotNull(payload)
        assertNull(payload!!["children"])
        assertNull(payload["compositeType"])
    }

    @Test
    fun `a weighted mix posts children and keeps servers to the address rows`() {
        val form = RouteForm(
            name = "app",
            compositeType = "weighted",
            backends = listOf(
                BackendServer(host = "10.0.0.10", port = "80", share = "9"),
                BackendServer(kind = BackendKind.SERVICE, serviceName = "canary", share = "1"),
            ),
        )
        val payload = backendsJson(form)!!
        assertEquals("weighted", payload["compositeType"]!!.jsonPrimitive.content)
        val children = payload["children"]!!.jsonArray
        assertEquals(2, children.size)
        assertEquals("manual", children[0].jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("10.0.0.10:80", children[0].jsonObject["address"]!!.jsonPrimitive.content)
        assertEquals(9, children[0].jsonObject["weight"]!!.jsonPrimitive.content.toInt())
        assertEquals("service", children[1].jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("canary", children[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(1, payload["servers"]!!.jsonArray.size)
    }

    @Test
    fun `mirroring carries the share as a percent`() {
        val form = RouteForm(
            name = "app",
            compositeType = "mirroring",
            backends = listOf(
                BackendServer(host = "10.0.0.10", port = "80", share = "0"),
                BackendServer(kind = BackendKind.SERVICE, serviceName = "shadow", share = "10"),
            ),
        )
        val children = backendsJson(form)!!["children"]!!.jsonArray
        assertEquals(10, children[1].jsonObject["percent"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `an unchanged composite is preserved rather than flattened`() {
        val form = RouteForm(
            name = "app",
            serviceType = "weighted",
            serviceOwned = true,
            wasComposite = true,
            compositeType = "weighted",
            backends = listOf(BackendServer(kind = BackendKind.SERVICE, serviceName = "a", share = "1")),
        )
        val children = backendsJson(form)!!["children"]!!.jsonArray
        assertEquals(1, children.size)
    }

    @Test
    fun `choosing load balanced on a composite sends the empty list that flattens it`() {
        val form = RouteForm(
            name = "app",
            serviceType = "weighted",
            serviceOwned = true,
            wasComposite = true,
            compositeType = RouteForm.LOAD_BALANCER,
            backends = listOf(BackendServer(host = "10.0.0.10", port = "80")),
        )
        val payload = backendsJson(form)!!
        assertEquals(0, payload["children"]!!.jsonArray.size)
        assertNull(payload["compositeType"])
    }

    @Test
    fun `a route that was never composite never sends an empty children list`() {
        val form = RouteForm(
            name = "app",
            wasComposite = false,
            compositeType = RouteForm.LOAD_BALANCER,
            backends = listOf(BackendServer(host = "10.0.0.10", port = "80")),
        )
        assertNull(backendsJson(form)!!["children"])
    }

    @Test
    fun `an owned composite is editable rather than read only`() {
        val form = RouteForm(name = "app", serviceType = "weighted", serviceOwned = true)
        assertTrue(form.isManagedService)
    }

    @Test
    fun `a composite nobody manages stays read only`() {
        val form = RouteForm(name = "app", serviceType = "weighted", serviceOwned = false)
        assertFalse(form.isManagedService)
    }
}

class BackendModeTest {

    private fun encode(form: RouteForm) = RouteFormEncoder.fields(form, null)

    private fun valueOf(form: RouteForm, key: String) =
        encode(form).firstOrNull { it.first == key }?.second

    @Test
    fun `a tcp route can still point at an existing service`() {
        val form = RouteForm(
            name = "db",
            protocol = RouteProtocol.Tcp,
            backendMode = BackendMode.ExistingService,
            serviceRef = "db-service",
        )
        assertEquals("db-service", valueOf(form, "serviceRef"))
        assertNull(valueOf(form, "backendsJsonTcp"))
        assertNull(form.validationError)
    }

    @Test
    fun `a udp route can still point at an existing service`() {
        val form = RouteForm(
            name = "dns",
            protocol = RouteProtocol.Udp,
            backendMode = BackendMode.ExistingService,
            serviceRef = "dns-service",
        )
        assertEquals("dns-service", valueOf(form, "serviceRef"))
        assertNull(valueOf(form, "backendsJsonUdp"))
    }

    @Test
    fun `a service row on its own still authors a weighted service`() {
        val form = RouteForm(
            name = "app",
            backends = listOf(BackendServer(kind = BackendKind.SERVICE, serviceName = "canary")),
        )
        val payload = Json.parseToJsonElement(valueOf(form, "backendsJsonHttp")!!).jsonObject
        assertEquals("weighted", payload["compositeType"]!!.jsonPrimitive.content)
        assertEquals(1, payload["children"]!!.jsonArray.size)
    }
}

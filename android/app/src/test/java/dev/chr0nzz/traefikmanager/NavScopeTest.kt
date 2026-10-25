package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ApiForm
import dev.chr0nzz.traefikmanager.data.model.EntrypointForm
import dev.chr0nzz.traefikmanager.data.model.LogForm
import dev.chr0nzz.traefikmanager.data.model.ObservabilityForm
import dev.chr0nzz.traefikmanager.data.model.ProvidersForm
import dev.chr0nzz.traefikmanager.data.model.ResolverForm
import dev.chr0nzz.traefikmanager.data.model.StaticEntries
import dev.chr0nzz.traefikmanager.data.model.SystemForm
import dev.chr0nzz.traefikmanager.data.store.decodeScopes
import dev.chr0nzz.traefikmanager.data.store.encodeScopes
import org.junit.Assert.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

class NavScopeTest {

    @Test
    fun `a key containing the separator round-trips`() {
        val scopes = mapOf("host|rail" to listOf("home", "routes", "services"))
        assertEquals(scopes, decodeScopes(encodeScopes(scopes)))
    }

    @Test
    fun `layouts of one server stay apart`() {
        val scopes = mapOf(
            "host|bar" to listOf("home", "routes"),
            "host|rail" to listOf("home", "routes", "services", "logs"),
        )
        val decoded = decodeScopes(encodeScopes(scopes))
        assertEquals(listOf("home", "routes"), decoded["host|bar"])
        assertEquals(listOf("home", "routes", "services", "logs"), decoded["host|rail"])
    }

    @Test
    fun `servers stay apart`() {
        val scopes = mapOf(
            "host|rail" to listOf("home"),
            "agent-7|rail" to listOf("routes"),
        )
        assertEquals(scopes, decodeScopes(encodeScopes(scopes)))
    }

    @Test
    fun `an empty pick is kept, not dropped`() {
        val scopes = mapOf("host|bar" to emptyList<String>())
        val decoded = decodeScopes(encodeScopes(scopes))
        assertTrue(decoded.containsKey("host|bar"))
        assertEquals(emptyList<String>(), decoded["host|bar"])
    }

    @Test
    fun `nothing stored reads as nothing`() {
        assertEquals(emptyMap<String, List<String>>(), decodeScopes(null))
        assertEquals(emptyMap<String, List<String>>(), decodeScopes(""))
    }

    @Test
    fun `junk decodes to nothing rather than throwing`() {
        assertEquals(emptyMap<String, List<String>>(), decodeScopes("host|rail|home,routes"))
        assertEquals(emptyMap<String, List<String>>(), decodeScopes("{not json"))
    }
}

class StaticSectionFormTest {

    private val config = kotlinx.serialization.json.Json.parseToJsonElement(
        """
        {
          "entryPoints": {
            "websecure": {
              "address": ":443",
              "asDefault": true,
              "http3": {},
              "http": {
                "tls": {"certResolver": "cloudflare"},
                "middlewares": ["secure-headers@file", "rate-limit@file"],
                "underscoreHeadersStrategy": "delete",
                "redirections": {"entryPoint": {"to": "websecure"}}
              },
              "forwardedHeaders": {"trustedIPs": ["10.0.0.0/8", "172.16.0.0/12"]},
              "transport": {"respondingTimeouts": {"readTimeout": "60s"}}
            }
          },
          "certificatesResolvers": {
            "cloudflare": {
              "acme": {
                "email": "me@example.com",
                "storage": "/acme.json",
                "dnsChallenge": {"provider": "cloudflare", "propagation": {"delayBeforeChecks": "30s"}}
              }
            }
          },
          "api": {"dashboard": true, "insecure": true},
          "log": {"level": "info", "format": "json", "filePath": "/var/log/traefik.log"},
          "accessLog": {"filePath": "/var/log/access.log", "filters": {"statusCodes": ["400-499", "500"]}},
          "metrics": {"prometheus": {"addRoutersLabels": true}},
          "tracing": {"serviceName": "traefik"},
          "global": {"sendAnonymousUsage": true},
          "serversTransport": {"insecureSkipVerify": true, "rootCAs": ["/certs/ca.pem"]},
          "providers": {"docker": {"exposedByDefault": false}, "file": {"directory": "/dyn"}, "redis": {}},
          "experimental": {
            "plugins": {"crowdsec": {"moduleName": "github.com/x/y", "version": "v1.0.0"}},
            "localPlugins": {"mine": {"moduleName": "local/mine"}}
          }
        }
        """.trimIndent(),
    ) as kotlinx.serialization.json.JsonObject

    @Test
    fun `an entrypoint reads every field the form shows`() {
        val entry = StaticEntries.entrypoints(config).first()
        val form = EntrypointForm.read(entry.first, entry.second)
        assertEquals("websecure", form.name)
        assertEquals(":443", form.address)
        assertEquals("websecure", form.redirectTo)
        assertEquals(true, form.http3)
        assertEquals("delete", form.underscoreHeaders)
        assertEquals("10.0.0.0/8\n172.16.0.0/12", form.trustedIps)
        assertEquals("secure-headers@file, rate-limit@file", form.middlewares)
        assertEquals(true, form.tlsEnabled)
        assertEquals("cloudflare", form.tlsCertResolver)
        assertEquals(true, form.asDefault)
        assertEquals("60s", form.readTimeout)
    }

    @Test
    fun `an entrypoint payload uses the server's keys`() {
        val data = EntrypointForm(name = "web", address = ":80", http3 = true).data()
        assertEquals(":80", (data["address"] as JsonPrimitive).content)
        assertEquals("true", (data["http3"] as JsonPrimitive).content)
        assertTrue(data.containsKey("forwarded_insecure"))
        assertTrue(data.containsKey("proxy_trusted_ips"))
        assertTrue(data.containsKey("tls_enabled"))
        assertTrue(data.containsKey("as_default"))
        assertTrue(data.containsKey("read_timeout"))
    }

    @Test
    fun `a resolver reads its challenge and propagation`() {
        val entry = StaticEntries.resolvers(config).first()
        val form = ResolverForm.read(entry.first, entry.second)
        assertEquals("dnsChallenge", form.challengeType)
        assertEquals("cloudflare", form.provider)
        assertEquals("me@example.com", form.email)
        assertEquals("30s", form.dnsDelay)
    }

    @Test
    fun `api reads presence rather than a flag`() {
        val form = ApiForm.read(config)
        assertEquals(true, form.enabled)
        assertEquals(true, form.dashboard)
        assertEquals(true, form.insecure)
        assertEquals(false, form.debug)
    }

    @Test
    fun `log folds the access log in`() {
        val form = LogForm.read(config)
        assertEquals("INFO", form.level)
        assertEquals("json", form.logFormat)
        assertEquals("/var/log/traefik.log", form.logFile)
        assertEquals(true, form.accessLog)
        assertEquals("400-499, 500", form.alStatusCodes)
    }

    @Test
    fun `observability defaults follow Traefik's own`() {
        val form = ObservabilityForm.read(config)
        assertEquals(false, form.ping)
        assertEquals(true, form.prometheus)
        assertEquals(true, form.promEpLabels)
        assertEquals(true, form.promRouterLabels)
        assertEquals("traefik", form.traceService)
    }

    @Test
    fun `system reads global and servers transport`() {
        val form = SystemForm.read(config)
        assertEquals(true, form.checkNewVersion)
        assertEquals(true, form.sendUsage)
        assertEquals(true, form.stInsecure)
        assertEquals("/certs/ca.pem", form.stRootCAs)
    }

    @Test
    fun `providers separate the two cards from the rest`() {
        val providers = config["providers"] as kotlinx.serialization.json.JsonObject
        val form = ProvidersForm.read(providers)
        assertEquals(true, form.docker)
        assertEquals(false, form.dockerExposedByDefault)
        assertEquals("/dyn", form.fileDirectory)
        assertEquals(listOf("redis"), ProvidersForm.others(providers))
    }

    @Test
    fun `plugins list both maps and mark the local ones`() {
        val plugins = StaticEntries.plugins(config)
        assertEquals(2, plugins.size)
        assertEquals(Triple("crowdsec", "github.com/x/y", false), plugins[0])
        assertEquals(Triple("mine", "local/mine", true), plugins[1])
    }
}

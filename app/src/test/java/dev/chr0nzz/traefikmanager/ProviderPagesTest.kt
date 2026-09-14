package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Agent
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.ProviderPage
import dev.chr0nzz.traefikmanager.data.model.ProviderProtocol
import dev.chr0nzz.traefikmanager.data.model.ProviderRows
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceLoadBalancer
import dev.chr0nzz.traefikmanager.data.model.ServiceServer
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import dev.chr0nzz.traefikmanager.data.repo.ServerCapabilities
import dev.chr0nzz.traefikmanager.ui.nav.TmDestination
import dev.chr0nzz.traefikmanager.ui.nav.TmSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPagesTest {

    private val routers = ProtoEnvelope(
        http = listOf(
            TraefikObject(name = "whoami@docker", provider = "docker", status = "enabled", rule = "Host(`who.example.com`)", service = "whoami"),
            TraefikObject(name = "broken@docker", status = "disabled", rule = "PathPrefix(`/x`)"),
            TraefikObject(name = "app@kubernetescrd", provider = "kubernetescrd", status = "enabled"),
            TraefikObject(name = "ing@kubernetesingress", provider = "kubernetesingress", status = "enabled"),
            TraefikObject(name = "api@internal", provider = "internal", status = "enabled", service = "api@internal"),
            TraefikObject(name = "mine@file", provider = "file", status = "enabled"),
            TraefikObject(name = "legacy@file", provider = "file", status = "enabled", tls = JsonObject(emptyMap())),
        ),
        tcp = listOf(TraefikObject(name = "db@docker", provider = "docker", status = "enabled", rule = "HostSNI(`*`)")),
    )

    @Test
    fun `each page lists only its own providers, kubernetes covering every flavour`() {
        assertEquals(
            listOf("broken@docker", "db@docker", "whoami@docker"),
            ProviderRows.routes(ProviderPage.Docker, routers, null).map { it.name },
        )
        assertEquals(
            listOf("app@kubernetescrd", "ing@kubernetesingress"),
            ProviderRows.routes(ProviderPage.Kubernetes, routers, null).map { it.name },
        )
        assertEquals(listOf("api@internal"), ProviderRows.routes(ProviderPage.Internal, routers, null).map { it.name })
    }

    @Test
    fun `external file routes leave out the ones Traefik Manager manages`() {
        val routes = ProviderRows.routes(ProviderPage.FileExternal, routers, null, managed = setOf("mine"))
        assertEquals(listOf("legacy@file"), routes.map { it.name })
        assertTrue(routes.single().tls)
    }

    @Test
    fun `targets come from the provider's service and the verdict counts what is not serving`() {
        val services = ServiceEnvelope(
            http = listOf(
                TraefikService(
                    name = "whoami@docker",
                    provider = "docker",
                    loadBalancer = ServiceLoadBalancer(servers = listOf(ServiceServer(url = "http://172.18.0.5:80"))),
                ),
            ),
        )
        val routes = ProviderRows.routes(ProviderPage.Docker, routers, services)
        val whoami = routes.first { it.shortName == "whoami" }
        assertEquals("http://172.18.0.5:80", whoami.target)
        assertEquals("who.example.com", whoami.plainHost)
        assertNull(routes.first { it.shortName == "broken" }.target)

        val middlewares = ProviderRows.middlewares(
            ProviderPage.Docker,
            ProtoEnvelope(http = listOf(TraefikObject(name = "auth@docker", type = "basicauth"), TraefikObject(name = "x@file"))),
        )
        val verdict = ProviderRows.verdict(routes, middlewares)
        assertEquals("1 route not serving", verdict.headline)
        assertEquals(2, verdict.http)
        assertEquals(1, verdict.tcp)
        assertEquals(1, verdict.middlewares)
        assertEquals(ProviderProtocol.Tcp, routes.first { it.shortName == "db" }.protocol)
    }

    @Test
    fun `router tls and middlewares decode from the Traefik API`() {
        val json = Json { ignoreUnknownKeys = true }
        val router = json.decodeFromString<TraefikObject>(
            """{"name":"a@docker","provider":"docker","middlewares":["auth@docker"],"tls":{"certResolver":"le"}}""",
        )
        assertEquals(listOf("auth@docker"), router.middlewares)
        assertTrue(ProviderRows.routes(ProviderPage.Docker, ProtoEnvelope(http = listOf(router)), null).single().tls)
    }

    @Test
    fun `a provider page only shows when the server turned its tab on, on the host and on agents`() {
        val host = ServerCapabilities(settings = ServerSettings(visibleTabs = mapOf("docker" to true, "kubernetes" to false)))
        assertTrue(host.tabEnabled("docker"))
        assertFalse(host.tabEnabled("kubernetes"))
        assertFalse(host.tabEnabled("internal"))
        assertFalse(ServerCapabilities().tabEnabled("docker"))

        val agent = ServerCapabilities(isHost = false, agent = Agent(visibleTabs = mapOf("internal" to true)))
        assertTrue(agent.tabEnabled("internal"))
        assertFalse(agent.tabEnabled("docker"))
        assertFalse(ServerCapabilities(isHost = false, agent = Agent()).tabEnabled("docker"))
    }

    @Test
    fun `every provider has its own page in the Providers section`() {
        val pages = TmDestination.entries.mapNotNull { it.provider }
        assertEquals(ProviderPage.entries.toList(), pages)
        TmDestination.entries.filter { it.provider != null }.forEach {
            assertEquals(TmSection.Providers, it.section)
            assertEquals(it.provider!!.tab, it.serverTab)
        }
    }
}

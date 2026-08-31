package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.FailoverService
import dev.chr0nzz.traefikmanager.data.model.MirrorChild
import dev.chr0nzz.traefikmanager.data.model.MirroringService
import dev.chr0nzz.traefikmanager.data.model.ServerHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceKind
import dev.chr0nzz.traefikmanager.data.model.ServiceLoadBalancer
import dev.chr0nzz.traefikmanager.data.model.ServiceProtocol
import dev.chr0nzz.traefikmanager.data.model.ServiceRows
import dev.chr0nzz.traefikmanager.data.model.ServiceTypes
import dev.chr0nzz.traefikmanager.ui.services.ServiceChildDraft
import dev.chr0nzz.traefikmanager.ui.services.ServiceDraft
import dev.chr0nzz.traefikmanager.data.model.ServiceServer
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import dev.chr0nzz.traefikmanager.data.model.WeightedChild
import dev.chr0nzz.traefikmanager.data.model.WeightedService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRowsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun row(service: TraefikService) = ServiceRows.row(service, ServiceProtocol.Http)

    @Test
    fun `enabled with no health check counts as success`() {
        val service = TraefikService(name = "api@file", status = "enabled")
        assertEquals(ServiceHealth.Ok, row(service).health)
    }

    @Test
    fun `enabled with a down backend is a warning`() {
        val service = TraefikService(
            name = "media@file",
            status = "enabled",
            serverStatus = mapOf("http://a:80" to "UP", "http://b:80" to "DOWN"),
        )
        val built = row(service)
        assertEquals(ServiceHealth.Warning, built.health)
        assertEquals(1, built.backendsUp)
        assertEquals(2, built.backendsTotal)
        assertEquals("1/2 active", built.backendSummary)
    }

    @Test
    fun `disabled and unknown statuses follow the web truth table`() {
        assertEquals(ServiceHealth.Error, row(TraefikService(name = "a", status = "disabled")).health)
        assertEquals(ServiceHealth.Error, row(TraefikService(name = "a", status = "error")).health)
        assertEquals(ServiceHealth.Warning, row(TraefikService(name = "a", status = "warning")).health)
        assertEquals(ServiceHealth.Warning, row(TraefikService(name = "a")).health)
        assertEquals(ServiceHealth.Ok, row(TraefikService(name = "a", status = "ENABLED")).health)
    }

    @Test
    fun `server status is compared case insensitively`() {
        val service = TraefikService(
            name = "a@file",
            status = "enabled",
            serverStatus = mapOf("http://a:80" to "up"),
        )
        assertEquals(ServiceHealth.Ok, row(service).health)
        assertEquals(1, row(service).backendsUp)
    }

    @Test
    fun `provider comes from the last at-segment and falls back to file`() {
        assertEquals("docker", row(TraefikService(name = "app@docker")).provider)
        assertEquals("file", row(TraefikService(name = "app")).provider)
        assertEquals("kubernetes", row(TraefikService(name = "we@ird@kubernetes")).provider)
        assertEquals("file", row(TraefikService(name = "app@docker", provider = "file")).provider)
    }

    @Test
    fun `short name drops the provider suffix`() {
        assertEquals("my-app", row(TraefikService(name = "my-app@file")).shortName)
    }

    @Test
    fun `servers join loadBalancer targets with their health`() {
        val service = TraefikService(
            name = "media@file",
            status = "enabled",
            serverStatus = mapOf("http://a:80" to "UP", "http://b:80" to "DOWN"),
            loadBalancer = ServiceLoadBalancer(
                servers = listOf(
                    ServiceServer(url = "http://a:80"),
                    ServiceServer(url = "http://b:80"),
                    ServiceServer(url = "http://c:80"),
                ),
            ),
        )
        val servers = row(service).servers
        assertEquals(3, servers.size)
        assertEquals(ServerHealth.Up, servers[0].health)
        assertEquals(ServerHealth.Down, servers[1].health)
        assertEquals(ServerHealth.Unknown, servers[2].health)
    }

    @Test
    fun `tcp servers use address instead of url`() {
        val service = TraefikService(
            name = "postgres@file",
            status = "enabled",
            loadBalancer = ServiceLoadBalancer(servers = listOf(ServiceServer(address = "10.0.0.2:5432"))),
        )
        assertEquals("10.0.0.2:5432", ServiceRows.row(service, ServiceProtocol.Tcp).servers.single().target)
    }

    @Test
    fun `serverStatus keys are used when the load balancer has no servers`() {
        val service = TraefikService(
            name = "a@file",
            status = "enabled",
            serverStatus = mapOf("http://a:80" to "UP"),
        )
        assertEquals(listOf("http://a:80"), row(service).servers.map { it.target })
    }

    @Test
    fun `composite targets match the web label forms`() {
        val service = TraefikService(
            name = "pool@file",
            status = "enabled",
            weighted = WeightedService(listOf(WeightedChild("a@file", 3), WeightedChild("b@file", 0), WeightedChild("c@file"))),
            mirroring = MirroringService(service = "main@file", mirrors = listOf(MirrorChild("shadow@file", 10))),
            failover = FailoverService(service = "primary@file", fallback = "backup@file"),
        )
        assertEquals(
            listOf("a@file (3)", "b@file (0)", "c@file", "main@file", "shadow@file mirror (10%)", "primary@file", "backup@file fallback"),
            row(service).composite,
        )
    }

    @Test
    fun `the server count is suppressed for composite services`() {
        val weighted = row(
            TraefikService(
                name = "pool@file",
                status = "enabled",
                weighted = WeightedService(listOf(WeightedChild("a@file", 1))),
            ),
        )
        assertTrue(weighted.metaParts.none { it.endsWith("server") || it.endsWith("servers") })

        val plain = row(
            TraefikService(
                name = "a@file",
                status = "enabled",
                serverStatus = mapOf("http://a:80" to "UP"),
                loadBalancer = ServiceLoadBalancer(servers = listOf(ServiceServer(url = "http://a:80"))),
            ),
        )
        assertEquals(listOf("1 server", "1/1 active"), plain.metaParts)
    }

    @Test
    fun `kind prefers the payload type and falls back to the present sub-object`() {
        assertEquals(ServiceKind.Weighted, ServiceRows.kindOf(TraefikService(type = "weighted")))
        assertEquals(
            ServiceKind.Mirroring,
            ServiceRows.kindOf(TraefikService(mirroring = MirroringService(service = "a"))),
        )
        assertNull(ServiceRows.kindOf(TraefikService(name = "a")))
    }

    @Test
    fun `sticky and health check are presence flags`() {
        val service = TraefikService(
            name = "a@file",
            status = "enabled",
            loadBalancer = ServiceLoadBalancer(
                sticky = JsonObject(emptyMap()),
                healthCheck = JsonObject(emptyMap()),
            ),
        )
        val built = row(service)
        assertTrue(built.sticky)
        assertTrue(built.healthCheck)
        assertEquals(listOf("sticky", "health check"), built.metaParts)
    }

    @Test
    fun `pass host header defaults to true when absent and is null without a load balancer`() {
        assertEquals(true, row(TraefikService(name = "a", loadBalancer = ServiceLoadBalancer())).passHostHeader)
        assertEquals(
            false,
            row(TraefikService(name = "a", loadBalancer = ServiceLoadBalancer(passHostHeader = false))).passHostHeader,
        )
        assertNull(row(TraefikService(name = "a")).passHostHeader)
    }

    @Test
    fun `used by labels are pluralised like the web`() {
        assertNull(row(TraefikService(name = "a")).usedByLabel)
        assertEquals("used by 1 route", row(TraefikService(name = "a", usedBy = listOf("r@file"))).usedByLabel)
        assertEquals(
            "used by 2 routes",
            row(TraefikService(name = "a", usedBy = listOf("r@file", "s@file"))).usedByLabel,
        )
    }

    @Test
    fun `errors normalise strings, arrays and objects`() {
        val fromString = json.decodeFromString<TraefikService>("""{"name":"a","error":"boom"}""")
        assertEquals(listOf("boom"), ServiceRows.errorsOf(fromString.error))

        val fromArray = json.decodeFromString<TraefikService>("""{"name":"a","error":["one","two"]}""")
        assertEquals(listOf("one", "two"), ServiceRows.errorsOf(fromArray.error))

        val fromObjects = json.decodeFromString<TraefikService>("""{"name":"a","error":[{"message":"bad"}]}""")
        assertEquals(listOf("bad"), ServiceRows.errorsOf(fromObjects.error))

        assertEquals(emptyList<String>(), ServiceRows.errorsOf(null))
    }

    @Test
    fun `rows are keyed by protocol and name and sorted by full name`() {
        val envelope = ServiceEnvelope(
            http = listOf(TraefikService(name = "zeta@file"), TraefikService(name = "alpha@docker")),
            tcp = listOf(TraefikService(name = "alpha@file")),
        )
        val rows = ServiceRows.from(envelope)
        assertEquals(listOf("alpha@docker", "alpha@file", "zeta@file"), rows.map { it.name })
        assertEquals("Tcp:alpha@file", rows[1].key)
    }

    @Test
    fun `the envelope tolerates absent protocol arrays and defaults reachable to true`() {
        val envelope = json.decodeFromString<ServiceEnvelope>("""{"http":[{"name":"a@file"}]}""")
        assertTrue(envelope.reachable)
        assertTrue(envelope.tcp.isEmpty())
        assertEquals(1, ServiceRows.from(envelope).size)
    }

    @Test
    fun `unknown traefik fields do not break decoding`() {
        val service = json.decodeFromString<TraefikService>(
            """{"name":"a@file","status":"enabled","futureField":{"x":1},
               "loadBalancer":{"servers":[{"url":"http://a:80"}],"responseForwarding":{"flushInterval":"100ms"}}}""",
        )
        assertEquals("http://a:80", service.loadBalancer?.servers?.single()?.url)
    }
}

class HighestRandomWeightTest {

    private fun row(service: TraefikService) = ServiceRows.row(service, ServiceProtocol.Http)

    @Test
    fun `the declared type is recognised`() {
        val service = TraefikService(name = "hrw", type = "highestRandomWeight")
        assertEquals(ServiceKind.HighestRandomWeight, row(service).kind)
    }

    @Test
    fun `the structure is recognised when no type is declared`() {
        val service = TraefikService(
            name = "hrw",
            highestRandomWeight = WeightedService(
                services = listOf(WeightedChild(name = "a", weight = 3)),
            ),
        )
        assertEquals(ServiceKind.HighestRandomWeight, row(service).kind)
    }

    @Test
    fun `it no longer renders as the word service`() {
        val service = TraefikService(name = "hrw", type = "highestRandomWeight")
        assertEquals("highestrandomweight", row(service).kindLabel)
    }

    @Test
    fun `children are listed with their weights`() {
        val service = TraefikService(
            name = "hrw",
            type = "highestRandomWeight",
            highestRandomWeight = WeightedService(
                services = listOf(
                    WeightedChild(name = "blue", weight = 3),
                    WeightedChild(name = "green"),
                ),
            ),
        )
        assertEquals(listOf("blue (3)", "green"), row(service).composite)
    }

    @Test
    fun `an unknown type still falls back rather than crashing`() {
        val service = TraefikService(name = "odd", type = "somethingNew")
        assertEquals(null, row(service).kind)
        assertEquals("service", row(service).kindLabel)
    }
}

class MirrorPercentTest {

    private fun row(service: TraefikService) = ServiceRows.row(service, ServiceProtocol.Http)

    @Test
    fun `a configured percent is shown`() {
        val service = TraefikService(
            name = "m",
            type = "mirroring",
            mirroring = MirroringService(service = "main", mirrors = listOf(MirrorChild("shadow", 10))),
        )
        assertEquals(listOf("main", "shadow mirror (10%)"), row(service).composite)
    }

    @Test
    fun `a missing percent reads as zero rather than vanishing`() {
        val service = TraefikService(
            name = "m",
            type = "mirroring",
            mirroring = MirroringService(service = "main", mirrors = listOf(MirrorChild("shadow"))),
        )
        assertEquals(listOf("main", "shadow mirror (0%)"), row(service).composite)
    }
}

class ServiceOwnershipTest {

    private fun envelope(owned: List<String> = emptyList(), children: List<String> = emptyList()) =
        ServiceEnvelope(
            http = listOf(
                TraefikService(name = "pool@file", type = "weighted"),
                TraefikService(name = "pool-backend-1@file", type = "loadBalancer"),
                TraefikService(name = "hand@file", type = "weighted"),
            ),
            ownedServices = owned,
            ownedChildren = children,
        )

    @Test
    fun `owned parents are flagged and hand written ones are not`() {
        val rows = ServiceRows.from(envelope(owned = listOf("pool")))
        assertEquals(true, rows.first { it.shortName == "pool" }.owned)
        assertEquals(false, rows.first { it.shortName == "hand" }.owned)
    }

    @Test
    fun `generated children are hidden from the list`() {
        val rows = ServiceRows.from(envelope(owned = listOf("pool"), children = listOf("pool-backend-1")))
        assertEquals(listOf("hand", "pool"), rows.map { it.shortName })
    }

    @Test
    fun `an envelope without the ownership keys leaves everything unowned and visible`() {
        val rows = ServiceRows.from(envelope())
        assertEquals(3, rows.size)
        assertTrue(rows.none { it.owned })
    }
}

class ServiceDraftTest {

    @Test
    fun `a weighted draft posts one child per row with its weight`() {
        val draft = ServiceDraft(
            name = "pool",
            type = "weighted",
            children = listOf(
                ServiceChildDraft(kind = ServiceChildDraft.MANUAL, address = "10.0.0.10:80", share = "9"),
                ServiceChildDraft(kind = ServiceChildDraft.SERVICE, name = "canary", share = "1"),
            ),
        )
        val payload = draft.payload()
        assertEquals("weighted", payload.type)
        assertEquals(listOf("manual", "service"), payload.children.map { it.kind })
        assertEquals(listOf(9, 1), payload.children.map { it.weight })
        assertEquals("canary", payload.children[1].name)
        assertNull(draft.problem())
    }

    @Test
    fun `blank rows are dropped rather than posted`() {
        val draft = ServiceDraft(
            name = "pool",
            type = "weighted",
            children = listOf(
                ServiceChildDraft(kind = ServiceChildDraft.MANUAL, address = "10.0.0.10:80"),
                ServiceChildDraft(kind = ServiceChildDraft.MANUAL, address = "   "),
                ServiceChildDraft(kind = ServiceChildDraft.SERVICE, name = ""),
            ),
        )
        assertEquals(1, draft.payload().children.size)
    }

    @Test
    fun `an empty draft is refused before it reaches the server`() {
        assertEquals("Give the service a name", ServiceDraft().problem())
        assertEquals(
            "Add at least one backend",
            ServiceDraft(name = "pool", children = listOf(ServiceChildDraft())).problem(),
        )
    }

    @Test
    fun `a name the server would reject is refused here first`() {
        assertEquals(
            "Use letters, numbers, dots, dashes or underscores",
            ServiceDraft(name = "bad name@file").problem(),
        )
    }

    @Test
    fun `a load balancer cannot be built only from service references`() {
        val draft = ServiceDraft(
            name = "pool",
            type = "loadBalancer",
            children = listOf(ServiceChildDraft(kind = ServiceChildDraft.SERVICE, name = "other")),
        )
        assertEquals("A load balancer needs at least one address", draft.problem())
    }

    @Test
    fun `an existing weighted service reads back into rows`() {
        val service = TraefikService(
            name = "pool@file",
            type = "weighted",
            weighted = WeightedService(listOf(WeightedChild("a@file", 3), WeightedChild("b@file", 1))),
        )
        val draft = ServiceDraft.of("pool@file", service)
        assertEquals("pool", draft.name)
        assertEquals("pool", draft.originalName)
        assertEquals("weighted", draft.type)
        assertEquals(listOf("a", "b"), draft.children.map { it.name })
        assertEquals(listOf("3", "1"), draft.children.map { it.share })
    }

    @Test
    fun `an existing mirroring service puts the served row first`() {
        val service = TraefikService(
            name = "m@file",
            type = "mirroring",
            mirroring = MirroringService(service = "main@file", mirrors = listOf(MirrorChild("shadow@file", 10))),
        )
        val draft = ServiceDraft.of("m@file", service)
        assertEquals(listOf("main", "shadow"), draft.children.map { it.name })
        assertEquals(listOf("0", "10"), draft.children.map { it.share })
    }

    @Test
    fun `highest random weight reads back but is marked unauthorable`() {
        val service = TraefikService(
            name = "h@file",
            type = "highestRandomWeight",
            highestRandomWeight = WeightedService(listOf(WeightedChild("a@file", 2))),
        )
        val draft = ServiceDraft.of("h@file", service)
        assertEquals("highestRandomWeight", draft.type)
        assertTrue(ServiceTypes.authorable.none { it.first == draft.type })
    }
}

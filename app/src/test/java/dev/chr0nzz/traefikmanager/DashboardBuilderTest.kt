package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.EntrypointHttp
import dev.chr0nzz.traefikmanager.data.model.EntrypointTls
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.OverviewCounts
import dev.chr0nzz.traefikmanager.data.model.OverviewSection
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import dev.chr0nzz.traefikmanager.data.repo.DashboardBuilder
import dev.chr0nzz.traefikmanager.data.repo.ObjectState
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBuilderTest {

    private fun router(
        name: String,
        status: String? = "enabled",
        using: List<String>? = listOf("websecure"),
    ) = TraefikObject(name = "$name@file", provider = "file", status = status, using = using)

    @Test
    fun `disabled router is an error, warning is a warning`() {
        assertEquals(ObjectState.Err, DashboardBuilder.classifyRouter(router("a", "disabled")).state)
        assertEquals(ObjectState.Warn, DashboardBuilder.classifyRouter(router("b", "warning")).state)
        assertEquals(ObjectState.Ok, DashboardBuilder.classifyRouter(router("c")).state)
    }

    @Test
    fun `unbound and unreported routers are idle, never errors`() {
        assertEquals(ObjectState.Idle, DashboardBuilder.classifyRouter(router("a", using = emptyList())).state)
        assertEquals(ObjectState.Idle, DashboardBuilder.classifyRouter(router("b", status = null)).state)
    }

    @Test
    fun `service with a down backend is degraded and one without health checks is idle`() {
        val degraded = TraefikObject(
            name = "svc@file",
            status = "enabled",
            serverStatus = mapOf("a" to "UP", "b" to "DOWN"),
        )
        assertEquals(ObjectState.Warn, DashboardBuilder.classifyService(degraded).state)
        assertEquals("1 of 2 backends down", DashboardBuilder.classifyService(degraded).reason)

        val unchecked = TraefikObject(name = "svc2@file", status = "enabled")
        assertEquals(ObjectState.Idle, DashboardBuilder.classifyService(unchecked).state)
    }

    @Test
    fun `middleware referenced only by an entry point is not unused`() {
        val mw = TraefikObject(name = "crowdsec@file", status = "enabled")
        assertEquals(ObjectState.Idle, DashboardBuilder.classifyMiddleware(mw, emptySet()).state)
        assertEquals(ObjectState.Ok, DashboardBuilder.classifyMiddleware(mw, setOf("crowdsec")).state)
    }

    @Test
    fun `unreachable traefik reports unknown totals, not zero`() {
        val snapshot = DashboardBuilder.build(
            overview = null,
            entrypoints = null,
            routers = ProtoEnvelope(reachable = false),
            services = ProtoEnvelope(reachable = false),
            middlewares = ProtoEnvelope(reachable = false),
        )

        assertEquals(TmStatus.Warn, snapshot.verdict.status)
        assertEquals("Traefik API unreachable", snapshot.verdict.headline)
        snapshot.cards.forEach { card ->
            assertNull(card.total)
            assertEquals("Traefik API unreachable", card.sub)
            assertTrue(card.cells.isEmpty())
        }
    }

    @Test
    fun `overview-only mode keeps totals but blanks the strips`() {
        val snapshot = DashboardBuilder.build(
            overview = Overview(http = OverviewSection(routers = OverviewCounts(total = 12))),
            entrypoints = null,
            routers = null,
            services = null,
            middlewares = null,
        )

        val http = snapshot.cards.first { it.key == "http" }
        assertEquals(12, http.total)
        assertTrue(http.cells.isEmpty())
        assertEquals("total from overview, the list is unavailable", http.sub)
    }

    @Test
    fun `genuinely empty renders zero, not unknown`() {
        val snapshot = DashboardBuilder.build(
            overview = Overview(http = OverviewSection(routers = OverviewCounts(total = 0))),
            entrypoints = emptyList(),
            routers = ProtoEnvelope(reachable = true),
            services = ProtoEnvelope(reachable = true),
            middlewares = ProtoEnvelope(reachable = true),
        )

        val http = snapshot.cards.first { it.key == "http" }
        assertEquals(0, http.total)
        assertEquals("no HTTP routers configured", http.sub)
        assertEquals(TmStatus.Ok, snapshot.verdict.status)
    }

    @Test
    fun `errors escalate the verdict and idle objects never do`() {
        val routers = ProtoEnvelope(
            http = listOf(
                router("ok"),
                router("broken", status = "disabled"),
                router("unbound", using = emptyList()),
            ),
        )
        val snapshot = DashboardBuilder.build(
            overview = null,
            entrypoints = emptyList(),
            routers = routers,
            services = ProtoEnvelope(reachable = true),
            middlewares = ProtoEnvelope(reachable = true),
        )

        assertEquals(TmStatus.Error, snapshot.verdict.status)
        assertEquals("1 object is down", snapshot.verdict.headline)

        val idleOnly = DashboardBuilder.build(
            overview = null,
            entrypoints = emptyList(),
            routers = ProtoEnvelope(http = listOf(router("unbound", using = emptyList()))),
            services = ProtoEnvelope(reachable = true),
            middlewares = ProtoEnvelope(reachable = true),
        )
        assertEquals(TmStatus.Ok, idleOnly.verdict.status)
    }

    @Test
    fun `strip cells are ordered worst first`() {
        val routers = ProtoEnvelope(
            http = listOf(
                router("ok1"),
                router("bad", status = "disabled"),
                router("idle", using = emptyList()),
                router("warn", status = "warning"),
            ),
        )
        val snapshot = DashboardBuilder.build(null, emptyList(), routers, null, null)
        val cells = snapshot.cards.first { it.key == "http" }.cells

        assertEquals(listOf(TmStatus.Error, TmStatus.Warn, TmStatus.Unknown, TmStatus.Ok), cells)
    }

    @Test
    fun `entry point rows infer protocol and bind counts`() {
        val routers = ProtoEnvelope(
            http = listOf(router("a", using = listOf("websecure"))),
            udp = listOf(TraefikObject(name = "wg@file", status = "enabled", using = listOf("wireguard"))),
        )
        val entrypoints = listOf(
            Entrypoint(
                name = "websecure",
                address = ":443",
                http = EntrypointHttp(tls = EntrypointTls(certResolver = "letsencrypt")),
            ),
            Entrypoint(name = "wireguard", address = ":51820/udp"),
            Entrypoint(name = "web", address = ":80"),
        )
        val rows = DashboardBuilder.build(null, entrypoints, routers, null, null).entrypoints

        assertEquals("HTTPS", rows[0].proto)
        assertEquals(1, rows[0].routerCount)
        assertEquals("TLS via letsencrypt", rows[0].facts)
        assertFalse(rows[0].idle)
        assertEquals("UDP", rows[1].proto)
        assertEquals("HTTP", rows[2].proto)
        assertTrue(rows[2].idle)
        assertEquals("no router binds this entry point", rows[2].facts)
    }

    @Test
    fun `healthy objects render as quiet cells, not green`() {
        val snapshot = DashboardBuilder.build(
            overview = null,
            entrypoints = emptyList(),
            routers = ProtoEnvelope(http = listOf(router("a"), router("b"))),
            services = null,
            middlewares = null,
        )
        val card = snapshot.cards.first { it.key == "http" }

        assertEquals(listOf(TmStatus.Ok, TmStatus.Ok), card.cells)
        assertEquals("healthy", card.healthLabel)
        assertTrue(card.flags.isEmpty())
    }
}

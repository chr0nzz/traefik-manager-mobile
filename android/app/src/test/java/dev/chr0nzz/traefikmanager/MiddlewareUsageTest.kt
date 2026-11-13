package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.MiddlewareUsage
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.TraefikObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MiddlewareUsageTest {

    private val envelope = ProtoEnvelope(
        http = listOf(
            TraefikObject("auth@file", "file", "enabled", usedBy = listOf("api@file", "web@docker", "dash@kubernetescrd")),
            TraefikObject("auth@docker", "docker", "enabled", usedBy = listOf("other@docker")),
            TraefikObject("unused@file", "file", "enabled", usedBy = listOf("api@file")),
        ),
        tcp = listOf(TraefikObject("ipallow@file", "file", "enabled", usedBy = listOf("db@docker"))),
    )

    @Test
    fun `live users keep only routers from other providers on file middlewares`() {
        assertEquals(
            mapOf(
                "http:auth" to listOf("web@docker", "dash@kubernetescrd"),
                "tcp:ipallow" to listOf("db@docker"),
            ),
            MiddlewareUsage.liveUsers(envelope),
        )
    }

    @Test
    fun `external users match the protocol and skip routers already in the route list`() {
        val external = MiddlewareUsage.externalUsersFor(
            MiddlewareUsage.liveUsers(envelope),
            routes = listOf(Route(id = "web@docker", name = "web")),
            middlewares = listOf(
                MiddlewareDef(name = "auth", type = "http"),
                MiddlewareDef(name = "ipallow", type = "http"),
                MiddlewareDef(name = "unused", type = "http"),
            ),
        )
        assertEquals(mapOf("auth" to listOf("dash@kubernetescrd")), external)
    }
}

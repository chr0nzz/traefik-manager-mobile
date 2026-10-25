package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.MapNodeKind
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteMapBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapTest {

    private fun strings(vararg values: String): JsonElement =
        Json.parseToJsonElement(values.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })

    private fun route(
        name: String,
        service: String = "$name-service",
        entryPoints: List<String> = listOf("websecure"),
        middlewares: List<String> = emptyList(),
        provider: String = "file",
        target: String = "http://10.0.0.1:80",
    ) = Route(
        id = name,
        name = name,
        serviceName = service,
        target = target,
        provider = provider,
        entryPoints = strings(*entryPoints.toTypedArray()),
        middlewares = strings(*middlewares.toTypedArray()),
    )

    @Test
    fun `every node lands in the column its type belongs to`() {
        val graph = RouteMapBuilder.build(listOf(route("blog", middlewares = listOf("auth"))))
        val columns = graph.nodes.associate { it.id to it.column }
        assertEquals(0, columns["ep:websecure"])
        assertEquals(1, columns["route:blog"])
        assertEquals(2, columns["mw:auth"])
        assertEquals(3, columns["svc:blog-service"])
    }

    @Test
    fun `a route without middleware reaches its service directly`() {
        val graph = RouteMapBuilder.build(listOf(route("api")))
        assertTrue(graph.edges.any { it.from == "route:api" && it.to == "svc:api-service" })
    }

    @Test
    fun `a busy provider collapses into one node`() {
        val docker = (1..RouteMapBuilder.COLLAPSE_MIN).map { route("d$it", provider = "docker") }
        val graph = RouteMapBuilder.build(docker + route("kept"))
        assertTrue(graph.nodes.any { it.kind == MapNodeKind.Group && it.label == "docker" })
        assertTrue(graph.nodes.none { it.id == "route:d1" })
        assertTrue(graph.nodes.any { it.id == "route:kept" })
    }

    @Test
    fun `a provider under the threshold stays as separate routes`() {
        val docker = (1 until RouteMapBuilder.COLLAPSE_MIN).map { route("d$it", provider = "docker") }
        val graph = RouteMapBuilder.build(docker)
        assertTrue(graph.nodes.none { it.kind == MapNodeKind.Group })
        assertEquals(docker.size, graph.nodes.count { it.kind == MapNodeKind.Route })
    }

    @Test
    fun `shared middleware is one node both routes point at`() {
        val graph = RouteMapBuilder.build(
            listOf(
                route("one", middlewares = listOf("auth")),
                route("two", middlewares = listOf("auth")),
            ),
        )
        assertEquals(1, graph.nodes.count { it.id == "mw:auth" })
        assertTrue(graph.edges.any { it.from == "route:one" && it.to == "mw:auth" })
        assertTrue(graph.edges.any { it.from == "route:two" && it.to == "mw:auth" })
    }

    @Test
    fun `nodes in a column never overlap`() {
        val graph = RouteMapBuilder.build((1..12).map { route("r$it") })
        graph.nodes.groupBy { it.column }.forEach { (_, column) ->
            column.sortedBy { it.y }.zipWithNext { above, below ->
                assertTrue(above.y + above.height <= below.y)
            }
        }
    }

    @Test
    fun `ordering puts a route beside the entry point that feeds it`() {
        val graph = RouteMapBuilder.build(
            listOf(
                route("z", entryPoints = listOf("web")),
                route("a", entryPoints = listOf("websecure")),
                route("m", entryPoints = listOf("web")),
            ),
        )
        val onWeb = listOf("route:z", "route:m").map { graph.byId.getValue(it).y }
        val onSecure = graph.byId.getValue("route:a").y
        assertTrue(onWeb.all { it < onSecure })
    }

    @Test
    fun `focus follows a chain in both directions`() {
        val graph = RouteMapBuilder.build(
            listOf(
                route("blog", middlewares = listOf("auth")),
                route("other", service = "other-service"),
            ),
        )
        val focus = graph.connected("mw:auth")
        assertTrue(focus.containsAll(listOf("route:blog", "svc:blog-service", "ep:websecure")))
        assertTrue(focus.none { it.startsWith("svc:other") })
    }

    @Test
    fun `search keeps only the routes that match`() {
        val graph = RouteMapBuilder.build(listOf(route("blog"), route("api")), query = "blo")
        assertTrue(graph.nodes.any { it.id == "route:blog" })
        assertTrue(graph.nodes.none { it.id == "route:api" })
    }
}

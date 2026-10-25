package dev.chr0nzz.traefikmanager.data.model

enum class MapNodeKind { EntryPoint, Route, Group, Middleware, Service }

data class MapNode(
    val id: String,
    val kind: MapNodeKind,
    val label: String,
    val detail: String = "",
    val route: Route? = null,
    val members: List<Route> = emptyList(),
    val uses: Int = 0,
    val health: MapHealth = MapHealth.Quiet,
    val column: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val centreY: Float get() = y + height / 2f
}

enum class MapHealth { Quiet, Warn, Down, Idle }

data class MapEdge(val from: String, val to: String)

data class RouteMapGraph(
    val nodes: List<MapNode> = emptyList(),
    val edges: List<MapEdge> = emptyList(),
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val byId: Map<String, MapNode> by lazy { nodes.associateBy { it.id } }

    fun connected(id: String): Set<String> {
        val out = mutableSetOf(id)
        walk(id, out) { edge, frontier -> if (edge.to in frontier) edge.from else null }
        walk(id, out) { edge, frontier -> if (edge.from in frontier) edge.to else null }
        return out
    }

    fun subgraph(id: String): RouteMapGraph {
        val ids = connected(id)
        val members = nodes.filter { it.id in ids }
        val links = edges.filter { it.from in ids && it.to in ids }
        return RouteMapBuilder.layout(members, links)
    }

    private fun walk(start: String, out: MutableSet<String>, step: (MapEdge, Set<String>) -> String?) {
        var frontier = setOf(start)
        while (frontier.isNotEmpty()) {
            val next = mutableSetOf<String>()
            edges.forEach { edge ->
                val found = step(edge, frontier)
                if (found != null && out.add(found)) next += found
            }
            frontier = next
        }
    }
}

object RouteMapBuilder {
    const val COLLAPSE_MIN = 6

    private const val COLUMN_GAP = 92f
    private const val ROW_GAP = 16f
    private const val NODE_HEIGHT = 34f
    private const val MARGIN = 20f
    private const val CHAR_WIDTH = 6.1f
    private const val SIDE_PADDING = 16f
    private const val GLYPH_ROOM = 17f
    private const val DOT_ROOM = 13f
    private const val MIN_WIDTH = 104f
    private const val MAX_WIDTH = 320f

    fun build(
        routes: List<Route>,
        protocol: String? = null,
        provider: String? = null,
        entryPoint: String? = null,
        query: String = "",
    ): RouteMapGraph {
        val wanted = routes.filter { route ->
            (protocol == null || route.protocol.equals(protocol, ignoreCase = true)) &&
                (provider == null || route.provider == provider) &&
                (entryPoint == null || entryPoint in route.entryPointNames) &&
                (
                    query.isEmpty() ||
                        route.name.contains(query, true) ||
                        route.rule.contains(query, true) ||
                        route.serviceName.contains(query, true)
                    )
        }
        if (wanted.isEmpty()) return RouteMapGraph()

        val usage = wanted.flatMap { it.middlewareNames }.groupingBy { it }.eachCount()
        val nodes = mutableListOf<MapNode>()
        val edges = mutableListOf<MapEdge>()
        val seen = mutableSetOf<String>()
        fun add(node: MapNode) {
            if (seen.add(node.id)) nodes += node
        }
        fun link(from: String, to: String) {
            val edge = MapEdge(from, to)
            if (edge !in edges) edges += edge
        }

        val collapsed = wanted
            .filter { it.provider.isNotEmpty() && it.provider != "file" }
            .groupBy { it.provider }
            .filterValues { it.size >= COLLAPSE_MIN }
        val collapsedIds = collapsed.values.flatten().map { it.id }.toSet()

        wanted.flatMap { it.entryPointNames }.distinct().forEach { name ->
            add(MapNode("ep:$name", MapNodeKind.EntryPoint, name))
        }

        collapsed.forEach { (name, members) ->
            val id = "group:$name"
            add(
                MapNode(
                    id = id,
                    kind = MapNodeKind.Group,
                    label = name,
                    detail = "${members.size} routes",
                    members = members,
                    health = worst(members),
                ),
            )
            members.flatMap { it.entryPointNames }.distinct().forEach { link("ep:$it", id) }
            members.mapNotNull { it.serviceName.takeIf(String::isNotEmpty) }.distinct().forEach { service ->
                add(serviceNode(service, members.first { it.serviceName == service }))
                link(id, "svc:$service")
            }
        }

        wanted.filterNot { it.id in collapsedIds }.forEach { route ->
            val id = "route:${route.id}"
            add(
                MapNode(
                    id = id,
                    kind = MapNodeKind.Route,
                    label = route.name,
                    detail = route.hosts.firstOrNull().orEmpty(),
                    route = route,
                    health = health(route),
                ),
            )
            route.entryPointNames.forEach { link("ep:$it", id) }

            val middlewares = route.middlewareNames
            val service = route.serviceName.takeIf(String::isNotEmpty)
            if (middlewares.isEmpty()) {
                service?.let {
                    add(serviceNode(it, route))
                    link(id, "svc:$it")
                }
            } else {
                middlewares.forEach { name ->
                    add(
                        MapNode(
                            id = "mw:$name",
                            kind = MapNodeKind.Middleware,
                            label = name.substringBefore('@'),
                            uses = usage[name] ?: 0,
                        ),
                    )
                    link(id, "mw:$name")
                    service?.let {
                        add(serviceNode(it, route))
                        link("mw:$name", "svc:$it")
                    }
                }
            }
        }

        return layout(nodes, edges)
    }

    private fun serviceNode(name: String, owner: Route) = MapNode(
        id = "svc:$name",
        kind = MapNodeKind.Service,
        label = name.substringBefore('@'),
        detail = owner.target.ifEmpty {
            owner.compositeChildren.joinToString(", ") { it.name.substringBefore('@') }
        },
        route = owner,
        health = if (owner.backendCount == 0) MapHealth.Down else MapHealth.Quiet,
    )

    private fun health(route: Route): MapHealth = when {
        !route.enabled -> MapHealth.Idle
        route.target.isEmpty() && route.servers.isEmpty() -> MapHealth.Down
        else -> MapHealth.Quiet
    }

    private fun worst(routes: List<Route>): MapHealth = when {
        routes.any { health(it) == MapHealth.Down } -> MapHealth.Down
        routes.any { health(it) == MapHealth.Idle } -> MapHealth.Idle
        else -> MapHealth.Quiet
    }

    private fun columnOf(kind: MapNodeKind): Int = when (kind) {
        MapNodeKind.EntryPoint -> 0
        MapNodeKind.Route, MapNodeKind.Group -> 1
        MapNodeKind.Middleware -> 2
        MapNodeKind.Service -> 3
    }

    fun layout(nodes: List<MapNode>, edges: List<MapEdge>): RouteMapGraph {
        val columns = nodes.groupBy { columnOf(it.kind) }
            .mapValues { (_, group) -> group.sortedBy { it.label.lowercase() }.toMutableList() }
            .toMutableMap()
        val order = columns.keys.sorted()
        val outgoing = edges.groupBy { it.from }
        val incoming = edges.groupBy { it.to }

        repeat(4) { pass ->
            val sweep = if (pass % 2 == 0) order else order.reversed()
            val position = mutableMapOf<String, Float>()
            columns.values.forEach { column ->
                column.forEachIndexed { index, node -> position[node.id] = index.toFloat() }
            }
            sweep.forEach { index ->
                val column = columns[index] ?: return@forEach
                val scored = column.mapIndexed { current, node ->
                    val neighbours = if (pass % 2 == 0) {
                        incoming[node.id].orEmpty().map { it.from }
                    } else {
                        outgoing[node.id].orEmpty().map { it.to }
                    }
                    val places = neighbours.mapNotNull { position[it] }
                    node to if (places.isEmpty()) current.toFloat() else places.average().toFloat()
                }
                val sorted = scored.sortedBy { it.second }.map { it.first }
                column.clear()
                column.addAll(sorted)
                column.forEachIndexed { i, node -> position[node.id] = i.toFloat() }
            }
        }

        val widthOf = { node: MapNode ->
            val chip = when {
                node.kind == MapNodeKind.Route -> 30f
                node.kind == MapNodeKind.Group -> 20f
                node.kind == MapNodeKind.Middleware && node.uses > 1 -> 22f
                else -> 0f
            }
            val chrome = SIDE_PADDING + chip +
                (if (node.kind == MapNodeKind.Route) DOT_ROOM else GLYPH_ROOM)
            (chrome + node.label.length * CHAR_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)
        }
        val columnWidth = order.associateWith { index ->
            columns[index].orEmpty().maxOfOrNull(widthOf) ?: MIN_WIDTH
        }
        val columnX = mutableMapOf<Int, Float>()
        var cursor = MARGIN
        order.forEach { index ->
            columnX[index] = cursor
            cursor += (columnWidth[index] ?: MIN_WIDTH) + COLUMN_GAP
        }

        val height = mutableMapOf<String, Float>()
        val desired = mutableMapOf<String, Float>()
        val anchor = columns[1] ?: columns[order.first()].orEmpty()
        var y = MARGIN
        anchor.forEach { node ->
            desired[node.id] = y
            y += NODE_HEIGHT + ROW_GAP
        }

        val preds = edges.groupBy({ it.to }, { it.from })
        val succs = edges.groupBy({ it.from }, { it.to })
        val anchorColumn = anchor.firstOrNull()?.let { columnOf(it.kind) } ?: 0

        fun settle(index: Int, neighbours: Map<String, List<String>>) {
            val column = columns[index] ?: return
            val wanted = column.map { node ->
                val places = neighbours[node.id].orEmpty().mapNotNull { desired[it] }
                node to (places.average().takeIf { !it.isNaN() }?.toFloat() ?: MARGIN)
            }.sortedBy { it.second }
            var floor = MARGIN
            wanted.forEach { (node, want) ->
                val place = maxOf(want, floor)
                desired[node.id] = place
                floor = place + NODE_HEIGHT + ROW_GAP
            }
            column.clear()
            column.addAll(wanted.map { it.first })
        }

        order.filter { it > anchorColumn }.sorted().forEach { settle(it, preds) }
        order.filter { it < anchorColumn }.sortedDescending().forEach { settle(it, succs) }

        val placed = mutableListOf<MapNode>()
        var widest = 0f
        var tallest = 0f
        order.forEach { index ->
            columns[index].orEmpty().forEach { node ->
                val x = columnX[index] ?: MARGIN
                val width = widthOf(node)
                val top = desired[node.id] ?: MARGIN
                placed += node.copy(
                    column = index,
                    x = x,
                    y = top,
                    width = width,
                    height = NODE_HEIGHT,
                )
                widest = maxOf(widest, x + width + MARGIN)
                tallest = maxOf(tallest, top + NODE_HEIGHT + MARGIN)
                height[node.id] = top
            }
        }

        return RouteMapGraph(nodes = placed, edges = edges, width = widest, height = tallest)
    }
}

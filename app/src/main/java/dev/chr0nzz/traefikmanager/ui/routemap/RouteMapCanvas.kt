package dev.chr0nzz.traefikmanager.ui.routemap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chr0nzz.traefikmanager.data.model.MapHealth
import dev.chr0nzz.traefikmanager.data.model.MapNode
import dev.chr0nzz.traefikmanager.data.model.MapNodeKind
import dev.chr0nzz.traefikmanager.data.model.RouteMapGraph
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import kotlin.math.max
import kotlin.math.min

@Composable
fun RouteMapCanvas(
    graph: RouteMapGraph,
    focusIds: Set<String>,
    onTap: (MapNode?) -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    interactive: Boolean = true,
) {
    val palette = LocalTmPalette.current
    val measurer = rememberTextMeasurer()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val fitted = remember(graph, viewport) {
        if (viewport.width == 0 || graph.width <= 0f) {
            1f
        } else {
            min(viewport.width / graph.width, viewport.height / max(graph.height, 1f))
                .coerceIn(0.3f, 2.5f)
        }
    }
    var zoom by remember(graph) { mutableFloatStateOf(1f) }
    var offset by remember(graph) { mutableStateOf(Offset.Zero) }
    val scale = fitted * zoom

    val nodeColour: (MapNode) -> Color = { node ->
        when (node.kind) {
            MapNodeKind.EntryPoint -> palette.teal
            MapNodeKind.Route -> when (node.route?.protocol?.lowercase()) {
                "tcp" -> palette.green
                "udp" -> palette.yellow
                else -> palette.blue
            }
            MapNodeKind.Group -> palette.muted
            MapNodeKind.Middleware -> palette.purple
            MapNodeKind.Service -> palette.orange
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(graph, interactive) {
                if (!interactive) return@pointerInput
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val before = fitted * zoom
                    val next = (zoom * gestureZoom).coerceIn(0.4f, 6f)
                    val after = fitted * next
                    offset = (offset + centroid) * (after / before) - centroid - pan * (after / before)
                    zoom = next
                }
            }
            .pointerInput(graph, focusIds, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { position ->
                    val centreX = ((size.width - graph.width * scale) / 2f).coerceAtLeast(0f)
                    val centreY = ((size.height - graph.height * scale) / 2f).coerceAtLeast(0f)
                    val world = (position + offset - Offset(centreX, centreY)) / scale
                    val hit = graph.nodes.lastOrNull { node ->
                        Rect(node.x, node.y, node.x + node.width, node.y + node.height)
                            .contains(Offset(world.x, world.y))
                    }
                    onTap(hit)
                }
            },
    ) {
        if (graph.nodes.isEmpty()) return@Canvas

        val centreX = ((size.width - graph.width * scale) / 2f).coerceAtLeast(0f)
        val centreY = ((size.height - graph.height * scale) / 2f).coerceAtLeast(0f)
        translate(left = centreX - offset.x, top = centreY - offset.y) {
            scale(scale = scale, pivot = Offset.Zero) {
                graph.edges.forEach { edge ->
                    val from = graph.byId[edge.from] ?: return@forEach
                    val to = graph.byId[edge.to] ?: return@forEach
                    val lit = focusIds.isEmpty() || (edge.from in focusIds && edge.to in focusIds)
                    val colour = when (to.kind) {
                        MapNodeKind.Middleware -> palette.purple
                        MapNodeKind.Service -> palette.orange
                        else -> palette.teal
                    }
                    drawEdge(from, to, colour, palette.border, lit, to.kind == MapNodeKind.Service)
                }
                graph.nodes.forEach { node ->
                    val lit = focusIds.isEmpty() || node.id in focusIds
                    drawNode(
                        highlighted = node.id == highlight,
                        node = node,
                        accent = nodeColour(node),
                        measurer = measurer,
                        text = palette.text,
                        muted = palette.muted,
                        card = palette.card,
                        border = palette.border,
                        health = when (node.health) {
                            MapHealth.Down -> palette.red
                            MapHealth.Warn -> palette.yellow
                            MapHealth.Idle -> palette.muted
                            MapHealth.Quiet -> palette.green
                        },
                        lit = lit,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawEdge(
    from: MapNode,
    to: MapNode,
    colour: Color,
    quiet: Color,
    lit: Boolean,
    toService: Boolean,
) {
    val startX = from.x + from.width
    val startY = from.centreY
    val endX = to.x
    val endY = to.centreY
    val control = (endX - startX) / 2f
    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(startX + control, startY, endX - control, endY, endX, endY)
    }
    drawPath(
        path = path,
        color = if (lit) colour.copy(alpha = if (toService) 0.5f else 0.6f) else quiet.copy(alpha = 0.2f),
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawNode(
    highlighted: Boolean,
    node: MapNode,
    accent: Color,
    measurer: TextMeasurer,
    text: Color,
    muted: Color,
    card: Color,
    border: Color,
    health: Color,
    lit: Boolean,
) {
    val alpha = if (lit) 1f else 0.22f
    val radius = androidx.compose.ui.geometry.CornerRadius(9f, 9f)
    drawRoundRect(
        color = card.copy(alpha = alpha),
        topLeft = Offset(node.x, node.y),
        size = Size(node.width, node.height),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = accent.copy(alpha = if (highlighted) 1f else alpha * 0.55f),
        topLeft = Offset(node.x, node.y),
        size = Size(node.width, node.height),
        cornerRadius = radius,
        style = Stroke(width = if (highlighted) 2.2f else 1.2f),
    )

    var cursor = node.x + 8f
    if (node.kind != MapNodeKind.Route) {
        drawGlyph(node.kind, cursor, node.y + node.height / 2f, accent.copy(alpha = alpha))
        cursor += 17f
    }
    val chip = chipFor(node)
    if (chip != null) {
        val chipText = measurer.measure(
            text = chip,
            style = TextStyle(
                color = accent.copy(alpha = alpha),
                fontSize = (8f / density).sp,
                fontFamily = MonoFamily,
            ),
            maxLines = 1,
        )
        val chipWidth = chipText.size.width + 8f
        drawRoundRect(
            color = accent.copy(alpha = alpha * 0.16f),
            topLeft = Offset(cursor, node.y + node.height / 2f - 8f),
            size = Size(chipWidth, 16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        )
        drawText(chipText, topLeft = Offset(cursor + 4f, node.y + node.height / 2f - chipText.size.height / 2f))
        cursor += chipWidth + 6f
    }

    val dotRoom = if (node.kind == MapNodeKind.Route) 12f else 0f
    val available = (node.x + node.width - 8f - dotRoom - cursor).toInt().coerceAtLeast(1)
    val label = measurer.measure(
        text = node.label,
        style = TextStyle(color = text.copy(alpha = alpha), fontSize = (11f / density).sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = available),
    )
    drawText(label, topLeft = Offset(cursor, node.y + node.height / 2f - label.size.height / 2f))

    if (node.kind == MapNodeKind.Route) {
        drawCircle(
            color = health.copy(alpha = alpha),
            radius = 3f,
            center = Offset(node.x + node.width - 9f, node.y + node.height / 2f),
        )
    }
}

private fun chipFor(node: MapNode): String? = when (node.kind) {
    MapNodeKind.Route -> node.route?.protocol?.uppercase() ?: "HTTP"
    MapNodeKind.Group -> node.members.size.toString()
    MapNodeKind.Middleware -> node.uses.takeIf { it > 1 }?.let { "${it}\u00d7" }
    MapNodeKind.Service, MapNodeKind.EntryPoint -> null
}

private fun DrawScope.drawGlyph(kind: MapNodeKind, left: Float, centreY: Float, colour: Color) {
    val stroke = Stroke(width = 1.1f)
    when (kind) {
        MapNodeKind.EntryPoint -> {
            drawRect(
                color = colour,
                topLeft = Offset(left + 1f, centreY - 6f),
                size = Size(9f, 12f),
                style = stroke,
            )
            drawCircle(color = colour, radius = 1.1f, center = Offset(left + 7.5f, centreY))
        }
        MapNodeKind.Middleware -> listOf(-4f, 0f, 4f).forEach { dy ->
            drawLine(
                color = colour,
                start = Offset(left, centreY + dy),
                end = Offset(left + 11f, centreY + dy),
                strokeWidth = 1.4f,
            )
        }
        MapNodeKind.Service -> listOf(-6f, 0.5f).forEach { dy ->
            drawRect(
                color = colour,
                topLeft = Offset(left, centreY + dy),
                size = Size(11f, 5.5f),
                style = stroke,
            )
        }
        MapNodeKind.Group -> listOf(0f to 0f, 6f to 0f, 0f to 6f, 6f to 6f).forEach { (dx, dy) ->
            drawRect(
                color = colour,
                topLeft = Offset(left + dx, centreY - 6f + dy),
                size = Size(4.5f, 4.5f),
                style = stroke,
            )
        }
        MapNodeKind.Route -> Unit
    }
}

package dev.chr0nzz.traefikmanager.ui.routemap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.MapNode
import dev.chr0nzz.traefikmanager.data.model.MapNodeKind
import dev.chr0nzz.traefikmanager.data.model.RouteMapGraph
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSheet(
    node: MapNode,
    graph: RouteMapGraph,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState()
    val feeds = graph.edges.filter { it.to == node.id }.mapNotNull { graph.byId[it.from] }
    val serves = graph.edges.filter { it.from == node.id }.mapNotNull { graph.byId[it.to] }
    val routes = graph.subgraph(node.id).let { chain ->
        chain.nodes.count { it.kind == MapNodeKind.Route } +
            chain.nodes.filter { it.kind == MapNodeKind.Group }.sumOf { it.members.size }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TmSpacing.lg)
                .padding(bottom = TmSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = kindLabel(node),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.bg)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            TmCard {
                when (node.kind) {
                    MapNodeKind.Route -> {
                        val route = node.route
                        DetailRow("Protocol", route?.protocol?.uppercase().orEmpty().ifEmpty { "HTTP" })
                        if (node.detail.isNotEmpty()) DetailRow("Host", node.detail, mono = true)
                        route?.rule?.takeIf { it.isNotEmpty() }?.let { DetailRow("Rule", it, mono = true) }
                        DetailRow("Entry points", feeds.joinToString(", ") { it.label }.ifEmpty { "none" })
                        val middlewares = serves.filter { it.kind == MapNodeKind.Middleware }
                        DetailRow(
                            label = "Middlewares",
                            value = middlewares.joinToString(", ") { it.label }.ifEmpty { "none" },
                        )
                        DetailRow(
                            label = "Service",
                            value = route?.serviceName.orEmpty().ifEmpty { "none" },
                            mono = true,
                        )
                        DetailRow("Provider", route?.provider.orEmpty().ifEmpty { "file" }, last = true)
                    }
                    MapNodeKind.Middleware -> {
                        DetailRow("Used by", if (routes == 1) "1 route" else "$routes routes")
                        DetailRow(
                            label = "Routers",
                            value = feeds.joinToString(", ") { it.label }.ifEmpty { "none" },
                            last = true,
                        )
                    }
                    MapNodeKind.Service -> {
                        if (node.detail.isNotEmpty()) DetailRow("Target", node.detail, mono = true)
                        DetailRow("Routes", routes.toString())
                        DetailRow(
                            label = "Reached via",
                            value = feeds.joinToString(", ") { it.label }.ifEmpty { "directly" },
                            last = true,
                        )
                    }
                    MapNodeKind.EntryPoint -> {
                        DetailRow("Routers", serves.size.toString())
                        DetailRow("Routes", routes.toString(), last = true)
                    }
                    MapNodeKind.Group -> {
                        DetailRow("Provider", node.label)
                        DetailRow("Routes", node.members.size.toString())
                        DetailRow(
                            label = "Services",
                            value = serves.joinToString(", ") { it.label }.ifEmpty { "none" },
                            last = true,
                        )
                    }
                }
            }

            if (node.kind == MapNodeKind.Route && node.route != null) {
                OutlinedButton(
                    onClick = { onEdit(node.route.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Edit route") }
            }
        }
    }
}

private fun kindLabel(node: MapNode): String = when (node.kind) {
    MapNodeKind.EntryPoint -> "ENTRY POINT"
    MapNodeKind.Route -> "ROUTER"
    MapNodeKind.Group -> "PROVIDER"
    MapNodeKind.Middleware -> "MIDDLEWARE"
    MapNodeKind.Service -> "SERVICE"
}

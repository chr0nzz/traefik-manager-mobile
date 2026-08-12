package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dev.chr0nzz.traefikmanager.ui.components.TooltipIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.HealthLabel
import dev.chr0nzz.traefikmanager.ui.components.MessageState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmRadius
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RouteDetailPane(
    route: Route?,
    showBack: Boolean,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onToggle: (Route) -> Unit = {},
    onEdit: (Route) -> Unit = {},
    onDelete: (Route) -> Unit = {},
    onEditYaml: (Route) -> Unit = {},
    onPing: (Route) -> Unit = {},
    ping: PingState? = null,
    modifier: Modifier = Modifier,
) {
    if (route == null) {
        MessageState(
            icon = Icons.Outlined.TouchApp,
            headline = "Select a route",
            body = "Pick a route from the list to see its configuration.",
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(top = TmSpacing.xxl),
        )
        return
    }

    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = TmSpacing.lg, end = TmSpacing.lg, top = TmSpacing.lg, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to routes")
                }
            }
            Text(
                text = route.protocol.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = when (route.protocol) {
                    "udp" -> palette.orange
                    "tcp" -> palette.teal
                    else -> palette.blue
                },
            )
            Text(
                text = route.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (route.provider == "file") {
                Switch(
                    checked = route.enabled,
                    onCheckedChange = { onToggle(route) },
                    thumbContent = if (route.enabled) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (route.enabled) {
                            "Disable ${route.name}"
                        } else {
                            "Enable ${route.name}"
                        }
                    },
                )
            }
        }

        TrafficFlow(route)

        if (route.protocol == "http" && route.hosts.isNotEmpty()) {
            TmCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SectionLabel("Reachability", modifier = Modifier.weight(1f))
                    when {
                        ping?.running == true -> Text(
                            text = "Checking",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted,
                        )
                        ping?.ok == true -> HealthLabel(TmStatus.Ok, ping.detail.ifEmpty { "reachable" })
                        ping?.ok == false -> HealthLabel(TmStatus.Error, ping.detail.ifEmpty { "unreachable" })
                        else -> TextButton(onClick = { onPing(route) }) { Text("Ping") }
                    }
                    if (ping != null && ping.running.not()) {
                        IconButton(onClick = { onPing(route) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Ping again")
                        }
                    }
                }
            }
        }

        DetailSection("Router Details", Icons.Outlined.Info, palette.blue) {
            DetailRow("Status", if (route.enabled) "Enabled" else "Disabled", status = if (route.enabled) TmStatus.Ok else TmStatus.Disabled)
            DetailRow("Provider", route.provider)
            if (route.rule.isNotEmpty()) DetailRow("Rule", route.rule, mono = true)
            DetailRow("Name", route.name, mono = true)
            DetailRow("Entry points", route.entryPointNames.joinToString(", ").ifEmpty { "none" })
            DetailRow("Service", route.serviceName.ifEmpty { "none" }, mono = true)
            route.priority?.let { DetailRow("Priority", it.toString()) }
            if (route.configFile.isNotEmpty()) DetailRow("Config file", route.configFile, mono = true, last = true)
        }

        if (route.protocol != "udp") {
            DetailSection("TLS", Icons.Outlined.Shield, palette.green) {
                DetailRow("TLS", if (route.tlsEnabled) "Enabled" else "Disabled")
                DetailRow("Certificate resolver", route.certResolver.ifEmpty { "none" })
                DetailRow("Options", route.tlsOptionsProfile.ifEmpty { "default" })
                DetailRow("Skip verify", if (route.insecureSkipVerify) "Yes" else "No", last = true)
            }
        }

        DetailSection("Middlewares", Icons.Outlined.Extension, palette.purple) {
            val middlewares = route.middlewareNames +
                route.entrypointMiddlewares.map { "${it.substringBefore('@')} (entry point)" }
            if (middlewares.isEmpty()) {
                Text(
                    text = "No middlewares configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TmSpacing.lg),
                )
            } else {
                middlewares.forEachIndexed { index, name ->
                    DetailRow("", name, mono = true, last = index == middlewares.lastIndex)
                }
            }
        }

        DetailSection("Service", Icons.Outlined.Bolt, palette.yellow) {
            DetailRow("Type", if (route.serviceType == "loadBalancer") "Load Balancer" else route.serviceType)
            route.passHostHeader?.let { DetailRow("Pass host header", it.toString()) }
            val servers = route.servers.ifEmpty { listOf(route.target) }
            servers.forEachIndexed { index, server ->
                DetailRow(
                    label = if (servers.size == 1) "Server" else "Server ${index + 1}",
                    value = server,
                    mono = true,
                    last = index == servers.lastIndex,
                )
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(bottom = TmSpacing.lg),
        ) {
            if (route.provider == "file") {
                TooltipIconButton(
                    label = "Edit route",
                    icon = Icons.Outlined.Edit,
                    onClick = { onEdit(route) },
                )
                TooltipIconButton(
                    label = "Delete route",
                    icon = Icons.Outlined.Delete,
                    onClick = { onDelete(route) },
                )
                TooltipIconButton(
                    label = "Edit raw YAML",
                    icon = Icons.Outlined.Code,
                    onClick = { onEditYaml(route) },
                )
            }
            val host = route.hosts.firstOrNull()
            if (host != null) {
                TooltipIconButton(
                    label = "Open $host",
                    icon = Icons.Outlined.OpenInNew,
                    onClick = { uriHandler.openUri("https://$host") },
                )
            }
            TooltipIconButton(
                label = "Copy target",
                icon = Icons.Outlined.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(route.target.ifEmpty { route.name })) },
            )
        }
    }
}

@Composable
private fun TrafficFlow(route: Route) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Traffic flow")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.sm),
        ) {
            FlowBox(
                label = "Entry point",
                title = (route.entryPointNames.firstOrNull() ?: "none").uppercase(),
                lines = listOfNotNull(
                    route.entryPointNames.getOrNull(1)?.let { "+${route.entryPointNames.size - 1} more" },
                    if (route.tlsEnabled) "TLS" else null,
                ),
                lineColor = palette.green,
                modifier = Modifier.weight(1f),
            )
            FlowArrow()
            FlowBox(
                label = "Router",
                title = route.name,
                lines = listOfNotNull(
                    if (route.tlsEnabled) "TLS" else null,
                    if (route.enabled) "Enabled" else "Disabled",
                ),
                lineColor = if (route.enabled) palette.green else palette.muted,
                highlighted = true,
                modifier = Modifier.weight(1f),
            )
            FlowArrow()
            FlowBox(
                label = "Service",
                title = route.serviceName.ifEmpty { "none" },
                mono = route.target.takeIf { it.isNotEmpty() && it != "N/A" },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FlowArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
        contentDescription = null,
        tint = LocalTmPalette.current.muted,
        modifier = Modifier
            .size(14.dp)
            .padding(horizontal = 1.dp),
    )
}

@Composable
private fun FlowBox(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    lines: List<String> = emptyList(),
    lineColor: Color? = null,
    mono: String? = null,
    highlighted: Boolean = false,
) {
    val palette = LocalTmPalette.current
    val shape = RoundedCornerShape(TmRadius.sm)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (highlighted) palette.blue.copy(alpha = 0.08f) else palette.bg)
            .then(
                if (highlighted) Modifier.border(1.dp, palette.blue, shape) else Modifier,
            )
            .padding(vertical = TmSpacing.sm, horizontal = TmSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = if (highlighted) palette.blue else palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = lineColor ?: palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (mono != null) {
            Text(
                text = mono,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.green,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.card)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: ImageVector,
    tint: Color,
    content: @Composable () -> Unit,
) {
    TmCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = TmSpacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    status: TmStatus? = null,
    last: Boolean = false,
) {
    val palette = LocalTmPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TmSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotEmpty()) {
            SectionLabel(label, modifier = Modifier.weight(0.42f))
        }
        Row(
            modifier = Modifier.weight(if (label.isEmpty()) 1f else 0.58f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
        ) {
            if (status != null) {
                StatusDot(status = status, size = 6.dp)
            }
            Text(
                text = value,
                style = if (mono) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (status == TmStatus.Ok) palette.green else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (!last) CardDivider()
}

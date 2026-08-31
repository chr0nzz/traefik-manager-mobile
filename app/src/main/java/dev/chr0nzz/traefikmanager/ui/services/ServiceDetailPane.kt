package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.ServerHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceRow
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.MessageState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.TooltipIconButton
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServiceDetailPane(
    service: ServiceRow?,
    showBack: Boolean,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    authorable: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onOwnership: (Boolean) -> Unit = {},
) {
    if (service == null) {
        MessageState(
            icon = Icons.Outlined.TouchApp,
            headline = "Select a service",
            body = "Pick one from the list to see its backends.",
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(top = TmSpacing.xxl),
        )
        return
    }

    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current

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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to services")
                    }
                }
                Text(
                    text = service.proto.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = protocolColor(service.proto),
                )
                Text(
                    text = service.shortName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
            }

            TmCard {
                CardHeader(
                    icon = if (service.composite.isNotEmpty()) Icons.Outlined.Hub else Icons.Outlined.Info,
                    tint = palette.blue,
                    title = "Service details",
                )
                DetailRow("Type", service.kindLabel, mono = true)
                if (service.composite.isNotEmpty()) {
                    DetailRow(
                        "Managed",
                        if (service.owned) "by Traefik Manager" else "in the config file",
                    )
                }
                DetailRow("Provider", service.provider, mono = true)
                DetailRow(
                    label = "Status",
                    value = when (service.health) {
                        ServiceHealth.Ok -> "Success"
                        ServiceHealth.Warning -> "Warning"
                        ServiceHealth.Error -> "Error"
                    },
                    valueColor = when (service.health) {
                        ServiceHealth.Ok -> palette.green
                        ServiceHealth.Warning -> palette.yellow
                        ServiceHealth.Error -> palette.red
                    },
                    last = service.passHostHeader == null,
                )
                service.passHostHeader?.let { pass ->
                    DetailRow(
                        label = "Pass host header",
                        value = if (pass) "Yes" else "No",
                        valueColor = if (pass) palette.green else palette.muted,
                        last = true,
                    )
                }
            }

            if (service.errors.isNotEmpty()) {
                SectionLabel("Errors")
                TmCard(accent = TmStatus.Error) {
                    service.errors.forEach { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            color = palette.red,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }

            if (service.servers.isNotEmpty() || service.composite.isEmpty()) {
                TmCard {
                    CardHeader(
                        icon = Icons.Outlined.Public,
                        tint = palette.green,
                        title = "Servers",
                        trailing = service.servers.size.toString().takeIf { service.servers.isNotEmpty() },
                    )
                    if (service.servers.isEmpty()) {
                        Text(
                            text = "No servers configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted,
                            modifier = Modifier.padding(top = TmSpacing.xs),
                        )
                    } else {
                        service.servers.forEachIndexed { index, server ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = TmSpacing.sm),
                            ) {
                                StatusDot(
                                    when (server.health) {
                                        ServerHealth.Up -> TmStatus.Ok
                                        ServerHealth.Down -> TmStatus.Error
                                        ServerHealth.Unknown -> TmStatus.Unknown
                                    },
                                )
                                Text(
                                    text = server.target,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = when (server.health) {
                                        ServerHealth.Up -> "Up"
                                        ServerHealth.Down -> "Down"
                                        ServerHealth.Unknown -> "Unchecked"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (server.health) {
                                        ServerHealth.Up -> palette.green
                                        ServerHealth.Down -> palette.red
                                        ServerHealth.Unknown -> palette.muted
                                    },
                                )
                            }
                            if (index < service.servers.lastIndex) CardDivider()
                        }
                    }
                }
            }

            if (service.composite.isNotEmpty()) {
                TmCard {
                    CardHeader(
                        icon = Icons.AutoMirrored.Outlined.CallSplit,
                        tint = palette.orange,
                        title = "Targets",
                        trailing = service.composite.size.toString(),
                    )
                    service.composite.forEachIndexed { index, target ->
                        Text(
                            text = target,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = TmSpacing.sm),
                        )
                        if (index < service.composite.lastIndex) CardDivider()
                    }
                }
            }

            TmCard {
                CardHeader(
                    icon = Icons.Outlined.Dns,
                    tint = palette.purple,
                    title = "Used by routers",
                    trailing = service.usedBy.size.toString().takeIf { service.usedBy.isNotEmpty() },
                )
                if (service.usedBy.isEmpty()) {
                    Text(
                        text = "No router references this service",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                } else {
                    service.usedBy.forEachIndexed { index, router ->
                        Text(
                            text = router,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = TmSpacing.sm),
                        )
                        if (index < service.usedBy.lastIndex) CardDivider()
                    }
                }
            }
        }

        val composite = service.composite.isNotEmpty()
        val fileProvider = service.provider == "file"
        val canAuthor = authorable && fileProvider
        if (service.servers.isNotEmpty() || canAuthor) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .padding(bottom = TmSpacing.lg),
            ) {
                if (service.servers.isNotEmpty()) {
                    TooltipIconButton(
                        label = "Copy backend URLs",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString(service.servers.joinToString("\n") { it.target }))
                        },
                    )
                }
                if (canAuthor && composite) {
                    TooltipIconButton(
                        label = if (service.owned) "Stop managing" else "Manage this service",
                        icon = if (service.owned) Icons.Outlined.LinkOff else Icons.Outlined.Link,
                        onClick = { onOwnership(!service.owned) },
                    )
                }
                if (canAuthor && (service.owned || !composite)) {
                    TooltipIconButton(
                        label = "Edit service",
                        icon = Icons.Outlined.Edit,
                        onClick = onEdit,
                    )
                    TooltipIconButton(
                        label = "Delete service",
                        icon = Icons.Outlined.Delete,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    trailing: String? = null,
) {
    val palette = LocalTmPalette.current
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
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
        }
    }
}

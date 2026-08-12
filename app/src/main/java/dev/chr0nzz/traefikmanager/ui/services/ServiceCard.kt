package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chr0nzz.traefikmanager.data.model.ServerHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceProtocol
import dev.chr0nzz.traefikmanager.data.model.ServiceRow
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.IconTile
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.ValueRow
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

fun ServiceHealth.asTmStatus(): TmStatus = when (this) {
    ServiceHealth.Ok -> TmStatus.Ok
    ServiceHealth.Warning -> TmStatus.Warn
    ServiceHealth.Error -> TmStatus.Error
}

@Composable
fun protocolColor(proto: ServiceProtocol): Color {
    val palette = LocalTmPalette.current
    return when (proto) {
        ServiceProtocol.Http -> palette.blue
        ServiceProtocol.Tcp -> palette.teal
        ServiceProtocol.Udp -> palette.orange
    }
}

@Composable
fun ServiceCard(
    service: ServiceRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val status = service.health.asTmStatus()

    TmCard(
        modifier = modifier,
        accent = if (status != TmStatus.Ok) status else null,
        accentColor = if (selected && status == TmStatus.Ok) palette.blue else null,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconTile(
                icon = if (service.composite.isNotEmpty()) Icons.Outlined.Hub else Icons.Outlined.Dns,
                status = status,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                ) {
                    if (service.proto != ServiceProtocol.Http) {
                        Text(
                            text = service.proto.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = protocolColor(service.proto),
                        )
                    }
                    Text(
                        text = service.shortName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${service.kindLabel} · ${service.provider}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val targets = service.servers.map { it.target to it.health } +
            service.composite.map { it to ServerHealth.Unknown }
        targets.take(2).forEach { (target, health) ->
            ValueRow(
                icon = Icons.Outlined.SubdirectoryArrowRight,
                value = target,
                color = when (health) {
                    ServerHealth.Up -> palette.green
                    ServerHealth.Down -> palette.red
                    ServerHealth.Unknown -> palette.muted
                },
            )
        }
        if (targets.size > 2) {
            Text(
                text = "+${targets.size - 2} more",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = TmSpacing.xs),
            )
        }

        CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.xs),
        ) {
            Text(
                text = service.metaParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (service.backendsTotal > 0 && !service.allBackendsUp) palette.orange else palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            service.usedByLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
    }
}

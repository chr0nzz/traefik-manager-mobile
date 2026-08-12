package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

fun Route.status(): TmStatus = when {
    !enabled -> TmStatus.Disabled
    target == "N/A" || backendCount == 0 -> TmStatus.Error
    else -> TmStatus.Ok
}

@Composable
fun RouteCard(
    route: Route,
    selected: Boolean,
    toggling: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val status = route.status()
    TmCard(
        modifier = modifier,
        accent = if (selected || status != TmStatus.Ok) status else null,
        dimmed = !route.enabled,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            dev.chr0nzz.traefikmanager.ui.components.StatusDot(status)
            if (route.protocol != "http") {
                Text(
                    text = route.protocol.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (route.protocol == "udp") palette.orange else palette.teal,
                )
            }
            Text(
                text = route.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (route.insecureSkipVerify) {
                Glyph(Icons.Outlined.ArrowOutward, palette.orange)
            }
            if (route.protocol != "udp") {
                if (route.tlsEnabled) Glyph(Icons.Outlined.Lock, palette.muted)
                else Glyph(Icons.Outlined.LockOpen, palette.yellow)
            }
            if (route.provider == "file") {
                Switch(
                    checked = route.enabled,
                    onCheckedChange = { onToggle() },
                    enabled = !toggling,
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

        val hosts = route.hosts
        if (hosts.isNotEmpty()) {
            ValueRow(
                icon = Icons.Outlined.Language,
                value = hosts.first(),
                color = palette.blue,
                extra = if (hosts.size > 1) "+${hosts.size - 1}" else null,
            )
        } else if (route.rule.isNotEmpty()) {
            ValueRow(icon = Icons.Outlined.Language, value = route.rule, color = palette.muted)
        }

        ValueRow(
            icon = Icons.Outlined.SubdirectoryArrowRight,
            value = route.target,
            color = if (route.target == "N/A") palette.red else palette.green,
            extra = if (route.backendCount > 1) "+${route.backendCount - 1}" else null,
        )

        val meta = buildList {
            addAll(route.entryPointNames)
            addAll(route.middlewareNames)
            if (route.serviceName.isNotEmpty()) add(route.serviceName)
        }
        if (meta.isNotEmpty()) {
            CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = TmSpacing.xs),
            )
        }
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(14.dp),
    )
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    extra: String? = null,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = Modifier.padding(top = TmSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (extra != null) {
            Text(
                text = extra,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
        }
    }
}

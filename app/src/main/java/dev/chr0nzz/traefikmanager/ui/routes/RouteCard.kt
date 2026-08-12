package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.ValueRow
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
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
            RouteLeading(status = status, iconUrl = iconUrl)
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
private fun RouteLeading(status: TmStatus, iconUrl: String?) {
    if (iconUrl == null) {
        dev.chr0nzz.traefikmanager.ui.components.StatusDot(status)
        return
    }
    var failed by remember(iconUrl) { mutableStateOf(false) }
    if (failed) {
        dev.chr0nzz.traefikmanager.ui.components.StatusDot(status)
        return
    }
    Box(modifier = Modifier.size(22.dp)) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            onError = { failed = true },
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        dev.chr0nzz.traefikmanager.ui.components.StatusDot(
            status = status,
            size = 7.dp,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
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

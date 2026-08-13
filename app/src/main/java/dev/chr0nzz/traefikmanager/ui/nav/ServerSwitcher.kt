package dev.chr0nzz.traefikmanager.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.chr0nzz.traefikmanager.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/**
 * The drawer header doubles as the server switcher: it names the server every screen is
 * currently reading from, and expands into the host plus each agent with its health.
 */
@Composable
fun ServerSwitcherHeader(
    servers: List<ServerEntry>,
    active: ServerEntry?,
    expanded: Boolean,
    switching: Boolean,
    onToggle: () -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val hasAgents = servers.size > 1

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_tm_logo),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Traefik Manager",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(if (hasAgents) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            HealthDot(reachable = active?.reachable ?: true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = active?.name ?: "Host",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        switching -> "switching…"
                        else -> active?.detail.orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasAgents) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Hide servers" else "Switch server",
                    tint = palette.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded && hasAgents) {
            Column(modifier = Modifier.fillMaxWidth()) {
                servers.forEach { server ->
                    val selected = server.id == active?.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .clickable { onSelect(server.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = if (server.isHost) Icons.Outlined.Home else Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = if (selected) palette.blue else palette.muted,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = server.detail,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = if (server.reachable) palette.muted else palette.red,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!server.isHost) HealthDot(reachable = server.reachable)
                        if (selected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Active server",
                                tint = palette.blue,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = TmSpacing.sm))
            }
        }
    }
}

@Composable
private fun HealthDot(reachable: Boolean) {
    val palette = LocalTmPalette.current
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (reachable) palette.green else palette.red)
            .clearAndSetSemantics { contentDescription = if (reachable) "reachable" else "unreachable" },
    )
}

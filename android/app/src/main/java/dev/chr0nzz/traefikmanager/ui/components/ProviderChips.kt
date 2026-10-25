package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.repo.ProviderCount
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmRadius
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

fun providerIcon(name: String): ImageVector = when {
    name.startsWith("docker") || name.startsWith("swarm") -> Icons.Outlined.Inventory2
    name.startsWith("kubernetes") -> Icons.Outlined.Hub
    name.startsWith("file") -> Icons.Outlined.Description
    name.startsWith("internal") -> Icons.Outlined.Settings
    name.startsWith("http") -> Icons.Outlined.Link
    name.startsWith("consul") || name.startsWith("redis") || name.startsWith("etcd") ||
        name.startsWith("zookeeper") -> Icons.Outlined.Storage
    name.startsWith("plugin") -> Icons.Outlined.Extension
    else -> Icons.Outlined.Power
}

@Composable
fun ProviderRow(
    providers: List<ProviderCount>,
    activeProvider: String?,
    onProviderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        providers.forEach { provider ->
            val active = provider.name == activeProvider
            val tint = when {
                provider.worst == TmStatus.Error -> palette.red
                provider.worst == TmStatus.Warn -> palette.yellow
                active -> palette.blue
                else -> palette.muted
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(TmRadius.sm))
                    .background(if (active) palette.blue.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onProviderClick(provider.name) }
                    .padding(horizontal = TmSpacing.sm, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = providerIcon(provider.name),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = if (active) palette.blue else palette.muted,
                )
                Text(
                    text = provider.count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (active) palette.blue else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

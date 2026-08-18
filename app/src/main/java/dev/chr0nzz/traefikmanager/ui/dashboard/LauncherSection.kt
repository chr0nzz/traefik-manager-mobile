package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.LauncherApp
import dev.chr0nzz.traefikmanager.data.model.LauncherGroup
import dev.chr0nzz.traefikmanager.data.model.RouteIcons
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/** Rows or tiles, the web's dashPodDensity. */
enum class LauncherDensity { List, Icons }

/**
 * Five across only when the card is wide enough for it. On a phone, and in any group card that is
 * sharing a row with another, five tiles leave about 60dp each, which is why it read as cramped.
 */
private const val TILE_ROWS = 5
private const val ROW_LIMIT = 5

private fun tileColumns(cardWidth: Dp): Int = when {
    cardWidth >= 560.dp -> 5
    cardWidth >= 300.dp -> 4
    else -> 3
}

/**
 * The dashboard launcher, drawn the way the web draws it: a group header whose rule runs to the
 * card's edge, then either tiles five across or rows. The status square sits on the icon plate's
 * corner, and a healthy one is quiet grey so a single red square reads as an alarm.
 *
 * [columns] lets a tablet put several groups side by side; each row of groups shares a height so
 * the cards line up.
 */
@Composable
fun LauncherSection(
    groups: List<LauncherGroup>,
    config: DashboardConfig,
    baseUrl: String,
    density: LauncherDensity,
    columns: Int,
    onOpen: (LauncherApp) -> Unit,
    onEdit: (LauncherApp) -> Unit,
    onInfo: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        groups.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                row.forEach { group ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        GroupCard(group, config, baseUrl, density, onOpen, onEdit, onInfo)
                    }
                }
                repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: LauncherGroup,
    config: DashboardConfig,
    baseUrl: String,
    density: LauncherDensity,
    onOpen: (LauncherApp) -> Unit,
    onEdit: (LauncherApp) -> Unit,
    onInfo: (LauncherApp) -> Unit,
) {
    val palette = LocalTmPalette.current
    val density2 = androidx.compose.ui.platform.LocalDensity.current
    var expanded by remember(group.name) { mutableStateOf(false) }
    var columnsForCard by remember { mutableStateOf(4) }
    val limit = if (density == LauncherDensity.Icons) columnsForCard * TILE_ROWS else ROW_LIMIT
    val shown = if (expanded) group.apps else group.apps.take(limit)
    val hidden = group.apps.size - shown.size

    TmCard(
        modifier = Modifier
            .fillMaxHeight()
            .onSizeChanged { size -> columnsForCard = tileColumns(with(density2) { size.width.toDp() }) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = groupIcon(group),
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = group.name.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = group.apps.size.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
            // The rule finishes the header, as it does on the web.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TmSpacing.xs)
                    .height(1.dp)
                    .background(palette.border),
            )
        }

        Box(modifier = Modifier.height(TmSpacing.md))

        when (density) {
            LauncherDensity.List -> shown.forEachIndexed { index, app ->
                AppRow(app, config, baseUrl, onOpen, onEdit, onInfo)
                if (index < shown.lastIndex) CardDivider()
            }
            LauncherDensity.Icons -> {
                TileGrid(shown, columnsForCard, config, baseUrl, onOpen, onEdit)
                Box(modifier = Modifier.height(TmSpacing.xs))
            }
        }

        if (hidden > 0 || expanded) {
            Text(
                text = if (expanded) "show less" else "$hidden more",
                style = MaterialTheme.typography.labelSmall,
                color = palette.blue,
                modifier = Modifier
                    .padding(top = TmSpacing.xs)
                    .combinedClickable { expanded = !expanded },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRow(
    app: LauncherApp,
    config: DashboardConfig,
    baseUrl: String,
    onOpen: (LauncherApp) -> Unit,
    onEdit: (LauncherApp) -> Unit,
    onInfo: (LauncherApp) -> Unit,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (app.url != null) onOpen(app) },
                onLongClick = { onEdit(app) },
            )
            .padding(vertical = 5.dp),
    ) {
        Plate(app, config, baseUrl, size = 34)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            app.reason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = app.host,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "About ${app.name}",
            tint = palette.muted,
            modifier = Modifier
                .combinedClickable { onInfo(app) }
                .size(16.dp),
        )
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit ${app.name}",
            tint = palette.muted,
            modifier = Modifier
                .combinedClickable { onEdit(app) }
                .size(16.dp),
        )
    }
}

@Composable
private fun TileGrid(
    apps: List<LauncherApp>,
    columns: Int,
    config: DashboardConfig,
    baseUrl: String,
    onOpen: (LauncherApp) -> Unit,
    onEdit: (LauncherApp) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.lg)) {
        apps.chunked(columns).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { app ->
                    Tile(app, config, baseUrl, onOpen, onEdit, Modifier.weight(1f))
                }
                repeat(columns - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(
    app: LauncherApp,
    config: DashboardConfig,
    baseUrl: String,
    onOpen: (LauncherApp) -> Unit,
    onEdit: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.combinedClickable(
            onClick = { if (app.url != null) onOpen(app) },
            onLongClick = { onEdit(app) },
        ),
    ) {
        // No affordances on a tile: a long press opens the editor, which is what the gesture is
        // for on a phone, and it keeps the grid clean.
        Plate(app, config, baseUrl, size = 52)
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun Plate(
    app: LauncherApp,
    config: DashboardConfig,
    baseUrl: String,
    size: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val icon = RouteIcons.urlFor(app.route, config, baseUrl)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.bg),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size((size - 8).dp),
                )
            } else {
                Text(
                    text = app.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.muted,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dotColour(app, palette.red, palette.yellow, palette.muted)),
        )
    }
}

/** Healthy is deliberately quiet: the web keeps green out of it so red carries all the weight. */
private fun dotColour(app: LauncherApp, red: Color, amber: Color, quiet: Color): Color = when {
    !app.route.enabled -> amber
    app.url == null -> quiet.copy(alpha = 0.3f)
    app.route.target.isEmpty() && app.route.servers.isEmpty() -> red
    else -> quiet.copy(alpha = 0.3f)
}

private fun groupIcon(group: LauncherGroup): ImageVector = when {
    group.custom -> Icons.Outlined.Sell
    group.name == "Media" -> Icons.Outlined.Movie
    group.name == "Monitoring" -> Icons.Outlined.ShowChart
    group.name == "Infrastructure" -> Icons.Outlined.Build
    group.name == "Security" -> Icons.Outlined.Shield
    group.name == "Home" -> Icons.Outlined.Home
    group.name == "Files & Data" -> Icons.Outlined.Folder
    group.name == "Network" -> Icons.Outlined.Lan
    group.name == "Dev" -> Icons.Outlined.Code
    group.name == "Servers" -> Icons.Outlined.Dns
    else -> Icons.Outlined.Apps
}

/** Custom Tabs share the browser's session, so anything already signed in opens signed in. */
fun openApp(context: android.content.Context, url: String) {
    val uri = android.net.Uri.parse(url)
    runCatching {
        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
    }
}

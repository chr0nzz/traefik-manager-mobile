package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.repo.EntrypointRow
import dev.chr0nzz.traefikmanager.data.repo.ProviderCount
import dev.chr0nzz.traefikmanager.data.repo.SignalCard
import dev.chr0nzz.traefikmanager.data.repo.Verdict
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.CountChip
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.HealthLabel
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.MonoText
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.SignalStrip
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.plus
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenRoutes: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding),
    ) {
        when {
            state.loading -> LoadingState()
            state.snapshot == null -> ErrorState(
                headline = "Could not load the overview",
                body = state.error,
                onRetry = viewModel::refresh,
            )
            else -> {
                val snapshot = state.snapshot ?: return@PullToRefreshBox
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    contentPadding = contentPadding + PaddingValues(TmSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        VerdictLine(snapshot.verdict, snapshot.providers)
                    }
                    items(snapshot.cards, key = { it.key }) { card ->
                        SignalCardView(card = card, onClick = onOpenRoutes)
                    }
                    if (snapshot.entrypoints.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EntrypointsSection(snapshot.entrypoints)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictLine(verdict: Verdict, providers: List<ProviderCount>) {
    val palette = LocalTmPalette.current
    TmCard(accent = verdict.status) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            StatusDot(verdict.status)
            Text(
                text = verdict.headline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = verdict.detail,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (providers.isNotEmpty()) {
            ProviderRow(providers, modifier = Modifier.padding(top = TmSpacing.sm))
        }
    }
}

@Composable
private fun SignalCardView(card: SignalCard, onClick: () -> Unit) {
    val palette = LocalTmPalette.current
    val (icon, tint) = when (card.key) {
        "http" -> Icons.Outlined.AltRoute to palette.blue
        "stream" -> Icons.Outlined.SwapHoriz to palette.teal
        "services" -> Icons.Outlined.Dns to palette.green
        else -> Icons.Outlined.Extension to palette.purple
    }
    TmCard(accent = if (card.health == TmStatus.Ok) null else card.health, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionLabel(card.title, modifier = Modifier.weight(1f))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.xs),
        ) {
            Text(
                text = card.total?.toString() ?: "-",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (card.flags.isEmpty()) {
                HealthLabel(status = card.health, text = card.healthLabel)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                    card.flags.forEach { flag ->
                        CountChip(count = flag.count, label = flag.label, status = flag.status)
                    }
                }
            }
        }
        MonoText(text = card.sub, modifier = Modifier.padding(top = TmSpacing.xs))
        SignalStrip(
            cells = card.cells,
            emptyLabel = card.stripEmptyLabel,
            modifier = Modifier.padding(top = TmSpacing.sm),
        )
        if (card.providers.isNotEmpty()) {
            CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
            ProviderRow(card.providers, modifier = Modifier.padding(top = TmSpacing.xs))
        }
    }
}

@Composable
private fun ProviderRow(providers: List<ProviderCount>, modifier: Modifier = Modifier) {
    val palette = LocalTmPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
    ) {
        providers.take(4).forEach { provider ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                )
                Text(
                    text = provider.count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = when (provider.worst) {
                        TmStatus.Error -> palette.red
                        TmStatus.Warn -> palette.yellow
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun EntrypointsSection(rows: List<EntrypointRow>) {
    val palette = LocalTmPalette.current
    TmCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.DoorFront,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(14.dp),
            )
            SectionLabel("Entry Points")
            Text(
                text = rows.size.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
            EntrypointRowView(row)
        }
    }
}

@Composable
private fun EntrypointRowView(row: EntrypointRow) {
    val palette = LocalTmPalette.current
    Column(
        modifier = Modifier
            .padding(top = TmSpacing.sm)
            .alpha(if (row.idle) 0.55f else 1f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = row.proto,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = when (row.proto) {
                    "UDP" -> palette.orange
                    "TCP" -> palette.teal
                    else -> palette.blue
                },
                modifier = Modifier.width(46.dp),
            )
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.routerCount?.toString() ?: "-",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
                color = if (row.routerCount == null || row.routerCount == 0) {
                    palette.muted
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.End,
                modifier = Modifier.width(30.dp),
            )
            Spacer(modifier = Modifier.width(TmSpacing.sm))
            if (row.idle) {
                Text(
                    text = "idle",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                )
            } else {
                StatusDot(status = row.health, size = 6.dp)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
        ) {
            Text(
                text = row.address,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
                modifier = Modifier.padding(start = 46.dp, end = TmSpacing.sm),
            )
            SignalStrip(cells = row.cells, cellSize = 4.dp, emptyLabel = null)
        }
        Text(
            text = row.facts,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 46.dp, top = 2.dp),
        )
    }
}

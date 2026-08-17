package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.chr0nzz.traefikmanager.data.repo.EntrypointRow
import dev.chr0nzz.traefikmanager.data.repo.ProviderCount
import dev.chr0nzz.traefikmanager.data.repo.RuntimeInfo
import dev.chr0nzz.traefikmanager.data.repo.SignalCard
import dev.chr0nzz.traefikmanager.data.repo.SignalFlag
import dev.chr0nzz.traefikmanager.data.repo.Verdict
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.CountChip
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.HealthLabel
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.MonoText
import dev.chr0nzz.traefikmanager.ui.components.ProviderRow
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.SignalStrip
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.plus
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmRadius
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    onOpenRoutes: (status: String?, proto: String?) -> Unit,
    onOpenServices: (status: String?) -> Unit = {},
    onOpenMiddlewares: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    bellViewModel: NotificationBellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unread by bellViewModel.unread.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val refreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Overview") },
                navigationIcon = {
                    DrawerButton(onOpenDrawer)
                },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(
                            badge = {
                                if (unread > 0) {
                                    Badge {
                                        Text(if (unread > 99) "99+" else unread.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = if (unread > 0) {
                                    if (unread == 1) "Notifications, 1 unread" else "Notifications, $unread unread"
                                } else {
                                    "Notifications"
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.refresh()
                            bellViewModel.refresh()
                        },
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh overview")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding),
    ) {
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(contentPadding))
            state.snapshot == null -> ErrorState(
                headline = "Could not load the overview",
                body = state.error,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                val snapshot = state.snapshot ?: return@PullToRefreshBox
                val compact = !currentWindowAdaptiveInfo().windowSizeClass
                    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
                LazyVerticalGrid(
                    columns = if (compact) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 280.dp),
                    contentPadding = contentPadding + PaddingValues(TmSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        VerdictLine(snapshot.verdict)
                    }
                    if (snapshot.providers.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ProvidersBar(
                                providers = snapshot.providers,
                                activeProvider = snapshot.providerFilter,
                                onProviderClick = viewModel::onProviderClick,
                            )
                        }
                    }
                    snapshot.cards.chunked(if (compact) 2 else 2).forEach { pair ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = pair.first().key) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
                                modifier = Modifier.height(IntrinsicSize.Min),
                            ) {
                                pair.forEach { card ->
                                    SignalCardView(
                                        card = card,
                                        onOpen = { flag -> openCard(card, flag, onOpenRoutes, onOpenServices, onOpenMiddlewares) },
                                        compact = compact,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (snapshot.entrypoints.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EntrypointsSection(snapshot.entrypoints)
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RuntimeFooter(snapshot.runtime)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun VerdictLine(verdict: Verdict) {
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
    }
}

@Composable
private fun SignalCardView(
    card: SignalCard,
    onOpen: (SignalFlag?) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val onClick = { onOpen(null) }
    val palette = LocalTmPalette.current
    val (icon, tint) = when (card.key) {
        "http" -> Icons.Outlined.AltRoute to palette.blue
        "stream" -> Icons.Outlined.SwapHoriz to palette.teal
        "services" -> Icons.Outlined.Dns to palette.green
        else -> Icons.Outlined.Extension to palette.purple
    }
    TmCard(modifier = modifier, accent = if (card.health == TmStatus.Ok) null else card.health, onClick = onClick) {
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
                style = if (compact) {
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (card.flags.isEmpty()) {
                HealthLabel(status = card.health, text = card.healthLabel, compact = compact)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                    card.flags.take(if (compact) 1 else 3).forEach { flag ->
                        CountChip(
                            count = flag.count,
                            label = flag.label,
                            status = flag.status,
                            showLabel = !compact,
                            // The web opens the matching list already filtered to this flag.
                            onClick = { onOpen(flag) },
                        )
                    }
                }
            }
        }
        MonoText(text = card.sub, modifier = Modifier.padding(top = TmSpacing.xs))
        SignalStrip(
            cells = card.cells,
            emptyLabel = card.stripEmptyLabel,
            cellSize = if (compact) 4.dp else 6.dp,
            maxCells = if (compact) 60 else 150,
            modifier = Modifier.padding(top = if (compact) TmSpacing.xs else TmSpacing.sm),
        )
        if (card.providers.isNotEmpty()) {
            CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
            ProviderRow(
                providers = card.providers,
                activeProvider = null,
                onProviderClick = {},
                modifier = Modifier.padding(top = TmSpacing.xs),
            )
        }
    }
}

@Composable
private fun ProvidersBar(
    providers: List<ProviderCount>,
    activeProvider: String?,
    onProviderClick: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SectionLabel("Providers")
        ProviderRow(
            providers = providers,
            activeProvider = activeProvider,
            onProviderClick = onProviderClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RuntimeFooter(runtime: RuntimeInfo) {
    val palette = LocalTmPalette.current
    val parts = buildList {
        runtime.version?.let { add("v$it" + (runtime.codename?.let { c -> " $c" } ?: "")) }
        add(if (runtime.metrics.isNullOrEmpty()) "metrics off" else "metrics ${runtime.metrics}")
        add(if (runtime.accessLog == true) "access log on" else "access log off")
        add(if (runtime.tracing.isNullOrEmpty()) "tracing off" else "tracing ${runtime.tracing}")
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
        color = palette.muted,
        modifier = Modifier.padding(horizontal = TmSpacing.xs, vertical = TmSpacing.xs),
    )
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

/**
 * Where a card sends you. The web opens the list the card counts, filtered to whatever the
 * chip you pressed was reporting (dashboard.js:513-516, 772-774) - not always the route list.
 */
private fun openCard(
    card: SignalCard,
    flag: SignalFlag?,
    onOpenRoutes: (String?, String?) -> Unit,
    onOpenServices: (String?) -> Unit,
    onOpenMiddlewares: () -> Unit,
) {
    val disabled = flag?.status == TmStatus.Error
    when (card.key) {
        "services" -> onOpenServices(
            when {
                flag == null -> null
                disabled -> "error"
                flag.status == TmStatus.Warn -> "warning"
                else -> null
            },
        )
        "middlewares" -> onOpenMiddlewares()
        "stream" -> onOpenRoutes(if (disabled) "disabled" else null, null)
        else -> onOpenRoutes(if (disabled) "disabled" else null, "http")
    }
}

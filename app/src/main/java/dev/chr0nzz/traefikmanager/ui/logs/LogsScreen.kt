package dev.chr0nzz.traefikmanager.ui.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.LogLine
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.CountryStrip
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.ModalSideSheet
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.only
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val palette = LocalTmPalette.current

    val wide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val selected = state.lines.firstOrNull { it.index == selectedIndex }
    val detailFullScreen = !wide && selected != null

    LaunchedEffect(viewModel.queryState) {
        snapshotFlow { viewModel.queryState.text.toString() }.collect(viewModel::onQueryChange)
    }

    val select: (LogLine) -> Unit = { line -> selectedIndex = line.index }
    val closeDetail: () -> Unit = { selectedIndex = null }

    if (detailFullScreen) BackHandler(onBack = closeDetail)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!detailFullScreen) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = { Text("Logs") },
                    navigationIcon = {
                        DrawerButton(onOpenDrawer)
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search logs")
                        }
                        IconButton(onClick = viewModel::toggleAutoRefresh) {
                            Icon(
                                imageVector = if (state.autoRefresh) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = if (state.autoRefresh) {
                                    "Stop auto refresh"
                                } else {
                                    "Start auto refresh"
                                },
                                tint = if (state.autoRefresh) palette.green else palette.muted,
                            )
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh logs")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { insets ->
        LogsSearchBar(
            searchBarState = searchBarState,
            queryState = viewModel.queryState,
            matches = state.visible.size,
        )

        if (detailFullScreen) {
            LogDetailPane(
                line = selected,
                country = selected?.entry?.let { state.countryByIp[it.ip] },
                showBack = true,
                contentPadding = insets,
                onBack = closeDetail,
            )
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LogsListPane(
                state = state,
                viewModel = viewModel,
                contentPadding = insets,
                selectedIndex = selectedIndex,
                onSelect = select,
            )

            ModalSideSheet(
                visible = selected != null,
                onDismiss = closeDetail,
                scrimLabel = "Close the log record",
            ) {
                LogDetailPane(
                    line = selected,
                    country = selected?.entry?.let { state.countryByIp[it.ip] },
                    showBack = true,
                    contentPadding = insets,
                    onBack = closeDetail,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogsListPane(
    state: LogsUiState,
    viewModel: LogsViewModel,
    contentPadding: PaddingValues,
    selectedIndex: Int?,
    onSelect: (LogLine) -> Unit,
) {
    val palette = LocalTmPalette.current
    val refreshState = rememberPullToRefreshState()
    val window = state.window

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        state = refreshState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.only(bottom = false)),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = refreshState,
                isRefreshing = state.refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when {
            state.loading -> LoadingState(label = "Loading logs")

            state.loadError != null -> ErrorState(
                headline = "Could not load the access log",
                body = state.loadError,
                onRetry = viewModel::refresh,
            )

            state.serverError != null && state.lines.isEmpty() -> ErrorState(
                headline = "Access log not available",
                body = state.serverError,
                onRetry = viewModel::refresh,
            )

            state.lines.isEmpty() -> EmptyState(
                headline = "No traffic yet",
                body = "The access log is empty, Traefik has not served a request since it was last rotated.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = TmSpacing.lg,
                    end = TmSpacing.lg,
                    top = TmSpacing.xs,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            ) {
                item { VerdictCard(state = state, onFacet = viewModel::toggleFacet) }

                item {
                    WindowKeyRow(
                        state = state,
                        onClearFacet = viewModel::clearFacet,
                        onClearFacets = viewModel::clearFacets,
                        onWiden = { viewModel.setLineCount(it) },
                    )
                }

                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        LOG_LINE_STEPS.forEachIndexed { index, step ->
                            SegmentedButton(
                                selected = state.lineCount == step,
                                onClick = { viewModel.setLineCount(step) },
                                shape = SegmentedButtonDefaults.itemShape(index, LOG_LINE_STEPS.size),
                            ) {
                                Text(step.toString())
                            }
                        }
                    }
                }

                item {
                    LogSignalCards(
                        window = window,
                        facets = state.facets,
                        onFacet = viewModel::toggleFacet,
                    )
                }

                item { RuntimeFooter(state = state) }

                if (state.geoEnabled && state.countries.isNotEmpty()) {
                    item {
                        CountryStrip(
                            countries = state.countries,
                            selected = state.country,
                            onSelect = viewModel::onCountryChange,
                        )
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    ) {
                        SectionLabel("Access log", modifier = Modifier.weight(1f))
                        Text(
                            text = "${state.visible.size} of ${state.fetched} lines",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                }

                items(state.visible.reversed(), key = { it.index }) { line ->
                    Column {
                        LogRow(
                            line = line,
                            country = line.entry?.let { state.countryByIp[it.ip] },
                            selected = line.index == selectedIndex,
                            onClick = { onSelect(line) },
                        )
                        CardDivider()
                    }
                }

                if (state.visible.isEmpty()) {
                    item {
                        Text(
                            text = "0 of the last ${state.fetched} lines match the current filters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                            modifier = Modifier.padding(TmSpacing.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(state: LogsUiState, onFacet: (LogFacet, String) -> Unit) {
    val palette = LocalTmPalette.current
    val window = state.window
    val latency = window.latency
    val health = window.health

    val headline = when {
        window.serverErrors > 0 ->
            "${window.serverErrors} server error" + if (window.serverErrors == 1) "" else "s"
        window.clientErrors > 0 ->
            "${window.clientErrors} client error" + if (window.clientErrors == 1) "" else "s"
        latency.slow > 0 -> "${latency.slow} slow request" + if (latency.slow == 1) "" else "s"
        window.parsed == 0 -> "Nothing matches"
        else -> "All clean"
    }
    val accent = when (health) {
        dev.chr0nzz.traefikmanager.data.model.LogHealth.Down -> palette.red
        dev.chr0nzz.traefikmanager.data.model.LogHealth.Warn -> palette.yellow
        dev.chr0nzz.traefikmanager.data.model.LogHealth.Up -> palette.green
    }

    TmCard(accentColor = accent) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (health == dev.chr0nzz.traefikmanager.data.model.LogHealth.Up) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.Warning
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp, end = TmSpacing.sm)
                    .size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    window.codeRank.take(3).forEach { (code, count) ->
                        val name = LogParser.statusName(code).lowercase().ifEmpty { "response" }
                        VerdictItem(
                            text = "$count $code $name",
                            color = if (code >= 500) palette.red else palette.yellow,
                            onClick = { onFacet(LogFacet.Status, code.toString()) },
                        )
                    }
                    if (latency.slow > 0) {
                        VerdictItem(
                            text = "${latency.slow} over 500ms",
                            color = palette.yellow,
                            onClick = { onFacet(LogFacet.Duration, "slow") },
                        )
                    }
                    if (window.retries > 0) {
                        VerdictItem(text = "${window.retries} retries", color = palette.yellow, onClick = null)
                    }
                    val calm = buildList {
                        if (window.serverErrors == 0) add("no server errors")
                        if (window.clientErrors == 0 && window.serverErrors > 0) add("no client errors")
                        if (latency.slow == 0) {
                            add(
                                latency.max?.let { "nothing slower than ${LogParser.formatMs(it)}" }
                                    ?: "no timing recorded",
                            )
                        }
                    }
                    if (calm.isNotEmpty()) {
                        Text(
                            text = calm.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                }
            }
            Text(
                text = window.spanMillis
                    ?.let { "${LogParser.spanText(it)} window" }
                    ?: "${state.fetched} lines",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun VerdictItem(text: String, color: Color, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun WindowKeyRow(
    state: LogsUiState,
    onClearFacet: (LogFacet) -> Unit,
    onClearFacets: () -> Unit,
    onWiden: (Int) -> Unit,
) {
    val palette = LocalTmPalette.current
    val window = state.window

    Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionLabel("Window")
            KeyItem(Icons.AutoMirrored.Outlined.ListAlt, "last ${state.fetched} lines")
            window.spanMillis?.takeIf { it > 0 }?.let {
                KeyItem(Icons.Outlined.Schedule, "span ${LogParser.spanText(it)}")
            }
            window.requestsPerMinute?.let { KeyItem(Icons.Outlined.Bolt, "$it req/min") }
            KeyItem(Icons.AutoMirrored.Outlined.ListAlt, "${state.unparsed} unparsed")
        }

        if (state.facets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SectionLabel("Filters")
                state.facets.forEach { (facet, value) ->
                    val dead = facet in state.deadFacets
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.blue.copy(alpha = if (dead) 0.05f else 0.14f))
                            .clickable { onClearFacet(facet) }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterAlt,
                            contentDescription = null,
                            tint = if (dead) palette.muted else palette.blue,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = facet.key,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dead) palette.muted else palette.blue,
                        )
                        Text(
                            text = LogFacets.label(facet, value),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (dead) palette.muted else palette.blue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear ${facet.key} filter",
                            tint = if (dead) palette.muted else palette.blue,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
                Text(
                    text = "clear",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.blue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onClearFacets() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            state.selectionSpan?.takeIf { it > 0 }?.let {
                KeyItem(Icons.Outlined.Schedule, "selection spans ${LogParser.spanText(it)}")
            }
            Box(modifier = Modifier.weight(1f))
            val widen = state.nextLineStep
            Text(
                text = if (state.selected) {
                    "${state.visible.size} of the last ${state.fetched} lines"
                } else {
                    "sample, not all traffic"
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.yellow,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (widen != null) Modifier.clickable { onWiden(widen) } else Modifier)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun KeyItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(11.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
        )
    }
}

@Composable
private fun RuntimeFooter(state: LogsUiState) {
    val palette = LocalTmPalette.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        RuntimeChip(Icons.Outlined.DataObject, state.formatLabel, state.parsedEntries.isNotEmpty())
        RuntimeChip(
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            text = "${state.parsedEntries.size} of ${state.fetched} lines parsed",
            on = state.unparsed == 0,
        )
        RuntimeChip(Icons.Outlined.Timer, if (state.nanoPrecision) "ns precision" else "ms precision", state.nanoPrecision)
        RuntimeChip(Icons.Outlined.VerifiedUser, if (state.tlsFields) "tls fields" else "no tls fields", state.tlsFields)
        RuntimeChip(Icons.Outlined.Public, if (state.geoEnabled) "geoip on" else "geoip off", state.geoEnabled)
        RuntimeChip(
            icon = Icons.Outlined.Refresh,
            text = if (state.autoRefresh) "auto refresh on" else "auto refresh off",
            on = state.autoRefresh,
        )
    }
}

@Composable
private fun RuntimeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, on: Boolean) {
    val palette = LocalTmPalette.current
    val tint = if (on) palette.green else palette.muted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = tint,
        )
    }
}

@Composable
private fun LogRow(line: LogLine, country: String?, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalTmPalette.current
    val entry = line.entry

    if (entry == null) {
        Text(
            text = line.raw,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = TmSpacing.sm, vertical = TmSpacing.xs),
        )
        return
    }

    val statusColor = when {
        entry.status >= 500 -> palette.red
        entry.status >= 400 -> palette.yellow
        entry.status > 0 -> palette.green
        else -> palette.muted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(
                when {
                    selected -> palette.blue.copy(alpha = 0.10f)
                    entry.status >= 400 -> statusColor.copy(alpha = 0.06f)
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = TmSpacing.sm, vertical = TmSpacing.xs),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.path.ifEmpty { "/" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(text = entry.method, style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (country != null) {
                    Text(text = Countries.flag(country), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = entry.ip,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    maxLines = 1,
                )
                val service = entry.service.ifEmpty { entry.router }
                if (service.isNotEmpty()) {
                    Text(
                        text = "· ${LogParser.shortName(service)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (entry.status == 0) "-" else entry.status.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = statusColor,
            )
            Text(
                text = entry.duration.ifEmpty { LogParser.formatMs(entry.durMs) },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsSearchBar(
    searchBarState: androidx.compose.material3.SearchBarState,
    queryState: TextFieldState,
    matches: Int,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalTmPalette.current

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = queryState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = { Text("Filter logs") },
                leadingIcon = {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close search")
                    }
                },
                trailingIcon = {
                    if (queryState.text.isNotEmpty()) {
                        IconButton(onClick = { queryState.clearText() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
            )
        },
    ) {
        Text(
            text = "$matches matching lines",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            modifier = Modifier.padding(TmSpacing.lg),
        )
    }
}

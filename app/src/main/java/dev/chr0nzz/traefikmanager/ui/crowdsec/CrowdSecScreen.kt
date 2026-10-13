package dev.chr0nzz.traefikmanager.ui.crowdsec

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.automirrored.outlined.CallMissedOutgoing
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import androidx.compose.ui.graphics.vector.ImageVector
import dev.chr0nzz.traefikmanager.data.model.CsFacet
import dev.chr0nzz.traefikmanager.data.model.CsRanked
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.CountryStrip
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.RankedRow
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.SignalCard
import dev.chr0nzz.traefikmanager.ui.components.SignalCells
import dev.chr0nzz.traefikmanager.ui.components.SignalChip
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmRadius
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.asString
import androidx.compose.ui.platform.LocalResources

private const val UNKNOWN = "?"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrowdSecScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CrowdSecViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val refreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var addSheetFor by remember { mutableStateOf<String?>(null) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CsDecision?>(null) }

    LaunchedEffect(viewModel.queryState) {
        snapshotFlow { viewModel.queryState.text.toString() }.collect(viewModel::onQueryChange)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolve(resources))
        viewModel.consumeMessage()
    }

    if (addSheetOpen) {
        AddDecisionSheet(
            initialValue = addSheetFor.orEmpty(),
            decisions = state.snapshot.ownRows,
            saving = state.saving,
            onAdd = { value, type, duration, reason ->
                viewModel.addDecision(value, type, duration, reason)
                addSheetOpen = false
                addSheetFor = null
            },
            onDelete = { pendingDelete = it },
            onDismiss = {
                addSheetOpen = false
                addSheetFor = null
            },
        )
    }

    pendingDelete?.let { decision ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.crowdsec_remove_title)) },
            text = { Text(stringResource(R.string.crowdsec_remove_body, decision.value)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDecision(decision)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.crowdsec_unban))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.notConfigured) {
                FloatingActionButton(onClick = { addSheetOpen = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.crowdsec_add_decision))
                }
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("CrowdSec") },
                navigationIcon = {
                    DrawerButton(onOpenDrawer)
                },
                actions = {
                    IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.crowdsec_search))
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.crowdsec_refresh))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        CrowdSecSearchBar(
            searchBarState = searchBarState,
            queryState = viewModel.queryState,
            matches = if (state.view == CrowdSecView.Evidence) {
                state.visibleAlerts.size
            } else {
                state.bansCount ?: 0
            },
        )

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            state = refreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                state.loading -> LoadingState(label = stringResource(R.string.crowdsec_loading))

                state.notConfigured -> EmptyState(
                    headline = stringResource(R.string.crowdsec_unconfigured),
                    body = stringResource(R.string.crowdsec_unconfigured_body),
                )

                state.loadError != null -> ErrorState(
                    headline = stringResource(R.string.crowdsec_error_load),
                    body = state.loadError?.asString(),
                    onRetry = viewModel::refresh,
                )

                !state.decisionsOk && !state.alertsOk -> ErrorState(
                    headline = stringResource(R.string.crowdsec_unavailable),
                    body = (state.decisionsError ?: state.alertsError)?.asString(),
                    onRetry = viewModel::refresh,
                )

                else -> CrowdSecBody(
                    state = state,
                    onViewChange = viewModel::onViewChange,
                    onCountryClick = viewModel::onCountryChange,
                    onScenarioClick = viewModel::onScenarioChange,
                    onFacet = viewModel::toggleFacet,
                    onRemoveFacet = viewModel::removeFacet,
                    onClearFilters = viewModel::clearFilters,
                    onBan = { ip ->
                        addSheetFor = ip
                        addSheetOpen = true
                    },
                    onDelete = { pendingDelete = it },
                    onLoadMore = viewModel::loadMoreDecisions,
                )
            }
        }
    }
}

@Composable
private fun CrowdSecBody(
    state: CrowdSecUiState,
    onViewChange: (CrowdSecView) -> Unit,
    onCountryClick: (String) -> Unit,
    onScenarioClick: (String) -> Unit,
    onFacet: (CsFacet, String) -> Unit,
    onRemoveFacet: (CsFacet) -> Unit,
    onClearFilters: () -> Unit,
    onBan: (String) -> Unit,
    onDelete: (CsDecision) -> Unit,
    onLoadMore: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val wide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val alerts = state.visibleAlerts
    val snapshot = state.snapshot
    val sources = remember(alerts, snapshot) { CrowdSecAnalytics.sources(alerts, snapshot::handled) }
    val networks = remember(alerts, snapshot) { CrowdSecAnalytics.networks(alerts, snapshot::handled) }
    val scenarios = remember(alerts, snapshot) { CrowdSecAnalytics.scenarios(alerts, snapshot::handled) }
    val paths = remember(alerts, snapshot) { CrowdSecAnalytics.paths(alerts, snapshot::handled) }
    val accounts = remember(alerts, snapshot) { CrowdSecAnalytics.accounts(alerts, snapshot::handled) }
    val tooling = remember(alerts, snapshot) { CrowdSecAnalytics.tooling(alerts, snapshot::handled) }
    val routes = remember(alerts, snapshot) { CrowdSecAnalytics.routes(alerts, snapshot::handled) }
    val hosts = remember(alerts, snapshot) { CrowdSecAnalytics.hosts(alerts, snapshot::handled) }
    val origins = snapshot.origins
    val decisionTotal = snapshot.decisionTotal
    val span = remember(alerts) { CrowdSecAnalytics.spanMillis(alerts) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TmSpacing.lg,
            end = TmSpacing.lg,
            top = TmSpacing.xs,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        item {
            CrowdSecVerdictCard(
                state = state,
                sources = sources,
                scenarios = scenarios,
                span = span,
            )
        }

        item { CrowdSecWindowRow(state = state, span = span) }

        if (state.filtersActive) {
            item { FilterBar(state = state, onRemove = onRemoveFacet, onClear = onClearFilters) }
        }

        item {
            val columns = if (wide) 4 else 2
            val loose = sources.filter { it.open > 0 }
            val repeats = sources.filter { it.count > 1 }
            val bannedSources = sources.size - loose.size
            val alertTotal = alerts.size

            val cards: List<DeskCard> = listOf(
                DeskCard(1) { cardModifier ->
                    SignalCard(
                        label = stringResource(R.string.crowdsec_card_sources),
                        hero = if (state.alertsOk) LogParser.formatCount(sources.size) else "-",
                        accent = palette.red,
                        glyph = Icons.Outlined.GpsFixed,
                        health = when {
                            loose.any { it.count > 1 } -> palette.red
                            loose.isNotEmpty() -> palette.yellow
                            else -> null
                        },
                        subtitle = if (!state.alertsOk) {
                            stringResource(R.string.crowdsec_sources_blind)
                        } else {
                            sources.firstOrNull()
                                ?.let {
                                    pluralStringResource(
                                        R.plurals.crowdsec_worst_events,
                                        it.weight,
                                        it.key,
                                        LogParser.formatCount(it.weight),
                                    )
                                }
                                ?: stringResource(R.string.crowdsec_no_sources)
                        },
                        flags = when {
                            !state.alertsOk -> listOf(SignalChip(Icons.Outlined.Info, stringResource(R.string.crowdsec_chip_ban_unknown)))
                            !state.decisionsOk -> listOf(SignalChip(Icons.Outlined.Info, stringResource(R.string.crowdsec_chip_ban_unknown)))
                            loose.isNotEmpty() -> listOf(
                                SignalChip(
                                    Icons.Outlined.LockOpen,
                                    stringResource(R.string.crowdsec_chip_loose, LogParser.formatCount(loose.size)),
                                    if (loose.any { it.count > 1 }) palette.red else palette.yellow,
                                    onClick = { onFacet(CsFacet.Outcome, "loose") },
                                ),
                                SignalChip(
                                    Icons.Outlined.Block,
                                    stringResource(R.string.crowdsec_chip_banned, LogParser.formatCount(bannedSources)),
                                    onClick = { onFacet(CsFacet.Outcome, "banned") },
                                ),
                            )
                            sources.isEmpty() -> emptyList()
                            else -> listOf(SignalChip(Icons.Outlined.CheckCircle, stringResource(R.string.crowdsec_chip_all_banned), palette.green))
                        },
                        footer = if (sources.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(
                                SignalChip(Icons.Outlined.Repeat, stringResource(R.string.crowdsec_chip_repeat, LogParser.formatCount(repeats.size))),
                                SignalChip(
                                    Icons.AutoMirrored.Outlined.CallMissedOutgoing,
                                    stringResource(R.string.crowdsec_chip_one_shot, LogParser.formatCount(sources.size - repeats.size)),
                                ),
                            )
                        },
                        modifier = cardModifier,
                    ) {
                        SignalCells(
                            cells = sources.map {
                                when {
                                    it.open > 0 && it.count > 1 -> palette.red
                                    it.open > 0 -> palette.yellow
                                    else -> palette.muted.copy(alpha = 0.30f)
                                }
                            },
                        )
                    }
                },
                DeskCard(1) { cardModifier ->
                    RankCard(
                        label = stringResource(R.string.crowdsec_card_networks),
                        accent = palette.purple,
                        glyph = Icons.Outlined.Public,
                        rows = networks,
                        onRowClick = { onFacet(CsFacet.Asn, it) },
                        blind = !state.alertsOk,
                        blindReason = stringResource(R.string.crowdsec_networks_blind),
                        emptyReason = stringResource(R.string.crowdsec_networks_empty),
                        subtitle = networks.firstOrNull()
                            ?.let {
                                pluralStringResource(
                                    R.plurals.crowdsec_worst_alerts,
                                    it.count,
                                    it.label,
                                    LogParser.formatCount(it.count),
                                )
                            },
                        rowGlyph = { row ->
                            if (row.extra.isNotEmpty()) {
                                Text(
                                    text = Countries.flag(row.extra),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        modifier = cardModifier,
                    )
                },
                DeskCard(2) { cardModifier ->
                    RankCard(
                        label = stringResource(R.string.crowdsec_card_scenarios),
                        accent = palette.orange,
                        glyph = Icons.Outlined.Bolt,
                        rows = scenarios,
                        blind = !state.alertsOk,
                        blindReason = stringResource(R.string.crowdsec_scenarios_blind),
                        emptyReason = stringResource(R.string.crowdsec_scenarios_empty),
                        hero = LogParser.formatCount(alertTotal),
                        heroUnit = stringResource(R.string.crowdsec_unit_alerts),
                        more = R.plurals.crowdsec_more_scenarios,
                        subtitle = scenarios.firstOrNull()?.let {
                            val events = alerts.sumOf { a -> a.eventsCount }
                            listOf(
                                stringResource(R.string.crowdsec_worst, it.label),
                                pluralStringResource(
                                    R.plurals.crowdsec_events_rolled_up,
                                    events,
                                    LogParser.formatCount(events),
                                ),
                            ).joinToString(UiText.SEPARATOR)
                        },
                        onRowClick = onScenarioClick,
                        rowGlyph = {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier.size(11.dp),
                            )
                        },
                        modifier = cardModifier,
                    )
                },
                DeskCard(2) { cardModifier ->
                    val onPaths = paths.isNotEmpty() || accounts.isEmpty()
                    val targeting = if (onPaths) paths else accounts
                    RankCard(
                        label = stringResource(if (onPaths) R.string.crowdsec_card_paths else R.string.crowdsec_card_accounts),
                        accent = palette.blue,
                        glyph = Icons.Outlined.MyLocation,
                        rows = targeting,
                        onRowClick = { onFacet(if (onPaths) CsFacet.Uri else CsFacet.User, it) },
                        blind = !state.alertsOk,
                        blindReason = stringResource(
                            if (onPaths) R.string.crowdsec_paths_blind else R.string.crowdsec_accounts_blind,
                        ),
                        emptyReason = stringResource(R.string.crowdsec_targets_empty),
                        hero = LogParser.formatCount(targeting.size),
                        heroUnit = stringResource(if (onPaths) R.string.crowdsec_unit_paths else R.string.crowdsec_unit_accounts),
                        more = if (onPaths) R.plurals.crowdsec_more_paths else R.plurals.crowdsec_more_accounts,
                        subtitle = targeting.firstOrNull()?.let { stringResource(R.string.crowdsec_most_wanted, it.label) },
                        rowGlyph = {
                            Icon(
                                imageVector = if (onPaths) {
                                    Icons.Outlined.Description
                                } else {
                                    Icons.Outlined.PersonOutline
                                },
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier.size(11.dp),
                            )
                        },
                        modifier = cardModifier,
                    )
                },
                DeskCard(2) { cardModifier ->
                    val named = state.alerts.any { it.routers.isNotEmpty() }
                    RankCard(
                        label = stringResource(R.string.crowdsec_card_routes),
                        accent = palette.purple,
                        glyph = Icons.Outlined.AltRoute,
                        rows = routes,
                        onRowClick = { onFacet(CsFacet.Router, it) },
                        blind = !state.alertsOk,
                        blindReason = stringResource(R.string.crowdsec_routes_blind),
                        emptyReason = when {
                            state.alerts.isEmpty() -> stringResource(R.string.crowdsec_routes_empty_none)
                            named -> stringResource(R.string.crowdsec_routes_empty_match)
                            else -> stringResource(R.string.crowdsec_routes_empty_evidence)
                        },
                        hero = LogParser.formatCount(routes.size),
                        heroUnit = stringResource(R.string.crowdsec_unit_routers),
                        more = R.plurals.crowdsec_more_routers,
                        subtitle = routes.firstOrNull()?.let { stringResource(R.string.crowdsec_most_wanted, it.label) },
                        flags = hosts.take(3).map { host ->
                            SignalChip(
                                Icons.Outlined.Language,
                                "${LogParser.formatCount(host.count)} ${host.key}",
                                onClick = { onFacet(CsFacet.Host, host.key) },
                            )
                        },
                        rowGlyph = {
                            Icon(
                                imageVector = Icons.Outlined.AltRoute,
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier.size(11.dp),
                            )
                        },
                        modifier = cardModifier,
                    )
                },
                DeskCard(1) { cardModifier ->
                    RankCard(
                        label = stringResource(R.string.crowdsec_card_tooling),
                        accent = palette.teal,
                        glyph = Icons.Outlined.SmartToy,
                        rows = tooling,
                        onRowClick = { onFacet(CsFacet.Agent, it) },
                        blind = !state.alertsOk,
                        blindReason = stringResource(R.string.crowdsec_tooling_blind),
                        emptyReason = stringResource(R.string.crowdsec_tooling_empty),
                        more = R.plurals.crowdsec_more_agents,
                        subtitle = tooling.firstOrNull()?.let { stringResource(R.string.crowdsec_worst, it.label) },
                        flags = listOf(
                            SignalChip(Icons.Outlined.Code, pluralStringResource(R.plurals.crowdsec_tools, tooling.size, LogParser.formatCount(tooling.size))),
                        ),
                        rowGlyph = {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier.size(11.dp),
                            )
                        },
                        modifier = cardModifier,
                    )
                },
                DeskCard(1) { cardModifier ->
                    val perCell = ((decisionTotal + CELL_CAP - 1) / CELL_CAP).coerceAtLeast(1)
                    val stale = snapshot.decisionsStale
                    SignalCard(
                        label = stringResource(if (stale != null) R.string.crowdsec_card_bans_stale else R.string.crowdsec_card_bans),
                        hero = if (state.decisionsOk) LogParser.formatCount(decisionTotal) else "-",
                        accent = if (stale != null) palette.yellow else palette.green,
                        glyph = Icons.Outlined.Shield,
                        health = when {
                            !state.decisionsOk -> palette.red
                            stale != null -> palette.yellow
                            else -> null
                        },
                        subtitle = when {
                            !state.decisionsOk -> stringResource(R.string.crowdsec_bans_blind)
                            stale != null -> stale
                            else -> listOf(
                                stringResource(R.string.crowdsec_bans_own, LogParser.formatCount(snapshot.ownBans)),
                                stringResource(
                                    R.string.crowdsec_bans_subscribed,
                                    LogParser.formatCount(snapshot.subscribedBans),
                                ),
                            ).joinToString(UiText.SEPARATOR)
                        },
                        flags = if (state.decisionsOk) {
                            listOf(
                                SignalChip(
                                    Icons.Outlined.Block,
                                    "${LogParser.formatCount(snapshot.typeCount("ban"))} ban",
                                    onClick = { onFacet(CsFacet.Type, "ban") },
                                ),
                            )
                        } else {
                            emptyList()
                        },
                        footer = origins.map { origin ->
                            SignalChip(
                                onClick = { onFacet(CsFacet.Origin, origin.origin) },
                                icon = when (origin.origin) {
                                    "crowdsec" -> Icons.Outlined.GpsFixed
                                    "cscli" -> Icons.Outlined.Code
                                    "capi" -> Icons.Outlined.People
                                    "lists" -> Icons.AutoMirrored.Outlined.ListAlt
                                    else -> Icons.Outlined.MoreHoriz
                                },
                                text = "${LogParser.formatCount(origin.count)} ${origin.origin}",
                                color = if (origin.origin == "cscli") palette.yellow else null,
                            )
                        },
                        modifier = cardModifier,
                    ) {
                        SignalCells(
                            cells = List((decisionTotal + perCell - 1) / perCell) { index ->
                                val ownCells = (snapshot.ownBans + perCell - 1) / perCell
                                if (index < ownCells) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                                } else {
                                    palette.muted.copy(alpha = 0.30f)
                                }
                            },
                            cap = CELL_CAP,
                        )
                        if (perCell > 1) {
                            Text(
                                text = stringResource(R.string.crowdsec_cell_scale, LogParser.formatCount(perCell)),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.muted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                packRows(cards, columns).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.height(IntrinsicSize.Min),
                    ) {
                        row.forEach { card ->
                            card.content(
                                Modifier
                                    .weight(card.span.toFloat())
                                    .fillMaxHeight(),
                            )
                        }
                        val used = row.sumOf { it.span }
                        if (used < columns) Box(modifier = Modifier.weight((columns - used).toFloat()))
                    }
                }
            }
        }

        if (state.countries.isNotEmpty()) {
            item {
                CountryStrip(
                    countries = state.countries,
                    selected = state.country,
                    onSelect = onCountryClick,
                )
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                CrowdSecView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.view == view,
                        onClick = { onViewChange(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, CrowdSecView.entries.size),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Text(
                            text = when (view) {
                                CrowdSecView.Evidence ->
                                    stringResource(
                                        R.string.crowdsec_tab_evidence,
                                        if (state.alertsOk) state.visibleAlerts.size.toString() else UNKNOWN,
                                    )
                                CrowdSecView.Bans ->
                                    stringResource(R.string.crowdsec_tab_bans, state.bansCount?.toString() ?: UNKNOWN)
                            },
                        )
                    }
                }
            }
        }

        if (state.view == CrowdSecView.Evidence) {
            itemsIndexed(
                items = state.visibleAlerts.take(200),
                key = { index, alert -> alert.key(index) },
            ) { _, alert ->
                AlertRow(
                    alert = alert,
                    country = state.countryOf(alert),
                    handled = state.snapshot.handled(alert),
                    decisionsOk = state.decisionsOk,
                    onBan = { onBan(alert.ip) },
                    onFilterIp = { onFacet(CsFacet.Ip, alert.ip) },
                    onFilterScenario = { onFacet(CsFacet.Scenario, alert.scenarioName) },
                    onFilterRouter = { onFacet(CsFacet.Router, it) },
                    onFilterHost = { onFacet(CsFacet.Host, it) },
                )
            }
            if (state.visibleAlerts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(if (state.alertsOk) R.string.crowdsec_alerts_no_match else R.string.crowdsec_alerts_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(TmSpacing.lg),
                    )
                }
            }
        } else if (snapshot.serverSearch) {
            itemsIndexed(
                items = state.feed?.rows.orEmpty(),
                key = { index, decision -> "${decision.id}:$index" },
            ) { _, decision ->
                DecisionRow(decision = decision, onDelete = { onDelete(decision) })
            }
            item { DecisionFeedFooter(state = state, onLoadMore = onLoadMore) }
        } else {
            itemsIndexed(
                items = state.visibleDecisions.take(200),
                key = { index, decision -> "${decision.id}:$index" },
            ) { _, decision ->
                DecisionRow(decision = decision, onDelete = { onDelete(decision) })
            }
            if (state.visibleDecisions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (state.decisionsOk) R.string.crowdsec_decisions_none else R.string.crowdsec_decisions_unavailable,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(TmSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun RankCard(
    label: String,
    accent: Color,
    rows: List<CsRanked>,
    blind: Boolean,
    blindReason: String,
    emptyReason: String,
    modifier: Modifier = Modifier,
    @PluralsRes more: Int = R.plurals.crowdsec_more_networks,
    hero: String? = null,
    heroUnit: String? = null,
    subtitle: String? = null,
    flags: List<SignalChip> = emptyList(),
    glyph: androidx.compose.ui.graphics.vector.ImageVector? = null,
    rowGlyph: (@Composable (CsRanked) -> Unit)? = null,
    onRowClick: ((String) -> Unit)? = null,
) {
    val palette = LocalTmPalette.current
    val shown = rows.take(ROW_CAP)
    SignalCard(
        label = label,
        hero = when {
            blind -> "-"
            rows.isEmpty() -> "0"
            else -> hero ?: rows.size.toString()
        },
        heroUnit = heroUnit?.takeIf { !blind && rows.isNotEmpty() },
        accent = accent,
        glyph = glyph,
        flags = if (blind || rows.isEmpty()) emptyList() else flags,
        subtitle = when {
            blind -> blindReason
            rows.isEmpty() -> emptyReason
            else -> subtitle ?: stringResource(R.string.crowdsec_worst, rows.first().key)
        },
        modifier = modifier,
    ) {
        shown.forEachIndexed { index, row ->
            RankedRow(
                label = row.label,
                count = LogParser.formatCount(row.count),
                warn = row.open.takeIf { it > 0 }?.let { LogParser.formatCount(it) },
                warnSevere = row.open == row.count,
                warnIcon = Icons.Outlined.LockOpen,
                rail = when {
                    row.open == row.count -> palette.red
                    row.open > 0 -> palette.yellow
                    else -> null
                },
                leading = rowGlyph?.let { glyphFor -> { glyphFor(row) } },
                onClick = onRowClick?.let { click -> { click(row.key) } },
            )
            if (index < shown.lastIndex) CardDivider()
        }
        if (rows.size > ROW_CAP) {
            val rest = rows.drop(ROW_CAP)
            Text(
                text = pluralStringResource(
                    more,
                    rest.size,
                    LogParser.formatCount(rest.sumOf { it.count }),
                    LogParser.formatCount(rest.size),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DecisionFeedFooter(state: CrowdSecUiState, onLoadMore: () -> Unit) {
    val palette = LocalTmPalette.current
    val feed = state.feed
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val empty = feed == null || feed.rows.isEmpty()
        Text(
            text = when {
                !state.decisionsOk -> stringResource(R.string.crowdsec_decisions_unavailable)
                feed == null || (feed.loading && !feed.loaded) -> stringResource(R.string.crowdsec_decisions_loading)
                feed.error != null && feed.rows.isEmpty() -> feed.error.asString()
                feed.rows.isEmpty() -> stringResource(R.string.crowdsec_decisions_none)
                else -> stringResource(
                    R.string.crowdsec_decisions_showing,
                    LogParser.formatCount(feed.rows.size),
                    LogParser.formatCount(feed.total),
                )
            },
            style = if (empty) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(if (empty) TmSpacing.lg else TmSpacing.sm),
        )
        if (feed?.error != null && feed.rows.isNotEmpty()) {
            Text(
                text = feed.error.asString(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.red,
            )
        }
        if (feed?.more == true) {
            TextButton(onClick = onLoadMore, enabled = !feed.loading) {
                Text(stringResource(if (feed.loading) R.string.common_loading else R.string.common_load_more))
            }
        }
    }
}

private const val ROW_CAP = 4

private const val CELL_CAP = 240

@Composable
private fun AlertRow(
    alert: CsAlert,
    country: String,
    handled: Boolean,
    decisionsOk: Boolean,
    onBan: () -> Unit,
    onFilterIp: () -> Unit = {},
    onFilterScenario: () -> Unit = {},
    onFilterRouter: (String) -> Unit = {},
    onFilterHost: (String) -> Unit = {},
) {
    val palette = LocalTmPalette.current

    TmCard(accentColor = if (handled) palette.green else palette.yellow) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = alert.ip.ifEmpty { stringResource(R.string.common_unknown) },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onFilterIp() }
                            .padding(horizontal = 2.dp),
                    )
                    Text(
                        text = LogParser.shortName(alert.scenarioName.substringAfter('/')),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onFilterScenario() }
                            .padding(horizontal = 2.dp),
                    )
                }
                Text(
                    text = buildString {
                        alert.uris.firstOrNull()?.let { append(it) }
                        if (alert.users.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(alert.users.first())
                        }
                        if (isEmpty()) append(alert.scenarioName)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val router = alert.routers.firstOrNull()
                val host = alert.hosts.firstOrNull()
                if (router != null || host != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        router?.let {
                            EvidenceValue(
                                icon = Icons.Outlined.AltRoute,
                                text = it.substringBefore('@').ifEmpty { it },
                                description = stringResource(R.string.crowdsec_filter_route),
                                onClick = { onFilterRouter(it) },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        host?.let {
                            EvidenceValue(
                                icon = Icons.Outlined.Language,
                                text = it,
                                description = stringResource(R.string.crowdsec_filter_host),
                                onClick = { onFilterHost(it) },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (country.isNotEmpty()) {
                        Text(text = Countries.flag(country), style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = country,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                    if (alert.source.asName.isNotEmpty()) {
                        Text(
                            text = "· ${alert.source.asName}",
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
                    text = alert.eventsCount.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = palette.muted,
                )
                Text(
                    text = when {
                        !decisionsOk -> UNKNOWN
                        handled -> stringResource(R.string.crowdsec_alert_banned)
                        else -> stringResource(R.string.crowdsec_alert_open)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !decisionsOk -> palette.muted
                        handled -> palette.green
                        else -> palette.yellow
                    },
                )
                if (!handled && alert.ip.isNotEmpty()) {
                    IconButton(onClick = onBan, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Block,
                            contentDescription = stringResource(R.string.crowdsec_ban_named, alert.ip),
                            tint = palette.red,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceValue(
    icon: ImageVector,
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClickLabel = description) { onClick() }
            .padding(horizontal = 2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(11.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DecisionRow(decision: CsDecision, onDelete: () -> Unit) {
    val palette = LocalTmPalette.current

    TmCard(accentColor = if (decision.own) palette.blue else palette.muted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = decision.value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(decision.type)
                        if (decision.scope.isNotEmpty()) append(" · ${decision.scope}")
                        if (decision.originKey.isNotEmpty()) append(" · ${decision.originKey}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                if (decision.scenario.isNotEmpty()) {
                    Text(
                        text = decision.scenario,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = decision.duration,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
            if (decision.id != 0L) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.crowdsec_unban_named, decision.value),
                        tint = palette.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrowdSecSearchBar(
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
                placeholder = { Text(stringResource(R.string.crowdsec_search_placeholder)) },
                leadingIcon = {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_close_search))
                    }
                },
                trailingIcon = {
                    if (queryState.text.isNotEmpty()) {
                        IconButton(onClick = { queryState.clearText() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_clear_search))
                        }
                    }
                },
            )
        },
    ) {
        Text(
            text = pluralStringResource(R.plurals.common_matching, matches, matches),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            modifier = Modifier.padding(TmSpacing.lg),
        )
    }
}

@Composable
private fun CrowdSecVerdictCard(
    state: CrowdSecUiState,
    sources: List<CsRanked>,
    scenarios: List<CsRanked>,
    span: Long?,
) {
    val palette = LocalTmPalette.current
    val alerts = state.alerts
    val events = remember(alerts) { alerts.sumOf { it.eventsCount } }
    val unbanned = sources.count { it.open > 0 }

    val healthy = state.alertsOk && alerts.isEmpty()
    val accent = when {
        !state.alertsOk || !state.decisionsOk -> palette.red
        alerts.isEmpty() -> palette.green
        else -> palette.yellow
    }
    val headline = when {
        !state.alertsOk -> stringResource(R.string.crowdsec_alerts_unavailable)
        alerts.isEmpty() -> stringResource(R.string.crowdsec_verdict_quiet)
        else -> stringResource(R.string.crowdsec_verdict_probed)
    }

    TmCard(accentColor = accent) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (healthy) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
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
                    if (state.alertsOk) {
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.GpsFixed,
                            text = pluralStringResource(R.plurals.crowdsec_sources_count, sources.size, sources.size),
                            color = palette.muted,
                        )
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.Bolt,
                            text = pluralStringResource(R.plurals.crowdsec_scenarios_count, scenarios.size, scenarios.size),
                            color = palette.muted,
                        )
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.Timeline,
                            text = pluralStringResource(R.plurals.crowdsec_events_count, events, LogParser.formatCount(events)),
                            color = palette.muted,
                        )
                    }
                    if (unbanned > 0) {
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.LockOpen,
                            text = pluralStringResource(R.plurals.crowdsec_no_active_ban, unbanned, unbanned),
                            color = palette.yellow,
                        )
                    }
                    if (state.alertsOk && alerts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.crowdsec_no_alerts_window),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                }
            }
        }

        val stamp = listOfNotNull(
            span?.let { stringResource(R.string.crowdsec_span_of_alerts, LogParser.spanText(it)) },
            state.readAt?.let { read ->
                stringResource(
                    R.string.crowdsec_read_ago,
                    LogParser.spanText((System.currentTimeMillis() - read).coerceAtLeast(0)),
                )
            },
        ).joinToString(UiText.SEPARATOR)
        if (stamp.isNotEmpty()) {
            Text(
                text = stamp,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
                modifier = Modifier.align(Alignment.End),
            )
        }

        if (state.snapshot.alertsCapped == true) {
            Text(
                text = stringResource(
                    R.string.crowdsec_alerts_capped,
                    state.snapshot.alertLimit ?: alerts.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = palette.yellow,
            )
        }
        if (!state.decisionsOk) {
            Text(
                text = state.decisionsError?.asString().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.red,
            )
        }
        if (!state.alertsOk) {
            Text(
                text = state.alertsError?.asString().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.red,
            )
        }
    }
}

@Composable
private fun CrowdSecVerdictItem(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CrowdSecWindowRow(state: CrowdSecUiState, span: Long?) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TmSpacing.xs),
    ) {
        SectionLabel(stringResource(R.string.logs_window))
        Text(
            text = listOfNotNull(
                if (state.alertsOk) {
                    pluralStringResource(R.plurals.crowdsec_retained_alerts, state.alerts.size, state.alerts.size.toString())
                } else {
                    pluralStringResource(R.plurals.crowdsec_retained_alerts, 0, UNKNOWN)
                },
                span?.let { stringResource(R.string.logs_span, LogParser.spanText(it)) },
                if (state.decisionsOk) {
                    pluralStringResource(
                        R.plurals.crowdsec_bans_count,
                        state.snapshot.decisionTotal,
                        LogParser.formatCount(state.snapshot.decisionTotal),
                    )
                } else {
                    pluralStringResource(R.plurals.crowdsec_bans_count, 0, UNKNOWN)
                },
            ).joinToString(UiText.SEPARATOR),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(start = TmSpacing.sm),
        )
    }
}

private data class DeskCard(val span: Int, val content: @Composable (Modifier) -> Unit)

private fun packRows(cards: List<DeskCard>, columns: Int): List<List<DeskCard>> {
    val rows = mutableListOf<List<DeskCard>>()
    var row = mutableListOf<DeskCard>()
    var used = 0
    cards.forEach { card ->
        val span = card.span.coerceAtMost(columns)
        if (used + span > columns && row.isNotEmpty()) {
            rows += row
            row = mutableListOf()
            used = 0
        }
        row += card
        used += span
    }
    if (row.isNotEmpty()) rows += row
    return rows
}

@Composable
private fun FilterBar(
    state: CrowdSecUiState,
    onRemove: (CsFacet) -> Unit,
    onClear: () -> Unit,
) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.crowdsec_filtered_by), modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.crowdsec_clear_all),
                style = MaterialTheme.typography.labelSmall,
                color = palette.blue,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClear() }
                    .padding(horizontal = TmSpacing.xs, vertical = 2.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            modifier = Modifier.padding(top = TmSpacing.xs),
        ) {
            state.facets.active.forEach { (facet, value) ->
                FacetChip(
                    label = facet.label.asString(),
                    value = if (facet == CsFacet.Country) {
                        "${Countries.flag(value)} ${Countries.name(value)}"
                    } else {
                        value
                    },
                    onRemove = { onRemove(facet) },
                )
            }
            if (state.query.isNotEmpty()) {
                FacetChip(label = stringResource(R.string.crowdsec_facet_search), value = state.query, onRemove = null)
            }
        }
    }
}

@Composable
private fun FacetChip(label: String, value: String, onRemove: (() -> Unit)?) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(TmRadius.sm))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(if (onRemove != null) Modifier.clickable { onRemove() } else Modifier)
            .padding(horizontal = TmSpacing.sm, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.crowdsec_remove_filter, label),
                tint = palette.muted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.crowdsec

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
import androidx.compose.material.icons.outlined.Menu
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
    val scope = rememberCoroutineScope()
    var addSheetFor by remember { mutableStateOf<String?>(null) }
    var addSheetOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CsDecision?>(null) }

    LaunchedEffect(viewModel.queryState) {
        snapshotFlow { viewModel.queryState.text.toString() }.collect(viewModel::onQueryChange)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    if (addSheetOpen) {
        AddDecisionSheet(
            initialValue = addSheetFor.orEmpty(),
            decisions = state.decisions.filter { it.own },
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
            title = { Text("Remove decision") },
            text = { Text("Unban ${decision.value}? CrowdSec will stop blocking it immediately.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDecision(decision)
                        pendingDelete = null
                    },
                ) {
                    Text("Unban")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
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
                    Icon(Icons.Outlined.Add, contentDescription = "Add decision")
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
                        Icon(Icons.Outlined.Search, contentDescription = "Search CrowdSec")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh CrowdSec")
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
                state.visibleDecisions.size
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
                state.loading -> LoadingState(label = "Loading CrowdSec")

                state.notConfigured -> EmptyState(
                    headline = "CrowdSec is not configured",
                    body = "Set CROWDSEC_LAPI_URL and a bouncer API key on this server, then recreate " +
                        "the container, to see decisions and alerts.",
                )

                state.loadError != null -> ErrorState(
                    headline = "Could not reach the CrowdSec LAPI",
                    body = state.loadError,
                    onRetry = viewModel::refresh,
                )

                !state.decisionsOk && !state.alertsOk -> ErrorState(
                    headline = "CrowdSec LAPI unavailable",
                    body = state.decisionsError ?: state.alertsError,
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
) {
    val palette = LocalTmPalette.current
    val wide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // The cards rank the filtered window, the way the web feeds them sel.alerts (crowdsec.js:1484).
    val alerts = state.visibleAlerts
    val banned = state.snapshot.bannedIps
    val sources = remember(alerts, banned) { CrowdSecAnalytics.sources(alerts, banned) }
    val networks = remember(alerts, banned) { CrowdSecAnalytics.networks(alerts, banned) }
    val scenarios = remember(alerts, banned) { CrowdSecAnalytics.scenarios(alerts, banned) }
    val paths = remember(alerts, banned) { CrowdSecAnalytics.paths(alerts, banned) }
    val accounts = remember(alerts, banned) { CrowdSecAnalytics.accounts(alerts, banned) }
    val tooling = remember(alerts, banned) { CrowdSecAnalytics.tooling(alerts, banned) }
    val origins = remember(state.decisions) { CrowdSecAnalytics.origins(state.decisions) }
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
            // The web lays the desk out in columns that grow with the viewport; a phone gets two
            // across, a tablet three, in the same worst-first order.
            // The web's desk grid: two columns on a phone, four on a tablet, with the ranked-list
            // cards spanning two of them (app.css:197, 258, 377).
            val columns = if (wide) 4 else 2
            val loose = sources.filter { it.open > 0 }
            val repeats = sources.filter { it.count > 1 }
            val bannedSources = sources.size - loose.size
            val alertTotal = alerts.size

            val cards: List<DeskCard> = listOf(
                DeskCard(1) { cardModifier ->
                    SignalCard(
                        label = "Attacking sources",
                        hero = if (state.alertsOk) LogParser.formatCount(sources.size) else "-",
                        accent = palette.red,
                        glyph = Icons.Outlined.GpsFixed,
                        health = when {
                            loose.any { it.count > 1 } -> palette.red
                            loose.isNotEmpty() -> palette.yellow
                            else -> null
                        },
                        subtitle = if (!state.alertsOk) {
                            "sources are only listed on /v1/alerts"
                        } else {
                            sources.firstOrNull()
                                ?.let { "worst ${it.key} ${LogParser.formatCount(it.weight)} events" }
                                ?: "no sources"
                        },
                        flags = when {
                            !state.alertsOk -> listOf(SignalChip(Icons.Outlined.Info, "ban state unknown"))
                            !state.decisionsOk -> listOf(SignalChip(Icons.Outlined.Info, "ban state unknown"))
                            loose.isNotEmpty() -> listOf(
                                SignalChip(
                                    Icons.Outlined.LockOpen,
                                    "${LogParser.formatCount(loose.size)} loose",
                                    if (loose.any { it.count > 1 }) palette.red else palette.yellow,
                                    onClick = { onFacet(CsFacet.Outcome, "loose") },
                                ),
                                SignalChip(
                                    Icons.Outlined.Block,
                                    "${LogParser.formatCount(bannedSources)} banned",
                                    onClick = { onFacet(CsFacet.Outcome, "banned") },
                                ),
                            )
                            sources.isEmpty() -> emptyList()
                            else -> listOf(SignalChip(Icons.Outlined.CheckCircle, "every source banned", palette.green))
                        },
                        footer = if (sources.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(
                                SignalChip(Icons.Outlined.Repeat, "${LogParser.formatCount(repeats.size)} repeat"),
                                SignalChip(
                                    Icons.AutoMirrored.Outlined.CallMissedOutgoing,
                                    "${LogParser.formatCount(sources.size - repeats.size)} one-shot",
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
                        label = "Networks",
                        accent = palette.purple,
                        glyph = Icons.Outlined.Public,
                        rows = networks,
                        onRowClick = { onFacet(CsFacet.Asn, it) },
                        blind = !state.alertsOk,
                        blindReason = "AS names ride on alert.source",
                        emptyReason = "this agent reports ip only",
                        thing = "networks",
                        subtitle = networks.firstOrNull()
                            ?.let { "worst ${it.label} ${LogParser.formatCount(it.count)} alerts" },
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
                        label = "Scenarios",
                        accent = palette.orange,
                        glyph = Icons.Outlined.Bolt,
                        rows = scenarios,
                        blind = !state.alertsOk,
                        blindReason = "scenarios come from /v1/alerts",
                        emptyReason = "nothing to rank in the retained window",
                        hero = LogParser.formatCount(alertTotal),
                        heroUnit = "alerts",
                        thing = "scenarios",
                        subtitle = scenarios.firstOrNull()?.let {
                            "worst ${it.label} · ${LogParser.formatCount(alerts.sumOf { a -> a.eventsCount })} " +
                                "events rolled up"
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
                        label = if (onPaths) "Targeted paths" else "Targeted accounts",
                        accent = palette.blue,
                        glyph = Icons.Outlined.MyLocation,
                        rows = targeting,
                        onRowClick = { onFacet(if (onPaths) CsFacet.Uri else CsFacet.User, it) },
                        blind = !state.alertsOk,
                        blindReason = if (onPaths) "paths live in alert.meta[]" else "accounts live in alert.meta[]",
                        emptyReason = "no meta reported",
                        hero = LogParser.formatCount(targeting.size),
                        heroUnit = if (onPaths) "paths" else "accounts",
                        noun = "hits",
                        thing = if (onPaths) "paths" else "accounts",
                        subtitle = targeting.firstOrNull()?.let { "most wanted ${it.label}" },
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
                DeskCard(1) { cardModifier ->
                    RankCard(
                        label = "Tooling",
                        accent = palette.teal,
                        glyph = Icons.Outlined.SmartToy,
                        rows = tooling,
                        onRowClick = { onFacet(CsFacet.Agent, it) },
                        blind = !state.alertsOk,
                        blindReason = "user agents live in alert.meta[]",
                        emptyReason = "no HTTP scenario fired here",
                        noun = "hits",
                        thing = "agents",
                        subtitle = tooling.firstOrNull()?.let { "worst ${it.label}" },
                        flags = listOf(
                            SignalChip(Icons.Outlined.Code, "${LogParser.formatCount(tooling.size)} tools"),
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
                    val perCell = ((state.decisions.size + CELL_CAP - 1) / CELL_CAP).coerceAtLeast(1)
                    SignalCard(
                        label = "Bans in force",
                        hero = if (state.decisionsOk) LogParser.formatCount(state.decisions.size) else "-",
                        accent = palette.green,
                        glyph = Icons.Outlined.Shield,
                        health = if (!state.decisionsOk) palette.red else null,
                        subtitle = if (!state.decisionsOk) {
                            "nothing was read from /v1/decisions"
                        } else {
                            "${LogParser.formatCount(state.ownBans)} from this host · " +
                                "${LogParser.formatCount(state.subscribedBans)} subscribed"
                        },
                        flags = if (state.decisionsOk) {
                            listOf(
                                SignalChip(
                                    Icons.Outlined.Block,
                                    "${LogParser.formatCount(state.decisions.size)} ban",
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
                            cells = List((state.decisions.size + perCell - 1) / perCell) { index ->
                                val ownCells = (state.ownBans + perCell - 1) / perCell
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
                                text = "1 cell = ${LogParser.formatCount(perCell)}",
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CrowdSecView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = state.view == view,
                        onClick = { onViewChange(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, CrowdSecView.entries.size),
                    ) {
                        Text(
                            text = when (view) {
                                CrowdSecView.Evidence ->
                                    "Evidence ${if (state.alertsOk) state.visibleAlerts.size else UNKNOWN}"
                                CrowdSecView.Bans ->
                                    "Bans ${if (state.decisionsOk) state.visibleDecisions.size else UNKNOWN}"
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
                )
            }
            if (state.visibleAlerts.isEmpty()) {
                item {
                    Text(
                        text = if (state.alertsOk) "No alerts match" else "Alerts unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(TmSpacing.lg),
                    )
                }
            }
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
                        text = if (state.decisionsOk) "No active decisions" else "Decisions unavailable",
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
    noun: String = "alerts",
    thing: String = "networks",
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
            else -> subtitle ?: "worst ${rows.first().key}"
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
                text = "+${LogParser.formatCount(rest.sumOf { it.count })} $noun across " +
                    "${LogParser.formatCount(rest.size)} more $thing",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The web's compact desk shows four ranked rows and counts the tail off the same cut. */
private const val ROW_CAP = 4

/** The web draws at most 240 cells and prints what one cell stands for (crowdsec.js:1). */
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
                        text = alert.ip.ifEmpty { "unknown" },
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
                        handled -> "banned"
                        else -> "open"
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
                            contentDescription = "Ban ${alert.ip}",
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
                        contentDescription = "Unban ${decision.value}",
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
                placeholder = { Text("Filter by address, scenario, network or path") },
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
            text = "$matches matching",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            modifier = Modifier.padding(TmSpacing.lg),
        )
    }
}

/**
 * The web's banner: what the LAPI is seeing right now, boxed and accented by how bad it is, with
 * the window's totals underneath. Same shape as the logs verdict so the two screens read alike.
 */
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
        !state.alertsOk -> "Alerts unavailable"
        alerts.isEmpty() -> "Nothing probing right now"
        else -> "Actively probed"
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
                            text = "${sources.size} " + if (sources.size == 1) "source" else "sources",
                            color = palette.muted,
                        )
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.Bolt,
                            text = "${scenarios.size} " + if (scenarios.size == 1) "scenario" else "scenarios",
                            color = palette.muted,
                        )
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.Timeline,
                            text = "${LogParser.formatCount(events)} " + if (events == 1) "event" else "events",
                            color = palette.muted,
                        )
                    }
                    if (unbanned > 0) {
                        CrowdSecVerdictItem(
                            icon = Icons.Outlined.LockOpen,
                            text = "$unbanned no active ban",
                            color = palette.yellow,
                        )
                    }
                    if (state.alertsOk && alerts.isEmpty()) {
                        Text(
                            text = "no alerts in the retained window",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                }
            }
        }

        val stamp = buildString {
            span?.let { append("${LogParser.spanText(it)} of alerts") }
            state.readAt?.let { read ->
                if (isNotEmpty()) append(" · ")
                append("read ${LogParser.spanText((System.currentTimeMillis() - read).coerceAtLeast(0))} ago")
            }
        }
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
                text = "showing the newest ${state.snapshot.alertLimit ?: alerts.size} alerts, " +
                    "the LAPI holds more (CROWDSEC_ALERT_LIMIT)",
                style = MaterialTheme.typography.labelSmall,
                color = palette.yellow,
            )
        }
        if (!state.decisionsOk) {
            Text(
                text = state.decisionsError.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.red,
            )
        }
        if (!state.alertsOk) {
            Text(
                text = state.alertsError.orEmpty(),
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

/**
 * The web keeps this outside the banner: what the window holds, and the note that the alert feed
 * is local detections rather than everything the community has seen.
 */
@Composable
private fun CrowdSecWindowRow(state: CrowdSecUiState, span: Long?) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TmSpacing.xs),
    ) {
        SectionLabel("Window")
        Text(
            text = buildString {
                append(if (state.alertsOk) "retained ${state.alerts.size} alerts" else "retained $UNKNOWN alerts")
                span?.let { append(" · span ${LogParser.spanText(it)}") }
                append(
                    if (state.decisionsOk) {
                        " · ${LogParser.formatCount(state.decisions.size)} bans"
                    } else {
                        " · $UNKNOWN bans"
                    },
                )
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(start = TmSpacing.sm),
        )
    }
}

/** A desk card and how many grid columns it takes, mirroring the web's lg-wide. */
private data class DeskCard(val span: Int, val content: @Composable (Modifier) -> Unit)

/** Fills each row up to [columns] in order, starting a new row when the next card will not fit. */
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

/**
 * What the desk is filtered by right now: one chip per facet, each removable on its own, the way
 * the web's window row carries them. Every chip applies together.
 */
@Composable
private fun FilterBar(
    state: CrowdSecUiState,
    onRemove: (CsFacet) -> Unit,
    onClear: () -> Unit,
) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Filtered by", modifier = Modifier.weight(1f))
            Text(
                text = "Clear all",
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
                    label = facet.label,
                    value = if (facet == CsFacet.Country) {
                        "${Countries.flag(value)} ${Countries.name(value)}"
                    } else {
                        value
                    },
                    onRemove = { onRemove(facet) },
                )
            }
            if (state.query.isNotEmpty()) {
                FacetChip(label = "search", value = state.query, onRemove = null)
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
                contentDescription = "Remove the $label filter",
                tint = palette.muted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.crowdsec

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsRanked
import dev.chr0nzz.traefikmanager.data.model.LogParser
import dev.chr0nzz.traefikmanager.ui.components.CountryStrip
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.RankedRow
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.SignalCard
import dev.chr0nzz.traefikmanager.ui.components.SignalCells
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
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
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
                    }
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
                    body = "Set CROWDSEC_LAPI_URL and a bouncer API key on this server to see decisions and alerts.",
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
    onClearFilters: () -> Unit,
    onBan: (String) -> Unit,
    onDelete: (CsDecision) -> Unit,
) {
    val palette = LocalTmPalette.current
    val alerts = state.alerts
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
            Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                Text(
                    text = when {
                        !state.alertsOk -> "Alerts unavailable"
                        alerts.isEmpty() -> "Nothing probing right now"
                        else -> "Actively probed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alerts.isEmpty()) palette.green else palette.yellow,
                )
                Text(
                    text = buildString {
                        append(if (state.alertsOk) "retained ${alerts.size} alerts" else "retained $UNKNOWN alerts")
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
                )
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

        if (state.filtersActive) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            state.country?.let { append("${Countries.flag(it)} ${Countries.name(it)}  ") }
                            state.scenario?.let { append(it) }
                        }.ifBlank { "filtered" },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.blue,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Clear filters",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.blue,
                        modifier = Modifier.clickable { onClearFilters() },
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                SignalCard(
                    label = "Attacking sources",
                    hero = if (state.alertsOk) sources.size.toString() else "-",
                    accent = palette.red,
                    subtitle = if (!state.alertsOk) {
                        "needs a watcher login"
                    } else {
                        sources.firstOrNull()?.let { "worst ${it.key} · ${it.weight} events" } ?: "nothing to rank"
                    },
                    trailing = sources.count { it.open == 0 }.takeIf { it > 0 }?.let { "$it banned" },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SignalCells(
                        cells = sources.map { if (it.open == 0) palette.green else palette.red },
                    )
                }

                SignalCard(
                    label = "Bans in force",
                    hero = if (state.decisionsOk) LogParser.formatCount(state.decisions.size) else "-",
                    accent = palette.green,
                    subtitle = if (!state.decisionsOk) {
                        "LAPI unreachable, this card reports the read failure instead of the zero it would invent"
                    } else {
                        "${state.ownBans} from this host · ${state.subscribedBans} subscribed"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    origins.take(4).forEach { origin ->
                        RankedRow(
                            label = origin.origin,
                            count = LogParser.formatCount(origin.count),
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                RankCard(
                    label = "Networks",
                    accent = palette.purple,
                    rows = networks,
                    blind = !state.alertsOk,
                    blindReason = "needs a watcher login",
                    emptyReason = "this agent reports ip only",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                RankCard(
                    label = "Scenarios",
                    accent = palette.orange,
                    rows = scenarios,
                    blind = !state.alertsOk,
                    blindReason = "needs a watcher login",
                    emptyReason = "nothing to rank in the retained window",
                    onRowClick = onScenarioClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        item {
            val targeting = if (paths.isNotEmpty() || accounts.isEmpty()) paths else accounts
            RankCard(
                label = if (paths.isNotEmpty() || accounts.isEmpty()) "Targeted paths" else "Targeted accounts",
                accent = palette.blue,
                rows = targeting,
                blind = !state.alertsOk,
                blindReason = "needs a watcher login",
                emptyReason = "no meta reported",
                noun = "hits",
            )
        }

        item {
            RankCard(
                label = "Tooling",
                accent = palette.teal,
                rows = tooling,
                blind = !state.alertsOk,
                blindReason = "needs a watcher login",
                emptyReason = "no HTTP scenario fired here",
                noun = "hits",
            )
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
    onRowClick: ((String) -> Unit)? = null,
) {
    val total = rows.sumOf { it.count }
    SignalCard(
        label = label,
        hero = when {
            blind -> "-"
            rows.isEmpty() -> "0"
            else -> rows.size.toString()
        },
        accent = accent,
        subtitle = when {
            blind -> blindReason
            rows.isEmpty() -> emptyReason
            else -> "worst ${rows.first().key}"
        },
        modifier = modifier,
    ) {
        CrowdSecAnalytics.top(rows).forEach { row ->
            RankedRow(
                label = row.label,
                count = row.count.toString(),
                trailing = CrowdSecAnalytics.percent(row.count, total),
                onClick = onRowClick?.let { click -> { click(row.key) } },
            )
        }
        if (rows.size > 6) {
            val remaining = rows.drop(6).sumOf { it.count }
            Text(
                text = "+$remaining $noun across ${rows.size - 6} more",
                style = MaterialTheme.typography.labelSmall,
                color = LocalTmPalette.current.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AlertRow(
    alert: CsAlert,
    country: String,
    handled: Boolean,
    decisionsOk: Boolean,
    onBan: () -> Unit,
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
                    )
                    Text(
                        text = LogParser.shortName(alert.scenarioName.substringAfter('/')),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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

package dev.chr0nzz.traefikmanager.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.chr0nzz.traefikmanager.data.model.ProviderMiddleware
import dev.chr0nzz.traefikmanager.data.model.ProviderPage
import dev.chr0nzz.traefikmanager.data.model.ProviderProtocol
import dev.chr0nzz.traefikmanager.data.model.ProviderRoute
import dev.chr0nzz.traefikmanager.data.repo.Verdict
import dev.chr0nzz.traefikmanager.ui.components.VerdictLine
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.ValueRow
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.asString
import dev.chr0nzz.traefikmanager.ui.components.distinctItems

fun ProviderRoute.tmStatus(): TmStatus = when (status.lowercase()) {
    "enabled" -> TmStatus.Ok
    "disabled", "error" -> TmStatus.Error
    else -> TmStatus.Unknown
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProviderScreen(
    page: ProviderPage,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProviderViewModel = hiltViewModel(key = page.route),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val refreshState = rememberPullToRefreshState()
    val searchBarState = rememberSearchBarState()
    val searchScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val queryState = viewModel.queryState

    LaunchedEffect(page) { viewModel.bind(page) }

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }.collect(viewModel::onQueryChange)
    }

    state.selected?.let { route ->
        ProviderRouteSheet(route = route, onDismiss = { viewModel.select(null) })
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(searchScrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ProviderTopBar(
                page = page,
                onOpenDrawer = onOpenDrawer,
                searchBarState = searchBarState,
                queryState = queryState,
                results = state.visible,
                protocol = state.protocol,
                status = state.status,
                scrollBehavior = searchScrollBehavior,
                onProtocolChange = viewModel::onProtocolChange,
                onStatusChange = viewModel::onStatusChange,
                onResultClick = viewModel::select,
                onRefresh = viewModel::refresh,
            )
        },
    ) { insets ->
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
                state.loading -> LoadingState(label = stringResource(R.string.providers_loading, page.label.asString()))
                state.error != null && state.routes.isEmpty() -> ErrorState(
                    headline = stringResource(R.string.common_error_traefik_unreachable),
                    body = state.error?.asString(),
                    onRetry = viewModel::refresh,
                )
                !state.reachable -> ErrorState(
                    headline = stringResource(R.string.common_error_traefik_unreachable),
                    body = stringResource(R.string.providers_error_body),
                    onRetry = viewModel::refresh,
                )
                state.routes.isEmpty() && state.middlewares.isEmpty() -> EmptyState(
                    headline = stringResource(R.string.providers_empty, page.label.asString()),
                    body = stringResource(page.empty),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = TmSpacing.lg,
                        end = TmSpacing.lg,
                        top = TmSpacing.xs,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                ) {
                    item { VerdictCard(state = state) }
                    if (state.visible.isEmpty() && state.routes.isNotEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                headline = stringResource(R.string.providers_no_match),
                                body = stringResource(R.string.common_no_match_body),
                            )
                        }
                    }
                    distinctItems(state.visible, key = { "${it.protocol}:${it.name}" }) { route ->
                        ProviderRouteCard(
                            route = route,
                            onClick = { viewModel.select(route) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (state.middlewares.isNotEmpty()) {
                        item {
                            SectionLabel(
                                stringResource(R.string.providers_middlewares_title, state.middlewares.size),
                                modifier = Modifier.padding(top = TmSpacing.sm),
                            )
                        }
                        distinctItems(state.middlewares, key = { "mw:${it.name}" }) { middleware ->
                            ProviderMiddlewareCard(middleware = middleware)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(state: ProviderUiState) {
    val verdict = state.verdict
    VerdictLine(
        verdict = Verdict(
            headline = verdict.headline,
            detail = verdict.detail,
            status = if (verdict.notServing > 0) TmStatus.Error else TmStatus.Ok,
        ),
        info = stringResource(state.page.note),
    )
}

@Composable
private fun ProviderRouteCard(route: ProviderRoute, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalTmPalette.current
    val status = route.tmStatus()
    TmCard(
        modifier = modifier,
        accent = if (status == TmStatus.Error) status else null,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            StatusDot(status)
            if (route.protocol != ProviderProtocol.Http) {
                Text(
                    text = route.protocol.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (route.protocol == ProviderProtocol.Udp) palette.orange else palette.teal,
                )
            }
            Text(
                text = route.shortName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (route.protocol != ProviderProtocol.Udp) {
                Icon(
                    imageVector = if (route.tls) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = if (route.tls) palette.muted else palette.yellow,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        val host = route.plainHost
        if (host != null) {
            ValueRow(icon = Icons.Outlined.Language, value = host, color = palette.blue)
        } else if (route.rule.isNotEmpty()) {
            ValueRow(icon = Icons.Outlined.Language, value = route.rule, color = palette.muted)
        }
        route.target?.let { target ->
            ValueRow(icon = Icons.Outlined.SubdirectoryArrowRight, value = target, color = palette.green)
        }

        val meta = buildList {
            addAll(route.entryPoints)
            addAll(route.middlewares.map { it.substringBefore('@') })
            if (route.service.isNotEmpty()) add(route.service.substringBefore('@'))
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
private fun ProviderMiddlewareCard(middleware: ProviderMiddleware) {
    val palette = LocalTmPalette.current
    TmCard(accentColor = palette.purple) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            StatusDot(
                when (middleware.status.lowercase()) {
                    "enabled" -> TmStatus.Ok
                    "disabled", "error" -> TmStatus.Error
                    else -> TmStatus.Unknown
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = middleware.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (middleware.type.isNotEmpty()) {
                    Text(
                        text = middleware.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderRouteSheet(route: ProviderRoute, onDismiss: () -> Unit) {
    val palette = LocalTmPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, bottom = TmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.xs),
        ) {
            Text(
                text = route.shortName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.providers_read_only, route.provider),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            SheetRow(stringResource(R.string.common_label_status), route.status.ifEmpty { stringResource(R.string.common_unknown) })
            SheetRow(stringResource(R.string.common_label_protocol), route.protocol.label)
            SheetRow(stringResource(R.string.common_label_router), route.name, mono = true)
            if (route.rule.isNotEmpty()) SheetRow(stringResource(R.string.common_label_rule), route.rule, mono = true)
            if (route.service.isNotEmpty()) SheetRow(stringResource(R.string.common_label_service), route.service, mono = true)
            route.target?.let { SheetRow(stringResource(R.string.common_label_target), it, mono = true) }
            if (route.entryPoints.isNotEmpty()) SheetRow(stringResource(R.string.common_label_entry_points), route.entryPoints.joinToString(", "))
            if (route.middlewares.isNotEmpty()) SheetRow(stringResource(R.string.common_label_middlewares), route.middlewares.joinToString(", "), mono = true)
            if (route.protocol != ProviderProtocol.Udp) SheetRow("TLS", stringResource(if (route.tls) R.string.common_on else R.string.common_off))
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String, mono: Boolean = false) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.padding(top = TmSpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

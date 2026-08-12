package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.ui.components.ClearFiltersChip
import dev.chr0nzz.traefikmanager.ui.components.FilterChipRow
import dev.chr0nzz.traefikmanager.ui.components.FilterMenuChip
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.only
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun RoutesScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val searchBarState = rememberSearchBarState()
    val searchScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val queryState = viewModel.queryState
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }.collect(viewModel::onQueryChange)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    val select: (Route) -> Unit = { route ->
        selectedId = route.id
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route.id) }
    }

    val detailOnly = navigator.canNavigateBack()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(searchScrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!detailOnly) {
                RoutesSearchBar(
                    onOpenDrawer = onOpenDrawer,
                    searchBarState = searchBarState,
                    queryState = queryState,
                    results = state.visible,
                    protocol = state.protocol,
                    status = state.status,
                    scrollBehavior = searchScrollBehavior,
                    onProtocolChange = viewModel::onProtocolChange,
                    onStatusChange = viewModel::onStatusChange,
                    onResultClick = select,
                    onRefresh = viewModel::refresh,
                )
            }
        },
    ) { insets ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(insets),
            listPane = {
                AnimatedPane {
                    RoutesListPane(
                        state = state,
                        selectedId = selectedId,
                        contentPadding = insets,
                        onSelect = select,
                        onToggle = viewModel::toggle,
                        onRefresh = viewModel::refresh,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    RouteDetailPane(
                        route = state.routes.firstOrNull { it.id == selectedId },
                        showBack = navigator.canNavigateBack(),
                        contentPadding = insets,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onToggle = viewModel::toggle,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoutesListPane(
    state: RoutesUiState,
    selectedId: String?,
    contentPadding: PaddingValues,
    onSelect: (Route) -> Unit,
    onToggle: (Route) -> Unit,
    onRefresh: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val refreshState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.only(bottom = false)),
    ) {
        if (state.configErrors.isNotEmpty()) {
            Text(
                text = state.configErrors.joinToString("; ") { "${it.file}: ${it.error}" },
                style = MaterialTheme.typography.bodySmall,
                color = palette.red,
                modifier = Modifier.padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.routes.isEmpty() -> ErrorState(
                    headline = "Could not load routes",
                    body = state.error,
                    onRetry = onRefresh,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = TmSpacing.lg,
                        end = TmSpacing.lg,
                        top = TmSpacing.xs,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.visible.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                headline = if (state.routes.isEmpty()) "No routes yet" else "No routes match",
                                body = if (state.routes.isEmpty()) {
                                    "Routes you create in Traefik Manager appear here."
                                } else {
                                    "Try a different search or filter."
                                },
                            )
                        }
                    } else {
                        items(state.visible, key = { it.id }) { route ->
                            RouteCard(
                                route = route,
                                selected = route.id == selectedId,
                                toggling = state.togglingId == route.id,
                                onClick = { onSelect(route) },
                                onToggle = { onToggle(route) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

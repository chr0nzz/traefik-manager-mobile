package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.ServiceRow
import dev.chr0nzz.traefikmanager.data.repo.ProviderCount
import dev.chr0nzz.traefikmanager.ui.components.tmPaneScaffoldDirective
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.ProviderRow
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.only
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onOpenDrawer: () -> Unit = {},
    initialStatus: String? = null,
    initialProvider: String? = null,
    modifier: Modifier = Modifier,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = tmPaneScaffoldDirective(),
    )
    val scope = rememberCoroutineScope()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(initialStatus, initialProvider) {
        viewModel.applyDeepLink(initialStatus, initialProvider)
    }
    LaunchedEffect(viewModel.queryState) {
        snapshotFlow { viewModel.queryState.text.toString() }.collect(viewModel::onQueryChange)
    }

    val select: (ServiceRow) -> Unit = { service ->
        selectedKey = service.key
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, service.key) }
    }

    val detailOnly = navigator.canNavigateBack()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!detailOnly) {
                ServicesTopBar(
                    onOpenDrawer = onOpenDrawer,
                    searchBarState = searchBarState,
                    queryState = viewModel.queryState,
                    results = state.visible,
                    state = state,
                    scrollBehavior = scrollBehavior,
                    onStatusChange = viewModel::onStatusChange,
                    onProtocolChange = viewModel::onProtocolChange,
                    onClearFilters = viewModel::clearFilters,
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
                    ServicesListPane(
                        state = state,
                        selectedKey = selectedKey,
                        contentPadding = insets,
                        onSelect = select,
                        onProviderClick = viewModel::onProviderChange,
                        onRefresh = viewModel::refresh,
                        onClearFilters = viewModel::clearFilters,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    ServiceDetailPane(
                        service = state.services.firstOrNull { it.key == selectedKey },
                        showBack = navigator.canNavigateBack(),
                        contentPadding = insets,
                        onBack = { scope.launch { navigator.navigateBack() } },
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServicesListPane(
    state: ServicesUiState,
    selectedKey: String?,
    contentPadding: PaddingValues,
    onSelect: (ServiceRow) -> Unit,
    onProviderClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.only(bottom = false)),
    ) {
        if (state.providers.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TmSpacing.lg,
                        end = TmSpacing.lg,
                        bottom = TmSpacing.xs,
                    ),
            ) {
                SectionLabel("Providers")
                ProviderRow(
                    providers = state.providers.map { provider ->
                        ProviderCount(
                            name = provider,
                            count = state.services.count { it.provider == provider },
                            worst = worstStatus(state, provider),
                        )
                    },
                    activeProvider = state.provider,
                    onProviderClick = onProviderClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                state.loading -> LoadingState()

                state.error != null -> ErrorState(
                    headline = "Traefik API not reachable",
                    body = state.error,
                    onRetry = onRefresh,
                )

                !state.reachable -> ErrorState(
                    headline = "Traefik API not reachable",
                    body = "Set TRAEFIK_API_URL and enable api: {} in the Traefik static config.",
                    onRetry = onRefresh,
                )

                state.services.isEmpty() -> EmptyState(
                    headline = "No services registered",
                    body = "Traefik answered, but it has no services yet.",
                )

                state.visible.isEmpty() -> EmptyState(
                    headline = "No services match",
                    body = "Clear the filters to see everything.",
                    actionLabel = "Clear filters",
                    onAction = onClearFilters,
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
                    items(state.visible, key = { it.key }) { service ->
                        ServiceCard(
                            service = service,
                            selected = service.key == selectedKey,
                            onClick = { onSelect(service) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private fun worstStatus(state: ServicesUiState, provider: String): TmStatus {
    val rows = state.services.filter { it.provider == provider }
    return when {
        rows.any { it.health == dev.chr0nzz.traefikmanager.data.model.ServiceHealth.Error } -> TmStatus.Error
        rows.any { it.health == dev.chr0nzz.traefikmanager.data.model.ServiceHealth.Warning } -> TmStatus.Warn
        else -> TmStatus.Ok
    }
}

package dev.chr0nzz.traefikmanager.ui.routes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.Route
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
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding),
        listPane = {
            AnimatedPane {
                RoutesListPane(
                    state = state,
                    selectedId = selectedId,
                    contentPadding = contentPadding,
                    onSelect = { route ->
                        selectedId = route.id
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route.id)
                        }
                    },
                    onToggle = viewModel::toggle,
                    onRefresh = viewModel::refresh,
                    onQueryChange = viewModel::onQueryChange,
                    onProtocolChange = viewModel::onProtocolChange,
                    onStatusChange = viewModel::onStatusChange,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                RouteDetailPane(
                    route = state.routes.firstOrNull { it.id == selectedId },
                    showBack = navigator.canNavigateBack(),
                    contentPadding = contentPadding,
                    onBack = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutesListPane(
    state: RoutesUiState,
    selectedId: String?,
    contentPadding: PaddingValues,
    onSelect: (Route) -> Unit,
    onToggle: (Route) -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onProtocolChange: (ProtocolFilter) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
) {
    val palette = LocalTmPalette.current
    val queryState = rememberTextFieldState()

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }.collect(onQueryChange)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.only(bottom = false)),
    ) {
        OutlinedTextField(
            state = queryState,
            placeholder = { Text("Search routes") },
            lineLimits = TextFieldLineLimits.SingleLine,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                showKeyboardOnFocus = false,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.sm),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            modifier = Modifier.padding(horizontal = TmSpacing.lg),
        ) {
            ProtocolFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.protocol == filter,
                    onClick = { onProtocolChange(filter) },
                    label = { Text(filter.label) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            modifier = Modifier.padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
        ) {
            StatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.status == filter,
                    onClick = { onStatusChange(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

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
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.routes.isEmpty() -> ErrorState(
                    headline = "Could not load routes",
                    body = state.error,
                    onRetry = onRefresh,
                )
                state.visible.isEmpty() -> EmptyState(
                    headline = if (state.routes.isEmpty()) "No routes yet" else "No routes match",
                    body = if (state.routes.isEmpty()) {
                        "Routes you create in Traefik Manager appear here."
                    } else {
                        "Try a different search or filter."
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = TmSpacing.lg,
                        end = TmSpacing.lg,
                        top = TmSpacing.xs,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.visible, key = { it.id }) { route ->
                        RouteCard(
                            route = route,
                            selected = route.id == selectedId,
                            toggling = state.togglingId == route.id,
                            onClick = { onSelect(route) },
                            onToggle = { onToggle(route) },
                        )
                    }
                }
            }
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.tmPaneScaffoldDirective
import dev.chr0nzz.traefikmanager.ui.components.ConfirmDeleteDialog
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.only
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiddlewaresScreen(
    onOpenDrawer: () -> Unit = {},
    onCreate: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MiddlewaresViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val paneDirective = tmPaneScaffoldDirective()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = paneDirective)
    val twoPanes = paneDirective.maxHorizontalPartitions > 1
    val scope = rememberCoroutineScope()
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<MiddlewareDef?>(null) }
    val searchBarState = rememberSearchBarState()
    val queryState = remember { TextFieldState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }.collect(viewModel::onQueryChange)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    val detailOnly = navigator.canNavigateBack()

    pendingDelete?.let { middleware ->
        ConfirmDeleteDialog(
            routeName = middleware.name,
            consequence = "Any route still referencing it will stop working.",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(middleware)
                pendingDelete = null
                scope.launch { navigator.navigateBack() }
            },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            // The FAB lives in the list pane now, so Scaffold cannot offset this for us.
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 72.dp))
        },
        topBar = {
            if (!detailOnly) {
                MiddlewaresTopBar(
                    onOpenDrawer = onOpenDrawer,
                    searchBarState = searchBarState,
                    queryState = queryState,
                    results = state.visible,
                    filter = state.filter,
                    scrollBehavior = scrollBehavior,
                    onFilterChange = viewModel::onFilterChange,
                    onResultClick = { middleware ->
                        selectedName = middleware.name
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, middleware.name) }
                    },
                    onRefresh = viewModel::refresh,
                    onOpenTemplates = onOpenTemplates,
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        MiddlewareListPane(
                            state = state,
                            selectedName = selectedName,
                            contentPadding = insets,
                            onSelect = { middleware ->
                                selectedName = middleware.name
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, middleware.name)
                                }
                            },
                            onRefresh = viewModel::refresh,
                        )
                        FloatingActionButton(
                            onClick = onCreate,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    bottom = insets.calculateBottomPadding(),
                                    end = if (twoPanes) {
                                        0.dp
                                    } else {
                                        insets.calculateEndPadding(LocalLayoutDirection.current)
                                    },
                                )
                                .padding(TmSpacing.lg),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add middleware")
                        }
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    val selected = state.middlewares.firstOrNull { it.name == selectedName }
                    MiddlewareDetailPane(
                        middleware = selected,
                        usageCount = selected?.let { state.usageFor(it.name) } ?: 0,
                        showBack = navigator.canNavigateBack(),
                        contentPadding = insets,
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onEdit = { middleware -> onEdit(middleware.name) },
                        onDelete = { middleware -> pendingDelete = middleware },
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiddlewareListPane(
    state: MiddlewaresUiState,
    selectedName: String?,
    contentPadding: PaddingValues,
    onSelect: (MiddlewareDef) -> Unit,
    onRefresh: () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.only(bottom = false)),
    ) {
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
                state.error != null -> ErrorState(
                    headline = "Could not load middlewares",
                    body = state.error,
                    onRetry = onRefresh,
                )
                state.visible.isEmpty() -> EmptyState(
                    headline = if (state.middlewares.isEmpty()) {
                        "No middlewares configured"
                    } else {
                        "No middlewares match"
                    },
                    body = if (state.middlewares.isEmpty()) {
                        "Middlewares you create appear here."
                    } else {
                        "Try a different search or filter."
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = TmSpacing.lg,
                        end = TmSpacing.lg,
                        top = TmSpacing.xs,
                        bottom = contentPadding.calculateBottomPadding() + 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.visible, key = { it.name + it.configFile }) { middleware ->
                        MiddlewareCard(
                            middleware = middleware,
                            usageCount = state.usageFor(middleware.name),
                            selected = middleware.name == selectedName,
                            onClick = { onSelect(middleware) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiddlewaresTopBar(
    onOpenDrawer: () -> Unit,
    searchBarState: androidx.compose.material3.SearchBarState,
    queryState: TextFieldState,
    results: List<MiddlewareDef>,
    filter: MiddlewareFilter,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onFilterChange: (MiddlewareFilter) -> Unit,
    onResultClick: (MiddlewareDef) -> Unit,
    onRefresh: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalTmPalette.current
    var filterMenuOpen by remember { mutableStateOf(false) }
    val filtersActive = filter != MiddlewareFilter.All

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        title = { Text("Middleware") },
        navigationIcon = {
            DrawerButton(onOpenDrawer)
        },
        actions = {
            IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search middleware")
            }
            Box {
                IconButton(onClick = { filterMenuOpen = true }) {
                    Icon(Icons.Outlined.FilterList, contentDescription = "Filters")
                }
                if (filtersActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.blue),
                    )
                }
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false },
                    modifier = Modifier.padding(horizontal = 0.dp),
                ) {
                    Text(
                        text = "PROTOCOL",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(
                            start = TmSpacing.lg,
                            end = TmSpacing.lg,
                            top = TmSpacing.sm,
                            bottom = TmSpacing.xs,
                        ),
                    )
                    MiddlewareFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onFilterChange(option)
                                filterMenuOpen = false
                            },
                            trailingIcon = if (option == filter) {
                                { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = palette.blue) }
                            } else {
                                null
                            },
                            contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                        )
                    }
                    if (filtersActive) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Clear filter") },
                            leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                            contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                            onClick = {
                                onFilterChange(MiddlewareFilter.All)
                                filterMenuOpen = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onOpenTemplates) {
                Icon(Icons.Outlined.Style, contentDescription = "Middleware templates")
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh middlewares")
            }
        },
        scrollBehavior = scrollBehavior,
    )

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = queryState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = { Text("Search middleware") },
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
        if (results.isEmpty()) {
            Text(
                text = "No middleware matches",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                modifier = Modifier.padding(TmSpacing.lg),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(results, key = { it.name + it.configFile }) { middleware ->
                    ListItem(
                        headlineContent = { Text(middleware.name) },
                        supportingContent = {
                            Text(
                                text = dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
                                    .kindOf(middleware.yaml),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                            )
                        },
                        trailingContent = if (middleware.type == "tcp") {
                            { Text("TCP", style = MaterialTheme.typography.labelSmall) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable {
                            scope.launch { searchBarState.animateToCollapsed() }
                            onResultClick(middleware)
                        },
                    )
                }
            }
        }
    }
}

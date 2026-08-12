package dev.chr0nzz.traefikmanager.ui.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.PluginEntry
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.IconTile
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.MessageState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TooltipIconButton
import dev.chr0nzz.traefikmanager.ui.components.YamlPreview
import dev.chr0nzz.traefikmanager.ui.components.only
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(viewModel.queryState) {
        snapshotFlow { viewModel.queryState.text.toString() }.collect(viewModel::onQueryChange)
    }

    val select: (PluginEntry) -> Unit = { plugin ->
        selectedName = plugin.name
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, plugin.name) }
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
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = { Text("Plugins") },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search plugins")
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh plugins")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { insets ->
        PluginsSearchBar(
            searchBarState = searchBarState,
            queryState = viewModel.queryState,
            results = state.visible,
            usage = { name -> state.usageFor(name) },
            onResultClick = select,
        )

        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(insets),
            listPane = {
                AnimatedPane {
                    PluginsListPane(
                        state = state,
                        selectedName = selectedName,
                        contentPadding = insets,
                        onSelect = select,
                        onRefresh = viewModel::refresh,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    PluginDetailPane(
                        plugin = state.plugins.firstOrNull { it.name == selectedName },
                        users = selectedName?.let { state.usersOf(it) }.orEmpty(),
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
private fun PluginsListPane(
    state: PluginsUiState,
    selectedName: String?,
    contentPadding: PaddingValues,
    onSelect: (PluginEntry) -> Unit,
    onRefresh: () -> Unit,
) {
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
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
            state.loading -> LoadingState(label = "Loading plugins")

            state.loadError != null -> ErrorState(
                headline = "Could not load plugin data",
                body = state.loadError,
                onRetry = onRefresh,
            )

            state.plugins.isEmpty() && state.serverError != null -> ErrorState(
                headline = "No plugins declared",
                body = state.serverError,
                onRetry = onRefresh,
            )

            state.plugins.isEmpty() -> EmptyState(
                headline = "No plugins configured",
                body = "Plugins are declared under experimental.plugins in the Traefik static config.",
            )

            state.visible.isEmpty() -> EmptyState(headline = "No plugins match your search")

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
                items(state.visible, key = { it.name }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        usageCount = state.usageFor(plugin.name),
                        selected = plugin.name == selectedName,
                        onClick = { onSelect(plugin) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginEntry,
    usageCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current

    TmCard(
        modifier = modifier,
        accentColor = if (selected) palette.blue else null,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconTile(icon = Icons.Outlined.Extension)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plugin.displayModule,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = plugin.displayVersion,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.blue,
            )
        }

        CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
        Text(
            text = when (usageCount) {
                0 -> "not referenced"
                1 -> "used by 1 middleware"
                else -> "used by $usageCount middlewares"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (usageCount == 0) palette.yellow else palette.muted,
            modifier = Modifier.padding(top = TmSpacing.xs),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PluginDetailPane(
    plugin: PluginEntry?,
    users: List<dev.chr0nzz.traefikmanager.data.model.MiddlewareDef>,
    showBack: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    if (plugin == null) {
        MessageState(
            icon = Icons.Outlined.TouchApp,
            headline = "Select a plugin",
            body = "Pick one from the list to see its module and settings.",
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(top = TmSpacing.xxl),
        )
        return
    }

    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val settingsYaml = remember(plugin.settings) {
        plugin.settings?.let { element -> prettyJson.encodeToString(JsonElement.serializer(), element) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, top = TmSpacing.lg, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to plugins")
                    }
                }
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
            }

            TmCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TmSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = palette.blue,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Plugin",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                DetailRow("Name", plugin.name, mono = true)
                DetailRow("Module", plugin.displayModule, mono = true)
                DetailRow(
                    label = "Version",
                    value = plugin.displayVersion,
                    mono = true,
                    last = true,
                )
            }

            SectionLabel("Used by middlewares")
            TmCard {
                if (users.isEmpty()) {
                    Text(
                        text = "No middleware references this plugin",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                } else {
                    users.forEachIndexed { index, middleware ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = TmSpacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = palette.purple,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = middleware.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (index < users.lastIndex) CardDivider()
                    }
                }
            }

            if (settingsYaml != null && settingsYaml != "null") {
                SectionLabel("Settings")
                TmCard {
                    YamlPreview(source = settingsYaml)
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(bottom = TmSpacing.lg),
        ) {
            TooltipIconButton(
                label = "Copy module name",
                icon = Icons.Outlined.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(plugin.moduleName)) },
            )
            plugin.repoUrl?.let { url ->
                TooltipIconButton(
                    label = "Open repository",
                    icon = Icons.Outlined.OpenInNew,
                    onClick = { uriHandler.openUri(url) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginsSearchBar(
    searchBarState: androidx.compose.material3.SearchBarState,
    queryState: TextFieldState,
    results: List<PluginEntry>,
    usage: (String) -> Int,
    onResultClick: (PluginEntry) -> Unit,
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
                placeholder = { Text("Search plugins") },
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
        Column(modifier = Modifier.fillMaxWidth()) {
            if (results.isEmpty()) {
                Text(
                    text = "No plugins match your search",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(TmSpacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results, key = { it.name }) { plugin ->
                        ListItem(
                            headlineContent = { Text(plugin.name) },
                            supportingContent = {
                                Text(
                                    text = plugin.displayModule,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                )
                            },
                            leadingContent = { Icon(Icons.Outlined.Extension, contentDescription = null) },
                            trailingContent = {
                                Text(
                                    text = usage(plugin.name).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.clickable {
                                scope.launch { searchBarState.animateToCollapsed() }
                                onResultClick(plugin)
                            },
                        )
                    }
                }
            }
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

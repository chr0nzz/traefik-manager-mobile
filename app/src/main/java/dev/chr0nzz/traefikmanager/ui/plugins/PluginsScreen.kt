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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.TopBarTitle
import dev.chr0nzz.traefikmanager.ui.components.distinctItems
import dev.chr0nzz.traefikmanager.ui.components.tmPaneScaffoldDirective
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.asString
import androidx.compose.ui.platform.LocalResources

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = tmPaneScaffoldDirective(),
    )
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
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolve(resources))
        viewModel.consumeMessage()
    }

    state.installing?.let { draft ->
        PluginInstallSheet(
            draft = draft,
            busy = state.busy,
            error = state.formError?.asString(),
            onChange = viewModel::onInstallChange,
            onInstall = viewModel::install,
            onDismiss = viewModel::cancelInstall,
        )
    }

    state.editing?.let { draft ->
        PluginEditSheet(
            draft = draft,
            busy = state.busy,
            error = state.formError?.asString(),
            onChange = viewModel::onEditChange,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }

    state.pendingDelete?.let { plugin ->
        AlertDialog(
            onDismissRequest = { viewModel.askDelete(null) },
            title = { Text(stringResource(R.string.plugins_remove_title)) },
            text = { Text(stringResource(R.string.plugins_remove_body, plugin.name)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(plugin) }) { Text(stringResource(R.string.common_remove)) } },
            dismissButton = { TextButton(onClick = { viewModel.askDelete(null) }) { Text(stringResource(R.string.common_cancel)) } },
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
            if (state.canManage && !detailOnly) {
                FloatingActionButton(onClick = viewModel::startInstall) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.plugins_add))
                }
            }
        },
        topBar = {
            if (!detailOnly) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = { TopBarTitle(stringResource(R.string.nav_plugins)) },
                    navigationIcon = {
                        DrawerButton(onOpenDrawer)
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.plugins_search))
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.plugins_refresh))
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
                        onEdit = viewModel::startEdit,
                        onDelete = { viewModel.askDelete(it) },
                        onRestart = viewModel::restart,
                        onDismissRestart = viewModel::dismissRestart,
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    PluginDetailPane(
                        plugin = state.plugins.firstOrNull { it.name == selectedName },
                        latest = selectedName?.let { state.updates[it] },
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
    onEdit: (PluginEntry) -> Unit,
    onDelete: (PluginEntry) -> Unit,
    onRestart: () -> Unit,
    onDismissRestart: () -> Unit,
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
            state.loading -> LoadingState(label = stringResource(R.string.plugins_loading))

            state.loadError != null -> ErrorState(
                headline = stringResource(R.string.plugins_error_load),
                body = state.loadError?.asString(),
                onRetry = onRefresh,
            )

            state.plugins.isEmpty() && state.serverError != null -> ErrorState(
                headline = stringResource(R.string.plugins_empty_server),
                body = state.serverError,
                onRetry = onRefresh,
            )

            state.plugins.isEmpty() -> EmptyState(
                headline = stringResource(R.string.plugins_empty),
                body = stringResource(R.string.plugins_empty_body),
            )

            state.visible.isEmpty() -> EmptyState(headline = stringResource(R.string.plugins_no_match))

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
                if (state.restartPending) {
                    item {
                        PluginRestartNotice(
                            detail = state.restartDetail?.asString().orEmpty(),
                            busy = state.restarting,
                            onRestart = onRestart,
                            onDismiss = onDismissRestart,
                        )
                    }
                }
                distinctItems(state.visible, key = { it.name }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        usageCount = state.usageFor(plugin.name),
                        latest = state.updates[plugin.name],
                        selected = plugin.name == selectedName,
                        canManage = state.canManage,
                        onClick = { onSelect(plugin) },
                        onEdit = { onEdit(plugin) },
                        onDelete = { onDelete(plugin) },
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
    latest: String?,
    selected: Boolean,
    canManage: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = plugin.displayVersion,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.blue,
                )
                if (latest != null) {
                    Text(
                        text = "$latest available",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.yellow,
                    )
                }
            }
        }

        CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (usageCount == 0) {
                    stringResource(R.string.plugins_not_referenced)
                } else {
                    pluralStringResource(R.plurals.plugins_used_by, usageCount, usageCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (usageCount == 0) palette.yellow else palette.muted,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = TmSpacing.xs),
            )
            if (canManage) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.common_edit_named, plugin.name),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.common_remove_named, plugin.name),
                        tint = palette.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PluginDetailPane(
    plugin: PluginEntry?,
    latest: String?,
    users: List<dev.chr0nzz.traefikmanager.data.model.MiddlewareDef>,
    showBack: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    if (plugin == null) {
        MessageState(
            icon = Icons.Outlined.TouchApp,
            headline = stringResource(R.string.plugins_select),
            body = stringResource(R.string.plugins_select_body),
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
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, top = TmSpacing.lg, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.plugins_back))
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
                        text = stringResource(R.string.plugins_kind),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                DetailRow(stringResource(R.string.common_label_name), plugin.name, mono = true)
                DetailRow(stringResource(R.string.plugins_module), plugin.displayModule, mono = true)
                DetailRow(
                    label = stringResource(R.string.common_label_version),
                    value = plugin.displayVersion,
                    mono = true,
                    last = latest == null,
                )
                if (latest != null) {
                    DetailRow(
                        label = stringResource(R.string.plugins_update),
                        value = stringResource(R.string.plugins_update_available, latest),
                        mono = true,
                        last = true,
                    )
                }
            }

            SectionLabel(stringResource(R.string.plugins_used_by_title))
            TmCard {
                if (users.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugins_used_by_none),
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
                SectionLabel(stringResource(R.string.nav_settings))
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
                label = stringResource(R.string.plugins_copy_module),
                icon = Icons.Outlined.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(plugin.moduleName)) },
            )
            plugin.repoUrl?.let { url ->
                TooltipIconButton(
                    label = stringResource(R.string.plugins_open_repo),
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
                placeholder = { Text(stringResource(R.string.plugins_search)) },
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
        Column(modifier = Modifier.fillMaxWidth()) {
            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.plugins_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(TmSpacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    distinctItems(results, key = { it.name }) { plugin ->
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

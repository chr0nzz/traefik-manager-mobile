package dev.chr0nzz.traefikmanager.ui.backups

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.BackupEntry
import dev.chr0nzz.traefikmanager.data.model.BackupKind
import dev.chr0nzz.traefikmanager.data.model.GitCommit
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TypedConfirmDialog
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupsScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BackupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshState = rememberPullToRefreshState()

    var restoreTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var restoreCommitTarget by remember { mutableStateOf<GitCommit?>(null) }
    var pushOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    restoreTarget?.let { entry ->
        TypedConfirmDialog(
            title = "Restore ${entry.name}?",
            consequence = if (entry.kind == BackupKind.Static) {
                "This overwrites the live static config. The server backs up what is there now " +
                    "first, and Traefik has to restart before it takes effect."
            } else {
                "This overwrites the live config, replacing every route it holds. The server " +
                    "backs up what is there now first."
            },
            actionLabel = "Restore",
            onDismiss = { restoreTarget = null },
            onConfirm = {
                viewModel.restore(entry)
                restoreTarget = null
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this backup?") },
            text = { Text(entry.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entry)
                        deleteTarget = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    restoreCommitTarget?.let { commit ->
        TypedConfirmDialog(
            title = "Restore ${commit.shaShort}?",
            consequence = "This writes every config file from that commit over what is running now.",
            actionLabel = "Restore",
            onDismiss = { restoreCommitTarget = null },
            onConfirm = {
                viewModel.restoreCommit(commit)
                restoreCommitTarget = null
            },
        )
    }

    if (pushOpen) {
        PushDialog(
            onDismiss = { pushOpen = false },
            onPush = { message ->
                viewModel.push(message)
                pushOpen = false
            },
        )
    }

    state.diffFor?.let { commit ->
        DiffDialog(
            commit = commit,
            diff = state.diff,
            loading = state.diffLoading,
            onDismiss = viewModel::closeDiff,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Backups") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Open the server menu")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh backups")
                    }
                },
            )
        },
    ) { insets ->
        Column(modifier = Modifier.padding(insets)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
            ) {
                BackupsTab.entries.forEach { tab ->
                    if (tab == BackupsTab.Static && !state.staticConfigured) return@forEach
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        label = { Text(tab.label) },
                    )
                }
            }

            if (state.restartPending) {
                RestartNotice(
                    busy = state.busy,
                    onRestart = viewModel::restartTraefik,
                    onDismiss = viewModel::dismissRestartNotice,
                )
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
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
                when (state.tab) {
                    BackupsTab.Git -> GitPane(
                        state = state,
                        onPush = { pushOpen = true },
                        onDiff = viewModel::openDiff,
                        onRestore = { restoreCommitTarget = it },
                        onRetry = viewModel::refresh,
                    )
                    else -> LocalPane(
                        state = state,
                        onCreate = {
                            if (state.tab == BackupsTab.Static) {
                                viewModel.createStaticBackup()
                            } else {
                                viewModel.createBackup()
                            }
                        },
                        onRestore = { restoreTarget = it },
                        onDelete = { deleteTarget = it },
                        onRetry = viewModel::refresh,
                    )
                }
            }
        }
    }
}

private val BackupsTab.label: String
    get() = when (this) {
        BackupsTab.Dynamic -> "Dynamic"
        BackupsTab.Static -> "Static"
        BackupsTab.Git -> "Git"
    }

@Composable
private fun RestartNotice(busy: Boolean, onRestart: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalTmPalette.current
    TmCard(
        accentColor = palette.yellow,
        modifier = Modifier.padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
    ) {
        Text(
            text = "Traefik is still running the old static config",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "A restored static config only takes effect after a restart.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.padding(top = TmSpacing.sm),
        ) {
            Button(onClick = onRestart, enabled = !busy) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Restart Traefik", modifier = Modifier.padding(start = TmSpacing.xs))
            }
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    }
}

@Composable
private fun LocalPane(
    state: BackupsUiState,
    onCreate: () -> Unit,
    onRestore: (BackupEntry) -> Unit,
    onDelete: (BackupEntry) -> Unit,
    onRetry: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val static = state.tab == BackupsTab.Static
    val rows = if (static) state.static else state.dynamic

    if (state.loading) {
        LoadingState(label = "Loading backups")
        return
    }
    state.error?.let { message ->
        ErrorState(headline = "Could not read the backups", body = message, onRetry = onRetry)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TmSpacing.lg,
            end = TmSpacing.lg,
            top = TmSpacing.xs,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        item {
            TmCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (rows.isEmpty()) "No backups" else "${rows.size} kept",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = buildString {
                                append(formatSize(rows.sumOf { it.size }))
                                rows.firstOrNull()?.let { append(" · newest ${it.stamp}") }
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                    if (!static || state.onHost) {
                        Button(onClick = onCreate, enabled = !state.busy) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Back up", modifier = Modifier.padding(start = TmSpacing.xs))
                        }
                    }
                }
                if (static && !state.onHost) {
                    Text(
                        text = "This agent folds its static config into the ordinary backup, so " +
                            "use Back up on the Dynamic tab.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
                if (!state.kindKnown) {
                    Text(
                        text = "This agent is too old to say which backup is which, so everything " +
                            "is listed as dynamic.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.yellow,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                EmptyState(
                    headline = if (static) "No static backups" else "No backups yet",
                    body = if (static) {
                        "Backing up the static config keeps a copy of entrypoints and resolvers."
                    } else {
                        "A backup copies every dynamic config file the server holds."
                    },
                )
            }
            return@LazyColumn
        }

        item { SectionLabel(if (static) "Static config" else "Dynamic config") }

        items(rows.size, key = { rows[it].name }) { index ->
            val entry = rows[index]
            TmCard {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.stamp} · ${formatSize(entry.size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                    OutlinedButton(onClick = { onRestore(entry) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Restore", modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                    TextButton(onClick = { onDelete(entry) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Delete", modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun GitPane(
    state: BackupsUiState,
    onPush: () -> Unit,
    onDiff: (GitCommit) -> Unit,
    onRestore: (GitCommit) -> Unit,
    onRetry: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val status = state.gitStatus

    if (state.gitLoading) {
        LoadingState(label = "Loading Git backup")
        return
    }
    state.gitError?.let { message ->
        ErrorState(headline = "Could not read Git backup", body = message, onRetry = onRetry)
        return
    }
    if (status == null || !status.configured) {
        EmptyState(
            headline = "Git backup is not configured",
            body = "The repository, branch and token are set in the web UI or by environment. " +
                "Once a repo is configured, pushes and restores appear here.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TmSpacing.lg,
            end = TmSpacing.lg,
            top = TmSpacing.xs,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        item {
            TmCard(accentColor = if (status.enabled) palette.green else palette.yellow) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (status.enabled) "Git backup on" else "Configured, switched off",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = buildString {
                                if (status.branch.isNotEmpty()) append("branch ${status.branch} · ")
                                if (status.lastSha.isNotEmpty()) {
                                    append(status.lastSha)
                                    if (status.lastPush.isNotEmpty()) append(" · ${status.lastPush}")
                                } else {
                                    append("nothing pushed yet")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                    Button(onClick = onPush, enabled = !state.busy) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Push", modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                }
                if (!status.enabled) {
                    Text(
                        text = "Automatic pushes are off. Pushing here still works.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
            }
        }

        if (state.gitCommits.isEmpty()) {
            item {
                EmptyState(
                    headline = "No commits yet",
                    body = "Push once and the history shows up here.",
                )
            }
            return@LazyColumn
        }

        item { SectionLabel("History ${state.gitCommits.size}") }

        items(state.gitCommits.size, key = { state.gitCommits[it].sha }) { index ->
            val commit = state.gitCommits[index]
            TmCard {
                Text(
                    text = commit.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${commit.shaShort} · ${commit.timestamp}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                )
                CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                    OutlinedButton(onClick = { onDiff(commit) }) {
                        Icon(Icons.Outlined.Difference, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Changes", modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                    TextButton(onClick = { onRestore(commit) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Restore", modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun PushDialog(onDismiss: () -> Unit, onPush: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Push to Git") },
        text = {
            Column {
                Text(
                    text = "Commits every config file as it stands now. A blank message uses the " +
                        "server's own wording.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.md),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPush(message) }) { Text("Push") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DiffDialog(
    commit: GitCommit,
    diff: dev.chr0nzz.traefikmanager.data.model.GitDiff?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(commit.shaShort) },
        text = {
            when {
                loading -> Box(modifier = Modifier.fillMaxWidth()) { LoadingState() }
                diff == null || (diff.stat.isEmpty() && diff.files.isEmpty()) -> Text(
                    text = "Nothing to show for this commit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    if (diff.stat.isNotEmpty()) {
                        Text(
                            text = diff.stat.trim(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                    diff.files.forEach { file ->
                        Text(
                            text = "${statusWord(file.status)}  ${file.filename}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = when (file.status.firstOrNull()) {
                                'A' -> palette.green
                                'D' -> palette.red
                                else -> palette.yellow
                            },
                            modifier = Modifier.padding(top = TmSpacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun statusWord(status: String): String = when (status.firstOrNull()) {
    'A' -> "added"
    'D' -> "deleted"
    'M' -> "changed"
    'R' -> "renamed"
    else -> status.lowercase()
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    bytes == 1L -> "1 byte"
    else -> "$bytes bytes"
}

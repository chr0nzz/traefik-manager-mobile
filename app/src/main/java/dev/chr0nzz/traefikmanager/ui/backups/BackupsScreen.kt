package dev.chr0nzz.traefikmanager.ui.backups

import androidx.annotation.StringRes
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
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TopBarTitle
import dev.chr0nzz.traefikmanager.ui.components.TypedConfirmDialog
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.asString
import androidx.compose.ui.platform.LocalResources

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
    val resources = LocalResources.current
    val refreshState = rememberPullToRefreshState()

    var restoreTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupEntry?>(null) }
    var restoreCommitTarget by remember { mutableStateOf<GitCommit?>(null) }
    var pushOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolve(resources))
        viewModel.consumeMessage()
    }

    restoreTarget?.let { entry ->
        TypedConfirmDialog(
            title = stringResource(R.string.common_restore_named_question, entry.name),
            consequence = stringResource(
                when (entry.kind) {
                    BackupKind.Static -> R.string.backups_restore_static
                    BackupKind.Certs -> R.string.backups_restore_certs
                    BackupKind.Routes -> R.string.backups_restore_routes
                },
            ),
            actionLabel = stringResource(R.string.common_restore),
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
            title = { Text(stringResource(R.string.backups_delete_title)) },
            text = { Text(entry.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entry)
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    restoreCommitTarget?.let { commit ->
        TypedConfirmDialog(
            title = stringResource(R.string.common_restore_named_question, commit.shaShort),
            consequence = stringResource(R.string.backups_restore_commit),
            actionLabel = stringResource(R.string.common_restore),
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
                title = { TopBarTitle(stringResource(R.string.nav_backups)) },
                navigationIcon = {
                    DrawerButton(onOpenDrawer)
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.backups_refresh))
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
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }

            state.restartPending?.let { kind ->
                RestartNotice(
                    certs = kind == BackupKind.Certs,
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

private val BackupsTab.label: Int
    @StringRes get() = when (this) {
        BackupsTab.Dynamic -> R.string.backups_tab_dynamic
        BackupsTab.Static -> R.string.backups_tab_static
        BackupsTab.Certs -> R.string.nav_certificates
        BackupsTab.Git -> R.string.backups_tab_git
    }

@Composable
private fun RestartNotice(certs: Boolean, busy: Boolean, onRestart: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalTmPalette.current
    TmCard(
        accentColor = palette.yellow,
        modifier = Modifier.padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
    ) {
        Text(
            text = stringResource(
                if (certs) R.string.backups_restart_title_certs else R.string.backups_restart_title_static,
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                if (certs) R.string.backups_restart_body_certs else R.string.backups_restart_body_static,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.padding(top = TmSpacing.sm),
        ) {
            Button(onClick = onRestart, enabled = !busy) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.common_restart_traefik), modifier = Modifier.padding(start = TmSpacing.xs))
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_later)) }
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
    val certs = state.tab == BackupsTab.Certs
    val rows = when {
        static -> state.static
        certs -> state.certs
        else -> state.dynamic
    }

    if (state.loading) {
        LoadingState(label = stringResource(R.string.backups_loading))
        return
    }
    state.error?.let { message ->
        ErrorState(headline = stringResource(R.string.backups_error_load), body = message.asString(), onRetry = onRetry)
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
                            text = if (rows.isEmpty()) {
                                stringResource(R.string.backups_none)
                            } else {
                                pluralStringResource(R.plurals.backups_kept, rows.size, rows.size)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listOfNotNull(
                                formatSize(rows.sumOf { it.size }),
                                rows.firstOrNull()?.let { stringResource(R.string.backups_newest, it.stamp) },
                            ).joinToString(UiText.SEPARATOR),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                    if (!certs && (!static || state.onHost)) {
                        Button(onClick = onCreate, enabled = !state.busy) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.backups_create), modifier = Modifier.padding(start = TmSpacing.xs))
                        }
                    }
                }
                if (certs) {
                    Text(
                        text = stringResource(R.string.backups_certs_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
                if (static && !state.onHost) {
                    Text(
                        text = stringResource(R.string.backups_static_agent_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
                if (!state.kindKnown) {
                    Text(
                        text = stringResource(R.string.backups_old_agent_help),
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
                    headline = stringResource(
                        when {
                            static -> R.string.backups_empty_static
                            certs -> R.string.backups_empty_certs
                            else -> R.string.backups_empty
                        },
                    ),
                    body = stringResource(
                        when {
                            static -> R.string.backups_empty_static_body
                            certs -> R.string.backups_empty_certs_body
                            else -> R.string.backups_empty_body
                        },
                    ),
                )
            }
            return@LazyColumn
        }

        item {
            SectionLabel(
                stringResource(
                    when {
                        static -> R.string.backups_section_static
                        certs -> R.string.backups_section_certs
                        else -> R.string.backups_section_dynamic
                    },
                ),
            )
        }

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
                    text = listOf(entry.stamp, formatSize(entry.size)).joinToString(UiText.SEPARATOR),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                    OutlinedButton(onClick = { onRestore(entry) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.common_restore), modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                    TextButton(onClick = { onDelete(entry) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.common_delete), modifier = Modifier.padding(start = TmSpacing.xs))
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
        LoadingState(label = stringResource(R.string.backups_git_loading))
        return
    }
    state.gitError?.let { message ->
        ErrorState(headline = stringResource(R.string.backups_git_error), body = message.asString(), onRetry = onRetry)
        return
    }
    if (status == null || !status.configured) {
        EmptyState(
            headline = stringResource(R.string.backups_git_unconfigured),
            body = stringResource(R.string.backups_git_unconfigured_body),
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
                            text = stringResource(if (status.enabled) R.string.backups_git_on else R.string.backups_git_off),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listOfNotNull(
                                status.branch.takeIf { it.isNotEmpty() }
                                    ?.let { stringResource(R.string.backups_git_branch, it) },
                                status.lastSha.ifEmpty { stringResource(R.string.backups_git_nothing_pushed) },
                                status.lastPush.takeIf { it.isNotEmpty() && status.lastSha.isNotEmpty() },
                            ).joinToString(UiText.SEPARATOR),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                            color = palette.muted,
                        )
                    }
                    Button(onClick = onPush, enabled = !state.busy) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.backups_git_push), modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                }
                if (!status.enabled) {
                    Text(
                        text = stringResource(R.string.backups_git_off_help),
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
                    headline = stringResource(R.string.backups_git_empty),
                    body = stringResource(R.string.backups_git_empty_body),
                )
            }
            return@LazyColumn
        }

        item { SectionLabel(stringResource(R.string.backups_git_history, state.gitCommits.size)) }

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
                        Text(stringResource(R.string.backups_git_changes), modifier = Modifier.padding(start = TmSpacing.xs))
                    }
                    TextButton(onClick = { onRestore(commit) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.common_restore), modifier = Modifier.padding(start = TmSpacing.xs))
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
        title = { Text(stringResource(R.string.backups_git_push_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backups_git_push_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.backups_git_push_message)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.md),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPush(message) }) { Text(stringResource(R.string.backups_git_push)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
                    text = stringResource(R.string.backups_git_diff_empty),
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun statusWord(status: String): String = when (status.firstOrNull()) {
    'A' -> stringResource(R.string.backups_diff_added)
    'D' -> stringResource(R.string.backups_diff_deleted)
    'M' -> stringResource(R.string.backups_diff_changed)
    'R' -> stringResource(R.string.backups_diff_renamed)
    else -> status.lowercase()
}

@Composable
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> pluralStringResource(R.plurals.backups_size_bytes, bytes.toInt(), bytes.toInt())
}

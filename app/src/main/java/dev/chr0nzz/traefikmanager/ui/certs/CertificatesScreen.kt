package dev.chr0nzz.traefikmanager.ui.certs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.CertHealth
import dev.chr0nzz.traefikmanager.data.model.CertRow
import dev.chr0nzz.traefikmanager.data.model.CertVerdict
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.TypedConfirmDialog
import dev.chr0nzz.traefikmanager.ui.components.VerdictLine
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.IconTile
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import dev.chr0nzz.traefikmanager.ui.components.ValueRow
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

fun CertHealth.asTmStatus(): TmStatus = when (this) {
    CertHealth.Healthy -> TmStatus.Ok
    CertHealth.Expiring -> TmStatus.Warn
    CertHealth.Critical -> TmStatus.Error
    CertHealth.Unknown -> TmStatus.Unknown
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CertificatesScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CertificatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchBarState = rememberSearchBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val refreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val palette = LocalTmPalette.current

    var pendingRemoval by remember { mutableStateOf<List<CertRow>?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.queryState) {
        snapshotFlow { viewModel.queryState.text.toString() }.collect(viewModel::onQueryChange)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    pendingRemoval?.let { rows ->
        val inUse = rows.count { state.verdict(it)?.unused != true }
        TypedConfirmDialog(
            title = if (rows.size == 1) "Remove ${rows.first().main}?" else "Remove ${rows.size} certificates?",
            consequence = buildString {
                append("Traefik is restarted afterwards, because it only reads acme.json at startup.")
                if (inUse == 1) append(" One still serves a route, so Traefik requests a new certificate for it.")
                if (inUse > 1) append(" $inUse still serve routes, so Traefik requests new certificates for them.")
                append(" A copy of acme.json is saved to Backups first.")
            },
            actionLabel = "Remove",
            word = "DELETE",
            onDismiss = { pendingRemoval = null },
            onConfirm = {
                viewModel.remove(rows)
                pendingRemoval = null
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
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Certificates") },
                navigationIcon = {
                    DrawerButton(onOpenDrawer)
                },
                actions = {
                    IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search certificates")
                    }
                    Box {
                        IconButton(onClick = { filterMenuOpen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Filters")
                        }
                        if (state.filter != CertFilter.All) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(palette.blue),
                            )
                        }
                        CertFilterMenu(
                            expanded = filterMenuOpen,
                            filter = state.filter,
                            unusedCount = if (state.removing) 0 else state.unusedRemovable.size,
                            onDismiss = { filterMenuOpen = false },
                            onFilterChange = viewModel::onFilterChange,
                            onRemoveUnused = { pendingRemoval = state.unusedRemovable },
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh certificates")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        CertificatesSearchBar(
            searchBarState = searchBarState,
            queryState = viewModel.queryState,
            results = state.visible,
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
                state.loading -> LoadingState(label = "Loading certificates")

                state.loadError != null -> ErrorState(
                    headline = "Could not load certificate data",
                    body = state.loadError,
                    onRetry = viewModel::refresh,
                )

                state.certs.isEmpty() && state.serverError != null -> ErrorState(
                    headline = "No certificates",
                    body = state.serverError,
                    onRetry = viewModel::refresh,
                )

                state.certs.isEmpty() -> EmptyState(
                    headline = "No certificates found",
                    body = "acme.json may be empty - certs are issued on first request.",
                )

                state.visible.isEmpty() && state.filter == CertFilter.All -> EmptyState(
                    headline = "No certificates match your search",
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
                    item(key = "verdict") {
                        VerdictLine(verdict = state.statusVerdict, info = state.statusNote)
                    }
                    if (state.visible.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                headline = "No certificates match",
                                body = "Try a different search or filter.",
                            )
                        }
                    }
                    items(state.visible, key = { it.key }) { cert ->
                        CertificateCard(
                            cert = cert,
                            verdict = state.verdict(cert),
                            removable = state.removable(cert) && !state.removing,
                            onRemove = { pendingRemoval = listOf(cert) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CertFilterMenu(
    expanded: Boolean,
    filter: CertFilter,
    unusedCount: Int,
    onDismiss: () -> Unit,
    onFilterChange: (CertFilter) -> Unit,
    onRemoveUnused: () -> Unit,
) {
    val palette = LocalTmPalette.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 240.dp),
    ) {
        Text(
            text = "SHOW",
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(start = TmSpacing.lg, end = TmSpacing.lg, top = TmSpacing.sm, bottom = TmSpacing.xs),
        )
        CertFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onFilterChange(option)
                    onDismiss()
                },
                trailingIcon = if (option == filter) {
                    { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = palette.blue) }
                } else {
                    null
                },
                contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
            )
        }
        if (unusedCount > 0) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove $unusedCount unused") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = palette.red) },
                contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                onClick = {
                    onRemoveUnused()
                    onDismiss()
                },
            )
        }
        if (filter != CertFilter.All) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Clear filters") },
                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                onClick = {
                    onFilterChange(CertFilter.All)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun CertificateCard(
    cert: CertRow,
    verdict: CertVerdict?,
    removable: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    val status = cert.health.asTmStatus()
    val statusColor = when (cert.health) {
        CertHealth.Healthy -> palette.green
        CertHealth.Expiring -> palette.yellow
        CertHealth.Critical -> palette.red
        CertHealth.Unknown -> palette.muted
    }

    TmCard(
        modifier = modifier.animateContentSize(),
        accent = if (cert.health == CertHealth.Critical) TmStatus.Error else null,
        accentColor = if (cert.health != CertHealth.Critical) statusColor else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconTile(icon = Icons.Outlined.VerifiedUser, status = status)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cert.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                    Text(
                        text = cert.resolverLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                        color = palette.muted,
                    )
                    if (verdict?.unused == true) {
                        Text(
                            text = "unused",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.yellow,
                        )
                    }
                    if (verdict?.orphaned == true) {
                        Text(
                            text = "no resolver",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.red,
                        )
                    }
                }
            }
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove certificate",
                        tint = palette.red,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            IconButton(
                onClick = {
                    val all = listOf(cert.main).plus(cert.extraDomains).filter { it.isNotEmpty() }
                    clipboard.setText(AnnotatedString(all.joinToString("\n")))
                },
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy domains",
                    tint = palette.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        val shown = if (expanded) cert.extraDomains else cert.extraDomains.take(2)
        shown.forEach { domain ->
            ValueRow(icon = Icons.Outlined.Language, value = domain, color = palette.blue)
        }
        if (cert.extraDomains.size > 2) {
            Text(
                text = if (expanded) "Show less" else "+${cert.extraDomains.size - 2} more",
                style = MaterialTheme.typography.labelSmall,
                color = palette.blue,
                modifier = Modifier
                    .padding(top = TmSpacing.xs)
                    .clickable { expanded = !expanded },
            )
        }

        CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.xs),
        ) {
            Text(
                text = buildString {
                    append(cert.expiresOn?.let { "expires $it" } ?: "expiry unknown")
                    if (cert.domainCount > 1) append(" · ${cert.domainCount} domains")
                    cert.origin?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            cert.daysLeftLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CertificatesSearchBar(
    searchBarState: androidx.compose.material3.SearchBarState,
    queryState: TextFieldState,
    results: List<CertRow>,
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
                placeholder = { Text("Search certificates") },
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
            Text(
                text = if (results.isEmpty()) {
                    "No certificates match your search"
                } else {
                    "${results.size} matching"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                modifier = Modifier.padding(TmSpacing.lg),
            )
        }
    }
}

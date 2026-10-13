package dev.chr0nzz.traefikmanager.ui.routemap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.ModalSideSheet
import dev.chr0nzz.traefikmanager.ui.routes.RouteFormScreen
import dev.chr0nzz.traefikmanager.ui.components.EmptyState
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.TopBarTitle
import dev.chr0nzz.traefikmanager.ui.components.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteMapScreen(
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RouteMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    var filterMenuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { TopBarTitle(stringResource(R.string.nav_route_map)) },
                navigationIcon = {
                    DrawerButton(onOpenDrawer)
                },
                actions = {
                    Box {
                        IconButton(onClick = { filterMenuOpen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.common_filters))
                        }
                        if (state.filtered) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 10.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(palette.blue),
                            )
                        }
                        RouteMapFilterMenu(
                            expanded = filterMenuOpen,
                            state = state,
                            onDismiss = { filterMenuOpen = false },
                            onProtocol = viewModel::onProtocol,
                            onProvider = viewModel::onProvider,
                            onEntryPoint = viewModel::onEntryPoint,
                            onClear = viewModel::clearFilters,
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.routemap_refresh))
                    }
                },
            )
        },
    ) { insets ->
        Column(modifier = Modifier.padding(insets)) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingState(label = stringResource(R.string.routemap_loading))
                    state.error != null -> ErrorState(
                        headline = stringResource(R.string.routemap_error),
                        body = state.error?.asString(),
                        onRetry = viewModel::refresh,
                    )
                    state.graph.nodes.isEmpty() -> EmptyState(
                        headline = if (state.filtered) stringResource(R.string.routemap_no_match) else stringResource(R.string.routemap_empty),
                        body = if (state.filtered) {
                            stringResource(R.string.routemap_no_match_body)
                        } else {
                            stringResource(R.string.routemap_empty_body)
                        },
                    )
                    else -> RouteMapCanvas(
                        graph = state.graph,
                        focusIds = state.focusIds,
                        onTap = viewModel::focus,
                    )
                }

                editing?.let { id ->
                    RouteMapEditSheet(routeId = id, onClose = { editing = null })
                }

                state.focus?.let { node ->
                    NodeSheet(
                        node = node,
                        graph = state.graph,
                        onDismiss = { viewModel.focus(null) },
                        onEdit = { id ->
                            viewModel.focus(null)
                            editing = id
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteMapEditSheet(routeId: String, onClose: () -> Unit) {
    ModalSideSheet(visible = true, onDismiss = onClose) {
        RouteFormScreen(routeId = routeId, onClose = onClose, onSaved = onClose)
    }
}

@Composable
private fun RouteMapFilterMenu(
    expanded: Boolean,
    state: RouteMapUiState,
    onDismiss: () -> Unit,
    onProtocol: (String?) -> Unit,
    onProvider: (String?) -> Unit,
    onEntryPoint: (String?) -> Unit,
    onClear: () -> Unit,
) {
    val palette = LocalTmPalette.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.common_label_protocol).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = palette.muted,
            modifier = Modifier.padding(start = TmSpacing.md, top = TmSpacing.xs),
        )
        state.protocols.forEach { value ->
            DropdownMenuItem(
                text = { Text(value.uppercase()) },
                onClick = { onProtocol(value.takeIf { state.protocol != value }) },
                trailingIcon = {
                    if (state.protocol == value) Icon(Icons.Outlined.Check, contentDescription = null)
                },
            )
        }
        if (state.providers.size > 1) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.common_label_provider).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
                modifier = Modifier.padding(start = TmSpacing.md, top = TmSpacing.xs),
            )
            state.providers.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = { onProvider(value.takeIf { state.provider != value }) },
                    trailingIcon = {
                        if (state.provider == value) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                )
            }
        }
        if (state.entryPoints.size > 1) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.common_label_entry_point).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
                modifier = Modifier.padding(start = TmSpacing.md, top = TmSpacing.xs),
            )
            state.entryPoints.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = { onEntryPoint(value.takeIf { state.entryPoint != value }) },
                    trailingIcon = {
                        if (state.entryPoint == value) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                )
            }
        }
        if (state.filtered) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_clear_filters), color = palette.blue) },
                onClick = {
                    onClear()
                    onDismiss()
                },
            )
        }
    }
}
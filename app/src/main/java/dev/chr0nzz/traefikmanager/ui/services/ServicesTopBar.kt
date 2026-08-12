package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.ServiceProtocol
import dev.chr0nzz.traefikmanager.data.model.ServiceRow
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesTopBar(
    onOpenDrawer: () -> Unit,
    searchBarState: SearchBarState,
    queryState: TextFieldState,
    results: List<ServiceRow>,
    state: ServicesUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onStatusChange: (ServiceStatusFilter) -> Unit,
    onProtocolChange: (ServiceProtocol?) -> Unit,
    onClearFilters: () -> Unit,
    onResultClick: (ServiceRow) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalTmPalette.current
    var filterMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        title = { Text("Services") },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open navigation menu")
            }
        },
        actions = {
            IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search services")
            }
            Box {
                IconButton(onClick = { filterMenuOpen = true }) {
                    Icon(Icons.Outlined.FilterList, contentDescription = "Filters")
                }
                if (state.filtersActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.blue),
                    )
                }
                ServiceFilterMenu(
                    expanded = filterMenuOpen,
                    state = state,
                    onDismiss = { filterMenuOpen = false },
                    onStatusChange = onStatusChange,
                    onProtocolChange = onProtocolChange,
                    onClearFilters = onClearFilters,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh services")
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
                placeholder = { Text("Search services") },
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
                    text = "No services match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(TmSpacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results, key = { it.key }) { service ->
                        ListItem(
                            headlineContent = { Text(service.shortName) },
                            supportingContent = {
                                Text(
                                    text = service.servers.firstOrNull()?.target
                                        ?: service.composite.firstOrNull()
                                        ?: service.provider,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                )
                            },
                            leadingContent = { StatusDot(service.health.asTmStatus()) },
                            trailingContent = if (service.proto != ServiceProtocol.Http) {
                                { Text(service.proto.label, style = MaterialTheme.typography.labelSmall) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable {
                                scope.launch { searchBarState.animateToCollapsed() }
                                onResultClick(service)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceFilterMenu(
    expanded: Boolean,
    state: ServicesUiState,
    onDismiss: () -> Unit,
    onStatusChange: (ServiceStatusFilter) -> Unit,
    onProtocolChange: (ServiceProtocol?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val palette = LocalTmPalette.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 240.dp),
    ) {
        MenuSectionLabel("STATUS")
        ServiceStatusFilter.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onStatusChange(option)
                    onDismiss()
                },
                trailingIcon = if (option == state.status) {
                    { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = palette.blue) }
                } else {
                    null
                },
                contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
            )
        }
        val protocols = state.protocols
        if (protocols.size > 1) {
            HorizontalDivider()
            MenuSectionLabel("PROTOCOL")
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    onProtocolChange(null)
                    onDismiss()
                },
                trailingIcon = if (state.protocol == null) {
                    { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = palette.blue) }
                } else {
                    null
                },
                contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
            )
            protocols.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onProtocolChange(option)
                        onDismiss()
                    },
                    trailingIcon = if (option == state.protocol) {
                        { Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = palette.blue) }
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                )
            }
        }
        if (state.filtersActive) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Clear filters") },
                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                contentPadding = PaddingValues(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                onClick = {
                    onClearFilters()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    val palette = LocalTmPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = palette.muted,
        modifier = Modifier.padding(
            start = TmSpacing.lg,
            end = TmSpacing.lg,
            top = TmSpacing.sm,
            bottom = TmSpacing.xs,
        ),
    )
}

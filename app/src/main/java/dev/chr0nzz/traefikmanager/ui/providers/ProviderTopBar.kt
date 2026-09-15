package dev.chr0nzz.traefikmanager.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import dev.chr0nzz.traefikmanager.data.model.ProviderPage
import dev.chr0nzz.traefikmanager.data.model.ProviderProtocol
import dev.chr0nzz.traefikmanager.data.model.ProviderRoute
import dev.chr0nzz.traefikmanager.ui.components.DrawerButton
import dev.chr0nzz.traefikmanager.ui.components.StatusDot
import dev.chr0nzz.traefikmanager.ui.routes.FilterMenu
import dev.chr0nzz.traefikmanager.ui.routes.ProtocolFilter
import dev.chr0nzz.traefikmanager.ui.routes.StatusFilter
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderTopBar(
    page: ProviderPage,
    onOpenDrawer: () -> Unit,
    searchBarState: SearchBarState,
    queryState: TextFieldState,
    results: List<ProviderRoute>,
    protocol: ProtocolFilter,
    status: StatusFilter,
    scrollBehavior: TopAppBarScrollBehavior,
    onProtocolChange: (ProtocolFilter) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
    onResultClick: (ProviderRoute) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalTmPalette.current
    var filterMenuOpen by remember { mutableStateOf(false) }
    val filtersActive = protocol != ProtocolFilter.All || status != StatusFilter.All

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        title = { Text(page.label) },
        navigationIcon = {
            DrawerButton(onOpenDrawer)
        },
        actions = {
            IconButton(onClick = { scope.launch { searchBarState.animateToExpanded() } }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search ${page.label} routes")
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
                FilterMenu(
                    expanded = filterMenuOpen,
                    protocol = protocol,
                    status = status,
                    filtersActive = filtersActive,
                    onDismiss = { filterMenuOpen = false },
                    onProtocolChange = onProtocolChange,
                    onStatusChange = onStatusChange,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh ${page.label} routes")
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
                placeholder = { Text("Search ${page.label} routes") },
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
                    text = "No routes match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(TmSpacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results, key = { "${it.protocol}:${it.name}" }) { route ->
                        ListItem(
                            headlineContent = { Text(route.shortName) },
                            supportingContent = {
                                Text(
                                    text = route.plainHost ?: route.target ?: route.rule,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                )
                            },
                            leadingContent = { StatusDot(route.tmStatus()) },
                            trailingContent = if (route.protocol != ProviderProtocol.Http) {
                                { Text(route.protocol.label, style = MaterialTheme.typography.labelSmall) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable {
                                scope.launch { searchBarState.animateToCollapsed() }
                                onResultClick(route)
                            },
                        )
                    }
                }
            }
        }
    }
}

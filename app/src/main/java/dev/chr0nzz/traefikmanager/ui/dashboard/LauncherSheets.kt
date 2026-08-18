package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.LauncherApp
import dev.chr0nzz.traefikmanager.data.model.LauncherBuilder
import dev.chr0nzz.traefikmanager.data.model.RouteIcons
import dev.chr0nzz.traefikmanager.data.model.RouteOverride
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

/** How this app appears on the dashboard. It never touches the route itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSettingsSheet(
    app: LauncherApp,
    config: DashboardConfig,
    baseUrl: String,
    onDismiss: () -> Unit,
    onSave: (RouteOverride) -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = config.routeOverrides[app.id] ?: RouteOverride()
    var displayName by remember { mutableStateOf(current.displayName) }
    var iconType by remember { mutableStateOf(current.iconType.ifEmpty { "auto" }) }
    var iconSlug by remember { mutableStateOf(current.iconSlug) }
    var iconUrl by remember { mutableStateOf(current.iconUrl) }
    var group by remember { mutableStateOf(current.group) }
    var url by remember { mutableStateOf(current.url) }
    var linkDisabled by remember { mutableStateOf(current.linkDisabled) }
    var hidden by remember { mutableStateOf(current.hidden) }
    var groupOpen by remember { mutableStateOf(false) }

    val draft = RouteOverride(
        iconType = if (iconType == "auto") "" else iconType,
        iconUrl = if (iconType == "url") iconUrl else "",
        iconSlug = if (iconType == "slug") iconSlug else "",
        displayName = displayName,
        hidden = hidden,
        url = url,
        linkDisabled = linkDisabled,
        group = group,
    )
    val groups = listOf("" to "Auto-detect") +
        config.customGroups.map { it.name to it.name } +
        LauncherBuilder.builtInGroups.map { it to it }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, bottom = TmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Text(
                text = "Card settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
                Text(
                    text = "These settings change how this app appears on the dashboard. They do " +
                        "not touch the route itself.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    placeholder = { Text("Leave blank to use route name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel("Icon")
                Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                    listOf("auto" to "Auto", "slug" to "selfh.st slug", "url" to "Custom URL")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = iconType == key,
                                onClick = { iconType = key },
                                label = { Text(label) },
                            )
                        }
                }
                when (iconType) {
                    "slug" -> OutlinedTextField(
                        value = iconSlug,
                        onValueChange = { iconSlug = it },
                        singleLine = true,
                        placeholder = { Text("jellyfin") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "url" -> OutlinedTextField(
                        value = iconUrl,
                        onValueChange = { iconUrl = it },
                        singleLine = true,
                        placeholder = { Text("https://…/icon.png") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // The preview resolves exactly as the card will, so what you see is what lands.
                RouteIcons.urlFor(app.route, config.copy(routeOverrides = config.routeOverrides + (app.id to draft)), baseUrl)
                    ?.let { preview ->
                        AsyncImage(
                            model = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                SectionLabel("Group")
                ExposedDropdownMenuBox(
                    expanded = groupOpen,
                    onExpandedChange = { groupOpen = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = groups.firstOrNull { it.first == group }?.second ?: "Auto-detect",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupOpen) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = groupOpen, onDismissRequest = { groupOpen = false }) {
                        groups.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    group = key
                                    groupOpen = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link URL") },
                    placeholder = { Text("Leave blank to use the route's URL") },
                    supportingText = {
                        Text("Useful when the route has a wildcard host, or the app lives on a different port.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = linkDisabled, onCheckedChange = { linkDisabled = it })
                    Text("Do not make this card clickable", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                    Text("Hide from the dashboard", style = MaterialTheme.typography.bodyMedium)
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.padding(top = TmSpacing.sm),
            ) {
                Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** Custom groups and whatever has been hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardSettingsSheet(
    config: DashboardConfig,
    hidden: List<LauncherApp>,
    onDismiss: () -> Unit,
    onAddGroup: (String) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onShow: (LauncherApp) -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, bottom = TmSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            Text(
                text = "Dashboard settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
                SectionLabel("Custom groups ${config.customGroups.size}")
                Text(
                    text = "Assign a route to a group in its card settings. A group disappears " +
                        "when nothing uses it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                config.customGroups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemoveGroup(group.name) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${group.name}")
                        }
                    }
                    CardDivider()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("New group name…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onAddGroup(name)
                            name = ""
                        },
                        enabled = name.isNotBlank(),
                    ) { Text("Add") }
                }

                SectionLabel("Hidden apps ${hidden.size}", modifier = Modifier.padding(top = TmSpacing.sm))
                Text(
                    text = "Hidden apps stay off the dashboard only. They keep running and still " +
                        "appear on the Routes tab.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                hidden.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = app.route.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onShow(app) }) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("show", modifier = Modifier.padding(start = TmSpacing.xs))
                        }
                    }
                    CardDivider()
                }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = TmSpacing.sm)) {
                Text("Close")
            }
        }
    }
}

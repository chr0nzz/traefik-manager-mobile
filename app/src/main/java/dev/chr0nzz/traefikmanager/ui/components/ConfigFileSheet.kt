package dev.chr0nzz.traefikmanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.ConfigFile
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigFileSheet(
    files: List<ConfigFile>,
    selected: String,
    canCreate: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    val matches = remember(files, query) {
        val needle = query.trim().lowercase()
        val unique = files.filter { it.label.isNotEmpty() }.distinctBy { it.path.ifEmpty { it.label } }
        if (needle.isEmpty()) unique else unique.filter { it.label.lowercase().contains(needle) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(modifier = Modifier.padding(bottom = TmSpacing.lg)) {
            Text(
                text = stringResource(R.string.files_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = TmSpacing.lg, end = TmSpacing.lg, bottom = TmSpacing.sm),
            )

            if (files.size > 6) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.files_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TmSpacing.lg),
                )
            }

            if (canCreate) {
                if (creating) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.sm),
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(stringResource(R.string.files_new_name)) },
                            placeholder = { Text("media.yml") },
                            supportingText = { Text(stringResource(R.string.files_new_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val name = newName.trim().let {
                                    if (it.endsWith(".yml") || it.endsWith(".yaml")) it else "$it.yml"
                                }
                                onSelect(name)
                                onDismiss()
                            },
                            enabled = newName.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.files_create))
                        }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.files_new)) },
                        supportingContent = { Text(stringResource(R.string.files_new_body)) },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
                HorizontalDivider()
            }

            if (matches.isEmpty()) {
                Text(
                    text = stringResource(if (files.isEmpty()) R.string.files_empty else R.string.files_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(TmSpacing.lg),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    distinctItems(matches, key = { it.path.ifEmpty { it.label } }) { file ->
                        ListItem(
                            headlineContent = {
                                Text(file.label, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily))
                            },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            trailingContent = if (file.label == selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.common_selected)) }
                            } else {
                                null
                            },
                            modifier = Modifier.clickable {
                                onSelect(file.label)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

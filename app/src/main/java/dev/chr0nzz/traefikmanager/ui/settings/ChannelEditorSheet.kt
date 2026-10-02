package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.chr0nzz.traefikmanager.data.model.ChannelKinds
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.asString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelEditorSheet(
    draft: ChannelDraft,
    saving: Boolean,
    test: TestState,
    error: String?,
    onChange: (ChannelDraft) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val kind = ChannelKinds.of(draft.kind)
    var kindOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }

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
                text = stringResource(if (draft.adding) R.string.channels_add_title else R.string.channels_edit_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.common_label_name)) },
                placeholder = { Text(stringResource(R.string.channels_name_placeholder)) },
                supportingText = { Text(stringResource(R.string.channels_name_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = kindOpen,
                onExpandedChange = { kindOpen = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = kind?.pickerLabel ?: draft.kind,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.common_label_type)) },
                    supportingText = { Text(stringResource(R.string.channels_type_help)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindOpen) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = kindOpen, onDismissRequest = { kindOpen = false }) {
                    ChannelKinds.all.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.pickerLabel) },
                            onClick = {
                                onChange(draft.copy(kind = option.key))
                                kindOpen = false
                            },
                        )
                    }
                }
            }

            kind?.fields?.forEach { field ->
                OutlinedTextField(
                    value = draft.valueOf(field.key),
                    onValueChange = { onChange(draft.withValue(field.key, it)) },
                    label = { Text(field.label) },
                    placeholder = { if (field.placeholder.isNotEmpty()) Text(field.placeholder) },
                    supportingText = { Text(field.description) },
                    singleLine = true,
                    visualTransformation = if (field.secret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            field.secret -> KeyboardType.Password
                            field.key == "url" -> KeyboardType.Uri
                            else -> KeyboardType.Text
                        },
                        showKeyboardOnFocus = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (kind?.basicAuth == true) {
                SectionLabel(stringResource(R.string.channels_auth_section), modifier = Modifier.padding(top = TmSpacing.xs))
                Text(
                    text = stringResource(R.string.channels_auth_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
                OutlinedTextField(
                    value = draft.username,
                    onValueChange = { onChange(draft.copy(username = it)) },
                    label = { Text(stringResource(R.string.middlewares_generate_username)) },
                    placeholder = { Text(stringResource(R.string.channels_auth_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.password,
                    onValueChange = { onChange(draft.copy(password = it)) },
                    label = { Text(stringResource(R.string.middlewares_generate_password)) },
                    placeholder = { Text(stringResource(R.string.channels_auth_placeholder)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        showKeyboardOnFocus = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(stringResource(R.string.channels_delivery_section), modifier = Modifier.padding(top = TmSpacing.sm))

            ToggleLine(
                title = stringResource(R.string.common_enabled),
                subtitle = stringResource(R.string.channels_enabled_body),
                checked = draft.enabled,
                onChange = { onChange(draft.copy(enabled = it)) },
            )

            FieldLabel(stringResource(R.string.channels_categories), stringResource(R.string.channels_categories_help))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                ChannelKinds.categories.forEach { (key, label) ->
                    FilterChip(
                        selected = key in draft.categories,
                        onClick = {
                            val next = if (key in draft.categories) {
                                draft.categories - key
                            } else {
                                draft.categories + key
                            }
                            onChange(draft.copy(categories = next))
                        },
                        label = { Text(label) },
                    )
                }
            }

            FieldLabel(stringResource(R.string.channels_severity), stringResource(R.string.channels_severity_help))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                ChannelKinds.severities.forEach { (key, label) ->
                    FilterChip(
                        selected = draft.minSeverity == key,
                        onClick = { onChange(draft.copy(minSeverity = key)) },
                        label = { Text(label) },
                    )
                }
            }

            FieldLabel(stringResource(R.string.channels_digest), stringResource(R.string.channels_digest_help))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
                ChannelKinds.digests.forEach { (key, label) ->
                    FilterChip(
                        selected = draft.digest == key,
                        onClick = { onChange(draft.copy(digest = key)) },
                        label = { Text(label) },
                    )
                }
            }

            FieldLabel(
                stringResource(R.string.channels_quiet),
                stringResource(R.string.channels_quiet_help),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimeField(
                    value = draft.quietStart,
                    label = stringResource(R.string.channels_quiet_from),
                    onClick = { picking = "start" },
                    modifier = Modifier.weight(1f),
                )
                Text(text = stringResource(R.string.channels_quiet_between), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                TimeField(
                    value = draft.quietEnd,
                    label = stringResource(R.string.channels_quiet_to),
                    onClick = { picking = "end" },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(draft.copy(quietStart = "", quietEnd = "")) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.channels_quiet_clear), modifier = Modifier.size(18.dp))
                }
            }

            ToggleLine(
                title = stringResource(R.string.channels_break_through),
                subtitle = stringResource(R.string.channels_break_through_body),
                checked = draft.breakThrough,
                onChange = { onChange(draft.copy(breakThrough = it)) },
            )

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.red,
                    modifier = Modifier.padding(top = TmSpacing.xs),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.padding(top = TmSpacing.sm),
            ) {
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_save)) }
                OutlinedButton(onClick = onTest, enabled = !saving && test != TestState.Running) { Text(stringResource(R.string.channels_test)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
            when (test) {
                TestState.Idle -> Unit
                TestState.Running -> LoadingIndicator(modifier = Modifier.size(24.dp))
                is TestState.Ok -> Text(
                    text = stringResource(R.string.channels_test_ok),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.green,
                )
                is TestState.Failed -> Text(
                    text = test.message.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.red,
                )
            }
        }
    }

    picking?.let { slot ->
        val current = if (slot == "start") draft.quietStart else draft.quietEnd
        TimePickerDialog(
            initial = current,
            onDismiss = { picking = null },
            onPick = { picked ->
                onChange(
                    if (slot == "start") draft.copy(quietStart = picked) else draft.copy(quietEnd = picked),
                )
                picking = null
            },
        )
    }
}

@Composable
private fun FieldLabel(title: String, subtitle: String) {
    val palette = LocalTmPalette.current
    Column(modifier = Modifier.padding(top = TmSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TmSpacing.xs),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeField(value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        placeholder = { Text("--:--") },
        singleLine = true,
        modifier = modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = initial.split(':')
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 22,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPick("%02d:%02d".format(state.hour, state.minute))
                },
            ) { Text(stringResource(R.string.channels_quiet_set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.ServiceTypes
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.settings.FormField
import dev.chr0nzz.traefikmanager.ui.settings.FormSelect
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEditorSheet(
    draft: ServiceDraft,
    services: List<String>,
    busy: Boolean,
    error: String?,
    onChange: (ServiceDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val readOnly = draft.type == "highestRandomWeight"

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
                text = if (draft.adding) "New service" else "Edit service",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (readOnly) {
                Text(
                    text = "Highest random weight is not authorable in Traefik Manager. " +
                        "It is read here and edited in the config file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.yellow,
                )
            }

            FormField(
                label = "Name",
                value = draft.name,
                onChange = { onChange(draft.copy(name = it)) },
                placeholder = "api-pool",
            )

            FormSelect(
                label = "Type",
                value = draft.type,
                options = ServiceTypes.authorable,
                onChange = { onChange(draft.copy(type = it)) },
            )

            if (draft.adding) {
                FormField(
                    label = "Config file",
                    value = draft.configFile,
                    onChange = { onChange(draft.copy(configFile = it)) },
                    placeholder = "dynamic.yml",
                    help = "Leave empty for the default file.",
                )
            }

            SectionLabel("Backends", modifier = Modifier.padding(top = TmSpacing.sm))
            Text(
                text = when (draft.type) {
                    "mirroring" -> "The first row serves traffic. The rest mirror it by percent."
                    "failover" -> "The first row is primary, the second is the fallback."
                    "loadBalancer" -> "Addresses to balance across."
                    else -> "Weights decide the share each backend takes."
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )

            draft.children.forEachIndexed { index, child ->
                ChildRow(
                    child = child,
                    index = index,
                    type = draft.type,
                    services = services,
                    removable = draft.children.size > 1,
                    onChange = { updated ->
                        onChange(
                            draft.copy(
                                children = draft.children.toMutableList().apply { this[index] = updated },
                            ),
                        )
                    },
                    onRemove = {
                        onChange(
                            draft.copy(
                                children = draft.children.filterIndexed { at, _ -> at != index },
                            ),
                        )
                    },
                )
            }

            if (draft.children.size < ServiceTypes.maxRows(draft.type)) {
                OutlinedButton(
                    onClick = { onChange(draft.copy(children = draft.children + ServiceChildDraft())) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add backend") }
            }

            if (error != null) {
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = palette.red)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.padding(top = TmSpacing.sm),
            ) {
                Button(
                    onClick = onSave,
                    enabled = !busy && !readOnly,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ChildRow(
    child: ServiceChildDraft,
    index: Int,
    type: String,
    services: List<String>,
    removable: Boolean,
    onChange: (ServiceChildDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalTmPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ServiceTypes.rowHint(type, index)?.let { hint ->
                Text(text = hint, style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
            FilterChip(
                selected = child.kind == ServiceChildDraft.MANUAL,
                onClick = { onChange(child.copy(kind = ServiceChildDraft.MANUAL)) },
                label = { Text("Address") },
            )
            FilterChip(
                selected = child.kind == ServiceChildDraft.SERVICE,
                onClick = { onChange(child.copy(kind = ServiceChildDraft.SERVICE)) },
                label = { Text("Service") },
            )
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove this backend",
                        tint = palette.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (child.kind == ServiceChildDraft.SERVICE) {
            FormSelect(
                label = "Service",
                value = child.name,
                options = services.map { it to it },
                onChange = { onChange(child.copy(name = it)) },
            )
        } else {
            FormSelect(
                label = "Scheme",
                value = child.scheme,
                options = listOf("http" to "http", "https" to "https"),
                onChange = { onChange(child.copy(scheme = it)) },
            )
            FormField(
                label = "Address",
                value = child.address,
                onChange = { onChange(child.copy(address = it)) },
                placeholder = "10.0.0.10:80",
                mono = true,
            )
        }

        if (ServiceTypes.usesShare(type)) {
            Row(modifier = Modifier.width(160.dp)) {
                FormField(
                    label = ServiceTypes.shareLabel(type),
                    value = child.share,
                    onChange = { onChange(child.copy(share = it)) },
                    numeric = true,
                )
            }
        }
    }
}

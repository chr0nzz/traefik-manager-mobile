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
import dev.chr0nzz.traefikmanager.data.model.ServiceHealthDraft
import dev.chr0nzz.traefikmanager.data.model.ServiceTypes
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.settings.FormField
import dev.chr0nzz.traefikmanager.ui.settings.FormSelect
import dev.chr0nzz.traefikmanager.ui.settings.FormToggle
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

            if (draft.type == "loadBalancer") {
                HealthCheckSection(
                    health = draft.healthCheck,
                    onChange = { onChange(draft.copy(healthCheck = it)) },
                )
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
private fun HealthCheckSection(health: ServiceHealthDraft, onChange: (ServiceHealthDraft) -> Unit) {
    val palette = LocalTmPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
        SectionLabel("Health check", modifier = Modifier.padding(top = TmSpacing.sm))
        FormToggle(
            title = "Poll the servers",
            subtitle = "Traefik stops sending traffic to servers that fail",
            checked = health.enabled,
            onChange = { onChange(health.copy(enabled = it)) },
        )
        if (!health.enabled) return@Column

        FormField(
            label = "Path",
            value = health.path,
            onChange = { onChange(health.copy(path = it)) },
            placeholder = "/ (server root)",
            mono = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Interval",
                    value = health.interval,
                    onChange = { onChange(health.copy(interval = it)) },
                    placeholder = "30s",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Timeout",
                    value = health.timeout,
                    onChange = { onChange(health.copy(timeout = it)) },
                    placeholder = "5s",
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Method",
                    value = health.method,
                    onChange = { onChange(health.copy(method = it)) },
                    placeholder = "GET",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Expected status",
                    value = health.status,
                    onChange = { onChange(health.copy(status = it)) },
                    placeholder = "any 2xx or 3xx",
                    numeric = true,
                )
            }
        }
        FormField(
            label = "Interval when down",
            value = health.unhealthyInterval,
            onChange = { onChange(health.copy(unhealthyInterval = it)) },
            placeholder = "same as interval",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormSelect(
                    label = "Scheme",
                    value = health.scheme,
                    options = listOf("" to "same as server", "http" to "http", "https" to "https"),
                    onChange = { onChange(health.copy(scheme = it)) },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Port",
                    value = health.port,
                    onChange = { onChange(health.copy(port = it)) },
                    placeholder = "same as server",
                    numeric = true,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Host header",
                    value = health.hostname,
                    onChange = { onChange(health.copy(hostname = it)) },
                    placeholder = "optional",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormSelect(
                    label = "Mode",
                    value = health.mode,
                    options = listOf("" to "http", "grpc" to "grpc"),
                    onChange = { onChange(health.copy(mode = it)) },
                )
            }
        }
        FormToggle(
            title = "Follow redirects",
            checked = health.followRedirects,
            onChange = { onChange(health.copy(followRedirects = it)) },
        )

        health.headers.forEachIndexed { index, (key, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FormField(
                        label = "Header",
                        value = key,
                        onChange = { typed ->
                            onChange(health.copy(headers = health.headers.toMutableList().apply { this[index] = typed to value }))
                        },
                        mono = true,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FormField(
                        label = "Value",
                        value = value,
                        onChange = { typed ->
                            onChange(health.copy(headers = health.headers.toMutableList().apply { this[index] = key to typed }))
                        },
                    )
                }
                IconButton(
                    onClick = { onChange(health.copy(headers = health.headers.filterIndexed { at, _ -> at != index })) },
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove this header",
                        tint = palette.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(health.copy(headers = health.headers + ("" to ""))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add header") }
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
                options = listOf("http" to "http", "https" to "https", "h2c" to "h2c"),
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

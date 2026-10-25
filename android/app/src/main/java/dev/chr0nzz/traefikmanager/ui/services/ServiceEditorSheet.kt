package dev.chr0nzz.traefikmanager.ui.services

import dev.chr0nzz.traefikmanager.data.model.UiText
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
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.asString

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
                text = stringResource(if (draft.adding) R.string.services_new else R.string.services_edit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (readOnly) {
                Text(
                    text = stringResource(R.string.services_hrw_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.yellow,
                )
            }

            FormField(
                label = stringResource(R.string.common_label_name),
                value = draft.name,
                onChange = { onChange(draft.copy(name = it)) },
                placeholder = "api-pool",
            )

            FormSelect(
                label = stringResource(R.string.common_label_type),
                value = draft.type,
                options = ServiceTypes.authorable,
                onChange = { onChange(draft.copy(type = it)) },
            )

            if (draft.adding) {
                FormField(
                    label = stringResource(R.string.common_label_config_file),
                    value = draft.configFile,
                    onChange = { onChange(draft.copy(configFile = it)) },
                    placeholder = "dynamic.yml",
                    help = stringResource(R.string.services_form_file_help),
                )
            }

            SectionLabel(stringResource(R.string.routes_form_backends), modifier = Modifier.padding(top = TmSpacing.sm))
            Text(
                text = when (draft.type) {
                    "mirroring" -> stringResource(R.string.services_form_help_mirroring)
                    "failover" -> stringResource(R.string.services_form_help_failover)
                    "loadBalancer" -> stringResource(R.string.services_form_help_load_balancer)
                    else -> stringResource(R.string.services_form_help_weighted)
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
                ) { Text(stringResource(R.string.routes_form_add_backend)) }
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
                ) { Text(stringResource(R.string.common_save)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

@Composable
private fun HealthCheckSection(health: ServiceHealthDraft, onChange: (ServiceHealthDraft) -> Unit) {
    val palette = LocalTmPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(TmSpacing.xs)) {
        SectionLabel(stringResource(R.string.routes_form_health_check), modifier = Modifier.padding(top = TmSpacing.sm))
        FormToggle(
            title = stringResource(R.string.services_form_health_enable),
            subtitle = stringResource(R.string.services_form_health_enable_help),
            checked = health.enabled,
            onChange = { onChange(health.copy(enabled = it)) },
        )
        if (!health.enabled) return@Column

        FormField(
            label = stringResource(R.string.common_label_path),
            value = health.path,
            onChange = { onChange(health.copy(path = it)) },
            placeholder = stringResource(R.string.services_form_path_placeholder),
            mono = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.common_label_interval),
                    value = health.interval,
                    onChange = { onChange(health.copy(interval = it)) },
                    placeholder = "30s",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.common_label_timeout),
                    value = health.timeout,
                    onChange = { onChange(health.copy(timeout = it)) },
                    placeholder = "5s",
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.services_form_method),
                    value = health.method,
                    onChange = { onChange(health.copy(method = it)) },
                    placeholder = "GET",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.services_form_status),
                    value = health.status,
                    onChange = { onChange(health.copy(status = it)) },
                    placeholder = stringResource(R.string.services_form_status_placeholder),
                    numeric = true,
                )
            }
        }
        FormField(
            label = stringResource(R.string.services_form_unhealthy_interval),
            value = health.unhealthyInterval,
            onChange = { onChange(health.copy(unhealthyInterval = it)) },
            placeholder = stringResource(R.string.services_form_unhealthy_interval_placeholder),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormSelect(
                    label = stringResource(R.string.common_label_scheme),
                    value = health.scheme,
                    options = listOf(
                        "" to UiText.of(R.string.services_form_port_placeholder),
                        "http" to UiText.Raw("http"),
                        "https" to UiText.Raw("https"),
                    ),
                    onChange = { onChange(health.copy(scheme = it)) },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.common_label_port),
                    value = health.port,
                    onChange = { onChange(health.copy(port = it)) },
                    placeholder = stringResource(R.string.services_form_port_placeholder),
                    numeric = true,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.services_form_host_header),
                    value = health.hostname,
                    onChange = { onChange(health.copy(hostname = it)) },
                    placeholder = "optional",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                FormSelect(
                    label = stringResource(R.string.services_form_mode),
                    value = health.mode,
                    options = listOf("" to UiText.Raw("http"), "grpc" to UiText.Raw("grpc")),
                    onChange = { onChange(health.copy(mode = it)) },
                )
            }
        }
        FormToggle(
            title = stringResource(R.string.services_form_follow_redirects),
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
                        label = stringResource(R.string.services_form_header),
                        value = key,
                        onChange = { typed ->
                            onChange(health.copy(headers = health.headers.toMutableList().apply { this[index] = typed to value }))
                        },
                        mono = true,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FormField(
                        label = stringResource(R.string.services_form_header_value),
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
                        contentDescription = stringResource(R.string.services_form_remove_header),
                        tint = palette.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(health.copy(headers = health.headers + ("" to ""))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.services_form_add_header)) }
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
                Text(text = hint.asString(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
            FilterChip(
                selected = child.kind == ServiceChildDraft.MANUAL,
                onClick = { onChange(child.copy(kind = ServiceChildDraft.MANUAL)) },
                label = { Text(stringResource(R.string.services_form_address)) },
            )
            FilterChip(
                selected = child.kind == ServiceChildDraft.SERVICE,
                onClick = { onChange(child.copy(kind = ServiceChildDraft.SERVICE)) },
                label = { Text(stringResource(R.string.common_label_service)) },
            )
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.services_form_remove_backend),
                        tint = palette.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (child.kind == ServiceChildDraft.SERVICE) {
            FormSelect(
                label = stringResource(R.string.common_label_service),
                value = child.name,
                options = services.map { it to UiText.Raw(it) },
                onChange = { onChange(child.copy(name = it)) },
            )
        } else {
            FormSelect(
                label = stringResource(R.string.common_label_scheme),
                value = child.scheme,
                options = listOf("http" to UiText.Raw("http"), "https" to UiText.Raw("https"), "h2c" to UiText.Raw("h2c")),
                onChange = { onChange(child.copy(scheme = it)) },
            )
            FormField(
                label = stringResource(R.string.services_form_address),
                value = child.address,
                onChange = { onChange(child.copy(address = it)) },
                placeholder = "10.0.0.10:80",
                mono = true,
            )
        }

        if (ServiceTypes.usesShare(type)) {
            Row(modifier = Modifier.width(160.dp)) {
                FormField(
                    label = ServiceTypes.shareLabel(type).asString(),
                    value = child.share,
                    onChange = { onChange(child.copy(share = it)) },
                    numeric = true,
                )
            }
        }
    }
}

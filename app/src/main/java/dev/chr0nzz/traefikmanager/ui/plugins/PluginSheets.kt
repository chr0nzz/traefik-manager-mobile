package dev.chr0nzz.traefikmanager.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginInstallSheet(
    draft: PluginInstall,
    busy: Boolean,
    error: String?,
    onChange: (PluginInstall) -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = "Add plugin",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            TmCard(accentColor = palette.purple) {
                Text(
                    text = "Only install plugins from trusted sources",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Plugins run inside the Traefik process with full access to your " +
                        "traffic. Review the plugin source on GitHub before installing. A Traefik " +
                        "restart is required to activate the plugin after saving.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }

            SectionLabel("Step 1 - static config snippet")
            Text(
                text = "Paste it from the plugin's page on plugins.traefik.io.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            YamlField(
                value = draft.staticYaml,
                onValueChange = { onChange(draft.copy(staticYaml = it)) },
            )

            SectionLabel("Step 2 - middleware snippet", modifier = Modifier.padding(top = TmSpacing.xs))
            Text(
                text = "Edit the settings for your needs. Replace every placeholder and any " +
                    "{{ }} block with real values or Traefik will crash.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.yellow,
            )
            YamlField(
                value = draft.middlewareYaml,
                onValueChange = { onChange(draft.copy(middlewareYaml = it)) },
            )

            OutlinedTextField(
                value = draft.middlewareFile,
                onValueChange = { onChange(draft.copy(middlewareFile = it)) },
                label = { Text("Middleware file") },
                placeholder = { Text("plugin-middlewares.yml") },
                supportingText = { Text("Which config file the middleware is written to.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.red,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.padding(top = TmSpacing.sm),
            ) {
                Button(onClick = onInstall, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginEditSheet(
    draft: PluginEdit,
    busy: Boolean,
    error: String?,
    onChange: (PluginEdit) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = "Edit plugin",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Name") },
                placeholder = { Text("myPlugin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.moduleName,
                onValueChange = { onChange(draft.copy(moduleName = it)) },
                label = { Text("Module") },
                placeholder = { Text("github.com/author/plugin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.version,
                onValueChange = { onChange(draft.copy(version = it)) },
                label = { Text("Version") },
                placeholder = { Text("v0.1.0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(text = error, style = MaterialTheme.typography.bodySmall, color = palette.red)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.padding(top = TmSpacing.sm),
            ) {
                Button(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun PluginRestartNotice(detail: String, busy: Boolean, onRestart: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalTmPalette.current
    TmCard(accentColor = palette.yellow) {
        Text(
            text = "Restart Traefik to activate",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = detail.ifBlank { "The static config changed. Traefik is still running the old one." },
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.padding(top = TmSpacing.sm),
        ) {
            Button(onClick = onRestart, enabled = !busy) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Restart now", modifier = Modifier.padding(start = TmSpacing.xs))
            }
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    }
}

@Composable
private fun YamlField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
        minLines = 5,
        maxLines = 12,
        modifier = Modifier.fillMaxWidth(),
    )
}

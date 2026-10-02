package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.EntrypointForm
import dev.chr0nzz.traefikmanager.data.model.ResolverForm
import dev.chr0nzz.traefikmanager.data.model.StaticPluginForm
import dev.chr0nzz.traefikmanager.data.model.StaticSections
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.YamlEditor
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.asString

private sealed interface StaticEditing {
    data class Entrypoint(val form: EntrypointForm, val adding: Boolean) : StaticEditing
    data class Resolver(val form: ResolverForm, val adding: Boolean) : StaticEditing
    data class Plugin(val form: StaticPluginForm, val adding: Boolean) : StaticEditing
    data object Providers : StaticEditing
    data object Api : StaticEditing
    data object Log : StaticEditing
    data object Observability : StaticEditing
    data object System : StaticEditing
}

private data class PendingRemoval(val section: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaticConfigScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StaticConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var rawOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StaticEditing?>(null) }
    var removing by remember { mutableStateOf<PendingRemoval?>(null) }
    var formError by remember { mutableStateOf<UiText?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolve(resources))
        viewModel.consumeMessage()
    }

    val stage: (String, String, String, String, kotlinx.serialization.json.JsonObject) -> Unit =
        { section, action, name, oldName, data ->
            viewModel.applySection(section, action, name, oldName, data) { error ->
                formError = error
                if (error == null) editing = null
            }
        }

    when (val open = editing) {
        is StaticEditing.Entrypoint -> EntrypointSheet(
            initial = open.form,
            adding = open.adding,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form ->
                stage(
                    StaticSections.ENTRYPOINTS,
                    if (open.adding) "add" else "edit",
                    form.name.trim(),
                    open.form.name,
                    form.data(),
                )
            },
            onDismiss = { editing = null; formError = null },
        )
        is StaticEditing.Resolver -> ResolverSheet(
            initial = open.form,
            adding = open.adding,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form ->
                stage(
                    StaticSections.RESOLVERS,
                    if (open.adding) "add" else "edit",
                    form.name.trim(),
                    open.form.name,
                    form.data(),
                )
            },
            onDismiss = { editing = null; formError = null },
        )
        is StaticEditing.Plugin -> StaticPluginSheet(
            initial = open.form,
            adding = open.adding,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form ->
                stage(
                    StaticSections.PLUGINS,
                    if (open.adding) "add" else "edit",
                    form.name.trim(),
                    open.form.name,
                    form.data(),
                )
            },
            onDismiss = { editing = null; formError = null },
        )
        StaticEditing.Providers -> ProvidersSheet(
            initial = state.providers,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form -> stage(StaticSections.PROVIDERS, "set", "", "", form.data()) },
            onDismiss = { editing = null; formError = null },
        )
        StaticEditing.Api -> ApiSheet(
            initial = state.api,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form -> stage(StaticSections.API, "set", "", "", form.data()) },
            onDismiss = { editing = null; formError = null },
        )
        StaticEditing.Log -> LogSheet(
            initial = state.log,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form -> stage(StaticSections.LOG, "set", "", "", form.data()) },
            onDismiss = { editing = null; formError = null },
        )
        StaticEditing.Observability -> ObservabilitySheet(
            initial = state.observability,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form -> stage(StaticSections.OBSERVABILITY, "set", "", "", form.data()) },
            onDismiss = { editing = null; formError = null },
        )
        StaticEditing.System -> SystemSheet(
            initial = state.system,
            busy = state.busy,
            error = formError?.asString(),
            onSave = { form -> stage(StaticSections.SYSTEM, "set", "", "", form.data()) },
            onDismiss = { editing = null; formError = null },
        )
        null -> Unit
    }

    removing?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.static_remove_title)) },
            text = { Text(stringResource(R.string.static_remove_body, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stage(target.section, "remove", target.name, "", kotlinx.serialization.json.JsonObject(emptyMap()))
                        removing = null
                    },
                ) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.static_discard_title)) },
            text = { Text(stringResource(R.string.static_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.discard()
                    },
                ) { Text(stringResource(R.string.static_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.static_keep_editing)) } },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(if (rawOpen) R.string.routes_raw_title else R.string.settings_static_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (rawOpen) rawOpen = false else onClose() }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(if (rawOpen) R.string.static_back_sections else R.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { rawOpen = !rawOpen }) {
                        Icon(Icons.Outlined.Code, contentDescription = stringResource(R.string.static_raw_editor))
                    }
                    IconButton(
                        onClick = { if (state.pending || viewModel.dirty) confirmDiscard = true else viewModel.load() },
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.static_reload))
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.saving && (state.pending || viewModel.dirty),
                    ) { Text(stringResource(R.string.common_save)) }
                },
            )
        },
    ) { insets ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(insets))
            state.loadError != null -> ErrorState(
                headline = stringResource(R.string.static_error_load),
                body = state.loadError?.asString(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(insets),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .imePadding(),
            ) {
                StateBar(
                    pending = state.pending || viewModel.dirty,
                    saved = state.restartPending,
                    restarting = state.restarting,
                    onDiscard = { confirmDiscard = true },
                    onSave = viewModel::save,
                    onRestart = viewModel::restart,
                )
                state.saveError?.let { error ->
                    Text(
                        text = error.asString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = palette.red,
                        modifier = Modifier.padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs),
                    )
                }

                if (rawOpen) {
                    YamlEditor(
                        state = viewModel.content,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = TmSpacing.xs),
                    )
                } else {
                    Sections(
                        state = state,
                        onEdit = { editing = it; formError = null },
                        onRemove = { section, name -> removing = PendingRemoval(section, name) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StateBar(
    pending: Boolean,
    saved: Boolean,
    restarting: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onRestart: () -> Unit,
) {
    val palette = LocalTmPalette.current
    when {
        pending -> TmCard(
            accentColor = palette.yellow,
            modifier = Modifier
                .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs)
                .height(IntrinsicSize.Min),
        ) {
            Text(
                text = stringResource(R.string.static_unsaved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.padding(top = TmSpacing.xs),
            ) {
                Button(onClick = onSave) { Text(stringResource(R.string.common_save)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.static_discard)) }
            }
        }
        saved -> TmCard(
            accentColor = palette.red,
            modifier = Modifier
                .padding(horizontal = TmSpacing.lg, vertical = TmSpacing.xs)
                .height(IntrinsicSize.Min),
        ) {
            Text(
                text = stringResource(R.string.static_saved_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(modifier = Modifier.padding(top = TmSpacing.xs)) {
                Button(onClick = onRestart, enabled = !restarting) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.common_restart_traefik), modifier = Modifier.padding(start = TmSpacing.xs))
                }
            }
        }
    }
}

@Composable
private fun Sections(
    state: StaticConfigUiState,
    onEdit: (StaticEditing) -> Unit,
    onRemove: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TmSpacing.lg,
            end = TmSpacing.lg,
            top = TmSpacing.xs,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
    ) {
        item { SectionLabel(stringResource(R.string.static_traffic_in)) }

        item {
            ListSectionCard(
                title = stringResource(R.string.static_entrypoints),
                count = state.entrypoints.size,
                empty = stringResource(R.string.static_no_entrypoints_configured),
                onAdd = { onEdit(StaticEditing.Entrypoint(EntrypointForm(), adding = true)) },
            ) {
                state.entrypoints.forEachIndexed { index, (name, entry) ->
                    ItemRow(
                        title = name,
                        subtitle = EntrypointForm.read(name, entry).address.ifBlank { stringResource(R.string.static_no_address) },
                        onClick = { onEdit(StaticEditing.Entrypoint(EntrypointForm.read(name, entry), false)) },
                        onRemove = { onRemove(StaticSections.ENTRYPOINTS, name) },
                    )
                    if (index < state.entrypoints.lastIndex) CardDivider()
                }
            }
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.nav_section_providers),
                summary = buildList {
                    add(if (state.providers.docker) "docker" else stringResource(R.string.static_chip_off, "docker"))
                    add(if (state.providers.file) "file" else stringResource(R.string.static_chip_off, "file"))
                    state.otherProviders.forEach { add(it) }
                }.joinToString(" · "),
                onClick = { onEdit(StaticEditing.Providers) },
            )
        }

        item { SectionLabel(stringResource(R.string.nav_certificates), modifier = Modifier.padding(top = TmSpacing.sm)) }

        item {
            ListSectionCard(
                title = stringResource(R.string.static_certificate_resolvers),
                count = state.resolvers.size,
                empty = stringResource(R.string.static_no_certificate_resolvers_configured),
                onAdd = { onEdit(StaticEditing.Resolver(ResolverForm(), adding = true)) },
            ) {
                state.resolvers.forEachIndexed { index, (name, entry) ->
                    val form = ResolverForm.read(name, entry)
                    ItemRow(
                        title = name,
                        subtitle = form.email.ifBlank { stringResource(R.string.static_no_email) },
                        warn = form.email.isBlank(),
                        onClick = { onEdit(StaticEditing.Resolver(form, false)) },
                        onRemove = { onRemove(StaticSections.RESOLVERS, name) },
                    )
                    if (index < state.resolvers.lastIndex) CardDivider()
                }
            }
        }

        item { SectionLabel(stringResource(R.string.static_operations), modifier = Modifier.padding(top = TmSpacing.sm)) }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.static_api_and_dashboard),
                summary = buildList {
                    add(stringResource(if (state.api.enabled) R.string.static_chip_enabled else R.string.static_chip_disabled))
                    add(stringResource(if (state.api.dashboard) R.string.static_chip_dashboard_on else R.string.static_chip_dashboard_off))
                    if (state.api.insecure) add(stringResource(R.string.static_chip_insecure_on))
                }.joinToString(" · "),
                warn = state.api.insecure || !state.api.enabled,
                onClick = { onEdit(StaticEditing.Api) },
            )
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.static_logging),
                summary = buildList {
                    add(state.log.level)
                    add(state.log.logFormat.ifBlank { "text" })
                    add(state.log.logFile.ifBlank { "stdout" })
                    add(stringResource(if (state.log.accessLog) R.string.dashboard_runtime_access_log_on else R.string.dashboard_runtime_access_log_off))
                }.joinToString(" · "),
                warn = !state.log.accessLog,
                onClick = { onEdit(StaticEditing.Log) },
            )
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.nav_section_observability),
                summary = buildList {
                    add(stringResource(if (state.observability.ping) R.string.static_chip_ping_on else R.string.static_chip_ping_off))
                    add(stringResource(if (state.observability.prometheus) R.string.static_chip_metrics_on else R.string.dashboard_runtime_metrics_off))
                    add(stringResource(if (state.observability.tracing) R.string.static_chip_tracing_on else R.string.dashboard_runtime_tracing_off))
                }.joinToString(" · "),
                onClick = { onEdit(StaticEditing.Observability) },
            )
        }

        item {
            SettingsSectionCard(
                title = stringResource(R.string.nav_section_system),
                summary = buildList {
                    add(stringResource(if (state.system.checkNewVersion) R.string.static_chip_version_on else R.string.static_chip_version_off))
                    add(stringResource(if (state.system.sendUsage) R.string.static_chip_usage_on else R.string.static_chip_usage_off))
                    add(stringResource(R.string.static_chip_rule_syntax, if (state.system.ruleSyntax == "v2") "v2" else "v3"))
                }.joinToString(" · "),
                onClick = { onEdit(StaticEditing.System) },
            )
        }

        item {
            ListSectionCard(
                title = stringResource(R.string.nav_plugins),
                count = state.plugins.size,
                empty = stringResource(R.string.static_no_plugins_installed),
                onAdd = { onEdit(StaticEditing.Plugin(StaticPluginForm(), adding = true)) },
            ) {
                state.plugins.forEachIndexed { index, (name, module, local) ->
                    ItemRow(
                        title = name,
                        subtitle = if (local) stringResource(R.string.static_plugin_is_local) else module,
                        onClick = {
                            onEdit(
                                StaticEditing.Plugin(
                                    StaticPluginForm(name = name, moduleName = module, local = local),
                                    adding = false,
                                ),
                            )
                        },
                        onRemove = { onRemove(StaticSections.PLUGINS, name) },
                    )
                    if (index < state.plugins.lastIndex) CardDivider()
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.static_raw_note),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = TmSpacing.sm),
            )
        }
    }
}

@Composable
private fun ListSectionCard(
    title: String,
    count: Int,
    empty: String,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalTmPalette.current
    TmCard(modifier = Modifier.height(IntrinsicSize.Min)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = palette.muted,
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.static_add_to, title), modifier = Modifier.size(18.dp))
            }
        }
        if (count == 0) {
            Text(text = empty, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        } else {
            CardDivider(modifier = Modifier.padding(vertical = TmSpacing.xs))
            content()
        }
    }
}

@Composable
private fun ItemRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    warn: Boolean = false,
) {
    val palette = LocalTmPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = TmSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = if (warn) palette.yellow else palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onClick) { Text(stringResource(R.string.common_edit)) }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.common_remove_named, title),
                tint = palette.red,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
    warn: Boolean = false,
) {
    val palette = LocalTmPalette.current
    TmCard(onClick = onClick, modifier = Modifier.height(IntrinsicSize.Min)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = if (warn) palette.yellow else palette.muted,
        )
    }
}

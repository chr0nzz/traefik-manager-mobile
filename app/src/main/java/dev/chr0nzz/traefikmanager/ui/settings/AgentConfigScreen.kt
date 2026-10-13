package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.ErrorState
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.ui.components.TopBarTitle
import dev.chr0nzz.traefikmanager.ui.components.asString

private val RESTART_METHODS = listOf(
    "" to R.string.agent_restart_none,
    "proxy" to R.string.agent_restart_proxy,
    "poison-pill" to R.string.agent_restart_poison,
    "socket" to R.string.agent_restart_socket,
)

private val AGENT_TABS = listOf(
    "logs" to UiText.of(R.string.nav_logs),
    "crowdsec" to UiText.Raw("CrowdSec"),
    "certs" to UiText.of(R.string.nav_certificates),
    "plugins" to UiText.of(R.string.nav_plugins),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgentConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalTmPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.resolve(resources))
        viewModel.consumeMessage()
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
                title = { TopBarTitle(state.agentName.ifEmpty { stringResource(R.string.agent_agent) }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.agent_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.dirty && !state.saving) {
                        Text(stringResource(R.string.common_save))
                    }
                },
            )
        },
    ) { insets ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(insets))
            return@Scaffold
        }
        state.error?.let { message ->
            ErrorState(
                headline = stringResource(R.string.agent_error_load),
                body = message.asString(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(insets),
            )
            return@Scaffold
        }

        val form = state.form

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .imePadding(),
            contentPadding = PaddingValues(
                start = TmSpacing.lg,
                end = TmSpacing.lg,
                top = TmSpacing.xs,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
        ) {
            item { SectionLabel(stringResource(R.string.agent_agent)) }
            item {
                TmCard {
                    Field(stringResource(R.string.common_label_name), form.name, { v -> viewModel.edit { it.copy(name = v) } })
                    Field(
                        label = stringResource(R.string.agent_agent_url),
                        value = form.url,
                        onChange = { v -> viewModel.edit { it.copy(url = v) } },
                        support = stringResource(R.string.agent_where_the_manager_reaches_this_agent),
                        keyboard = KeyboardType.Uri,
                    )
                    Field(
                        label = stringResource(R.string.agent_listen_port),
                        value = form.tmaPort,
                        onChange = { v -> viewModel.edit { it.copy(tmaPort = v) } },
                        support = stringResource(R.string.agent_tma_port_on_the_agent_blank),
                        keyboard = KeyboardType.Number,
                    )
                    Field(
                        label = stringResource(R.string.agent_rate_limit),
                        value = form.tmaRateLimit,
                        onChange = { v -> viewModel.edit { it.copy(tmaRateLimit = v) } },
                        support = stringResource(R.string.agent_requests_a_minute_the_agent_accepts),
                        keyboard = KeyboardType.Number,
                        last = true,
                    )
                }
            }

            item { SectionLabel("Traefik", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Field(
                        label = stringResource(R.string.agent_traefik_api_url),
                        value = form.traefikApiUrl,
                        onChange = { v -> viewModel.edit { it.copy(traefikApiUrl = v) } },
                        support = stringResource(R.string.agent_where_the_agent_reaches_traefik_not),
                        keyboard = KeyboardType.Uri,
                    )
                    Field(
                        stringResource(R.string.common_label_cert_resolver),
                        form.certResolver,
                        { v -> viewModel.edit { it.copy(certResolver = v) } },
                    )
                    Field(
                        label = stringResource(R.string.common_label_domains),
                        value = form.domains,
                        onChange = { v -> viewModel.edit { it.copy(domains = v) } },
                        support = stringResource(R.string.agent_comma_separated_offered_when_you_create),
                    )
                    ToggleRow(
                        title = stringResource(R.string.agent_skip_tls_verification),
                        subtitle = stringResource(R.string.agent_only_for_a_traefik_api_behind),
                        checked = form.insecureSkipVerify,
                        onChange = { v -> viewModel.edit { it.copy(insecureSkipVerify = v) } },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.agent_paths_on_the_agent), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Field(stringResource(R.string.agent_dynamic_config_directory), form.configPath, { v -> viewModel.edit { it.copy(configPath = v) } })
                    Field(
                        stringResource(R.string.agent_static_config_file),
                        form.staticConfigPath,
                        { v -> viewModel.edit { it.copy(staticConfigPath = v) } },
                    )
                    Field(stringResource(R.string.agent_backup_directory), form.backupDir, { v -> viewModel.edit { it.copy(backupDir = v) } })
                    Field(
                        label = stringResource(R.string.agent_backups_to_keep),
                        value = form.backupKeepCount,
                        onChange = { v -> viewModel.edit { it.copy(backupKeepCount = v) } },
                        keyboard = KeyboardType.Number,
                    )
                    Field("acme.json", form.acmeJsonPath, { v -> viewModel.edit { it.copy(acmeJsonPath = v) } })
                    Field(stringResource(R.string.static_access_log), form.accessLogPath, { v -> viewModel.edit { it.copy(accessLogPath = v) } })
                    Field(
                        stringResource(R.string.agent_plugins_directory),
                        form.pluginsDir,
                        { v -> viewModel.edit { it.copy(pluginsDir = v) } },
                        last = true,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.agent_restart), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Text(
                        text = stringResource(R.string.agent_restart_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    ) {
                        RESTART_METHODS.forEach { (value, label) ->
                            FilterChip(
                                selected = form.restartMethod == value,
                                onClick = { viewModel.edit { it.copy(restartMethod = value) } },
                                label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    when (form.restartMethod) {
                        "proxy" -> {
                            Field(
                                label = stringResource(R.string.agent_docker_host),
                                value = form.dockerHost,
                                onChange = { v -> viewModel.edit { it.copy(dockerHost = v) } },
                                support = stringResource(R.string.agent_the_socket_proxy_for_example_tcp),
                            )
                            Field(
                                stringResource(R.string.agent_traefik_container),
                                form.traefikContainer,
                                { v -> viewModel.edit { it.copy(traefikContainer = v) } },
                                last = true,
                            )
                        }
                        "socket" -> Field(
                            label = stringResource(R.string.agent_traefik_container),
                            value = form.traefikContainer,
                            onChange = { v -> viewModel.edit { it.copy(traefikContainer = v) } },
                            support = stringResource(R.string.agent_the_agent_needs_var_run_docker),
                            last = true,
                        )
                        "poison-pill" -> Field(
                            label = stringResource(R.string.agent_signal_file),
                            value = form.signalFilePath,
                            onChange = { v -> viewModel.edit { it.copy(signalFilePath = v) } },
                            support = stringResource(R.string.agent_a_watcher_container_restarts_traefik_when),
                            last = true,
                        )
                    }
                }
            }

            item { SectionLabel("CrowdSec", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Field(
                        label = stringResource(R.string.agent_lapi_url),
                        value = form.crowdsecLapiUrl,
                        onChange = { v -> viewModel.edit { it.copy(crowdsecLapiUrl = v) } },
                        support = stringResource(R.string.agent_used_when_generating_this_agent_s),
                        keyboard = KeyboardType.Uri,
                    )
                    Field(
                        label = stringResource(R.string.agent_bouncer_api_key),
                        value = form.crowdsecApiKey,
                        onChange = { v -> viewModel.edit { it.copy(crowdsecApiKey = v) } },
                        support = if (state.crowdsecKeySet) {
                            stringResource(R.string.agent_key_stored)
                        } else {
                            stringResource(R.string.agent_key_help)
                        },
                        secret = true,
                    )
                    Field(
                        stringResource(R.string.agent_machine_id),
                        form.crowdsecMachineId,
                        { v -> viewModel.edit { it.copy(crowdsecMachineId = v) } },
                    )
                    Field(
                        label = stringResource(R.string.agent_machine_password),
                        value = form.crowdsecMachinePassword,
                        onChange = { v -> viewModel.edit { it.copy(crowdsecMachinePassword = v) } },
                        support = if (state.crowdsecPasswordSet) {
                            stringResource(R.string.agent_password_stored)
                        } else {
                            stringResource(R.string.agent_password_help)
                        },
                        secret = true,
                    )
                    Field(
                        stringResource(R.string.agent_client_certificate),
                        form.crowdsecClientCert,
                        { v -> viewModel.edit { it.copy(crowdsecClientCert = v) } },
                    )
                    Field(
                        stringResource(R.string.agent_client_key),
                        form.crowdsecClientKey,
                        { v -> viewModel.edit { it.copy(crowdsecClientKey = v) } },
                    )
                    Field(
                        stringResource(R.string.agent_ca_certificate),
                        form.crowdsecCaCert,
                        { v -> viewModel.edit { it.copy(crowdsecCaCert = v) } },
                        last = true,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.agent_tabs_on_this_server), modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    AGENT_TABS.forEachIndexed { index, (tab, label) ->
                        ToggleRow(
                            title = label.asString(),
                            subtitle = null,
                            checked = form.visibleTabs[tab] ?: true,
                            onChange = { visible -> viewModel.toggleTab(tab, visible) },
                        )
                        if (index < AGENT_TABS.lastIndex) CardDivider()
                    }
                    Text(
                        text = stringResource(R.string.agent_tabs_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.agent_git_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = TmSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    support: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = support?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
            showKeyboardOnFocus = false,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TmSpacing.sm),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalTmPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TmSpacing.sm),
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

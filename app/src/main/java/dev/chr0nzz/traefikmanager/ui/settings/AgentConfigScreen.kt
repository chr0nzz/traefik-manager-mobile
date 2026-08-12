package dev.chr0nzz.traefikmanager.ui.settings

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

private val RESTART_METHODS = listOf(
    "" to "None",
    "proxy" to "Socket proxy",
    "poison-pill" to "Poison pill",
    "socket" to "Direct socket",
)

/** The four optional tabs this app can show; the web has more that only it renders. */
private val AGENT_TABS = listOf(
    "logs" to "Logs",
    "crowdsec" to "CrowdSec",
    "certs" to "Certificates",
    "plugins" to "Plugins",
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

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
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
                title = { Text(state.agentName.ifEmpty { "Agent" }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to servers")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.dirty && !state.saving) {
                        Text("Save")
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
                headline = "Could not read the agent",
                body = message,
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
            item { SectionLabel("Agent") }
            item {
                TmCard {
                    Field("Name", form.name, { v -> viewModel.edit { it.copy(name = v) } })
                    Field(
                        label = "Agent URL",
                        value = form.url,
                        onChange = { v -> viewModel.edit { it.copy(url = v) } },
                        support = "Where the manager reaches this agent, for example http://host:8090",
                        keyboard = KeyboardType.Uri,
                    )
                    Field(
                        label = "Listen port",
                        value = form.tmaPort,
                        onChange = { v -> viewModel.edit { it.copy(tmaPort = v) } },
                        support = "TMA_PORT on the agent. Blank keeps its default.",
                        keyboard = KeyboardType.Number,
                    )
                    Field(
                        label = "Rate limit",
                        value = form.tmaRateLimit,
                        onChange = { v -> viewModel.edit { it.copy(tmaRateLimit = v) } },
                        support = "Requests a minute the agent accepts. Blank keeps its default.",
                        keyboard = KeyboardType.Number,
                        last = true,
                    )
                }
            }

            item { SectionLabel("Traefik", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Field(
                        label = "Traefik API URL",
                        value = form.traefikApiUrl,
                        onChange = { v -> viewModel.edit { it.copy(traefikApiUrl = v) } },
                        support = "Where the agent reaches Traefik, not where you do.",
                        keyboard = KeyboardType.Uri,
                    )
                    Field(
                        "Certificate resolver",
                        form.certResolver,
                        { v -> viewModel.edit { it.copy(certResolver = v) } },
                    )
                    Field(
                        label = "Domains",
                        value = form.domains,
                        onChange = { v -> viewModel.edit { it.copy(domains = v) } },
                        support = "Comma separated. Offered when you create a route on this server.",
                    )
                    ToggleRow(
                        title = "Skip TLS verification",
                        subtitle = "Only for a Traefik API behind a self-signed certificate.",
                        checked = form.insecureSkipVerify,
                        onChange = { v -> viewModel.edit { it.copy(insecureSkipVerify = v) } },
                    )
                }
            }

            item { SectionLabel("Paths on the agent", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Field("Dynamic config directory", form.configPath, { v -> viewModel.edit { it.copy(configPath = v) } })
                    Field(
                        "Static config file",
                        form.staticConfigPath,
                        { v -> viewModel.edit { it.copy(staticConfigPath = v) } },
                    )
                    Field("Backup directory", form.backupDir, { v -> viewModel.edit { it.copy(backupDir = v) } })
                    Field(
                        label = "Backups to keep",
                        value = form.backupKeepCount,
                        onChange = { v -> viewModel.edit { it.copy(backupKeepCount = v) } },
                        keyboard = KeyboardType.Number,
                    )
                    Field("acme.json", form.acmeJsonPath, { v -> viewModel.edit { it.copy(acmeJsonPath = v) } })
                    Field("Access log", form.accessLogPath, { v -> viewModel.edit { it.copy(accessLogPath = v) } })
                    Field(
                        "Plugins directory",
                        form.pluginsDir,
                        { v -> viewModel.edit { it.copy(pluginsDir = v) } },
                        last = true,
                    )
                }
            }

            item { SectionLabel("Restart", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Text(
                        text = "How the agent restarts Traefik after a config change.",
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
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    when (form.restartMethod) {
                        "proxy" -> {
                            Field(
                                label = "Docker host",
                                value = form.dockerHost,
                                onChange = { v -> viewModel.edit { it.copy(dockerHost = v) } },
                                support = "The socket proxy, for example tcp://socket-proxy:2375",
                            )
                            Field(
                                "Traefik container",
                                form.traefikContainer,
                                { v -> viewModel.edit { it.copy(traefikContainer = v) } },
                                last = true,
                            )
                        }
                        "socket" -> Field(
                            label = "Traefik container",
                            value = form.traefikContainer,
                            onChange = { v -> viewModel.edit { it.copy(traefikContainer = v) } },
                            support = "The agent needs /var/run/docker.sock mounted.",
                            last = true,
                        )
                        "poison-pill" -> Field(
                            label = "Signal file",
                            value = form.signalFilePath,
                            onChange = { v -> viewModel.edit { it.copy(signalFilePath = v) } },
                            support = "A watcher container restarts Traefik when this file changes.",
                            last = true,
                        )
                    }
                }
            }

            item { SectionLabel("CrowdSec", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    Field(
                        label = "LAPI URL",
                        value = form.crowdsecLapiUrl,
                        onChange = { v -> viewModel.edit { it.copy(crowdsecLapiUrl = v) } },
                        support = "Used when generating this agent's compose file.",
                        keyboard = KeyboardType.Uri,
                    )
                    Field(
                        label = "Bouncer API key",
                        value = form.crowdsecApiKey,
                        onChange = { v -> viewModel.edit { it.copy(crowdsecApiKey = v) } },
                        support = if (state.crowdsecKeySet) {
                            "A key is stored. Leave this empty to keep it."
                        } else {
                            "Reads /v1/decisions."
                        },
                        secret = true,
                    )
                    Field(
                        "Machine id",
                        form.crowdsecMachineId,
                        { v -> viewModel.edit { it.copy(crowdsecMachineId = v) } },
                    )
                    Field(
                        label = "Machine password",
                        value = form.crowdsecMachinePassword,
                        onChange = { v -> viewModel.edit { it.copy(crowdsecMachinePassword = v) } },
                        support = if (state.crowdsecPasswordSet) {
                            "A password is stored. Leave this empty to keep it."
                        } else {
                            "Needed for alerts, which the bouncer key cannot read."
                        },
                        secret = true,
                    )
                    Field(
                        "Client certificate",
                        form.crowdsecClientCert,
                        { v -> viewModel.edit { it.copy(crowdsecClientCert = v) } },
                    )
                    Field(
                        "Client key",
                        form.crowdsecClientKey,
                        { v -> viewModel.edit { it.copy(crowdsecClientKey = v) } },
                    )
                    Field(
                        "CA certificate",
                        form.crowdsecCaCert,
                        { v -> viewModel.edit { it.copy(crowdsecCaCert = v) } },
                        last = true,
                    )
                }
            }

            item { SectionLabel("Tabs on this server", modifier = Modifier.padding(top = TmSpacing.sm)) }
            item {
                TmCard {
                    AGENT_TABS.forEachIndexed { index, (tab, label) ->
                        ToggleRow(
                            title = label,
                            subtitle = null,
                            checked = form.visibleTabs[tab] ?: true,
                            onChange = { visible -> viewModel.toggleTab(tab, visible) },
                        )
                        if (index < AGENT_TABS.lastIndex) CardDivider()
                    }
                    Text(
                        text = "Hiding a tab here hides it in the app and the web UI for this server.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    )
                }
            }

            item {
                Text(
                    text = "Git backup for this agent is configured in the web UI.",
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

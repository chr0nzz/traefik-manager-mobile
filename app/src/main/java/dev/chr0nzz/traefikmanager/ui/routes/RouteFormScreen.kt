package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.HeadersPresetDefaults
import dev.chr0nzz.traefikmanager.data.model.BackendKind
import dev.chr0nzz.traefikmanager.data.model.BackendMode
import dev.chr0nzz.traefikmanager.data.model.RouteForm
import dev.chr0nzz.traefikmanager.data.model.RouteProtocol
import dev.chr0nzz.traefikmanager.data.model.TcpTlsMode
import dev.chr0nzz.traefikmanager.ui.components.ConfigFileSheet
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.FilterChipRow
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.ui.components.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteFormScreen(
    routeId: String?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RouteFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(routeId) {
        if (routeId != null) viewModel.loadRoute(routeId)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(if (routeId == null) R.string.routes_add else R.string.routes_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving) {
                        Text(stringResource(if (state.form.isEdit) R.string.common_save else R.string.common_create))
                    }
                },
            )
        },
    ) { insets ->
        if (state.loading) {
            LoadingState(modifier = Modifier.padding(insets))
        } else {
            RouteFormBody(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(insets),
            )
        }
    }
}

@Composable
fun RouteFormBody(
    state: RouteFormUiState,
    viewModel: RouteFormViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val form = state.form
    var configSheetOpen by remember { mutableStateOf(false) }
    var lbExpanded by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var newDomain by remember { mutableStateOf("") }

    if (configSheetOpen) {
        ConfigFileSheet(
            files = state.configFiles,
            selected = form.configFile,
            canCreate = state.canCreateConfigFile,
            onSelect = { name -> viewModel.update { it.copy(configFile = name) } },
            onDismiss = { configSheetOpen = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
    ) {
        if (state.configFiles.isNotEmpty() || state.canCreateConfigFile) {
            TmCard(onClick = { configSheetOpen = true }) {
                SectionLabel(stringResource(R.string.common_label_config_file))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.xs),
                ) {
                    Text(
                        text = form.configFile.ifEmpty { stringResource(R.string.routes_form_select_file) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (form.configFile.isEmpty()) palette.muted else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.configFiles.size > 1) {
                            pluralStringResource(R.plurals.routes_form_files, state.configFiles.size, state.configFiles.size)
                        } else {
                            stringResource(R.string.routes_form_change_file)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                }
            }
        }

        TmCard {
            SectionLabel(stringResource(R.string.common_label_protocol))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.xs),
            ) {
                RouteProtocol.entries.forEachIndexed { index, protocol ->
                    SegmentedButton(
                        selected = form.protocol == protocol,
                        onClick = { viewModel.setProtocol(protocol) },
                        shape = SegmentedButtonDefaults.itemShape(index, RouteProtocol.entries.size),
                        enabled = !form.isEdit,
                    ) {
                        Text(protocol.wire.uppercase())
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text(stringResource(R.string.routes_form_name)) },
                placeholder = { Text("my-app") },
                supportingText = if (form.isEdit && form.name != form.originalName) {
                    { Text(stringResource(R.string.routes_form_rename_help)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.sm),
            )
        }

        if (form.protocol == RouteProtocol.Http) {
            TmCard {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    listOf(false, true).forEachIndexed { index, advanced ->
                        SegmentedButton(
                            selected = form.advancedRule == advanced,
                            onClick = { viewModel.setAdvancedRule(advanced) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            Text(stringResource(if (advanced) R.string.routes_form_rule_advanced else R.string.routes_form_rule_simple))
                        }
                    }
                }

                if (form.advancedRule) {
                    OutlinedTextField(
                        value = form.httpRule,
                        onValueChange = { value -> viewModel.update { it.copy(httpRule = value) } },
                        label = { Text(stringResource(R.string.common_label_rule)) },
                        placeholder = { Text("Host(`app.example.com`)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )
                } else {
                    OutlinedTextField(
                        value = form.subdomain,
                        onValueChange = { value -> viewModel.update { it.copy(subdomain = value) } },
                        label = { Text(stringResource(R.string.routes_form_subdomain)) },
                        supportingText = {
                            val sub = form.subdomain.trim()
                            val domain = form.domains.firstOrNull().orEmpty()
                            Text(
                                when {
                                    sub.contains('.') -> sub
                                    sub.isNotEmpty() && domain.isNotEmpty() -> "$sub.$domain"
                                    domain.isNotEmpty() -> domain
                                    else -> stringResource(R.string.routes_form_subdomain_help)
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = TmSpacing.sm),
                    )

                    SectionLabel(stringResource(R.string.common_label_domains), modifier = Modifier.padding(top = TmSpacing.md))
                    FilterChipRow(modifier = Modifier.padding(top = TmSpacing.xs)) {
                        state.domainOptions.forEach { domain ->
                            val selected = domain in form.domains
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.update { current ->
                                        current.copy(
                                            domains = if (selected) current.domains - domain else current.domains + domain,
                                        )
                                    }
                                },
                                label = { Text(domain) },
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    ) {
                        OutlinedTextField(
                            value = newDomain,
                            onValueChange = { newDomain = it },
                            label = { Text(stringResource(R.string.routes_form_add_domain)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val value = newDomain.trim()
                                if (value.isNotEmpty()) {
                                    viewModel.update { it.copy(domains = (it.domains + value).distinct()) }
                                    newDomain = ""
                                }
                            },
                            enabled = newDomain.isNotBlank(),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    }
                }
            }
        }

        if (form.protocol == RouteProtocol.Tcp) {
            TmCard {
                SectionLabel(stringResource(R.string.routes_form_sni_rule))
                OutlinedTextField(
                    value = form.tcpRule,
                    onValueChange = { value -> viewModel.update { it.copy(tcpRule = value) } },
                    placeholder = { Text("HostSNI(`*`)") },
                    supportingText = { Text(stringResource(R.string.routes_form_sni_help)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.xs),
                )
            }
        }

        if (form.protocol == RouteProtocol.Udp) {
            TmCard {
                Text(
                    text = stringResource(R.string.routes_form_udp_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        }

        BackendSection(
            state = state,
            viewModel = viewModel,
            lbExpanded = lbExpanded,
            onToggleLb = { lbExpanded = !lbExpanded },
        )

        EntryPointSection(state = state, viewModel = viewModel)

        MiddlewareSection(state = state, viewModel = viewModel)

        TlsSection(
            state = state,
            viewModel = viewModel,
            presetExpanded = presetExpanded,
            onTogglePreset = { presetExpanded = !presetExpanded },
        )

        if (state.error != null) {
            Text(
                text = state.error?.asString().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.red,
            )
        }

        Spacer(modifier = Modifier.padding(bottom = 72.dp))
    }
}

private const val ADDRESS_KIND = "IP:Port"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackendSection(
    state: RouteFormUiState,
    viewModel: RouteFormViewModel,
    lbExpanded: Boolean,
    onToggleLb: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val form = state.form
    val serviceKind = stringResource(R.string.common_label_service)

    TmCard {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            BackendMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = form.backendMode == mode,
                    onClick = { viewModel.setBackendMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, BackendMode.entries.size),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Text(stringResource(if (mode == BackendMode.Manual) R.string.routes_form_backends_build else R.string.routes_form_backends_service))
                }
            }
        }

        if (form.backendMode == BackendMode.ExistingService) {
            DropdownField(
                label = stringResource(R.string.common_label_service),
                value = form.serviceRef,
                options = state.serviceOptions,
                placeholder = stringResource(R.string.routes_form_select_service),
                onSelect = { value -> viewModel.update { it.copy(serviceRef = value) } },
                modifier = Modifier.padding(top = TmSpacing.sm),
            )
            return@TmCard
        }

        if (!form.isManagedService) {
            Text(
                text = if (form.serviceOwned) {
                    stringResource(R.string.routes_form_service_owned, form.serviceType)
                } else {
                    stringResource(R.string.routes_form_service_external, form.serviceType)
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.yellow,
                modifier = Modifier.padding(top = TmSpacing.sm),
            )
            return@TmCard
        }

        SectionLabel(stringResource(R.string.routes_form_backends), modifier = Modifier.padding(top = TmSpacing.sm))
        val composite = form.protocol == RouteProtocol.Http
        form.backends.forEachIndexed { index, backend ->
            Column(
                verticalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.sm),
            ) {
                if (composite) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        DropdownField(
                            label = stringResource(R.string.routes_form_backend_kind),
                            value = if (backend.isService) serviceKind else ADDRESS_KIND,
                            options = listOf(ADDRESS_KIND, serviceKind),
                            onSelect = { value ->
                                viewModel.updateBackend(index) {
                                    it.copy(
                                        kind = if (value == serviceKind) {
                                            BackendKind.SERVICE
                                        } else {
                                            BackendKind.ADDRESS
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (form.compositeType != RouteForm.LOAD_BALANCER) {
                            OutlinedTextField(
                                value = backend.share,
                                onValueChange = { value ->
                                    viewModel.updateBackend(index) { it.copy(share = value) }
                                },
                                label = {
                                    Text(stringResource(if (form.compositeType == "mirroring") R.string.routes_form_percent else R.string.routes_form_weight))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(104.dp),
                            )
                        }
                        if (form.backends.size > 1) {
                            IconButton(onClick = { viewModel.removeBackend(index) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.routes_form_remove_backend))
                            }
                        }
                    }
                }

                if (backend.isService) {
                    DropdownField(
                        label = stringResource(R.string.common_label_service),
                        value = backend.serviceName,
                        options = state.serviceOptions,
                        placeholder = stringResource(R.string.routes_form_select_service),
                        onSelect = { value ->
                            viewModel.updateBackend(index) { it.copy(serviceName = value) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (form.protocol == RouteProtocol.Http) {
                            DropdownField(
                                label = stringResource(R.string.common_label_scheme),
                                value = backend.scheme,
                                options = listOf("http", "https", "h2c"),
                                onSelect = { value ->
                                    viewModel.updateBackend(index) { it.copy(scheme = value) }
                                },
                                modifier = Modifier.width(112.dp),
                            )
                        }
                        OutlinedTextField(
                            value = backend.host,
                            onValueChange = { value ->
                                viewModel.updateBackend(index) { it.copy(host = value) }
                            },
                            label = { Text(stringResource(R.string.common_label_host)) },
                            placeholder = { Text("10.0.0.10") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = backend.port,
                            onValueChange = { value ->
                                viewModel.updateBackend(index) { it.copy(port = value) }
                            },
                            label = { Text(stringResource(R.string.common_label_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                        )
                        if (!composite && form.backends.size > 1) {
                            IconButton(onClick = { viewModel.removeBackend(index) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.routes_form_remove_backend))
                            }
                        }
                    }
                }
            }
        }
        if (form.backendsAtCap) {
            Text(
                text = stringResource(R.string.routes_form_failover_help),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(top = TmSpacing.xs),
            )
        }
        OutlinedButton(
            enabled = !form.backendsAtCap,
            onClick = viewModel::addBackend,
            modifier = Modifier.padding(top = TmSpacing.xs),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(stringResource(R.string.routes_form_add_backend), modifier = Modifier.padding(start = TmSpacing.xs))
        }

        if (composite && form.backends.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.sm),
            ) {
                val compositeTypes = RouteForm.compositeTypes.map { it.first to stringResource(it.second) }
                DropdownField(
                    label = stringResource(R.string.routes_form_combine),
                    value = compositeTypes.firstOrNull { it.first == form.compositeType }?.second
                        ?: form.compositeType,
                    options = compositeTypes.map { it.second },
                    onSelect = { label ->
                        val picked = compositeTypes.firstOrNull { it.second == label }?.first
                        if (picked != null) viewModel.setCompositeType(picked)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(RouteForm.combineHint(form.compositeType)),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
            )
            if (form.wasComposite && form.compositeType == RouteForm.LOAD_BALANCER) {
                Text(
                    text = stringResource(R.string.routes_form_composite_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.yellow,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.sm),
        ) {
            SectionLabel(stringResource(R.string.routes_form_load_balancing), modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleLb) {
                Icon(
                    imageVector = if (lbExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (lbExpanded) R.string.common_collapse else R.string.common_expand),
                )
            }
        }

        if (lbExpanded) {
            if (form.protocol == RouteProtocol.Http) {
                CheckboxRow(
                    label = stringResource(R.string.routes_form_sticky),
                    checked = form.sticky.enabled,
                    onCheckedChange = { checked -> viewModel.updateSticky { it.copy(enabled = checked) } },
                )
                if (form.sticky.enabled) {
                    OutlinedTextField(
                        value = form.sticky.cookieName,
                        onValueChange = { value -> viewModel.updateSticky { it.copy(cookieName = value) } },
                        label = { Text(stringResource(R.string.routes_form_cookie_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CheckboxRow(
                        label = stringResource(R.string.routes_form_cookie_secure),
                        checked = form.sticky.secure,
                        onCheckedChange = { checked -> viewModel.updateSticky { it.copy(secure = checked) } },
                    )
                    CheckboxRow(
                        label = stringResource(R.string.routes_form_cookie_http_only),
                        checked = form.sticky.httpOnly,
                        onCheckedChange = { checked -> viewModel.updateSticky { it.copy(httpOnly = checked) } },
                    )
                }

                CheckboxRow(
                    label = stringResource(R.string.routes_form_health_check),
                    checked = form.healthCheck.enabled,
                    onCheckedChange = { checked -> viewModel.updateHealthCheck { it.copy(enabled = checked) } },
                )
                if (form.healthCheck.enabled) {
                    OutlinedTextField(
                        value = form.healthCheck.path,
                        onValueChange = { value -> viewModel.updateHealthCheck { it.copy(path = value) } },
                        label = { Text(stringResource(R.string.common_label_path)) },
                        placeholder = { Text("/health") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm)) {
                        OutlinedTextField(
                            value = form.healthCheck.interval,
                            onValueChange = { value -> viewModel.updateHealthCheck { it.copy(interval = value) } },
                            label = { Text(stringResource(R.string.common_label_interval)) },
                            placeholder = { Text("30s") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.healthCheck.timeout,
                            onValueChange = { value -> viewModel.updateHealthCheck { it.copy(timeout = value) } },
                            label = { Text(stringResource(R.string.common_label_timeout)) },
                            placeholder = { Text("5s") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (form.protocol != RouteProtocol.Udp) {
                OutlinedTextField(
                    value = form.priority?.toString().orEmpty(),
                    onValueChange = { value ->
                        viewModel.update { it.copy(priority = value.toIntOrNull()) }
                    },
                    label = { Text(stringResource(R.string.routes_form_priority)) },
                    supportingText = { Text(stringResource(R.string.routes_form_priority_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EntryPointSection(state: RouteFormUiState, viewModel: RouteFormViewModel) {
    val form = state.form
    TmCard {
        SectionLabel(stringResource(R.string.common_label_entry_points))
        if (state.entryPointsUnavailable) {
            OutlinedTextField(
                value = form.entryPoints.joinToString(","),
                onValueChange = { value ->
                    viewModel.update {
                        it.copy(entryPoints = value.split(',').map(String::trim).filter(String::isNotEmpty))
                    }
                },
                label = { Text(stringResource(R.string.common_label_entry_points)) },
                supportingText = { Text(stringResource(R.string.routes_form_entry_points_help)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.xs),
            )
            return@TmCard
        }
        val options = (state.entryPointOptions + form.entryPoints).distinct()
        FilterChipRow(modifier = Modifier.padding(top = TmSpacing.xs)) {
            options.forEach { entryPoint ->
                val selected = entryPoint in form.entryPoints
                FilterChip(
                    selected = selected,
                    onClick = {
                        viewModel.update { current ->
                            val next = if (selected) {
                                current.entryPoints - entryPoint
                            } else if (current.protocol == RouteProtocol.Udp) {
                                listOf(entryPoint)
                            } else {
                                current.entryPoints + entryPoint
                            }
                            current.copy(entryPoints = next)
                        }
                    },
                    label = { Text(entryPoint) },
                )
            }
        }
    }
}

@Composable
private fun MiddlewareSection(state: RouteFormUiState, viewModel: RouteFormViewModel) {
    val form = state.form
    val palette = LocalTmPalette.current
    val options = state.protocolMiddlewares
    if (options.isEmpty() && form.middlewares.isEmpty()) return

    TmCard {
        SectionLabel(stringResource(R.string.common_label_middlewares))
        FilterChipRow(modifier = Modifier.padding(top = TmSpacing.xs)) {
            options.forEach { middleware ->
                val selected = form.middlewares.any { it.substringBefore('@') == middleware.name }
                FilterChip(
                    selected = selected,
                    onClick = {
                        viewModel.update { current ->
                            current.copy(
                                middlewares = if (selected) {
                                    current.middlewares.filterNot { it.substringBefore('@') == middleware.name }
                                } else {
                                    current.middlewares + middleware.name
                                },
                            )
                        }
                    },
                    label = {
                        val position = form.middlewares.indexOfFirst { it.substringBefore('@') == middleware.name }
                        Text(if (position >= 0) "${position + 1}. ${middleware.name}" else middleware.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun TlsSection(
    state: RouteFormUiState,
    viewModel: RouteFormViewModel,
    presetExpanded: Boolean,
    onTogglePreset: () -> Unit,
) {
    val form = state.form
    val palette = LocalTmPalette.current

    TmCard {
        SectionLabel(stringResource(R.string.routes_form_tls_title))

        if (form.protocol == RouteProtocol.Tcp) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = TmSpacing.xs),
            ) {
                TcpTlsMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = form.tcpTlsMode == mode,
                        onClick = { viewModel.update { it.copy(tcpTlsMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, TcpTlsMode.entries.size),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Text(
                            when (mode) {
                                TcpTlsMode.None -> stringResource(R.string.routes_form_tls_none)
                                TcpTlsMode.Terminate -> "TLS"
                                TcpTlsMode.Passthrough -> stringResource(R.string.routes_form_tls_passthrough)
                            },
                        )
                    }
                }
            }
        }

        if (form.protocol == RouteProtocol.Http) {
            CheckboxRow(
                label = stringResource(R.string.routes_form_tls_enable),
                checked = form.tlsEnabled,
                onCheckedChange = { checked -> viewModel.update { it.copy(tlsEnabled = checked) } },
            )
        }

        val tlsActive = when (form.protocol) {
            RouteProtocol.Http -> form.tlsEnabled
            RouteProtocol.Tcp -> form.tcpTlsMode == TcpTlsMode.Terminate
            RouteProtocol.Udp -> false
        }

        if (tlsActive) {
            DropdownField(
                label = stringResource(R.string.common_label_cert_resolver),
                value = form.certResolver,
                options = state.certResolverOptions,
                placeholder = stringResource(R.string.routes_form_resolver_none),
                allowEmpty = true,
                onSelect = { value -> viewModel.update { it.copy(certResolver = value) } },
                modifier = Modifier.padding(top = TmSpacing.sm),
            )
        }

        if (form.protocol == RouteProtocol.Http) {
            DropdownField(
                label = stringResource(R.string.routes_form_tls_options),
                value = form.tlsOptionsProfile,
                options = state.tlsProfiles.map { it.name },
                placeholder = stringResource(R.string.routes_form_tls_options_none),
                allowEmpty = true,
                onSelect = { value -> viewModel.update { it.copy(tlsOptionsProfile = value) } },
                modifier = Modifier.padding(top = TmSpacing.sm),
            )

            CheckboxRow(
                label = stringResource(R.string.common_label_pass_host_header),
                checked = form.effectivePassHostHeader,
                enabled = !form.streamingEnabled,
                onCheckedChange = { checked -> viewModel.update { it.copy(passHostHeader = checked) } },
            )
            CheckboxRow(
                label = stringResource(R.string.routes_form_skip_verify),
                checked = form.insecureSkipVerify,
                onCheckedChange = { checked -> viewModel.update { it.copy(insecureSkipVerify = checked) } },
            )
            CheckboxRow(
                label = stringResource(R.string.routes_form_streaming),
                checked = form.streamingEnabled,
                onCheckedChange = { checked ->
                    viewModel.update { it.copy(streamingPresent = true, streamingEnabled = checked) }
                },
            )
            CheckboxRow(
                label = stringResource(R.string.routes_form_wildcard),
                checked = form.wildcardEnabled,
                onCheckedChange = { checked ->
                    viewModel.update { current ->
                        current.copy(
                            wildcardEnabled = checked,
                            tlsMainDomain = if (checked && current.tlsMainDomain.isEmpty()) {
                                current.domains.firstOrNull().orEmpty()
                            } else {
                                current.tlsMainDomain
                            },
                            tlsSans = if (checked && current.tlsSans.isEmpty()) {
                                listOfNotNull(current.domains.firstOrNull()?.let { "*.$it" })
                            } else {
                                current.tlsSans
                            },
                        )
                    }
                },
            )
            if (form.wildcardEnabled) {
                OutlinedTextField(
                    value = form.tlsMainDomain,
                    onValueChange = { value -> viewModel.update { it.copy(tlsMainDomain = value) } },
                    label = { Text(stringResource(R.string.routes_form_cert_domain)) },
                    supportingText = { Text(stringResource(R.string.routes_form_cert_domain_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.tlsSans.joinToString("\n"),
                    onValueChange = { value ->
                        viewModel.update {
                            it.copy(tlsSans = value.split('\n').map(String::trim).filter(String::isNotEmpty))
                        }
                    },
                    label = { Text(stringResource(R.string.routes_form_extra_domains)) },
                    supportingText = { Text(stringResource(R.string.routes_form_extra_domains_help)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = form.headersPreset.enabled,
                    onCheckedChange = viewModel::setPresetEnabled,
                )
                Text(
                    text = stringResource(R.string.routes_form_preset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (form.headersPreset.enabled) {
                    IconButton(onClick = onTogglePreset) {
                        Icon(
                            imageVector = if (presetExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(
                                if (presetExpanded) R.string.routes_form_preset_collapse else R.string.routes_form_preset_expand,
                            ),
                        )
                    }
                }
            }

            if (form.headersPreset.custom) {
                Text(
                    text = stringResource(R.string.routes_form_preset_custom),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.yellow,
                )
            }

            if (form.headersPreset.enabled && presetExpanded) {
                CheckboxRow(
                    label = "HSTS",
                    checked = form.headersPreset.hsts,
                    onCheckedChange = { checked -> viewModel.updatePreset { it.copy(hsts = checked) } },
                )
                CheckboxRow(
                    label = stringResource(R.string.routes_form_preset_nosniff),
                    checked = form.headersPreset.nosniff,
                    onCheckedChange = { checked -> viewModel.updatePreset { it.copy(nosniff = checked) } },
                )
                CheckboxRow(
                    label = stringResource(R.string.routes_form_preset_frame_deny),
                    checked = form.headersPreset.frameDeny,
                    onCheckedChange = { checked -> viewModel.updatePreset { it.copy(frameDeny = checked) } },
                )
                DropdownField(
                    label = stringResource(R.string.routes_form_preset_referrer),
                    value = form.headersPreset.referrer,
                    options = HeadersPresetDefaults.REFERRER_VALUES,
                    onSelect = { value -> viewModel.updatePreset { it.copy(referrer = value) } },
                    modifier = Modifier.padding(top = TmSpacing.xs),
                )
                SectionLabel(stringResource(R.string.routes_form_preset_permissions), modifier = Modifier.padding(top = TmSpacing.sm))
                HeadersPresetDefaults.FEATURES.forEach { feature ->
                    DropdownField(
                        label = feature,
                        value = form.headersPreset.perms[feature] ?: "block",
                        options = HeadersPresetDefaults.PERM_VALUES,
                        onSelect = { value ->
                            viewModel.updatePreset { preset ->
                                preset.copy(perms = preset.perms + (feature to value))
                            }
                        },
                        modifier = Modifier.padding(top = TmSpacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    allowEmpty: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value.ifEmpty { placeholder },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowEmpty) {
                DropdownMenuItem(
                    text = { Text(placeholder.ifEmpty { stringResource(R.string.common_none_option) }) },
                    onClick = {
                        onSelect("")
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

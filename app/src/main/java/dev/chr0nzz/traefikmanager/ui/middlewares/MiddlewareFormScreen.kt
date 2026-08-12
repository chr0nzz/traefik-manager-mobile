package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chr0nzz.traefikmanager.data.model.MiddlewareProtocol
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
import dev.chr0nzz.traefikmanager.data.model.WizardField
import dev.chr0nzz.traefikmanager.ui.components.ConfigFileSheet
import dev.chr0nzz.traefikmanager.ui.components.LoadingState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.YamlEditor
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiddlewareFormScreen(
    middlewareName: String?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: MiddlewareFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(middlewareName) {
        if (middlewareName != null) viewModel.load(middlewareName)
    }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved(state.form.name)
    }

    val waiting = state.loading || (middlewareName != null && !state.form.isEdit && state.error == null)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                title = { Text(if (middlewareName == null) "Add middleware" else "Edit middleware") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving && !waiting) {
                        Text(if (state.form.isEdit) "Save" else "Create")
                    }
                },
            )
        },
    ) { insets ->
        if (waiting) {
            LoadingState(modifier = Modifier.padding(insets))
        } else {
            MiddlewareFormBody(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(insets),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiddlewareFormBody(
    state: MiddlewareFormUiState,
    viewModel: MiddlewareFormViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val form = state.form
    var configSheetOpen by remember { mutableStateOf(false) }
    var templateSheetOpen by remember { mutableStateOf(false) }

    if (configSheetOpen) {
        ConfigFileSheet(
            files = state.configFiles,
            selected = form.configFile,
            canCreate = state.canCreateConfigFile,
            onSelect = { name -> viewModel.update { it.copy(configFile = name) } },
            onDismiss = { configSheetOpen = false },
        )
    }

    if (templateSheetOpen) {
        TemplatePickerSheet(
            state = state,
            onSelect = { id ->
                viewModel.selectTemplate(id)
                templateSheetOpen = false
            },
            onDismiss = { templateSheetOpen = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TmSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
    ) {
        if (state.showConfigFile) {
            TmCard(onClick = { configSheetOpen = true }) {
                SectionLabel("Config file")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.xs),
                ) {
                    Text(
                        text = form.configFile.ifEmpty { "Select a file" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (form.configFile.isEmpty()) palette.muted else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (state.configFiles.size > 1) "${state.configFiles.size} files" else "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                }
            }
        }

        TmCard {
            SectionLabel("Protocol")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.xs),
            ) {
                MiddlewareProtocol.entries.forEachIndexed { index, protocol ->
                    SegmentedButton(
                        selected = form.protocol == protocol,
                        onClick = { viewModel.setProtocol(protocol) },
                        shape = SegmentedButtonDefaults.itemShape(index, MiddlewareProtocol.entries.size),
                    ) {
                        Text(protocol.wire.uppercase())
                    }
                }
            }
            if (state.isTcp) {
                Text(
                    text = "TCP middlewares support only ipAllowList and inFlightConn, written as YAML.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.padding(top = TmSpacing.xs),
                )
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> viewModel.update { it.copy(name = value) } },
                label = { Text("Name") },
                placeholder = { Text("my-auth") },
                supportingText = if (form.isEdit && form.name != form.originalName) {
                    { Text("Renaming updates every route that references it") }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.sm),
            )
        }

        if (!state.isTcp) {
            TmCard(onClick = { templateSheetOpen = true }) {
                SectionLabel("Template")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = TmSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Style,
                        contentDescription = null,
                        tint = palette.blue,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = state.templateLabel.ifEmpty { "Custom" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.templateLabel.isEmpty()) palette.muted else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MiddlewareMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, MiddlewareMode.entries.size),
                        enabled = mode == MiddlewareMode.Yaml || state.wizard != null,
                    ) {
                        Text(if (mode == MiddlewareMode.Wizard) "Wizard" else "YAML")
                    }
                }
            }
        }

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                color = palette.red,
            )
        }

        if (state.mode == MiddlewareMode.Wizard && !state.isTcp) {
            val wizard = state.wizard
            if (wizard == null) {
                TmCard {
                    Text(
                        text = "Select a template above to use the wizard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TmSpacing.lg),
                    )
                }
            } else {
                TmCard {
                    SectionLabel(wizard.label)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                        modifier = Modifier.padding(top = TmSpacing.sm),
                    ) {
                        wizard.fields.forEach { field ->
                            WizardFieldEditor(
                                field = field,
                                state = state,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
                if (state.templateId == "basicAuth" || state.templateId == "digestAuth") {
                    HashGeneratorCard(
                        digest = state.templateId == "digestAuth",
                        state = state,
                        viewModel = viewModel,
                    )
                }
            }
        } else {
            SectionLabel("Configuration (YAML)")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                YamlEditor(state = viewModel.content, modifier = Modifier.fillMaxSize())
            }
            Text(
                text = "The middleware body only, without the name above it.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun WizardFieldEditor(
    field: WizardField,
    state: MiddlewareFormUiState,
    viewModel: MiddlewareFormViewModel,
) {
    when (field) {
        is WizardField.Text -> OutlinedTextField(
            value = state.wizardText[field.key].orEmpty(),
            onValueChange = { viewModel.setWizardText(field.key, it) },
            label = { Text(field.label) },
            placeholder = if (field.placeholder.isNotEmpty()) {
                { Text(field.placeholder, maxLines = 1) }
            } else {
                null
            },
            supportingText = if (field.help.isNotEmpty()) {
                { Text(field.help) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Next,
                showKeyboardOnFocus = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        is WizardField.Lines -> OutlinedTextField(
            value = state.wizardText[field.key].orEmpty(),
            onValueChange = { viewModel.setWizardText(field.key, it) },
            label = { Text(field.label) },
            placeholder = if (field.placeholder.isNotEmpty()) {
                { Text(field.placeholder) }
            } else {
                null
            },
            supportingText = { Text(field.help.ifEmpty { "One per line" }) },
            minLines = 3,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            modifier = Modifier.fillMaxWidth(),
        )

        is WizardField.Toggle -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.setWizardToggle(field.key, !(state.wizardToggles[field.key] ?: field.default))
                },
        ) {
            Checkbox(
                checked = state.wizardToggles[field.key] ?: field.default,
                onCheckedChange = { viewModel.setWizardToggle(field.key, it) },
            )
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is WizardField.Choice -> {
            val current = state.wizardText[field.key] ?: field.default
            ChoiceField(
                label = field.label,
                options = field.options,
                selected = current,
                onSelect = { viewModel.setWizardText(field.key, it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HashGeneratorCard(
    digest: Boolean,
    state: MiddlewareFormUiState,
    viewModel: MiddlewareFormViewModel,
) {
    val palette = LocalTmPalette.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var realm by remember { mutableStateOf("traefik") }

    TmCard {
        SectionLabel("Generate entry")
        Text(
            text = if (digest) {
                "The server hashes the password and appends the user:realm:md5 line above."
            } else {
                "The server hashes the password and appends the user:hash line above."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(top = TmSpacing.xs, bottom = TmSpacing.sm),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                showKeyboardOnFocus = false,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.sm),
        )
        if (digest) {
            OutlinedTextField(
                value = realm,
                onValueChange = { realm = it },
                label = { Text("Realm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TmSpacing.sm),
            )
        }
        if (state.generatorError != null) {
            Text(
                text = state.generatorError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = palette.red,
                modifier = Modifier.padding(top = TmSpacing.xs),
            )
        }
        OutlinedButton(
            onClick = {
                if (digest) {
                    viewModel.generateDigestEntry(username.trim(), password, realm.trim())
                } else {
                    viewModel.generateBasicAuthEntry(username.trim(), password)
                }
                password = ""
            },
            enabled = !state.generating && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.padding(top = TmSpacing.sm),
        ) {
            Text(if (state.generating) "Generating" else "Generate entry")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePickerSheet(
    state: MiddlewareFormUiState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalTmPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Text(
            text = "Template",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = TmSpacing.lg, end = TmSpacing.lg, bottom = TmSpacing.sm),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("Custom") },
                    supportingContent = { Text("Write the YAML yourself") },
                    trailingContent = if (state.templateId.isEmpty()) {
                        { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onSelect("") },
                )
                HorizontalDivider()
            }
            MiddlewareTemplates.categories.forEach { category ->
                val wizards = MiddlewareTemplates.all.filter { it.category == category }
                if (wizards.isEmpty()) return@forEach
                item(key = "cat-$category") {
                    SectionLabel(
                        text = category,
                        modifier = Modifier.padding(
                            start = TmSpacing.lg,
                            end = TmSpacing.lg,
                            top = TmSpacing.md,
                            bottom = TmSpacing.xs,
                        ),
                    )
                }
                items(wizards.size, key = { index -> wizards[index].id }) { index ->
                    val wizard = wizards[index]
                    ListItem(
                        headlineContent = { Text(wizard.label) },
                        trailingContent = if (state.templateId == wizard.id) {
                            { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onSelect(wizard.id) },
                    )
                }
            }
            if (state.customTemplates.isNotEmpty()) {
                item(key = "cat-custom") {
                    SectionLabel(
                        text = "My templates",
                        modifier = Modifier.padding(
                            start = TmSpacing.lg,
                            end = TmSpacing.lg,
                            top = TmSpacing.md,
                            bottom = TmSpacing.xs,
                        ),
                    )
                }
                items(state.customTemplates.size, key = { index -> state.customTemplates[index].id }) { index ->
                    val template = state.customTemplates[index]
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = {
                            Text(
                                text = template.yaml.lineSequence().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                color = palette.muted,
                            )
                        },
                        trailingContent = if (state.templateId == "custom:${template.id}") {
                            { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onSelect("custom:${template.id}") },
                    )
                }
            }
            item { Box(modifier = Modifier.height(TmSpacing.xl)) }
        }
    }
}

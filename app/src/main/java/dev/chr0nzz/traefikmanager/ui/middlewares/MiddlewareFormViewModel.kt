package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.ConfigFile
import dev.chr0nzz.traefikmanager.data.model.MiddlewareForm
import dev.chr0nzz.traefikmanager.data.model.MiddlewareProtocol
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplate
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
import dev.chr0nzz.traefikmanager.data.model.MiddlewareWizard
import dev.chr0nzz.traefikmanager.data.model.WizardField
import dev.chr0nzz.traefikmanager.data.model.WizardValues
import dev.chr0nzz.traefikmanager.data.repo.MiddlewaresRepository
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MiddlewareMode { Wizard, Yaml }

data class MiddlewareFormUiState(
    val form: MiddlewareForm = MiddlewareForm(),
    val loading: Boolean = false,
    val mode: MiddlewareMode = MiddlewareMode.Yaml,
    val templateId: String = "",
    val templateLabel: String = "",
    val wizardText: Map<String, String> = emptyMap(),
    val wizardToggles: Map<String, Boolean> = emptyMap(),
    val customTemplates: List<MiddlewareTemplate> = emptyList(),
    val configFiles: List<ConfigFile> = emptyList(),
    val canCreateConfigFile: Boolean = false,
    val generating: Boolean = false,
    val generatorError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val wizard: MiddlewareWizard? get() = MiddlewareTemplates.byId(templateId)

    val isTcp: Boolean get() = form.protocol == MiddlewareProtocol.Tcp

    val showConfigFile: Boolean get() = configFiles.isNotEmpty() || canCreateConfigFile
}

@HiltViewModel
class MiddlewareFormViewModel @Inject constructor(
    private val middlewaresRepository: MiddlewaresRepository,
    private val routesRepository: RoutesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MiddlewareFormUiState())
    val state: StateFlow<MiddlewareFormUiState> = _state.asStateFlow()

    val content = TextFieldState()

    private var editName: String? = null

    init {
        viewModelScope.launch {
            val configs = async { runCatching { routesRepository.configs() }.getOrNull() }
            val templates = async { middlewaresRepository.templates() }
            val loaded = configs.await()
            val custom = templates.await()
            _state.update { current ->
                current.copy(
                    configFiles = loaded?.files.orEmpty(),
                    canCreateConfigFile = loaded?.configDirSet ?: false,
                    customTemplates = custom,
                )
            }
        }
    }

    fun load(name: String) {
        if (editName == name) return
        editName = name
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val existing = runCatching { routesRepository.load() }
                .getOrNull()
                ?.middlewares
                ?.firstOrNull { it.name == name }
            if (existing == null) {
                _state.update { it.copy(loading = false, error = "Could not load $name") }
                return@launch
            }
            val protocol = MiddlewareProtocol.from(existing.type.ifEmpty { "http" })
            content.setTextAndPlaceCursorAtEnd(existing.yaml.trimEnd())
            _state.update { current ->
                current.copy(
                    loading = false,
                    mode = MiddlewareMode.Yaml,
                    templateId = "",
                    templateLabel = "",
                    form = MiddlewareForm(
                        name = existing.name,
                        protocol = protocol,
                        yaml = existing.yaml,
                        configFile = existing.configFile,
                        isEdit = true,
                        originalName = existing.name,
                        originalProtocol = protocol,
                    ),
                )
            }
        }
    }

    fun update(transform: (MiddlewareForm) -> MiddlewareForm) {
        _state.update { it.copy(form = transform(it.form), error = null) }
    }

    fun setProtocol(protocol: MiddlewareProtocol) {
        _state.update { current ->
            if (protocol == MiddlewareProtocol.Tcp) {
                current.copy(
                    form = current.form.copy(protocol = protocol),
                    mode = MiddlewareMode.Yaml,
                    templateId = "",
                    templateLabel = "",
                    error = null,
                )
            } else {
                current.copy(form = current.form.copy(protocol = protocol), error = null)
            }
        }
    }

    fun setMode(mode: MiddlewareMode) {
        if (mode == MiddlewareMode.Yaml) regenerate()
        _state.update { it.copy(mode = mode) }
    }

    fun selectTemplate(id: String) {
        if (id.isEmpty()) {
            _state.update { it.copy(templateId = "", templateLabel = "", mode = MiddlewareMode.Yaml) }
            return
        }
        val custom = _state.value.customTemplates.firstOrNull { "custom:${it.id}" == id }
        if (custom != null) {
            content.setTextAndPlaceCursorAtEnd(custom.yaml)
            _state.update {
                it.copy(
                    templateId = id,
                    templateLabel = custom.name,
                    mode = MiddlewareMode.Yaml,
                    error = null,
                )
            }
            return
        }
        val wizard = MiddlewareTemplates.byId(id) ?: return
        val text = wizard.fields.mapNotNull { field ->
            when (field) {
                is WizardField.Text -> field.key to field.default
                is WizardField.Lines -> field.key to field.default
                is WizardField.Choice -> field.key to field.default
                is WizardField.Toggle -> null
            }
        }.toMap()
        val toggles = wizard.fields.filterIsInstance<WizardField.Toggle>().associate { it.key to it.default }
        val yaml = wizard.build(WizardValues(text, toggles, wizard.fields))
        content.setTextAndPlaceCursorAtEnd(yaml)
        _state.update {
            it.copy(
                templateId = id,
                templateLabel = wizard.label,
                mode = MiddlewareMode.Wizard,
                wizardText = text,
                wizardToggles = toggles,
                error = null,
            )
        }
    }

    fun setWizardText(key: String, value: String) {
        _state.update { it.copy(wizardText = it.wizardText + (key to value), error = null) }
        regenerate()
    }

    fun setWizardToggle(key: String, value: Boolean) {
        _state.update { it.copy(wizardToggles = it.wizardToggles + (key to value), error = null) }
        regenerate()
    }

    fun generateBasicAuthEntry(username: String, password: String) =
        generateEntry { middlewaresRepository.htpasswdEntry(username, password) }

    fun generateDigestEntry(username: String, password: String, realm: String) =
        generateEntry { middlewaresRepository.digestEntry(username, password, realm) }

    fun consumeGeneratorError() = _state.update { it.copy(generatorError = null) }

    private fun generateEntry(block: suspend () -> String) {
        _state.update { it.copy(generating = true, generatorError = null) }
        viewModelScope.launch {
            runCatching { block() }.fold(
                onSuccess = { entry ->
                    _state.update { current ->
                        val existing = current.wizardText["users"].orEmpty().trimEnd()
                        val next = if (existing.isBlank()) entry else "$existing\n$entry"
                        current.copy(
                            generating = false,
                            wizardText = current.wizardText + ("users" to next),
                        )
                    }
                    regenerate()
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            generating = false,
                            generatorError = throwable.message ?: "Could not generate the entry",
                        )
                    }
                },
            )
        }
    }

    private fun regenerate() {
        val current = _state.value
        if (current.isTcp) return
        val wizard = current.wizard ?: return
        val values = WizardValues(current.wizardText, current.wizardToggles, wizard.fields)
        content.setTextAndPlaceCursorAtEnd(wizard.build(values))
    }

    fun save() {
        if (_state.value.mode == MiddlewareMode.Wizard) regenerate()
        val form = _state.value.form.copy(yaml = content.text.toString())
        val problem = validate(form)
        if (problem != null) {
            _state.update { it.copy(error = problem, form = form) }
            return
        }
        _state.update { it.copy(saving = true, error = null, form = form) }
        viewModelScope.launch {
            runCatching { middlewaresRepository.save(form) }.fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, error = throwable.message ?: "Could not save the middleware")
                    }
                },
            )
        }
    }

    private fun validate(form: MiddlewareForm): String? {
        val current = _state.value
        if (current.showConfigFile && form.configFile.isBlank()) {
            return "Select a config file for this middleware"
        }
        if (current.mode == MiddlewareMode.Wizard) {
            val wizardProblem = when (current.templateId) {
                "basicAuth", "digestAuth" ->
                    "Add at least one user before saving".takeIf { current.wizardText["users"].isNullOrBlank() }
                "forwardAuth", "forwardAuthAuthentik", "forwardAuthAuthelia" ->
                    "Forward auth address is required".takeIf { current.wizardText["address"].isNullOrBlank() }
                "forwardAuthGatekeeper" ->
                    "Gatekeeper URL is required".takeIf { current.wizardText["url"].isNullOrBlank() }
                else -> null
            }
            if (wizardProblem != null) return wizardProblem
        }
        return form.validationError
    }
}

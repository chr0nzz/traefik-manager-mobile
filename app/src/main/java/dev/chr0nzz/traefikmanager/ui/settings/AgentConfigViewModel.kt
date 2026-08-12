package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.AgentConfig
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

/** Everything the agent editor can change, as strings the form can bind to. */
data class AgentForm(
    val name: String = "",
    val url: String = "",
    val traefikApiUrl: String = "",
    val certResolver: String = "",
    val domains: String = "",
    val insecureSkipVerify: Boolean = false,
    val configPath: String = "",
    val staticConfigPath: String = "",
    val backupDir: String = "",
    val backupKeepCount: String = "",
    val acmeJsonPath: String = "",
    val accessLogPath: String = "",
    val pluginsDir: String = "",
    val restartMethod: String = "",
    val traefikContainer: String = "",
    val dockerHost: String = "",
    val signalFilePath: String = "",
    val crowdsecLapiUrl: String = "",
    val crowdsecApiKey: String = "",
    val crowdsecMachineId: String = "",
    val crowdsecMachinePassword: String = "",
    val crowdsecClientCert: String = "",
    val crowdsecClientKey: String = "",
    val crowdsecCaCert: String = "",
    val tmaPort: String = "",
    val tmaRateLimit: String = "",
    val visibleTabs: Map<String, Boolean> = emptyMap(),
)

data class AgentConfigUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val agentName: String = "",
    val form: AgentForm = AgentForm(),
    val crowdsecKeySet: Boolean = false,
    val crowdsecPasswordSet: Boolean = false,
    val dirty: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AgentConfigViewModel @Inject constructor(
    private val serversRepository: ServersRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val agentId: String = savedStateHandle.get<String>("agentId").orEmpty()

    private val _state = MutableStateFlow(AgentConfigUiState())
    val state: StateFlow<AgentConfigUiState> = _state.asStateFlow()

    private var original = AgentForm()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { serversRepository.config(agentId) }.fold(
                onSuccess = { config ->
                    if (config == null) {
                        _state.update { it.copy(loading = false, error = "That agent is no longer registered") }
                        return@fold
                    }
                    original = config.toForm()
                    _state.update {
                        it.copy(
                            loading = false,
                            agentName = config.name,
                            form = original,
                            // The server sends "***" for a stored secret and "" for none.
                            crowdsecKeySet = config.crowdsecApiKeySet,
                            crowdsecPasswordSet = config.crowdsecMachinePasswordSet,
                            dirty = false,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(loading = false, error = throwable.message ?: "Could not read the agent")
                    }
                },
            )
        }
    }

    fun edit(block: (AgentForm) -> AgentForm) {
        _state.update { current ->
            val next = block(current.form)
            current.copy(form = next, dirty = next != original, saved = false)
        }
    }

    fun toggleTab(tab: String, visible: Boolean) = edit { form ->
        form.copy(visibleTabs = form.visibleTabs + (tab to visible))
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Sends only what changed, so a field this screen does not know about is never overwritten. */
    fun save() {
        val form = _state.value.form
        if (form.name.isBlank() || form.url.isBlank()) {
            _state.update { it.copy(message = "Name and URL are required") }
            return
        }
        val changes = buildMap<String, JsonElement> {
            fun text(key: String, value: String, was: String) {
                if (value.trim() != was.trim()) put(key, JsonPrimitive(value.trim()))
            }
            text("name", form.name, original.name)
            text("url", form.url, original.url)
            text("traefik_api_url", form.traefikApiUrl, original.traefikApiUrl)
            text("cert_resolver", form.certResolver, original.certResolver)
            text("config_path", form.configPath, original.configPath)
            text("static_config_path", form.staticConfigPath, original.staticConfigPath)
            text("backup_dir", form.backupDir, original.backupDir)
            text("backup_keep_count", form.backupKeepCount, original.backupKeepCount)
            text("acme_json_path", form.acmeJsonPath, original.acmeJsonPath)
            text("access_log_path", form.accessLogPath, original.accessLogPath)
            text("plugins_dir", form.pluginsDir, original.pluginsDir)
            text("restart_method", form.restartMethod, original.restartMethod)
            text("traefik_container", form.traefikContainer, original.traefikContainer)
            text("docker_host", form.dockerHost, original.dockerHost)
            text("signal_file_path", form.signalFilePath, original.signalFilePath)
            text("crowdsec_lapi_url", form.crowdsecLapiUrl, original.crowdsecLapiUrl)
            text("crowdsec_machine_id", form.crowdsecMachineId, original.crowdsecMachineId)
            text("crowdsec_client_cert", form.crowdsecClientCert, original.crowdsecClientCert)
            text("crowdsec_client_key", form.crowdsecClientKey, original.crowdsecClientKey)
            text("crowdsec_ca_cert", form.crowdsecCaCert, original.crowdsecCaCert)
            text("tma_port", form.tmaPort, original.tmaPort)
            text("tma_rate_limit", form.tmaRateLimit, original.tmaRateLimit)

            if (form.insecureSkipVerify != original.insecureSkipVerify) {
                put("traefik_insecure_skip_verify", JsonPrimitive(form.insecureSkipVerify))
            }
            if (form.domains.trim() != original.domains.trim()) {
                put(
                    "domains",
                    buildJsonArray {
                        form.domains.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                    },
                )
            }
            if (form.visibleTabs != original.visibleTabs) {
                put(
                    "visible_tabs",
                    buildJsonObject { form.visibleTabs.forEach { (tab, visible) -> put(tab, visible) } },
                )
            }
            // Secrets are write-only: an empty box means "keep what is stored".
            if (form.crowdsecApiKey.isNotBlank()) put("crowdsec_api_key", JsonPrimitive(form.crowdsecApiKey))
            if (form.crowdsecMachinePassword.isNotBlank()) {
                put("crowdsec_machine_password", JsonPrimitive(form.crowdsecMachinePassword))
            }
        }

        if (changes.isEmpty()) {
            _state.update { it.copy(message = "Nothing to save") }
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { serversRepository.update(agentId, changes) }.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, saved = true, message = "Agent saved") }
                    load()
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, message = throwable.message ?: "Could not save the agent")
                    }
                },
            )
        }
    }
}

private fun AgentConfig.toForm() = AgentForm(
    name = name,
    url = url,
    traefikApiUrl = traefikApiUrl,
    certResolver = certResolver,
    domains = domains.joinToString(", "),
    insecureSkipVerify = traefikInsecureSkipVerify,
    configPath = configPath,
    staticConfigPath = staticConfigPath,
    backupDir = backupDir,
    backupKeepCount = backupKeepCount,
    acmeJsonPath = acmeJsonPath,
    accessLogPath = accessLogPath,
    pluginsDir = pluginsDir,
    restartMethod = restartMethod,
    traefikContainer = traefikContainer,
    dockerHost = dockerHost,
    signalFilePath = signalFilePath,
    crowdsecLapiUrl = crowdsecLapiUrl,
    crowdsecMachineId = crowdsecMachineId,
    crowdsecClientCert = crowdsecClientCert,
    crowdsecClientKey = crowdsecClientKey,
    crowdsecCaCert = crowdsecCaCert,
    tmaPort = tmaPort,
    tmaRateLimit = tmaRateLimit,
    visibleTabs = visibleTabs,
)

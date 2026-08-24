package dev.chr0nzz.traefikmanager.ui.plugins

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.PluginEntry
import dev.chr0nzz.traefikmanager.data.model.PluginUsage
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.PluginsRepository
import dev.chr0nzz.traefikmanager.data.repo.StaticConfigRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val plugins: List<PluginEntry> = emptyList(),
    val usage: Map<String, Int> = emptyMap(),
    val middlewares: List<MiddlewareDef> = emptyList(),
    val query: String = "",
    val serverError: String? = null,
    val loadError: String? = null,
    /** False when this server has no static config, which is where plugins are declared. */
    val canManage: Boolean = false,
    val installing: PluginInstall? = null,
    val editing: PluginEdit? = null,
    val pendingDelete: PluginEntry? = null,
    val formError: String? = null,
    val busy: Boolean = false,
    val restartPending: Boolean = false,
    val restartDetail: String = "",
    val restarting: Boolean = false,
    val message: String? = null,
) {
    val visible: List<PluginEntry>
        get() {
            val needle = query.trim().lowercase()
            if (needle.isEmpty()) return plugins
            return plugins.filter {
                it.name.lowercase().contains(needle) || it.moduleName.lowercase().contains(needle)
            }
        }

    fun usageFor(name: String): Int = usage[name] ?: 0

    fun usersOf(name: String): List<MiddlewareDef> = PluginUsage.usersOf(name, middlewares)
}

/** The paste-two-snippets install, which is how the web does it. */
data class PluginInstall(
    val staticYaml: String = DEFAULT_STATIC_SNIPPET,
    val middlewareYaml: String = DEFAULT_MIDDLEWARE_SNIPPET,
    val middlewareFile: String = "",
)

data class PluginEdit(
    val oldName: String,
    val name: String,
    val moduleName: String,
    val version: String,
)

const val DEFAULT_STATIC_SNIPPET = """experimental:
  plugins:
    myPlugin:
      moduleName: github.com/author/plugin
      version: v0.1.0"""

const val DEFAULT_MIDDLEWARE_SNIPPET = """http:
  middlewares:
    my-myPlugin:
      plugin:
        myPlugin:
          setting: value"""

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val repository: PluginsRepository,
    private val staticConfig: StaticConfigRepository,
    private val serverScope: ServerScope,
) : ViewModel() {

    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    init {
        load(initial = true)
        watchServerChanges()
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.plugins.isEmpty(), refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            val manageable = staticConfig.manageable()
            runCatching { repository.load() }.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            canManage = manageable,
                            plugins = snapshot.plugins,
                            usage = snapshot.usage,
                            middlewares = snapshot.middlewares,
                            serverError = snapshot.error,
                            loadError = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            loadError = throwable.message ?: "Could not load plugin data",
                        )
                    }
                },
            )
        }
    }

    /** A different server means different data: drop what is on screen and refetch. */
    private fun watchServerChanges() {
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                _state.value = PluginsUiState()
                load(initial = true)
            }
        }
    }

    fun startInstall() = _state.update { it.copy(installing = PluginInstall(), formError = null) }

    fun onInstallChange(value: PluginInstall) = _state.update { it.copy(installing = value) }

    fun cancelInstall() = _state.update { it.copy(installing = null, formError = null) }

    fun startEdit(plugin: PluginEntry) = _state.update {
        it.copy(
            editing = PluginEdit(
                oldName = plugin.name,
                name = plugin.name,
                moduleName = plugin.moduleName,
                version = plugin.version,
            ),
            formError = null,
        )
    }

    fun onEditChange(value: PluginEdit) = _state.update { it.copy(editing = value) }

    fun cancelEdit() = _state.update { it.copy(editing = null, formError = null) }

    fun askDelete(plugin: PluginEntry?) = _state.update { it.copy(pendingDelete = plugin) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissRestart() = _state.update { it.copy(restartPending = false) }

    fun install() {
        val draft = _state.value.installing ?: return
        if (draft.staticYaml.isBlank()) {
            _state.update { it.copy(formError = "Paste the static config snippet") }
            return
        }
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val result = staticConfig.install(
                staticYaml = draft.staticYaml,
                middlewareYaml = draft.middlewareYaml,
                middlewareFile = draft.middlewareFile.trim(),
            )
            if (result.ok) {
                val names = result.plugins.joinToString(", ")
                val wrote = result.middlewareFile
                _state.update {
                    it.copy(
                        busy = false,
                        installing = null,
                        restartPending = true,
                        restartDetail = buildString {
                            append(if (result.plugins.size > 1) "Plugins" else "Plugin")
                            append(" \"").append(names).append("\" saved to the static config")
                            if (!wrote.isNullOrBlank()) append(", middleware saved to ").append(wrote)
                            append(".")
                        },
                        message = result.warning,
                    )
                }
                load(initial = false)
            } else {
                _state.update {
                    it.copy(busy = false, formError = result.error ?: "Failed to install plugin")
                }
            }
        }
    }

    fun saveEdit() {
        val draft = _state.value.editing ?: return
        if (draft.name.isBlank() || draft.moduleName.isBlank() || draft.version.isBlank()) {
            _state.update { it.copy(formError = "Name, module, and version are required") }
            return
        }
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            runCatching {
                staticConfig.savePlugin(
                    name = draft.name.trim(),
                    oldName = draft.oldName,
                    moduleName = draft.moduleName.trim(),
                    version = draft.version.trim(),
                )
            }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            busy = false,
                            editing = null,
                            message = "Plugin saved - restart Traefik to apply",
                        )
                    }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update { it.copy(busy = false, formError = throwable.message ?: "Failed to save") }
                },
            )
        }
    }

    fun delete(plugin: PluginEntry) {
        _state.update { it.copy(pendingDelete = null, busy = true) }
        viewModelScope.launch {
            runCatching { staticConfig.removePlugin(plugin.name) }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(busy = false, message = "Plugin removed - restart Traefik to apply")
                    }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update { it.copy(busy = false, message = throwable.message ?: "Failed to save") }
                },
            )
        }
    }

    fun restart() {
        _state.update { it.copy(restarting = true) }
        viewModelScope.launch {
            runCatching { staticConfig.restart() }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(restarting = false, restartPending = false, message = "Traefik restarted")
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(restarting = false, message = throwable.message ?: "Restart failed")
                    }
                },
            )
        }
    }
}

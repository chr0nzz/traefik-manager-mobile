package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.AgentCompose
import dev.chr0nzz.traefikmanager.data.model.AgentConfig
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

data class IssuedKey(
    val agentName: String,
    val key: String,
    val rotated: Boolean,
)

data class ServersUiState(
    val loading: Boolean = true,
    val servers: List<ServerEntry> = emptyList(),
    val activeId: String? = null,
    val busy: Boolean = false,
    val issuedKey: IssuedKey? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val agents: List<ServerEntry> get() = servers.filterNot { it.isHost }
}

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val repository: ServersRepository,
    private val serverScope: ServerScope,
) : ViewModel() {

    private val _state = MutableStateFlow(ServersUiState())
    val state: StateFlow<ServersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.servers.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatching { repository.servers() }.fold(
                onSuccess = { servers ->
                    val cleared = repository.reconcileActive(servers.mapNotNull { it.agent })
                    _state.update {
                        it.copy(
                            loading = false,
                            servers = servers,
                            activeId = serverScope.activeAgentId.value,
                            message = if (cleared) {
                                "That server is no longer configured - switched to Host"
                            } else {
                                it.message
                            },
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(loading = false, error = throwable.message ?: "Could not read the servers")
                    }
                },
            )
        }
    }

    fun add(name: String, url: String) = mutate {
        val key = repository.create(name, url)
        _state.update {
            it.copy(issuedKey = key?.let { raw -> IssuedKey(name.trim(), raw, rotated = false) })
        }
        "Added ${name.trim()}"
    }

    fun rename(id: String, name: String) = mutate {
        repository.update(id, mapOf("name" to JsonPrimitive(name.trim())))
        "Renamed to ${name.trim()}"
    }

    fun setUrl(id: String, url: String) = mutate {
        repository.update(id, mapOf("url" to JsonPrimitive(url.trim().trimEnd('/'))))
        "URL updated"
    }

    fun delete(entry: ServerEntry) = mutate {
        repository.delete(entry.id ?: error("The host cannot be removed"))
        "Removed ${entry.name}"
    }

    fun rotateKey(entry: ServerEntry) = mutate {
        val key = repository.rotateKey(entry.id ?: error("The host has no agent key"))
        _state.update { it.copy(issuedKey = IssuedKey(entry.name, key, rotated = true)) }
        "API key rotated - update your agent"
    }

    fun composeFor(entry: ServerEntry, key: String?): String {
        val agent = entry.agent
        val config = AgentConfig(
            id = agent?.id.orEmpty(),
            name = entry.name,
            url = entry.url,
            certResolver = agent?.certResolver.orEmpty(),
            crowdsecLapiUrl = agent?.crowdsecLapiUrl.orEmpty(),
            domains = agent?.domains.orEmpty(),
        )
        return AgentCompose.compose(config, key)
    }

    fun dismissKey() = _state.update { it.copy(issuedKey = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun mutate(block: suspend () -> String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }.fold(
                onSuccess = { message ->
                    _state.update { it.copy(busy = false, message = message) }
                    load()
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(busy = false, message = throwable.message ?: "That did not work")
                    }
                },
            )
        }
    }
}

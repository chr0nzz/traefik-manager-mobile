package dev.chr0nzz.traefikmanager.ui.plugins

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.PluginEntry
import dev.chr0nzz.traefikmanager.data.model.PluginUsage
import dev.chr0nzz.traefikmanager.data.repo.PluginsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val repository: PluginsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.plugins.isEmpty(), refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
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
}

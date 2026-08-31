package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.LauncherApp
import dev.chr0nzz.traefikmanager.data.model.RouteOverride
import dev.chr0nzz.traefikmanager.data.repo.LauncherRepository
import dev.chr0nzz.traefikmanager.data.repo.LauncherSnapshot
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val snapshot: LauncherSnapshot = LauncherSnapshot(),
    val editing: LauncherApp? = null,
    val settingsOpen: Boolean = false,
    val message: String? = null,
) {
    val density: LauncherDensity
        get() = if (snapshot.density == "icons") LauncherDensity.Icons else LauncherDensity.List

    val ready: Boolean get() = !loading || snapshot.groups.isNotEmpty()
}

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LauncherRepository,
    serverScope: ServerScope,
    routesRepository: RoutesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LauncherUiState())
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.density.collect { value ->
                _state.update { it.copy(snapshot = it.snapshot.copy(density = value)) }
            }
        }
    }

    init {
        load()
        viewModelScope.launch { serverScope.generation.drop(1).collect { load() } }
        viewModelScope.launch { routesRepository.changes.drop(1).collect { load() } }
    }

    fun refresh() = load()

    fun edit(app: LauncherApp?) = _state.update { it.copy(editing = app) }

    fun openSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun load() {
        _state.update { it.copy(loading = it.snapshot.groups.isEmpty()) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { snapshot ->
                    _state.update { current ->
                        val density = snapshot.density.takeIf { it.isNotEmpty() }
                            ?: current.snapshot.density
                        current.copy(loading = false, snapshot = snapshot.copy(density = density))
                    }
                },
                onFailure = { _state.update { it.copy(loading = false) } },
            )
        }
    }

    fun setDensity(density: LauncherDensity) = write("Layout saved") {
        repository.setDensity(if (density == LauncherDensity.Icons) "icons" else "list")
    }

    fun saveOverride(routeId: String, override: RouteOverride) = write("Card saved") {
        repository.saveOverride(_state.value.snapshot.config, routeId, override)
    }

    fun setHidden(routeId: String, hidden: Boolean) = write(if (hidden) "Hidden" else "Shown") {
        repository.setHidden(_state.value.snapshot.config, routeId, hidden)
    }

    fun addGroup(name: String) = write("Group added") {
        repository.addGroup(_state.value.snapshot.config, name)
    }

    fun removeGroup(name: String) = write("Group removed") {
        repository.removeGroup(_state.value.snapshot.config, name)
    }

    private fun write(success: String, block: suspend () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = runCatching { block() }
            _state.update {
                it.copy(
                    saving = false,
                    editing = null,
                    message = result.exceptionOrNull()?.message?.take(120) ?: success,
                )
            }
            load()
        }
    }
}

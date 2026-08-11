package dev.chr0nzz.traefikmanager.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.ConfigError
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProtocolFilter(val label: String) {
    All("All"), Http("HTTP"), Tcp("TCP"), Udp("UDP")
}

enum class StatusFilter(val label: String) {
    All("All"), Active("Active"), Inactive("Inactive")
}

data class RoutesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val routes: List<Route> = emptyList(),
    val configErrors: List<ConfigError> = emptyList(),
    val query: String = "",
    val protocol: ProtocolFilter = ProtocolFilter.All,
    val status: StatusFilter = StatusFilter.All,
    val togglingId: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val visible: List<Route>
        get() = routes.filter { route ->
            val matchesProtocol = when (protocol) {
                ProtocolFilter.All -> true
                ProtocolFilter.Http -> route.protocol == "http"
                ProtocolFilter.Tcp -> route.protocol == "tcp"
                ProtocolFilter.Udp -> route.protocol == "udp"
            }
            val matchesStatus = when (status) {
                StatusFilter.All -> true
                StatusFilter.Active -> route.enabled
                StatusFilter.Inactive -> !route.enabled
            }
            val needle = query.trim().lowercase()
            val matchesQuery = needle.isEmpty() ||
                route.name.lowercase().contains(needle) ||
                route.rule.lowercase().contains(needle) ||
                route.target.lowercase().contains(needle) ||
                route.serviceName.lowercase().contains(needle)
            matchesProtocol && matchesStatus && matchesQuery
        }
}

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: RoutesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutesUiState())
    val state: StateFlow<RoutesUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onProtocolChange(value: ProtocolFilter) = _state.update { it.copy(protocol = value) }

    fun onStatusChange(value: StatusFilter) = _state.update { it.copy(status = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun toggle(route: Route) {
        if (_state.value.togglingId != null) return
        _state.update { it.copy(togglingId = route.id) }
        viewModelScope.launch {
            runCatching { repository.toggle(route.id, !route.enabled) }.fold(
                onSuccess = {
                    _state.update { current ->
                        current.copy(
                            togglingId = null,
                            routes = current.routes.map {
                                if (it.id == route.id) it.copy(enabled = !route.enabled) else it
                            },
                            message = if (route.enabled) "${route.name} disabled" else "${route.name} enabled",
                        )
                    }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(togglingId = null, message = throwable.message ?: "Could not toggle the route")
                    }
                },
            )
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.routes.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            routes = snapshot.routes,
                            configErrors = snapshot.configErrors,
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = throwable.message ?: "Could not load routes",
                        )
                    }
                },
            )
        }
    }
}

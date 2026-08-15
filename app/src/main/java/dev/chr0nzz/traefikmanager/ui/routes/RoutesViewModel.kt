package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.text.input.TextFieldState
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProtocolFilter(val label: String) {
    All("All"), Http("HTTP"), Tcp("TCP"), Udp("UDP")
}

enum class StatusFilter(val label: String) {
    All("All"), Active("Active"), Inactive("Inactive")
}

data class PingState(
    val running: Boolean = false,
    val ok: Boolean? = null,
    val latencyMs: Int? = null,
    val detail: String = "",
)

data class RoutesUiState(
    val pingResults: Map<String, PingState> = emptyMap(),
    val icons: dev.chr0nzz.traefikmanager.data.repo.IconContext = dev.chr0nzz.traefikmanager.data.repo.IconContext(),
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

    val queryState = TextFieldState()

    private val _state = MutableStateFlow(RoutesUiState())
    val state: StateFlow<RoutesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val icons = runCatching { repository.iconContext() }.getOrNull() ?: return@launch
            _state.update { it.copy(icons = icons) }
        }
        load(initial = true)
        viewModelScope.launch {
            repository.changes.drop(1).collect { load(initial = false) }
        }
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    /** Arriving from an Overview card, already filtered to whatever the card was reporting. */
    fun applyDeepLink(status: String?, proto: String?) {
        if (status == null && proto == null) return
        _state.update { current ->
            current.copy(
                status = when (status) {
                    "disabled" -> StatusFilter.Inactive
                    "enabled" -> StatusFilter.Active
                    else -> current.status
                },
                protocol = when (proto) {
                    "http" -> ProtocolFilter.Http
                    "tcp" -> ProtocolFilter.Tcp
                    "udp" -> ProtocolFilter.Udp
                    else -> current.protocol
                },
            )
        }
    }

    fun onProtocolChange(value: ProtocolFilter) = _state.update { it.copy(protocol = value) }

    fun onStatusChange(value: StatusFilter) = _state.update { it.copy(status = value) }

    fun ping(route: Route) {
        _state.update { it.copy(pingResults = it.pingResults + (route.id to PingState(running = true))) }
        viewModelScope.launch {
            val result = runCatching { repository.ping(route) }
            val next = result.fold(
                onSuccess = { ping ->
                    PingState(
                        running = false,
                        ok = ping.ok,
                        latencyMs = ping.latencyMs,
                        detail = when {
                            ping.self -> "this server"
                            ping.ok -> listOfNotNull(
                                ping.statusCode?.let { code -> "HTTP $code" },
                                ping.latencyMs?.let { ms -> "${ms}ms" },
                            ).joinToString(" · ")
                            else -> ping.error ?: "No response"
                        },
                    )
                },
                onFailure = { throwable ->
                    PingState(running = false, ok = false, detail = throwable.message ?: "Ping failed")
                },
            )
            _state.update { it.copy(pingResults = it.pingResults + (route.id to next)) }
        }
    }

    fun pingAll() {
        _state.value.visible.forEach(::ping)
    }

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

    fun delete(route: Route) {
        _state.update { it.copy(togglingId = route.id) }
        viewModelScope.launch {
            runCatching { repository.delete(route) }.fold(
                onSuccess = {
                    _state.update { it.copy(togglingId = null, message = "${route.name} deleted") }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(togglingId = null, message = throwable.message ?: "Could not delete the route")
                    }
                },
            )
        }
    }

    private fun describeLoadFailure(throwable: Throwable): String {
        val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
        return when (throwable) {
            is kotlinx.serialization.SerializationException ->
                "The server sent a route the app could not read: $detail"
            else -> detail
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
                            error = describeLoadFailure(throwable),
                        )
                    }
                },
            )
        }
    }
}

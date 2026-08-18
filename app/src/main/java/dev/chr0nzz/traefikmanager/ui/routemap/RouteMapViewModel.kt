package dev.chr0nzz.traefikmanager.ui.routemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.MapNode
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteMapBuilder
import dev.chr0nzz.traefikmanager.data.model.RouteMapGraph
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteMapUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val routes: List<Route> = emptyList(),
    val graph: RouteMapGraph = RouteMapGraph(),
    val protocol: String? = null,
    val provider: String? = null,
    val entryPoint: String? = null,
    val query: String = "",
    val focus: MapNode? = null,
    val focusIds: Set<String> = emptySet(),
) {
    val providers: List<String> get() = routes.map { it.provider }.filter { it.isNotEmpty() }.distinct().sorted()

    val entryPoints: List<String> get() = routes.flatMap { it.entryPointNames }.distinct().sorted()

    val protocols: List<String> get() = routes.map { it.protocol }.distinct().sorted()

    val filtered: Boolean
        get() = protocol != null || provider != null || entryPoint != null || query.isNotEmpty()
}

@HiltViewModel
class RouteMapViewModel @Inject constructor(
    private val routesRepository: RoutesRepository,
    serverScope: ServerScope,
) : ViewModel() {

    private val _state = MutableStateFlow(RouteMapUiState())
    val state: StateFlow<RouteMapUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch { serverScope.generation.drop(1).collect { load(initial = true) } }
        viewModelScope.launch { routesRepository.changes.drop(1).collect { load(initial = false) } }
    }

    fun refresh() = load(initial = false)

    fun onProtocol(value: String?) = update { it.copy(protocol = value) }

    fun onProvider(value: String?) = update { it.copy(provider = value) }

    fun onEntryPoint(value: String?) = update { it.copy(entryPoint = value) }

    fun onQuery(value: String) = update { it.copy(query = value) }

    fun clearFilters() = update {
        it.copy(protocol = null, provider = null, entryPoint = null, query = "")
    }

    fun focus(node: MapNode?) {
        _state.update { current ->
            val next = if (node == null || current.focus?.id == node.id) null else node
            current.copy(
                focus = next,
                focusIds = next?.let { current.graph.connected(it.id) }.orEmpty(),
            )
        }
    }

    private fun update(block: (RouteMapUiState) -> RouteMapUiState) {
        _state.update { current ->
            val next = block(current)
            next.copy(
                graph = rebuild(next),
                focus = null,
                focusIds = emptySet(),
            )
        }
    }

    private fun rebuild(state: RouteMapUiState) = RouteMapBuilder.build(
        routes = state.routes,
        protocol = state.protocol,
        provider = state.provider,
        entryPoint = state.entryPoint,
        query = state.query,
    )

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.routes.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { routesRepository.load().routes }.fold(
                onSuccess = { routes ->
                    _state.update { current ->
                        val next = current.copy(loading = false, refreshing = false, routes = routes)
                        next.copy(graph = rebuild(next))
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = throwable.message?.take(160) ?: "Could not read the routes",
                        )
                    }
                },
            )
        }
    }
}

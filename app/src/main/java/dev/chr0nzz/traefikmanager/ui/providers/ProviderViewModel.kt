package dev.chr0nzz.traefikmanager.ui.providers

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ProviderMiddleware
import dev.chr0nzz.traefikmanager.data.model.ProviderPage
import dev.chr0nzz.traefikmanager.data.model.ProviderProtocol
import dev.chr0nzz.traefikmanager.data.model.ProviderRoute
import dev.chr0nzz.traefikmanager.data.model.ProviderRows
import dev.chr0nzz.traefikmanager.data.model.ProviderVerdict
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.ui.routes.ProtocolFilter
import dev.chr0nzz.traefikmanager.ui.routes.StatusFilter
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderUiState(
    val page: ProviderPage = ProviderPage.Docker,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val reachable: Boolean = true,
    val routes: List<ProviderRoute> = emptyList(),
    val middlewares: List<ProviderMiddleware> = emptyList(),
    val query: String = "",
    val protocol: ProtocolFilter = ProtocolFilter.All,
    val status: StatusFilter = StatusFilter.All,
    val selected: ProviderRoute? = null,
    val error: String? = null,
) {
    val verdict: ProviderVerdict get() = ProviderRows.verdict(routes, middlewares)

    val visible: List<ProviderRoute>
        get() {
            val needle = query.trim().lowercase()
            return routes.filter { route ->
                val matchesProtocol = when (protocol) {
                    ProtocolFilter.All -> true
                    ProtocolFilter.Http -> route.protocol == ProviderProtocol.Http
                    ProtocolFilter.Tcp -> route.protocol == ProviderProtocol.Tcp
                    ProtocolFilter.Udp -> route.protocol == ProviderProtocol.Udp
                }
                val matchesStatus = when (status) {
                    StatusFilter.All -> true
                    StatusFilter.Active -> route.serving
                    StatusFilter.Inactive -> !route.serving
                }
                val matchesQuery = needle.isEmpty() ||
                    route.name.lowercase().contains(needle) ||
                    route.rule.lowercase().contains(needle)
                matchesProtocol && matchesStatus && matchesQuery
            }
        }
}

@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
    private val routesRepository: RoutesRepository,
    private val serverScope: ServerScope,
) : ViewModel() {

    val queryState = TextFieldState()

    private val _state = MutableStateFlow(ProviderUiState())
    val state: StateFlow<ProviderUiState> = _state.asStateFlow()

    private var bound: ProviderPage? = null

    init {
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                val page = bound ?: return@collect
                _state.update { ProviderUiState(page = page, query = it.query, protocol = it.protocol, status = it.status) }
                load(initial = true)
            }
        }
    }

    fun bind(page: ProviderPage) {
        if (bound == page) return
        bound = page
        _state.value = ProviderUiState(page = page)
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onProtocolChange(value: ProtocolFilter) = _state.update { it.copy(protocol = value) }

    fun onStatusChange(value: StatusFilter) = _state.update { it.copy(status = value) }

    fun select(route: ProviderRoute?) = _state.update { it.copy(selected = route) }

    private fun load(initial: Boolean) {
        val page = bound ?: return
        _state.update { it.copy(loading = initial && it.routes.isEmpty(), refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { read(page) }.fold(
                onSuccess = { (reachable, routes, middlewares) ->
                    if (bound != page) return@fold
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            reachable = reachable,
                            routes = routes,
                            middlewares = middlewares,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = throwable.message ?: "Traefik API not reachable",
                        )
                    }
                },
            )
        }
    }

    private suspend fun read(page: ProviderPage): Triple<Boolean, List<ProviderRoute>, List<ProviderMiddleware>> =
        coroutineScope {
            val ready = apiProvider.ready()
            val routersCall = async { ready.api.routers() }
            val servicesCall = async { runCatching { ready.api.services(agentId = ready.agentId) }.getOrNull() }
            val middlewaresCall = async { runCatching { ready.api.middlewares() }.getOrNull() }
            val managedCall = async {
                if (page == ProviderPage.FileExternal) {
                    routesRepository.load().routes.map { it.name.substringBefore('@') }.toSet()
                } else {
                    emptySet()
                }
            }
            val routers = routersCall.await()
            val reachable = routers.reachable && routers.all.isNotEmpty()
            val routes = ProviderRows.routes(page, routers, servicesCall.await(), managedCall.await())
            val middlewares = ProviderRows.middlewares(page, middlewaresCall.await())
            Triple(reachable, routes, middlewares)
        }
}

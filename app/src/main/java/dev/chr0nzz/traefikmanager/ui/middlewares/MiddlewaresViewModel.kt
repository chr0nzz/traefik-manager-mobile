package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.MiddlewareProtocol
import dev.chr0nzz.traefikmanager.data.model.MiddlewareUsage
import dev.chr0nzz.traefikmanager.data.repo.InUseException
import dev.chr0nzz.traefikmanager.data.repo.MiddlewaresRepository
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

enum class MiddlewareFilter(val label: UiText) {
    All(UiText.of(R.string.common_all)), Http(UiText.Raw("HTTP")), Tcp(UiText.Raw("TCP"))
}

data class BlockedMiddlewareDelete(
    val reason: UiText,
    val routers: List<String>,
    val middleware: MiddlewareDef,
)

data class MiddlewaresUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val middlewares: List<MiddlewareDef> = emptyList(),
    val usage: Map<String, Int> = emptyMap(),
    val query: String = "",
    val blockedDelete: BlockedMiddlewareDelete? = null,
    val filter: MiddlewareFilter = MiddlewareFilter.All,
    val deletingName: String? = null,
    val error: UiText? = null,
    val message: UiText? = null,
) {
    val visible: List<MiddlewareDef>
        get() = middlewares.filter { middleware ->
            val matchesProtocol = when (filter) {
                MiddlewareFilter.All -> true
                MiddlewareFilter.Http -> middleware.type.ifEmpty { "http" } == "http"
                MiddlewareFilter.Tcp -> middleware.type == "tcp"
            }
            val needle = query.trim().lowercase()
            val matchesQuery = needle.isEmpty() ||
                middleware.name.lowercase().contains(needle) ||
                middleware.yaml.lowercase().contains(needle)
            matchesProtocol && matchesQuery
        }.sortedBy { it.name.lowercase() }

    fun usageFor(name: String): Int = usage[name] ?: 0
}

@HiltViewModel
class MiddlewaresViewModel @Inject constructor(
    private val routesRepository: RoutesRepository,
    private val middlewaresRepository: MiddlewaresRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MiddlewaresUiState())
    val state: StateFlow<MiddlewaresUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            routesRepository.changes.drop(1).collect { load(initial = false) }
        }
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onFilterChange(value: MiddlewareFilter) = _state.update { it.copy(filter = value) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissBlockedDelete() = _state.update { it.copy(blockedDelete = null) }

    fun delete(middleware: MiddlewareDef, force: Boolean = false) {
        _state.update { it.copy(deletingName = middleware.name, blockedDelete = null) }
        viewModelScope.launch {
            runCatching {
                middlewaresRepository.delete(middleware.name, middleware.configFile, force)
            }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(deletingName = null, message = UiText.of(R.string.routes_deleted_named, middleware.name))
                    }
                },
                onFailure = { throwable ->
                    if (throwable is InUseException) {
                        _state.update {
                            it.copy(
                                deletingName = null,
                                blockedDelete = BlockedMiddlewareDelete(
                                    reason = throwable.reason,
                                    routers = throwable.routers,
                                    middleware = middleware,
                                ),
                            )
                        }
                        return@fold
                    }
                    _state.update {
                        it.copy(
                            deletingName = null,
                            message = UiText.failure(throwable, R.string.middlewares_error_delete),
                        )
                    }
                },
            )
        }
    }

    private fun load(initial: Boolean) {
        _state.update {
            it.copy(loading = initial && it.middlewares.isEmpty(), refreshing = !initial, error = null)
        }
        viewModelScope.launch {
            runCatching { routesRepository.load() }.fold(
                onSuccess = { snapshot ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            middlewares = snapshot.middlewares,
                            usage = MiddlewareUsage.countsFor(snapshot.routes, snapshot.middlewares),
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = UiText.failure(throwable, R.string.middlewares_error_load),
                        )
                    }
                },
            )
        }
    }
}

fun MiddlewareDef.protocol(): MiddlewareProtocol = MiddlewareProtocol.from(type.ifEmpty { "http" })

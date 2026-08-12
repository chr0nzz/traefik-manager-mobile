package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.ServiceHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceProtocol
import dev.chr0nzz.traefikmanager.data.model.ServiceRow
import dev.chr0nzz.traefikmanager.data.model.ServiceRows
import dev.chr0nzz.traefikmanager.data.repo.ServicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ServiceStatusFilter(val label: String) {
    All("All"),
    Success("Success"),
    Warnings("Warnings"),
    Errors("Errors"),
}

data class ServicesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val services: List<ServiceRow> = emptyList(),
    val reachable: Boolean = true,
    val query: String = "",
    val status: ServiceStatusFilter = ServiceStatusFilter.All,
    val protocol: ServiceProtocol? = null,
    val provider: String? = null,
    val error: String? = null,
) {
    val protocols: List<ServiceProtocol>
        get() = ServiceProtocol.entries.filter { proto -> services.any { it.proto == proto } }

    val providers: List<String>
        get() = services.map { it.provider }.distinct().sorted()

    val filtersActive: Boolean
        get() = status != ServiceStatusFilter.All || protocol != null || provider != null

    val visible: List<ServiceRow>
        get() {
            val needle = query.trim().lowercase()
            return services.filter { service ->
                val matchesQuery = needle.isEmpty() || service.name.lowercase().contains(needle)
                val matchesStatus = when (status) {
                    ServiceStatusFilter.All -> true
                    ServiceStatusFilter.Success -> service.health == ServiceHealth.Ok
                    ServiceStatusFilter.Warnings -> service.health == ServiceHealth.Warning
                    ServiceStatusFilter.Errors -> service.health == ServiceHealth.Error
                }
                val matchesProtocol = protocol == null || service.proto == protocol
                val matchesProvider = provider == null || service.provider == provider
                matchesQuery && matchesStatus && matchesProtocol && matchesProvider
            }
        }

    val backendsUp: Int get() = services.sumOf { it.backendsUp }

    val backendsTotal: Int get() = services.sumOf { it.backendsTotal }
}

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val repository: ServicesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServicesUiState())
    val state: StateFlow<ServicesUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    init {
        val cached = repository.cached()
        if (cached != null) {
            _state.update {
                it.copy(
                    loading = false,
                    services = ServiceRows.from(cached),
                    reachable = cached.reachable,
                )
            }
        }
        load(initial = cached == null)
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onStatusChange(value: ServiceStatusFilter) = _state.update { it.copy(status = value) }

    fun onProtocolChange(value: ServiceProtocol?) = _state.update { it.copy(protocol = value) }

    fun onProviderChange(value: String?) = _state.update { current ->
        current.copy(provider = if (current.provider == value) null else value)
    }

    fun clearFilters() = _state.update {
        it.copy(status = ServiceStatusFilter.All, protocol = null, provider = null)
    }

    fun applyDeepLink(status: String?, provider: String?) {
        if (status == null && provider == null) return
        _state.update { current ->
            current.copy(
                status = when (status) {
                    "success" -> ServiceStatusFilter.Success
                    "warning" -> ServiceStatusFilter.Warnings
                    "error" -> ServiceStatusFilter.Errors
                    else -> current.status
                },
                provider = provider ?: current.provider,
            )
        }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { envelope ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            services = ServiceRows.from(envelope),
                            reachable = envelope.reachable,
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = describeFailure(throwable),
                        )
                    }
                },
            )
        }
    }

    private fun describeFailure(throwable: Throwable): String {
        val detail = throwable.message ?: throwable::class.simpleName ?: "unknown error"
        return when (throwable) {
            is kotlinx.serialization.SerializationException ->
                "The server sent a service the app could not read: $detail"
            else -> detail
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.services

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.ServiceHealth
import dev.chr0nzz.traefikmanager.data.model.ServiceProtocol
import dev.chr0nzz.traefikmanager.data.model.ServiceRow
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceRows
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.ServicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
    val authorable: Boolean = false,
    val raw: Map<String, TraefikService> = emptyMap(),
    val editing: ServiceDraft? = null,
    val editError: String? = null,
    val busy: Boolean = false,
    val pendingDelete: ServiceRow? = null,
    val message: String? = null,
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
    private val serverScope: ServerScope,
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
                    raw = rawByName(cached),
                    authorable = true,
                    reachable = cached.reachable,
                )
            }
        }
        load(initial = cached == null)
        watchServerChanges()
    }

    private fun rawByName(envelope: ServiceEnvelope): Map<String, TraefikService> =
        (envelope.http + envelope.tcp + envelope.udp)
            .associateBy { it.name.substringBefore('@') }

    fun refresh() = load(initial = false)

    fun startCreate() = _state.update {
        it.copy(editing = ServiceDraft(), editError = null)
    }

    fun startEdit(row: ServiceRow) = _state.update {
        it.copy(editing = ServiceDraft.of(row.name, it.raw[row.shortName]), editError = null)
    }

    fun onDraftChange(draft: ServiceDraft) = _state.update { it.copy(editing = draft) }

    fun cancelEdit() = _state.update { it.copy(editing = null, editError = null) }

    fun askDelete(row: ServiceRow?) = _state.update { it.copy(pendingDelete = row) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val draft = _state.value.editing ?: return
        val problem = draft.problem()
        if (problem != null) {
            _state.update { it.copy(editError = problem) }
            return
        }
        _state.update { it.copy(busy = true, editError = null) }
        viewModelScope.launch {
            runCatching { repository.save(draft.payload()) }.fold(
                onSuccess = { name ->
                    _state.update { it.copy(busy = false, editing = null, message = "Service $name saved") }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(busy = false, editError = throwable.message ?: "Could not save the service")
                    }
                },
            )
        }
    }

    fun delete(row: ServiceRow) {
        _state.update { it.copy(pendingDelete = null, busy = true) }
        viewModelScope.launch {
            runCatching { repository.delete(row.shortName) }.fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, message = "Service ${row.shortName} deleted") }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(busy = false, message = throwable.message ?: "Could not delete the service")
                    }
                },
            )
        }
    }

    fun setOwnership(row: ServiceRow, adopt: Boolean) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { repository.setOwnership(row.shortName, adopt) }.fold(
                onSuccess = { owned ->
                    _state.update {
                        it.copy(
                            busy = false,
                            message = if (owned) {
                                "Traefik Manager now manages ${row.shortName}"
                            } else {
                                "${row.shortName} is no longer managed here"
                            },
                        )
                    }
                    load(initial = false)
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(busy = false, message = throwable.message ?: "Could not change the ownership")
                    }
                },
            )
        }
    }

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
                            raw = rawByName(envelope),
                            authorable = true,
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

    private fun watchServerChanges() {
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                _state.value = ServicesUiState()
                load(initial = true)
            }
        }
    }
}

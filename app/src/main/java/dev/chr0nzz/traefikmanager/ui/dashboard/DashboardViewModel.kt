package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.repo.DashboardBuilder
import dev.chr0nzz.traefikmanager.data.repo.DashboardRepository
import dev.chr0nzz.traefikmanager.data.repo.DashboardSnapshot
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: DashboardSnapshot? = null,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
    routesRepository: dev.chr0nzz.traefikmanager.data.repo.RoutesRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            routesRepository.changes.drop(1).collect { load(initial = false) }
        }
    }

    private val providerFilter = MutableStateFlow<String?>(null)
    private val loadState = MutableStateFlow(DashboardUiState())

    val state: StateFlow<DashboardUiState> =
        combine(repository.raw, providerFilter, loadState) { raw, filter, load ->
            load.copy(snapshot = raw?.let { DashboardBuilder.build(it, filter) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun onProviderClick(provider: String) {
        providerFilter.update { current -> if (current == provider) null else provider }
    }

    fun clearProviderFilter() {
        providerFilter.value = null
    }

    private fun load(initial: Boolean) {
        loadState.update {
            it.copy(loading = initial && repository.raw.value == null, refreshing = !initial, error = null)
        }
        viewModelScope.launch {
            runCatching { repository.refresh() }.fold(
                onSuccess = {
                    loadState.update { state -> state.copy(loading = false, refreshing = false, error = null) }
                },
                onFailure = { throwable ->
                    loadState.update { state ->
                        state.copy(
                            loading = false,
                            refreshing = false,
                            error = throwable.message ?: "Could not load the overview",
                        )
                    }
                },
            )
        }
    }
}

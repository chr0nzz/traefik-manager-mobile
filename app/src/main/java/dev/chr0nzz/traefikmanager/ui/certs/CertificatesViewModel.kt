package dev.chr0nzz.traefikmanager.ui.certs

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.CertHealth
import dev.chr0nzz.traefikmanager.data.model.CertRow
import dev.chr0nzz.traefikmanager.data.model.CertRows
import dev.chr0nzz.traefikmanager.data.repo.CertificatesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CertificatesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val certs: List<CertRow> = emptyList(),
    val query: String = "",
    val serverError: String? = null,
    val loadError: String? = null,
) {
    val visible: List<CertRow>
        get() {
            val needle = query.lowercase()
            if (needle.isEmpty()) return certs
            return certs.filter { cert ->
                cert.main.lowercase().contains(needle) ||
                    cert.extraDomains.any { it.lowercase().contains(needle) }
            }
        }

    val expiringSoon: Int
        get() = certs.count { it.health == CertHealth.Critical || it.health == CertHealth.Expiring }
}

@HiltViewModel
class CertificatesViewModel @Inject constructor(
    private val repository: CertificatesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CertificatesUiState())
    val state: StateFlow<CertificatesUiState> = _state.asStateFlow()

    val queryState = TextFieldState()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.certs.isEmpty(), refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            runCatching { repository.load() }.fold(
                onSuccess = { response ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            certs = CertRows.from(response.certs, System.currentTimeMillis()),
                            serverError = response.error?.takeIf { message -> message.isNotBlank() },
                            loadError = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            loadError = throwable.message ?: "Could not load certificate data",
                        )
                    }
                },
            )
        }
    }
}

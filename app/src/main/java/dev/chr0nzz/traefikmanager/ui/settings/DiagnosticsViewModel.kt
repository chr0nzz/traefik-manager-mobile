package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ClientIpDiagnostic
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.model.UiText

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val diagnostic: ClientIpDiagnostic? = null,
    val error: UiText? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { apiProvider.api().clientIpDiagnostic() }.fold(
                onSuccess = { result -> _state.update { it.copy(loading = false, diagnostic = result) } },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = if (throwable is HttpException && throwable.code() == 404) {
                                UiText.of(R.string.diagnostics_error_too_old)
                            } else {
                                UiText.messageOr(throwable.message, R.string.diagnostics_error_load)
                            },
                        )
                    }
                },
            )
        }
    }
}

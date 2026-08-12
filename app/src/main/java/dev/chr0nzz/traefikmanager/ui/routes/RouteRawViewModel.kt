package dev.chr0nzz.traefikmanager.ui.routes

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.repo.RoutesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteRawUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val configFile: String = "",
    val proto: String = "",
    val loadError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class RouteRawViewModel @Inject constructor(
    private val repository: RoutesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RouteRawUiState())
    val state: StateFlow<RouteRawUiState> = _state.asStateFlow()

    val content = TextFieldState()

    private var routeId: String = ""
    private var loadedFor: String? = null

    fun load(id: String) {
        if (loadedFor == id) return
        loadedFor = id
        routeId = id
        _state.update { it.copy(loading = true, loadError = null, error = null) }
        viewModelScope.launch {
            runCatching { repository.rawYaml(id) }.fold(
                onSuccess = { raw ->
                    content.setTextAndPlaceCursorAtEnd(raw.raw)
                    _state.update {
                        it.copy(loading = false, configFile = raw.configFile, proto = raw.proto)
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(loading = false, loadError = throwable.message ?: "Could not load the YAML")
                    }
                },
            )
        }
    }

    fun save() {
        val text = content.text.toString()
        if (text.isBlank()) {
            _state.update { it.copy(error = "The YAML is empty") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.saveRawYaml(routeId, text) }.fold(
                onSuccess = { _state.update { it.copy(saving = false, saved = true) } },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, error = throwable.message ?: "Could not save the YAML")
                    }
                },
            )
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.repo.StaticConfigRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaticConfigUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val restarting: Boolean = false,
    val path: String = "",
    val loadError: String? = null,
    val saveError: String? = null,
    val message: String? = null,
    /** True once a save lands: the file is new but Traefik is still running the old one. */
    val restartPending: Boolean = false,
)

/**
 * The raw `traefik.yml`, read and written whole.
 *
 * Nothing is validated here. The server parses the YAML before it writes and refuses anything that
 * will not load, which is the check that matters; anything it accepts is the user's call.
 */
@HiltViewModel
class StaticConfigViewModel @Inject constructor(
    private val repository: StaticConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StaticConfigUiState())
    val state: StateFlow<StaticConfigUiState> = _state.asStateFlow()

    val content = TextFieldState()

    /** What was last read, so the screen knows whether anything is actually unsaved. */
    private var original: String = ""

    val dirty: Boolean get() = content.text.toString() != original

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { repository.read() }.fold(
                onSuccess = { doc ->
                    original = doc.raw
                    content.setTextAndPlaceCursorAtEnd(doc.raw)
                    _state.update { it.copy(loading = false, path = doc.path, loadError = null) }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadError = throwable.message ?: "Could not read the static config",
                        )
                    }
                },
            )
        }
    }

    fun discard() {
        content.setTextAndPlaceCursorAtEnd(original)
        _state.update { it.copy(saveError = null) }
    }

    fun save() {
        val body = content.text.toString()
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            runCatching { repository.write(body) }.fold(
                onSuccess = {
                    original = body
                    _state.update {
                        it.copy(
                            saving = false,
                            restartPending = true,
                            message = "Static config saved",
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, saveError = throwable.message ?: "Save failed")
                    }
                },
            )
        }
    }

    fun restart() {
        _state.update { it.copy(restarting = true) }
        viewModelScope.launch {
            runCatching { repository.restart() }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(restarting = false, restartPending = false, message = "Traefik restarted")
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(restarting = false, message = throwable.message ?: "Restart failed")
                    }
                },
            )
        }
    }

    fun dismissRestart() = _state.update { it.copy(restartPending = false) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

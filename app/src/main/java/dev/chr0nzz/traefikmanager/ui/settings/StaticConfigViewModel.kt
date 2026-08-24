package dev.chr0nzz.traefikmanager.ui.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.ApiForm
import dev.chr0nzz.traefikmanager.data.model.LogForm
import dev.chr0nzz.traefikmanager.data.model.ObservabilityForm
import dev.chr0nzz.traefikmanager.data.model.ProvidersForm
import dev.chr0nzz.traefikmanager.data.model.StaticEntries
import dev.chr0nzz.traefikmanager.data.model.SystemForm
import dev.chr0nzz.traefikmanager.data.repo.StaticConfigRepository
import kotlinx.serialization.json.JsonObject
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
    /** The staged document, which section edits rewrite and only a save commits. */
    val staged: String = "",
    val parsed: JsonObject? = null,
    val pending: Boolean = false,
    val busy: Boolean = false,
) {
    val entrypoints: List<Pair<String, JsonObject>> get() = StaticEntries.entrypoints(parsed)
    val resolvers: List<Pair<String, JsonObject>> get() = StaticEntries.resolvers(parsed)
    val plugins: List<Triple<String, String, Boolean>> get() = StaticEntries.plugins(parsed)
    val providers: ProvidersForm get() = ProvidersForm.read(parsed?.get("providers") as? JsonObject)
    val otherProviders: List<String> get() = ProvidersForm.others(parsed?.get("providers") as? JsonObject)
    val api: ApiForm get() = ApiForm.read(parsed)
    val log: LogForm get() = LogForm.read(parsed)
    val observability: ObservabilityForm get() = ObservabilityForm.read(parsed)
    val system: SystemForm get() = SystemForm.read(parsed)
}

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
                    _state.update {
                        it.copy(
                            loading = false,
                            path = doc.path,
                            staged = doc.raw,
                            parsed = doc.parsed,
                            pending = false,
                            loadError = null,
                        )
                    }
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
        load()
    }

    /**
     * Run one section change through the server and keep the result staged.
     *
     * The raw editor and the section forms work on the same buffer, so a change made in a form is
     * visible in the YAML and the other way round, and neither is written until save.
     */
    fun applySection(
        section: String,
        action: String,
        name: String = "",
        oldName: String = "",
        data: JsonObject = JsonObject(emptyMap()),
        onDone: (String?) -> Unit = {},
    ) {
        val currentRaw = content.text.toString().ifBlank { _state.value.staged }
        _state.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                repository.applySection(section, action, name, oldName, data, currentRaw)
            }.fold(
                onSuccess = { response ->
                    content.setTextAndPlaceCursorAtEnd(response.raw)
                    _state.update {
                        it.copy(
                            busy = false,
                            staged = response.raw,
                            parsed = response.parsed,
                            pending = true,
                        )
                    }
                    onDone(null)
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "Could not apply the change"
                    _state.update { it.copy(busy = false) }
                    onDone(message)
                },
            )
        }
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
                            pending = false,
                            staged = body,
                            message = "Static config saved",
                        )
                    }
                    load()
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

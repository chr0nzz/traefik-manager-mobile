package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplate
import dev.chr0nzz.traefikmanager.data.repo.MiddlewaresRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val loading: Boolean = true,
    val templates: List<MiddlewareTemplate> = emptyList(),
    val editing: MiddlewareTemplate? = null,
    val editorOpen: Boolean = false,
    val name: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class MiddlewareTemplatesViewModel @Inject constructor(
    private val middlewaresRepository: MiddlewaresRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    val content = TextFieldState()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.templates.isEmpty()) }
        viewModelScope.launch {
            val templates = middlewaresRepository.templates()
            _state.update { it.copy(loading = false, templates = templates) }
        }
    }

    fun openEditor(template: MiddlewareTemplate?) {
        content.setTextAndPlaceCursorAtEnd(template?.yaml.orEmpty())
        _state.update {
            it.copy(
                editorOpen = true,
                editing = template,
                name = template?.name.orEmpty(),
                error = null,
            )
        }
    }

    fun closeEditor() {
        _state.update { it.copy(editorOpen = false, editing = null, name = "", error = null) }
        load()
    }

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        val name = current.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Name is required") }
            return
        }
        val yaml = content.text.toString().trim()
        val editing = current.editing
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (editing == null) {
                    middlewaresRepository.createTemplate(name, yaml)
                } else {
                    middlewaresRepository.updateTemplate(editing.id, name, yaml)
                }
            }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            saving = false,
                            editorOpen = false,
                            editing = null,
                            name = "",
                            message = if (editing == null) "Template created" else "Template updated",
                        )
                    }
                    load()
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, error = throwable.message ?: "Could not save the template")
                    }
                },
            )
        }
    }

    fun delete(template: MiddlewareTemplate) {
        viewModelScope.launch {
            runCatching { middlewaresRepository.deleteTemplate(template.id) }.fold(
                onSuccess = {
                    _state.update { it.copy(message = "Template deleted") }
                    load()
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(message = throwable.message ?: "Could not delete the template")
                    }
                },
            )
        }
    }
}

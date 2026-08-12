package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository.Companion.bool
import dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository.Companion.text
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val version: String) : TestState
    data class Failed(val message: String) : TestState
}

data class ConnectionSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val url: String = "",
    val user: String = "",
    val password: String = "",
    val passwordStored: Boolean = false,
    val domains: List<String> = emptyList(),
    val certResolver: String = "",
    val test: TestState = TestState.Idle,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val urlValid: Boolean
        get() = url.startsWith("http://") || url.startsWith("https://")

    val canSave: Boolean get() = !saving && urlValid && domains.isNotEmpty()
}

@HiltViewModel
class ConnectionSettingsViewModel @Inject constructor(
    private val repository: ManagerSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionSettingsUiState())
    val state: StateFlow<ConnectionSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.raw() }.fold(
                onSuccess = { document ->
                    _state.update {
                        it.copy(
                            loading = false,
                            url = document.text("traefik_api_url"),
                            user = document.text("traefik_api_user"),
                            passwordStored = document.bool("traefik_api_password_set"),
                            certResolver = document.text("cert_resolver"),
                            domains = (document["domains"] as? kotlinx.serialization.json.JsonArray)
                                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                                .orEmpty(),
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(loading = false, error = throwable.message ?: "Could not read the settings")
                    }
                },
            )
        }
    }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, test = TestState.Idle, error = null) }

    fun onUserChange(value: String) = _state.update { it.copy(user = value, test = TestState.Idle) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, test = TestState.Idle) }

    /** Tests the values on screen, so nothing has to be saved before it can be verified. */
    fun testConnection() {
        val current = _state.value
        _state.update { it.copy(test = TestState.Running) }
        viewModelScope.launch {
            runCatching { repository.testConnection(current.url, current.user, current.password) }.fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            test = if (result.ok) {
                                TestState.Ok(result.version ?: "?")
                            } else {
                                TestState.Failed(result.error ?: "No response from the API")
                            },
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(test = TestState.Failed(describeTestFailure(throwable)))
                    }
                },
            )
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val changes = buildMap {
                put("traefik_api_url", JsonPrimitive(current.url.trim()))
                put("traefik_api_user", JsonPrimitive(current.user.trim()))
                // An omitted password means "keep the stored one", so only send a typed value.
                if (current.password.isNotEmpty()) {
                    put("traefik_api_password", JsonPrimitive(current.password))
                }
            }
            runCatching { repository.patch(changes) }.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            saving = false,
                            saved = true,
                            password = "",
                            passwordStored = it.passwordStored || current.password.isNotEmpty(),
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(saving = false, error = throwable.message ?: "Could not save the settings")
                    }
                },
            )
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    private fun describeTestFailure(throwable: Throwable): String {
        val message = throwable.message.orEmpty()
        return when {
            message.contains("Target address not allowed") ->
                "Could not resolve that host, or the address is not allowed."
            message.contains("Invalid URL") -> "That URL is not valid. It must start with http:// or https://."
            message.isNotEmpty() -> message
            else -> "Connection failed"
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ApiKeyEntry
import dev.chr0nzz.traefikmanager.data.model.ChangePasswordRequest
import dev.chr0nzz.traefikmanager.data.model.GenerateKeyRequest
import dev.chr0nzz.traefikmanager.data.model.RevokeKeyRequest
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthSettingsUiState(
    val loading: Boolean = true,
    val keys: List<ApiKeyEntry> = emptyList(),
    val otpEnabled: Boolean = false,
    val busy: Boolean = false,
    val issuedKey: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthSettingsViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthSettingsUiState())
    val state: StateFlow<AuthSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.keys.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val api = apiProvider.api()
                    val keys = async { api.apiKeyStatus() }
                    val otp = async { runCatching { api.otpStatus() }.getOrNull() }
                    keys.await() to otp.await()
                }
            }.fold(
                onSuccess = { (status, otp) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            keys = status.keys,
                            otpEnabled = otp?.otpEnabled ?: it.otpEnabled,
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update {
                        it.copy(loading = false, error = throwable.message ?: "Could not read the auth settings")
                    }
                },
            )
        }
    }

    fun changePassword(current: String, new: String, confirm: String) {
        val problem = when {
            current.isEmpty() || new.isEmpty() || confirm.isEmpty() -> "Please fill in all fields."
            new.length < 8 -> "New password must be at least 8 characters."
            new != confirm -> "Passwords do not match."
            else -> null
        }
        if (problem != null) {
            _state.update { it.copy(message = problem) }
            return
        }
        run("Password changed") {
            val response = apiProvider.api().changePassword(ChangePasswordRequest(current, new, confirm))
            if (!response.worked) error(response.error ?: "Could not change the password")
        }
    }

    fun disableOtp() = run("Two-factor authentication disabled") {
        val response = apiProvider.api().disableOtp()
        if (!response.worked) error(response.error ?: "Could not disable two-factor authentication")
    }

    fun generateKey(deviceName: String) = run("API key created") {
        val response = apiProvider.api().generateApiKey(GenerateKeyRequest(deviceName.trim().take(50)))
        if (!response.ok || response.key == null) error(response.error ?: "Could not create the key")
        _state.update { it.copy(issuedKey = response.key) }
    }

    fun revokeKey(entry: ApiKeyEntry) = run("API key revoked") {
        val response = apiProvider.api().revokeApiKey(RevokeKeyRequest(entry.preview))
        if (!response.worked) error(response.error ?: "Could not revoke the key")
    }

    fun dismissKey() = _state.update { it.copy(issuedKey = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun run(success: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { block() }.fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, message = success) }
                    load()
                },
                onFailure = { throwable ->
                    _state.update { it.copy(busy = false, message = throwable.message ?: "That did not work") }
                },
            )
        }
    }
}

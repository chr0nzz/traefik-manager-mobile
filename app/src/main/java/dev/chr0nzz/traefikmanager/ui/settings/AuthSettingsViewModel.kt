package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ApiKeyEntry
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
import kotlinx.serialization.json.booleanOrNull

/** What the server reports about how it lets people in. Read-only: the app never changes it. */
data class AuthStatus(
    val authEnabled: Boolean = false,
    val noAuth: Boolean = false,
    val hasPassword: Boolean = false,
    val envForced: Boolean = false,
    val oidcEnabled: Boolean = false,
    val oidcActive: Boolean = false,
    val otpEnabled: Boolean = false,
)

data class AuthSettingsUiState(
    val loading: Boolean = true,
    val keys: List<ApiKeyEntry> = emptyList(),
    val status: AuthStatus = AuthStatus(),
    val otpEnabled: Boolean = false,
    val busy: Boolean = false,
    val issuedKey: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthSettingsViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
    private val settingsRepository: dev.chr0nzz.traefikmanager.data.repo.ManagerSettingsRepository,
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
                    val settings = async { runCatching { settingsRepository.raw() }.getOrNull() }
                    keys.await() to settings.await()
                }
            }.fold(
                onSuccess = { (keyStatus, settings) ->
                    val status = settings?.let { raw ->
                        AuthStatus(
                            authEnabled = raw.flag("auth_enabled"),
                            noAuth = raw.flag("no_auth"),
                            hasPassword = raw.flag("has_password"),
                            envForced = raw.flag("auth_env_forced"),
                            oidcEnabled = raw.flag("oidc_enabled"),
                            oidcActive = raw.flag("oidc_active"),
                            otpEnabled = raw.flag("otp_enabled"),
                        )
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            keys = keyStatus.keys,
                            status = status ?: it.status,
                            otpEnabled = status?.otpEnabled ?: it.otpEnabled,
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


/** The settings payload mixes real booleans with "true"/"1" strings, so read both. */
private fun kotlinx.serialization.json.JsonObject.flag(key: String): Boolean {
    val element = this[key] ?: return false
    val primitive = element as? kotlinx.serialization.json.JsonPrimitive ?: return false
    primitive.booleanOrNull?.let { return it }
    return primitive.content.lowercase() in setOf("true", "1", "yes", "on")
}

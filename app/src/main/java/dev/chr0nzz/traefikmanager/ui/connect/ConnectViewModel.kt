package dev.chr0nzz.traefikmanager.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.api.ApiFactory
import dev.chr0nzz.traefikmanager.data.model.UiText
import dev.chr0nzz.traefikmanager.data.store.ConnectionStore
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ConnectUiState(
    val url: String = "",
    val apiKey: String = "",
    val showKey: Boolean = false,
    val connecting: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectionStore: ConnectionStore,
    private val apiFactory: ApiFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }

    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, error = null) }

    fun toggleKeyVisibility() = _state.update { it.copy(showKey = !it.showKey) }

    fun connect() {
        val baseUrl = normalizeUrl(_state.value.url)
        if (baseUrl.isEmpty()) {
            _state.update { it.copy(error = UiText.of(R.string.connect_error_no_url)) }
            return
        }
        val key = _state.value.apiKey.trim()
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                apiFactory.create(baseUrl, key.ifEmpty { null }, null).apiKeyStatus()
            }
            result.fold(
                onSuccess = { status ->
                    val mismatch = when {
                        !status.enabled && key.isNotEmpty() -> UiText.of(R.string.connect_error_key_not_enabled)
                        status.enabled && key.isEmpty() -> UiText.of(R.string.connect_error_key_required)
                        else -> null
                    }
                    if (mismatch != null) {
                        _state.update { it.copy(connecting = false, error = mismatch) }
                    } else {
                        connectionStore.save(baseUrl, key.ifEmpty { null })
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(connecting = false, error = describe(throwable, key.isNotEmpty())) }
                },
            )
        }
    }

    fun tryDemo() {
        viewModelScope.launch { connectionStore.enterDemo() }
    }

    private fun describe(throwable: Throwable, keyProvided: Boolean): UiText = when (throwable) {
        is HttpException -> when (throwable.code()) {
            401 -> UiText.of(
                if (keyProvided) R.string.connect_error_key_invalid else R.string.connect_error_key_required,
            )
            else -> UiText.of(R.string.connect_error_server, throwable.code())
        }
        is IOException -> UiText.of(R.string.connect_error_unreachable)
        else -> throwable.message?.let(UiText::Raw) ?: UiText.of(R.string.connect_error_failed)
    }

    companion object {
        private val IPV4 = Regex("""^(\d{1,3}\.){3}\d{1,3}(:\d+)?$""")

        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val lower = trimmed.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed
            return if (IPV4.matches(trimmed)) "http://$trimmed" else "https://$trimmed"
        }
    }
}

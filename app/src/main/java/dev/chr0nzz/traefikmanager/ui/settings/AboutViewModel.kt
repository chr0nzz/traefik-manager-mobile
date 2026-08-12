package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AboutUiState(
    val managerVersion: String = "…",
    val traefikVersion: String = "…",
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val versions = runCatching {
                coroutineScope {
                    val api = apiProvider.api()
                    val manager = async { runCatching { api.managerVersion() }.getOrNull() }
                    val traefik = async { runCatching { api.traefikVersion() }.getOrNull() }
                    manager.await() to traefik.await()
                }
            }.getOrNull()
            _state.update {
                it.copy(
                    managerVersion = versions?.first?.version?.let { v -> "v$v" } ?: "unknown",
                    traefikVersion = versions?.second?.let { t ->
                        listOfNotNull(t.version?.let { v -> "v$v" }, t.codename).joinToString(" ")
                    }?.ifEmpty { "unknown" } ?: "unknown",
                )
            }
        }
    }
}

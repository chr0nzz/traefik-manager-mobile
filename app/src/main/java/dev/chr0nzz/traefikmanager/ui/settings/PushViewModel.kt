package dev.chr0nzz.traefikmanager.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.push.PushDistributor
import dev.chr0nzz.traefikmanager.push.PushRegistrar
import dev.chr0nzz.traefikmanager.push.PushSyncWorker
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PushUiState(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val error: String = "",
    val distributors: List<PushDistributor> = emptyList(),
    val current: String? = null,
    val picking: Boolean = false,
) {
    val currentLabel: String?
        get() = distributors.firstOrNull { it.packageName == current }?.label

    val noDistributor: Boolean get() = distributors.isEmpty()

    val registered: Boolean get() = enabled && endpoint.isNotBlank()
}

@HiltViewModel
class PushViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registrar: PushRegistrar,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PushUiState())
    val state: StateFlow<PushUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                _state.update {
                    it.copy(
                        enabled = prefs.pushEnabled,
                        endpoint = prefs.pushEndpoint,
                        error = prefs.pushError,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(distributors = registrar.distributors(), current = registrar.current()) }
        registrar.refresh()
    }

    fun enable() {
        val distributors = _state.value.distributors
        when {
            distributors.isEmpty() -> Unit
            distributors.size == 1 -> choose(distributors.first().packageName)
            else -> _state.update { it.copy(picking = true) }
        }
    }

    fun choose(packageName: String) {
        _state.update { it.copy(picking = false) }
        viewModelScope.launch {
            preferencesStore.setPushError("")
            preferencesStore.setPushEnabled(true)
            registrar.register(packageName)
            _state.update { it.copy(current = packageName) }
        }
    }

    fun cancelPicking() = _state.update { it.copy(picking = false) }

    fun disable() {
        viewModelScope.launch {
            registrar.unregister()
            _state.update { it.copy(current = null) }
            preferencesStore.setPushEnabled(false)
            PushSyncWorker.remove(context)
        }
    }

    fun onPermissionDenied() {
        viewModelScope.launch {
            preferencesStore.setPushError(
                "Android is blocking notifications for this app, so pushes will arrive silently or not at all.",
            )
        }
    }
}

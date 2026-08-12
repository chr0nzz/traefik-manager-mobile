package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How many notifications arrived since the history was last marked read, for the bell's badge. */
@HiltViewModel
class NotificationBellViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val history = runCatching { apiProvider.api().notifications() }.getOrNull() ?: return@launch
            val seen = preferencesStore.preferences.first().notificationsRead
            _unread.value = (history.size - seen).coerceAtLeast(0)
        }
    }
}

package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * How many notifications arrived since the history was last marked read, for the bell's badge.
 *
 * The count is derived rather than snapshotted: marking the history read only writes a preference,
 * and the badge has to clear on the way back to the dashboard without waiting for a refresh.
 */
@HiltViewModel
class NotificationBellViewModel @Inject constructor(
    private val apiProvider: ApiProvider,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {

    private val total = MutableStateFlow(0)

    val unread: StateFlow<Int> =
        combine(total, preferencesStore.preferences) { total, prefs ->
            (total - prefs.notificationsRead).coerceAtLeast(0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val history = runCatching { apiProvider.api().notifications() }.getOrNull() ?: return@launch
            total.value = history.size
        }
    }
}

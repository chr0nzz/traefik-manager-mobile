package dev.chr0nzz.traefikmanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.repo.NotificationsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The bell's badge. The count is the repository's, so marking the history read clears it here
 * without a refresh, and the list itself is fetched once for both screens.
 */
@HiltViewModel
class NotificationBellViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    val unread: StateFlow<Int> = repository.unread

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { runCatching { repository.refresh() } }
    }
}

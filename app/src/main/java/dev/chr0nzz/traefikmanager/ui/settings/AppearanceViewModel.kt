package dev.chr0nzz.traefikmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.data.store.ThemeMode
import dev.chr0nzz.traefikmanager.data.store.TmPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val launcherRepository: dev.chr0nzz.traefikmanager.data.repo.LauncherRepository,
    private val navEditorRequests: dev.chr0nzz.traefikmanager.ui.nav.NavEditorRequests,
) : ViewModel() {

    val density: StateFlow<String> = launcherRepository.density

    fun editNavBar() = navEditorRequests.request()

    fun setHideNavBar(hidden: Boolean) {
        viewModelScope.launch { preferencesStore.setHideNavBar(hidden) }
    }

    fun setDensity(value: String) {
        viewModelScope.launch { runCatching { launcherRepository.setDensity(value) } }
    }

    val preferences: StateFlow<TmPreferences> = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, TmPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setDynamicColor(enabled) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { preferencesStore.setAppLock(enabled) }
    }
}

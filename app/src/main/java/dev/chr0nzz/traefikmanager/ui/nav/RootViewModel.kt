package dev.chr0nzz.traefikmanager.ui.nav

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.api.ApiState
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.data.store.TmPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class RootViewModel @Inject constructor(
    apiProvider: ApiProvider,
    preferencesStore: PreferencesStore,
) : ViewModel() {

    val apiState: StateFlow<ApiState> = apiProvider.state

    val preferences = preferencesStore.preferences

    companion object {
        val DefaultPreferences = TmPreferences()
    }
}

package dev.chr0nzz.traefikmanager.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.api.ApiState
import dev.chr0nzz.traefikmanager.data.repo.DashboardRepository
import dev.chr0nzz.traefikmanager.data.repo.SignalCard
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.data.store.TmPreferences
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NavBadges(
    val routes: Int = 0,
    val middlewares: Int = 0,
    val services: Int = 0,
) {
    fun forRoute(route: String): Int = when (route) {
        TmDestination.Routes.route -> routes
        TmDestination.Middlewares.route -> middlewares
        TmDestination.Services.route -> services
        else -> 0
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    apiProvider: ApiProvider,
    private val preferencesStore: PreferencesStore,
    dashboardRepository: DashboardRepository,
) : ViewModel() {

    fun onMigrationNoticeShown() {
        viewModelScope.launch { preferencesStore.setMigrationNotice(null) }
    }

    val apiState: StateFlow<ApiState> = apiProvider.state

    val preferences = preferencesStore.preferences

    val badges: StateFlow<NavBadges> = dashboardRepository.snapshot
        .map { snapshot ->
            if (snapshot == null) {
                NavBadges()
            } else {
                NavBadges(
                    routes = snapshot.cards.filter { it.key == "http" || it.key == "stream" }.sumOf(::alertCount),
                    middlewares = snapshot.cards.filter { it.key == "middlewares" }.sumOf(::alertCount),
                    services = snapshot.cards.filter { it.key == "services" }.sumOf(::alertCount),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavBadges())

    private fun alertCount(card: SignalCard): Int = card.flags
        .filter { it.status == TmStatus.Error || it.status == TmStatus.Warn }
        .sumOf { it.count }

    companion object {
        val DefaultPreferences = TmPreferences()
    }
}

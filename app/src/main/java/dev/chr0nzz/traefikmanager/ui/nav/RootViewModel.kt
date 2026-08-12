package dev.chr0nzz.traefikmanager.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.api.ApiState
import dev.chr0nzz.traefikmanager.data.repo.DashboardRepository
import dev.chr0nzz.traefikmanager.data.repo.ServerCapabilities
import dev.chr0nzz.traefikmanager.data.repo.NavCountsStore
import dev.chr0nzz.traefikmanager.data.repo.ServerEntry
import dev.chr0nzz.traefikmanager.data.repo.ServerScope
import dev.chr0nzz.traefikmanager.data.repo.ServerSettingsRepository
import dev.chr0nzz.traefikmanager.data.repo.ServersRepository
import dev.chr0nzz.traefikmanager.data.repo.SignalCard
import dev.chr0nzz.traefikmanager.data.store.PreferencesStore
import dev.chr0nzz.traefikmanager.data.store.TmPreferences
import dev.chr0nzz.traefikmanager.ui.components.TmStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a nav entry reports: how many it holds, and how many of those want attention. */
data class NavBadge(val count: Int = 0, val alerts: Int = 0)

data class NavBadges(private val byRoute: Map<String, NavBadge> = emptyMap()) {
    fun forRoute(route: String): NavBadge = byRoute[route] ?: NavBadge()
}

@HiltViewModel
class RootViewModel @Inject constructor(
    apiProvider: ApiProvider,
    private val preferencesStore: PreferencesStore,
    private val connectionStore: dev.chr0nzz.traefikmanager.data.store.ConnectionStore,
    dashboardRepository: DashboardRepository,
    serverSettingsRepository: ServerSettingsRepository,
    private val serversRepository: ServersRepository,
    private val crowdSecRepository: dev.chr0nzz.traefikmanager.data.repo.CrowdSecRepository,
    private val geoRepository: dev.chr0nzz.traefikmanager.data.repo.GeoRepository,
    private val serverScope: ServerScope,
    private val navCountsStore: dev.chr0nzz.traefikmanager.data.repo.NavCountsStore,
    private val certificatesRepository: dev.chr0nzz.traefikmanager.data.repo.CertificatesRepository,
    private val pluginsRepository: dev.chr0nzz.traefikmanager.data.repo.PluginsRepository,
) : ViewModel() {

    private val _servers = MutableStateFlow<List<ServerEntry>>(emptyList())
    val servers: StateFlow<List<ServerEntry>> = _servers.asStateFlow()

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    val activeServer: StateFlow<ServerEntry?> = combine(serverScope.activeAgentId, _servers) { id, list ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun loadServers() {
        viewModelScope.launch {
            val fetched = runCatching { serversRepository.servers() }.getOrDefault(emptyList())
            if (fetched.size > 1 || _servers.value.isEmpty()) _servers.value = fetched
        }
    }

    fun switchServer(id: String?) {
        if (id == serverScope.activeAgentId.value) return
        _switching.value = true
        viewModelScope.launch {
            // A failed write must not leave the header stuck on "switching…".
            runCatching { serversRepository.select(id) }
            _switching.value = false
        }
    }

    /** Capabilities of whichever server is selected: the host answers for itself, an agent for itself. */
    val capabilities: StateFlow<ServerCapabilities> = combine(
        serverSettingsRepository.settings,
        serverScope.activeAgentId,
        _servers,
    ) { settings, agentId, servers ->
        val entry = servers.firstOrNull { it.id == agentId }
        ServerCapabilities(
            isHost = agentId == null,
            name = entry?.name ?: "Host",
            settings = settings,
            agent = entry?.agent,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ServerCapabilities())

    /** Destinations the selected server has switched on. Unknown means "show everything". */
    val destinations: StateFlow<List<TmDestination>> = capabilities
        .map { capabilities ->
            TmDestination.entries.filter { destination ->
                val tab = destination.serverTab ?: return@filter true
                val visible = capabilities.tabVisible(tab)
                if (destination == TmDestination.CrowdSec) visible && capabilities.crowdsecConfigured else visible
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TmDestination.entries.toList())

    init {
        serverSettingsRepository.refresh()
        loadServers()
        loadNavCounts()
        viewModelScope.launch {
            serverScope.generation.drop(1).collect {
                loadServers()
                loadNavCounts()
            }
        }
    }

    /** Totals the sidebar shows but no screen has fetched yet. Failures just leave the badge off. */
    private fun loadNavCounts() {
        viewModelScope.launch { runCatching { certificatesRepository.load() } }
        viewModelScope.launch { runCatching { pluginsRepository.count() } }
    }

    /** Forget the server, its API key and every cached page on this device. */
    fun disconnect() {
        viewModelScope.launch {
            crowdSecRepository.forget()
            geoRepository.clear()
            preferencesStore.setActiveAgent(null)
            connectionStore.clear()
        }
    }

    fun onMigrationNoticeShown() {
        viewModelScope.launch { preferencesStore.setMigrationNotice(null) }
    }

    val apiState: StateFlow<ApiState> = apiProvider.state

    val preferences = preferencesStore.preferences

    val badges: StateFlow<NavBadges> = combine(
        dashboardRepository.snapshot,
        navCountsStore.counts,
    ) { snapshot, counts ->
        val cards = snapshot?.cards.orEmpty()
        fun cardsFor(vararg keys: String) = cards.filter { it.key in keys }
        val routers = cardsFor("http", "stream")
        val services = cardsFor("services")
        val middlewares = cardsFor("middlewares")
        NavBadges(
            buildMap {
                put(
                    TmDestination.Routes.route,
                    NavBadge(routers.sumOf(::total), routers.sumOf(::alertCount)),
                )
                put(
                    TmDestination.Services.route,
                    NavBadge(services.sumOf(::total), services.sumOf(::alertCount)),
                )
                put(
                    TmDestination.Middlewares.route,
                    NavBadge(middlewares.sumOf(::total), middlewares.sumOf(::alertCount)),
                )
                counts[NavCountsStore.CERTIFICATES]?.let {
                    put(TmDestination.Certificates.route, NavBadge(count = it))
                }
                counts[NavCountsStore.PLUGINS]?.let {
                    put(TmDestination.Plugins.route, NavBadge(count = it))
                }
                counts[NavCountsStore.CROWDSEC]?.let {
                    put(TmDestination.CrowdSec.route, NavBadge(count = it))
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavBadges())

    private fun total(card: SignalCard): Int = card.total ?: 0

    private fun alertCount(card: SignalCard): Int = card.flags
        .filter { it.status == TmStatus.Error || it.status == TmStatus.Warn }
        .sumOf { it.count }

    companion object {
        val DefaultPreferences = TmPreferences()
    }
}

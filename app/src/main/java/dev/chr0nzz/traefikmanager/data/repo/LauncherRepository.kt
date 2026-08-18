package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.CustomGroup
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.LauncherBuilder
import dev.chr0nzz.traefikmanager.data.model.LauncherGroup
import dev.chr0nzz.traefikmanager.data.model.RouteOverride
import dev.chr0nzz.traefikmanager.data.model.UiPrefs
import dev.chr0nzz.traefikmanager.data.model.UiPrefsRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class LauncherSnapshot(
    val groups: List<LauncherGroup> = emptyList(),
    val config: DashboardConfig = DashboardConfig(),
    val baseUrl: String = "",
    val hidden: List<dev.chr0nzz.traefikmanager.data.model.LauncherApp> = emptyList(),
    val density: String = "list",
)

/**
 * The dashboard launcher: the routes of the selected server, dressed with the overrides the hub
 * holds for it.
 *
 * The config is a hub document scoped by a query parameter rather than by the agent proxy, so
 * every call here names the server instead of relying on the interceptor.
 */
@Singleton
class LauncherRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val routesRepository: RoutesRepository,
) {

    private val _density = kotlinx.coroutines.flow.MutableStateFlow("list")

    /** Watched by both the launcher and the appearance screen, so a change lands on both. */
    val density: kotlinx.coroutines.flow.StateFlow<String> = _density

    suspend fun load(): LauncherSnapshot = coroutineScope {
        val ready = apiProvider.ready()
        val config = async { runCatching { ready.api.dashboardConfig(ready.agentId) }.getOrDefault(DashboardConfig()) }
        val routes = async { runCatching { routesRepository.load().routes }.getOrDefault(emptyList()) }
        val prefs = async { runCatching { ready.api.uiPrefs() }.getOrNull() }
        val loaded = config.await()
        val all = routes.await()
        LauncherSnapshot(
            groups = LauncherBuilder.build(all, loaded, includeHidden = false),
            config = loaded,
            baseUrl = ready.baseUrl,
            hidden = all.filter { loaded.routeOverrides[it.id]?.hidden == true }
                .map { LauncherBuilder.app(it, loaded) },
            density = prefs.await()?.uiPrefs?.dashPodDensity.orEmpty()
                .also { fetched -> if (fetched.isNotEmpty()) _density.value = fetched },
        )
    }

    /**
     * Saving one card still posts the whole document: the hub replaces custom_groups and
     * route_overrides with whatever arrives, so anything left out is deleted.
     */
    private suspend fun save(config: DashboardConfig) {
        val ready = apiProvider.ready()
        val response = ready.api.saveDashboardConfig(
            body = config.copy(server = ready.agentId.orEmpty()),
            server = ready.agentId,
        )
        if (!response.ok) error(response.error ?: response.message ?: "Could not save the dashboard")
    }

    suspend fun saveOverride(config: DashboardConfig, routeId: String, override: RouteOverride) {
        val cleaned = if (override == RouteOverride()) {
            config.routeOverrides - routeId
        } else {
            config.routeOverrides + (routeId to override)
        }
        save(config.copy(routeOverrides = cleaned))
    }

    suspend fun addGroup(config: DashboardConfig, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || config.customGroups.any { it.name.equals(trimmed, true) }) return
        save(config.copy(customGroups = config.customGroups + CustomGroup(trimmed)))
    }

    suspend fun removeGroup(config: DashboardConfig, name: String) {
        // Cards pointing at a group that no longer exists fall back to auto-detection.
        val overrides = config.routeOverrides.mapValues { (_, override) ->
            if (override.group == name) override.copy(group = "") else override
        }
        save(
            config.copy(
                customGroups = config.customGroups.filterNot { it.name == name },
                routeOverrides = overrides,
            ),
        )
    }

    /** The density lives in the hub's ui_prefs, which merges, so this one key is safe to send. */
    suspend fun setDensity(density: String) {
        _density.value = density
        val ready = apiProvider.ready()
        val current = runCatching { ready.api.uiPrefs().uiPrefs }.getOrDefault(UiPrefs())
        ready.api.saveUiPrefs(UiPrefsRequest(current.copy(dashPodDensity = density)))
    }

    suspend fun setHidden(config: DashboardConfig, routeId: String, hidden: Boolean) {
        val current = config.routeOverrides[routeId] ?: RouteOverride()
        saveOverride(config, routeId, current.copy(hidden = hidden))
    }
}

package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ConfigsResponse
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.PingResult
import dev.chr0nzz.traefikmanager.data.model.RawRoute
import dev.chr0nzz.traefikmanager.data.model.RawRouteSave
import dev.chr0nzz.traefikmanager.data.model.ConfigError
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteForm
import dev.chr0nzz.traefikmanager.data.model.RouteFormEncoder
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.data.model.ServicesByProtocol
import dev.chr0nzz.traefikmanager.data.model.TlsOptionProfile
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody

data class IconContext(
    val enabled: Boolean = false,
    val config: DashboardConfig = DashboardConfig(),
    val baseUrl: String = "",
)

data class RoutesSnapshot(
    val routes: List<Route>,
    val middlewares: List<MiddlewareDef>,
    val configErrors: List<ConfigError>,
    val services: ServicesByProtocol = ServicesByProtocol(),
)

@Singleton
class RoutesRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun notifyChanged() {
        _changes.value = _changes.value + 1
    }

    suspend fun load(): RoutesSnapshot {
        val ready = apiProvider.ready()
        val response = if (ready.agentId != null) {
            ready.api.agentRoutes(ready.agentId)
        } else {
            ready.api.routes()
        }
        return RoutesSnapshot(response.apps, response.middlewares, response.configErrors, response.services)
    }

    suspend fun toggle(routeId: String, enable: Boolean) {
        val ready = apiProvider.ready()
        val result = ready.api.toggleRoute(routeId, ToggleRequest(enable, ready.agentId.orEmpty()))
        if (!result.ok) error(result.message ?: result.error ?: "Could not toggle the route")
    }

    suspend fun save(form: RouteForm) {
        val ready = apiProvider.ready()
        val body = FormBody.Builder().apply {
            RouteFormEncoder.fields(form, ready.agentId).forEach { (name, value) ->
                add(name, value)
            }
        }.build()
        val result = ready.api.saveRoute(body)
        if (!result.ok) error(result.message ?: result.error ?: "Could not save the route")
        notifyChanged()
    }

    suspend fun delete(route: Route) {
        val ready = apiProvider.ready()
        val body = FormBody.Builder()
            .add("configFile", route.configFile)
            .add("agent_id", ready.agentId.orEmpty())
            .build()
        val result = ready.api.deleteRoute(route.id, body)
        if (!result.ok) error(result.message ?: result.error ?: "Could not delete the route")
        notifyChanged()
    }

    suspend fun rawYaml(routeId: String): RawRoute = apiProvider.api().routeRaw(routeId)

    suspend fun saveRawYaml(routeId: String, content: String) {
        val result = apiProvider.api().saveRouteRaw(routeId, RawRouteSave(content))
        if (!result.ok) error(result.error ?: result.message ?: "Could not save the YAML")
        notifyChanged()
    }

    suspend fun ping(route: Route): PingResult {
        val host = route.hosts.firstOrNull()
            ?: return PingResult(ok = false, error = "This route has no host to ping")
        val fallback = route.target.takeIf { it.isNotEmpty() && it != "N/A" }
        return apiProvider.api().ping("https://$host", fallback)
    }

    suspend fun iconContext(): IconContext {
        val ready = apiProvider.ready()
        val prefs = runCatching { ready.api.uiPrefs().uiPrefs }.getOrNull()
        if (prefs?.showRouteIcons != true) return IconContext(enabled = false)
        val config = runCatching { ready.api.dashboardConfig(ready.agentId) }.getOrDefault(DashboardConfig())
        return IconContext(enabled = true, config = config, baseUrl = ready.baseUrl)
    }

    suspend fun serverSettings(): ServerSettings =
        runCatching { apiProvider.api().settings() }.getOrDefault(ServerSettings())

    suspend fun certResolvers(): List<String> {
        val ready = apiProvider.ready()
        val agentId = ready.agentId
        if (agentId != null) {
            return runCatching { ready.api.agentCertResolvers(agentId).resolvers }.getOrDefault(emptyList())
        }
        val configured = runCatching { ready.api.settings().certResolvers }.getOrDefault(emptyList())
        val fromStatic = runCatching {
            ready.api.staticConfig().parsed?.get("certificatesResolvers")?.jsonObject?.keys?.toList()
        }.getOrNull().orEmpty()
        return (configured + fromStatic).distinct()
    }

    suspend fun entryPointNames(): List<String> =
        runCatching { apiProvider.api().entrypoints().map { it.name }.filter { it.isNotEmpty() } }
            .getOrDefault(emptyList())

    suspend fun configs(): ConfigsResponse =
        runCatching { apiProvider.api().configs() }.getOrDefault(ConfigsResponse())

    suspend fun tlsOptions(): List<TlsOptionProfile> {
        val ready = apiProvider.ready()
        return runCatching { ready.api.tlsOptions(ready.agentId) }.getOrDefault(emptyList())
    }
}

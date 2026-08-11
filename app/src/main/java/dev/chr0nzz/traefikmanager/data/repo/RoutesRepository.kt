package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ConfigError
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import javax.inject.Inject
import javax.inject.Singleton

data class RoutesSnapshot(
    val routes: List<Route>,
    val configErrors: List<ConfigError>,
)

@Singleton
class RoutesRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {

    suspend fun load(): RoutesSnapshot {
        val ready = apiProvider.ready()
        val response = if (ready.agentId != null) {
            ready.api.agentRoutes(ready.agentId)
        } else {
            ready.api.routes()
        }
        return RoutesSnapshot(response.apps, response.configErrors)
    }

    suspend fun toggle(routeId: String, enable: Boolean) {
        val ready = apiProvider.ready()
        val result = ready.api.toggleRoute(routeId, ToggleRequest(enable, ready.agentId.orEmpty()))
        if (!result.ok) {
            throw IllegalStateException(result.message ?: result.error ?: "Toggle failed")
        }
    }
}

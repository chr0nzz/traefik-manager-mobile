package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.RouteHealth
import dev.chr0nzz.traefikmanager.data.model.RouteHealthSettings
import dev.chr0nzz.traefikmanager.data.model.RouteHealthSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

@Singleton
class RouteHealthRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serverScope: ServerScope,
) {

    private val _snapshot = MutableStateFlow<RouteHealthSnapshot?>(null)
    val snapshot: StateFlow<RouteHealthSnapshot?> = _snapshot.asStateFlow()

    private val _supported = MutableStateFlow<Boolean?>(null)
    val supported: StateFlow<Boolean?> = _supported.asStateFlow()

    init {
        serverScope.onServerChanged {
            _snapshot.value = null
            _supported.value = null
        }
    }

    fun healthFor(routeId: String): RouteHealth? = _snapshot.value?.routes?.get(routeId)

    suspend fun refresh(): RouteHealthSnapshot? = try {
        apiProvider.api().routeHealth(agentId = serverScope.activeAgentId.value)
            .also {
                _snapshot.value = it
                _supported.value = true
            }
    } catch (exception: HttpException) {
        if (exception.code() == 404) _supported.value = false
        null
    } catch (exception: Exception) {
        null
    }

    suspend fun save(enabled: Boolean, interval: Int): RouteHealthSettings {
        val response = try {
            apiProvider.apiFor(null).saveRouteHealth(RouteHealthSettings(enabled, interval))
        } catch (exception: HttpException) {
            throw InUse.from(exception, "Could not save the route checks")
        }
        if (!response.ok) error(response.error ?: "Could not save the route checks")
        _snapshot.value = _snapshot.value?.copy(enabled = response.enabled, interval = response.interval)
        return RouteHealthSettings(response.enabled, response.interval)
    }
}

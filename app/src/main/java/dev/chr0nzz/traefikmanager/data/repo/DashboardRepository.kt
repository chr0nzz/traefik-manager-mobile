package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class DashboardRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {

    suspend fun load(): DashboardSnapshot = coroutineScope {
        val api = apiProvider.api()
        val overview = async { runCatching { api.overview() }.getOrNull() }
        val entrypoints = async { runCatching { api.entrypoints() }.getOrNull() }
        val routers = async { runCatching { api.routers() }.getOrNull() }
        val services = async { runCatching { api.services() }.getOrNull() }
        val middlewares = async { runCatching { api.middlewares() }.getOrNull() }
        DashboardBuilder.build(
            overview = overview.await(),
            entrypoints = entrypoints.await(),
            routers = routers.await(),
            services = services.await(),
            middlewares = middlewares.await(),
        )
    }
}

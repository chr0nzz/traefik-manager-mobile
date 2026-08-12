package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.PluginEntry
import dev.chr0nzz.traefikmanager.data.model.PluginUsage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class PluginsSnapshot(
    val plugins: List<PluginEntry> = emptyList(),
    val usage: Map<String, Int> = emptyMap(),
    val middlewares: List<MiddlewareDef> = emptyList(),
    val error: String? = null,
)

@Singleton
class PluginsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val routesRepository: RoutesRepository,
    private val navCounts: NavCountsStore,
) {

    /** Just the total, for the nav badge: skips the route fetch that usage counts would need. */
    suspend fun count(): Int = apiProvider.api().plugins().plugins.size
        .also { navCounts.report(NavCountsStore.PLUGINS, it) }

    suspend fun load(): PluginsSnapshot = coroutineScope {
        val pluginsCall = async { apiProvider.api().plugins() }
        val middlewaresCall = async { runCatching { routesRepository.load().middlewares }.getOrDefault(emptyList()) }
        val response = pluginsCall.await()
        val middlewares = middlewaresCall.await()
        val plugins = response.plugins.sortedBy { it.name.lowercase() }
        navCounts.report(NavCountsStore.PLUGINS, plugins.size)
        PluginsSnapshot(
            plugins = plugins,
            usage = PluginUsage.countsFor(plugins, middlewares),
            middlewares = middlewares,
            error = response.error?.takeIf { it.isNotBlank() },
        )
    }
}

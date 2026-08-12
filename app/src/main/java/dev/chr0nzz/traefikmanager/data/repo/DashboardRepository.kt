package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import dev.chr0nzz.traefikmanager.di.ApplicationScope
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RawDashboard(
    val overview: Overview? = null,
    val entrypoints: List<Entrypoint>? = null,
    val routers: ProtoEnvelope? = null,
    val services: ServiceEnvelope? = null,
    val middlewares: ProtoEnvelope? = null,
    val version: TraefikVersion? = null,
)

@Singleton
class DashboardRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    serverScope: ServerScope,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _raw = MutableStateFlow<RawDashboard?>(null)
    val raw: StateFlow<RawDashboard?> = _raw.asStateFlow()

    // A fetch in flight when the server changes must never land on the new server's screen.
    private val generation = AtomicInteger(0)

    private val inFlightLock = Any()
    private var inFlight: Deferred<RawDashboard>? = null

    init {
        serverScope.onServerChanged {
            generation.incrementAndGet()
            _raw.value = null
            scope.launch { runCatching { refresh() } }
        }
    }

    val snapshot: StateFlow<DashboardSnapshot?> = _raw
        .map { raw -> raw?.let { DashboardBuilder.build(it, providerFilter = null) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The screen, the pull-to-refresh and the server-change listener all ask for this at once;
     * they share one fetch instead of firing six requests each.
     */
    suspend fun refresh(): RawDashboard {
        // Runs on the application scope so a screen leaving does not cancel a fetch others await.
        val job = synchronized(inFlightLock) {
            inFlight?.takeIf { it.isActive } ?: scope.async { fetch() }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun fetch(): RawDashboard = coroutineScope {
        val startedAt = generation.get()
        val api = apiProvider.api()
        val overview = async { runCatching { api.overview() }.getOrNull() }
        val entrypoints = async { runCatching { api.entrypoints() }.getOrNull() }
        val routers = async { runCatching { api.routers() }.getOrNull() }
        val services = async { runCatching { api.services() }.getOrNull() }
        val middlewares = async { runCatching { api.middlewares() }.getOrNull() }
        val version = async { runCatching { api.traefikVersion() }.getOrNull() }
        val fetched = RawDashboard(
            overview = overview.await(),
            entrypoints = entrypoints.await(),
            routers = routers.await(),
            services = services.await(),
            middlewares = middlewares.await(),
            version = version.await(),
        )
        if (generation.get() == startedAt) _raw.value = fetched
        fetched
    }
}

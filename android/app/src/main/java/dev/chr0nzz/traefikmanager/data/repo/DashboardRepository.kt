package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import dev.chr0nzz.traefikmanager.data.store.SnapshotStore
import dev.chr0nzz.traefikmanager.di.ApplicationScope
import kotlinx.serialization.Serializable
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

@Serializable
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
    private val snapshots: SnapshotStore,
    serverScope: ServerScope,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val _raw = MutableStateFlow<RawDashboard?>(null)
    val raw: StateFlow<RawDashboard?> = _raw.asStateFlow()

    private val generation = AtomicInteger(0)

    private val inFlightLock = Any()
    private var inFlight: Deferred<RawDashboard>? = null

    init {
        scope.launch { restore() }
        serverScope.onServerChanged {
            generation.incrementAndGet()
            _raw.value = null
            scope.launch {
                restore()
                runCatching { refresh() }
            }
        }
    }

    private suspend fun restore() {
        val ready = runCatching { apiProvider.ready() }.getOrNull() ?: return
        if (ready.demo) return
        val started = generation.get()
        val stored = snapshots.read(SNAPSHOT, snapshots.keyFor(ready.baseUrl, ready.agentId), RawDashboard.serializer())
        if (stored != null && _raw.value == null && generation.get() == started) {
            _raw.value = stored
        }
    }

    val snapshot: StateFlow<DashboardSnapshot?> = _raw
        .map { raw -> raw?.let { DashboardBuilder.build(it, providerFilter = null) } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun refresh(): RawDashboard {
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
        if (generation.get() == startedAt) {
            _raw.value = fetched
            val ready = apiProvider.ready()
            if (!ready.demo) {
                snapshots.write(
                    SNAPSHOT,
                    snapshots.keyFor(ready.baseUrl, ready.agentId),
                    fetched,
                    RawDashboard.serializer(),
                )
            }
        }
        fetched
    }

    private companion object {
        const val SNAPSHOT = "dashboard"
    }
}

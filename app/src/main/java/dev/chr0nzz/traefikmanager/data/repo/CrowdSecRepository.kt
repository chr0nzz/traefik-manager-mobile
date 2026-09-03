package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.store.SnapshotStore
import kotlinx.serialization.Serializable
import dev.chr0nzz.traefikmanager.data.model.AddDecisionRequest
import dev.chr0nzz.traefikmanager.data.model.CrowdSecAnalytics
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsRead
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response

@Serializable
private data class StoredCrowdSec(
    val decisions: List<CsDecision> = emptyList(),
    val alerts: List<CsAlert> = emptyList(),
    val alertLimit: Int? = null,
    val alertsCapped: Boolean? = null,
)

private data class CachedRead(val snapshot: CrowdSecSnapshot, val loadedAt: Long)

@Singleton
class CrowdSecRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val snapshots: SnapshotStore,
    private val serverScope: ServerScope,
    private val navCounts: NavCountsStore,
) {

    private val cache = mutableMapOf<String, CachedRead>()

    private fun key(): String = serverScope.activeAgentId.value ?: HOST_KEY

    fun cached(): CrowdSecSnapshot? = cache[key()]?.snapshot

    suspend fun restore(): CrowdSecSnapshot? {
        cache[key()]?.let { return it.snapshot }
        val ready = runCatching { apiProvider.ready() }.getOrNull() ?: return null
        if (ready.demo) return null
        val stored = snapshots.read(
            SNAPSHOT,
            snapshots.keyFor(ready.baseUrl, ready.agentId),
            StoredCrowdSec.serializer(),
        ) ?: return null
        val snapshot = CrowdSecSnapshot(
            decisions = CsRead.Loaded(stored.decisions),
            alerts = CsRead.Loaded(stored.alerts),
            alertLimit = stored.alertLimit,
            alertsCapped = stored.alertsCapped,
        )
        if (cache[key()] == null) cache[key()] = CachedRead(snapshot, 0L)
        return snapshot
    }

    fun cachedAge(): Long? = cache[key()]?.let { System.currentTimeMillis() - it.loadedAt }

    fun forget() = cache.clear()

    suspend fun load(full: Boolean): CrowdSecSnapshot = coroutineScope {
        val api = apiProvider.api()
        val decisionsCall = async { runCatching { api.crowdSecDecisions(if (full) "1" else null) } }
        val alertsCall = async { runCatching { api.crowdSecAlerts() } }

        val decisions = decisionsCall.await().fold(
            onSuccess = { response -> read(response) { it.orEmpty() } },
            onFailure = { CsRead.Failed(it.message ?: "Could not reach the CrowdSec LAPI", null) },
        )
        val alertsResponse = alertsCall.await()
        val alerts = alertsResponse.fold(
            onSuccess = { response ->
                read(response) { list -> CrowdSecAnalytics.filterAlerts(list.orEmpty()) }
            },
            onFailure = { CsRead.Failed(it.message ?: "Could not reach the CrowdSec LAPI", null) },
        )
        val headers = alertsResponse.getOrNull()?.headers()
        val staleNote = decisionsCall.await().getOrNull()?.headers()?.get("X-CS-Stale")

        val snapshot = CrowdSecSnapshot(
            decisions = decisions.withHint(),
            alerts = alerts,
            alertLimit = headers?.get("X-CS-Alert-Limit")?.toIntOrNull(),
            decisionsStale = staleNote?.takeIf { it.isNotBlank() },
            alertsCapped = headers?.get("X-CS-Alert-Capped")?.let { it == "1" },
        )
        if (snapshot.decisions.ok || snapshot.alerts.ok) {
            cache[key()] = CachedRead(snapshot, System.currentTimeMillis())
            val ready = apiProvider.ready()
            if (!ready.demo && (snapshot.decisions.ok || snapshot.alerts.ok)) {
                snapshots.write(
                    SNAPSHOT,
                    snapshots.keyFor(ready.baseUrl, ready.agentId),
                    StoredCrowdSec(
                        decisions = snapshot.decisionList,
                        alerts = snapshot.alertList,
                        alertLimit = snapshot.alertLimit,
                        alertsCapped = snapshot.alertsCapped,
                    ),
                    StoredCrowdSec.serializer(),
                )
            }
        }
        snapshot.alerts.valueOrNull()?.let { navCounts.report(NavCountsStore.CROWDSEC, it.size) }
        snapshot
    }

    suspend fun addDecision(request: AddDecisionRequest) {
        val response = apiProvider.api().crowdSecAddDecision(request)
        if (!response.isSuccessful) error(errorMessage(response) ?: "Could not add the decision")
        cache.remove(key())
    }

    suspend fun deleteDecision(id: Long) {
        val response = apiProvider.api().crowdSecDeleteDecision(id)
        if (!response.isSuccessful) error(errorMessage(response) ?: "Could not delete the decision")
        cache.remove(key())
    }

    private fun <T, R> read(response: Response<T>, transform: (T?) -> R): CsRead<R> = when {
        response.isSuccessful -> CsRead.Loaded(transform(response.body()))
        response.code() == 404 -> CsRead.NotConfigured
        else -> CsRead.Failed(
            message = errorMessage(response) ?: "CrowdSec LAPI unavailable (HTTP ${response.code()})",
            status = response.code(),
        )
    }

    private fun CsRead<List<CsDecision>>.withHint(): CsRead<List<CsDecision>> {
        val failed = this as? CsRead.Failed ?: return this
        val message = failed.message
        if (!message.contains("403") || message.contains("bouncer", ignoreCase = true)) return this
        return CsRead.Failed(
            message = message + ". CrowdSec only accepts a bouncer key on /v1/decisions, the machine " +
                "token is refused there, so CROWDSEC_API_KEY has to be set as well.",
            status = failed.status,
        )
    }

    private companion object {
        const val HOST_KEY = "__host__"
        const val SNAPSHOT = "crowdsec"
    }

    private fun errorMessage(response: Response<*>): String? {
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (parsed["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}

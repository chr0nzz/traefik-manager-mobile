package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.store.SnapshotStore
import kotlinx.serialization.Serializable
import dev.chr0nzz.traefikmanager.data.model.AddDecisionRequest
import dev.chr0nzz.traefikmanager.data.model.CrowdSecSnapshot
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.CsDecisionFeed
import dev.chr0nzz.traefikmanager.data.model.CsDecisionPage
import dev.chr0nzz.traefikmanager.data.model.CsDecisionQuery
import dev.chr0nzz.traefikmanager.data.model.CsDecisionsSummary
import dev.chr0nzz.traefikmanager.data.model.CsRead
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredCrowdSec(
    val decisions: List<CsDecision> = emptyList(),
    val alerts: List<CsAlert> = emptyList(),
    val alertLimit: Int? = null,
    val alertsCapped: Boolean? = null,
    val counts: CsDecisionsSummary? = null,
    val version: String? = null,
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
            counts = stored.counts,
            version = stored.version?.takeIf { stored.counts != null },
        )
        if (cache[key()] == null) cache[key()] = CachedRead(snapshot, 0L)
        return snapshot
    }

    fun cachedAge(): Long? = cache[key()]?.let { System.currentTimeMillis() - it.loadedAt }

    fun forget() = cache.clear()

    suspend fun load(full: Boolean): CrowdSecSnapshot {
        val previous = cache[key()]?.snapshot
        val snapshot = CrowdSecReader.read(apiProvider.api(), full, previous)
        if (previous != null && snapshot === previous) {
            cache[key()] = CachedRead(snapshot, System.currentTimeMillis())
            snapshot.alerts.valueOrNull()?.let { navCounts.report(NavCountsStore.CROWDSEC, it.size) }
            return snapshot
        }
        if (snapshot.decisions.ok || snapshot.alerts.ok) {
            cache[key()] = CachedRead(snapshot, System.currentTimeMillis())
            val ready = apiProvider.ready()
            if (!ready.demo) {
                val complete = snapshot.decisions.ok && snapshot.alerts.ok
                snapshots.write(
                    SNAPSHOT,
                    snapshots.keyFor(ready.baseUrl, ready.agentId),
                    StoredCrowdSec(
                        decisions = snapshot.decisionList,
                        alerts = snapshot.alertList,
                        alertLimit = snapshot.alertLimit,
                        alertsCapped = snapshot.alertsCapped,
                        counts = snapshot.counts,
                        version = snapshot.version?.takeIf { complete },
                    ),
                    StoredCrowdSec.serializer(),
                )
            }
        }
        snapshot.alerts.valueOrNull()?.let { navCounts.report(NavCountsStore.CROWDSEC, it.size) }
        return snapshot
    }

    suspend fun searchDecisions(query: CsDecisionQuery, page: Int): CsRead<CsDecisionPage> =
        runCatching { CrowdSecReader.search(apiProvider.api(), query, page, CsDecisionFeed.PER) }
            .getOrElse { CsRead.Failed(it.message ?: "Could not read decisions", null) }

    suspend fun addDecision(request: AddDecisionRequest) {
        val response = apiProvider.api().crowdSecAddDecision(request)
        if (!response.isSuccessful) error(CrowdSecReader.errorMessage(response) ?: "Could not add the decision")
        cache.remove(key())
    }

    suspend fun deleteDecision(id: Long) {
        val response = apiProvider.api().crowdSecDeleteDecision(id)
        if (!response.isSuccessful) error(CrowdSecReader.errorMessage(response) ?: "Could not delete the decision")
        cache.remove(key())
    }

    private companion object {
        const val HOST_KEY = "__host__"
        const val SNAPSHOT = "crowdsec"
    }
}

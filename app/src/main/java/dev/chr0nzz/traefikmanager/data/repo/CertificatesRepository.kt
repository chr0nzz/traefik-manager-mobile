package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.store.SnapshotStore
import dev.chr0nzz.traefikmanager.data.model.CertsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificatesRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val snapshots: SnapshotStore,
    private val navCounts: NavCountsStore,
) {
    suspend fun cached(): CertsResponse? {
        val ready = runCatching { apiProvider.ready() }.getOrNull() ?: return null
        if (ready.demo) return null
        return snapshots.read(
            SNAPSHOT,
            snapshots.keyFor(ready.baseUrl, ready.agentId),
            CertsResponse.serializer(),
        )
    }

    suspend fun load(): CertsResponse {
        val ready = apiProvider.ready()
        val response = ready.api.certs()
        navCounts.report(NavCountsStore.CERTIFICATES, response.certs.size)
        if (!ready.demo) {
            snapshots.write(
                SNAPSHOT,
                snapshots.keyFor(ready.baseUrl, ready.agentId),
                response,
                CertsResponse.serializer(),
            )
        }
        return response
    }

    private companion object {
        const val SNAPSHOT = "certs"
    }
}

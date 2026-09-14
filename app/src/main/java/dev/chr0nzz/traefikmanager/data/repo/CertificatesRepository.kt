package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.store.SnapshotStore
import dev.chr0nzz.traefikmanager.data.model.CertDeleteRequest
import dev.chr0nzz.traefikmanager.data.model.CertDeleteResponse
import dev.chr0nzz.traefikmanager.data.model.CertManageState
import dev.chr0nzz.traefikmanager.data.model.CertRef
import dev.chr0nzz.traefikmanager.data.model.CertUsage
import dev.chr0nzz.traefikmanager.data.model.CertsResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

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

    suspend fun manage(): CertManageState {
        val ready = apiProvider.ready()
        val response = ready.api.certManage(ready.agentId)
        return response.body()?.takeIf { response.isSuccessful }
            ?: CertManageState(reason = "This server is too old to remove certificates")
    }

    suspend fun usage(exclude: List<String> = emptyList()): CertUsage? {
        val ready = apiProvider.ready()
        val response = runCatching { ready.api.certUsage(ready.agentId, exclude.ifEmpty { null }) }.getOrNull()
            ?: return null
        return response.body()?.takeIf { response.isSuccessful }
    }

    suspend fun remove(certs: List<CertRef>): CertDeleteResponse {
        val ready = apiProvider.ready()
        val response = try {
            ready.api.deleteCerts(CertDeleteRequest(server = ready.agentId.orEmpty(), certs = certs))
        } catch (exception: IOException) {
            return CertDeleteResponse(error = LOST_CONNECTION)
        }
        if (response.code() == 502 || response.code() == 504) {
            return CertDeleteResponse(error = LOST_CONNECTION)
        }
        response.body()?.let { return it }
        val parsed = runCatching {
            json.decodeFromString(CertDeleteResponse.serializer(), response.errorBody()?.string().orEmpty())
        }.getOrNull()
        return parsed ?: CertDeleteResponse(error = "Could not remove the certificate (HTTP ${response.code()})")
    }

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val SNAPSHOT = "certs"
        const val LOST_CONNECTION = "Lost the connection while Traefik restarted. Pull to refresh to check."
    }
}

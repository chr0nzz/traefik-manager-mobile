package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServiceOwnershipRequest
import dev.chr0nzz.traefikmanager.data.model.ServicePayload
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

@Singleton
class ServicesRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val dashboardRepository: DashboardRepository,
    private val serverScope: ServerScope,
) {

    val authorable: Boolean get() = serverScope.activeAgentId.value == null

    fun cached(): ServiceEnvelope? = dashboardRepository.raw.value?.services

    suspend fun load(): ServiceEnvelope = try {
        apiProvider.api().services()
    } catch (exception: HttpException) {
        error(upstreamMessage(exception) ?: "Traefik API not reachable (HTTP ${exception.code()})")
    }

    suspend fun save(payload: ServicePayload): String = try {
        val response = apiProvider.api().saveService(payload)
        if (!response.ok) error(response.error ?: "Could not save the service")
        response.name.ifBlank { payload.name }
    } catch (exception: HttpException) {
        error(upstreamMessage(exception) ?: "Could not save the service (HTTP ${exception.code()})")
    }

    suspend fun delete(name: String) {
        try {
            apiProvider.api().deleteService(name)
        } catch (exception: HttpException) {
            error(upstreamMessage(exception) ?: "Could not delete the service (HTTP ${exception.code()})")
        }
    }

    suspend fun setOwnership(name: String, adopt: Boolean): Boolean = try {
        val response = apiProvider.api().setServiceOwnership(name, ServiceOwnershipRequest(adopt))
        if (!response.ok) error(response.error ?: "Could not change the ownership")
        response.owned
    } catch (exception: HttpException) {
        error(upstreamMessage(exception) ?: "Could not change the ownership (HTTP ${exception.code()})")
    }

    private fun upstreamMessage(exception: HttpException): String? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (parsed["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}

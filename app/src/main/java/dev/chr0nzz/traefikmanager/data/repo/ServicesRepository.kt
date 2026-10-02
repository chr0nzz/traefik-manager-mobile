package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.failHttp
import dev.chr0nzz.traefikmanager.data.model.fail
import dev.chr0nzz.traefikmanager.R
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

    private fun agent(): String? = serverScope.activeAgentId.value

    fun cached(): ServiceEnvelope? = dashboardRepository.raw.value?.services

    suspend fun load(): ServiceEnvelope = try {
        apiProvider.api().services(agentId = agent())
    } catch (exception: HttpException) {
        failHttp(upstreamMessage(exception), R.string.common_error_traefik_unreachable, exception.code())
    }

    suspend fun save(payload: ServicePayload): String = try {
        val response = apiProvider.api().saveService(payload.copy(agentId = agent().orEmpty()))
        if (!response.ok) fail(response.error, R.string.services_error_save)
        response.name.ifBlank { payload.name }
    } catch (exception: HttpException) {
        failHttp(upstreamMessage(exception), R.string.services_error_save, exception.code())
    }

    suspend fun delete(name: String, force: Boolean = false) {
        try {
            apiProvider.api().deleteService(
                name = name,
                agentId = agent(),
                force = if (force) "1" else null,
            )
        } catch (exception: HttpException) {
            throw InUse.from(exception, R.string.services_error_delete)
        }
    }

    suspend fun setOwnership(name: String, adopt: Boolean): Boolean = try {
        val response = apiProvider.api()
            .setServiceOwnership(name, ServiceOwnershipRequest(adopt), agentId = agent())
        if (!response.ok) fail(response.error, R.string.services_error_ownership)
        response.owned
    } catch (exception: HttpException) {
        failHttp(upstreamMessage(exception), R.string.services_error_ownership, exception.code())
    }

    private fun upstreamMessage(exception: HttpException): String? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (parsed["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}

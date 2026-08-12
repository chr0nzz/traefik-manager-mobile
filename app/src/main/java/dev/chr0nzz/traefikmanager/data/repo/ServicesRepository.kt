package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
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
) {

    fun cached(): ServiceEnvelope? = dashboardRepository.raw.value?.services

    suspend fun load(): ServiceEnvelope = try {
        apiProvider.api().services()
    } catch (exception: HttpException) {
        error(upstreamMessage(exception) ?: "Traefik API not reachable (HTTP ${exception.code()})")
    }

    private fun upstreamMessage(exception: HttpException): String? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (parsed["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}

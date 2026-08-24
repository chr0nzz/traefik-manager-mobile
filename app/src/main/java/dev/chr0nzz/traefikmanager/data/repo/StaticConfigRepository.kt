package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.PluginInstallRequest
import dev.chr0nzz.traefikmanager.data.model.PluginInstallResponse
import dev.chr0nzz.traefikmanager.data.model.StaticConfigResponse
import dev.chr0nzz.traefikmanager.data.model.StaticSaveRequest
import dev.chr0nzz.traefikmanager.data.model.StaticSectionRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException

/**
 * Traefik's static configuration, and the plugin entries that live inside it.
 *
 * Reads always go to the host, naming the agent when there is one, because the host knows how to
 * fetch an agent's file and hands back the same shape either way. Writes are the asymmetric part:
 * the host takes `/api/static/config`, an agent takes its own `/api/static` through the proxy.
 */
@Singleton
class StaticConfigRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serverScope: ServerScope,
) {

    private fun agent(): String? = serverScope.activeAgentId.value

    /** Whether this server has a static config at all, which is what gates every write. */
    suspend fun manageable(): Boolean = runCatching {
        val agent = agent()
        if (agent == null) {
            apiProvider.apiFor(null).staticAvailable().available
        } else {
            apiProvider.api().agentStaticStatus().configured
        }
    }.getOrDefault(false)

    suspend fun read(): StaticConfigResponse = try {
        apiProvider.apiFor(null).staticConfig(server = agent())
    } catch (exception: HttpException) {
        error(message(exception) ?: "Could not read the static config (HTTP ${exception.code()})")
    }

    suspend fun write(content: String) {
        val agent = agent()
        try {
            if (agent == null) {
                apiProvider.apiFor(null).saveStaticConfig(StaticSaveRequest(content))
            } else {
                apiProvider.api().saveAgentStaticConfig(StaticSaveRequest(content))
            }
        } catch (exception: HttpException) {
            error(message(exception) ?: "Could not save the static config (HTTP ${exception.code()})")
        }
    }

    suspend fun restart() {
        try {
            apiProvider.api().restartTraefik()
        } catch (exception: HttpException) {
            // Traefik usually carries this very request, so it dying mid-flight means it worked.
            if (exception.code() !in setOf(502, 504)) {
                error(message(exception) ?: "The restart failed (HTTP ${exception.code()})")
            }
        }
    }

    suspend fun install(
        staticYaml: String,
        middlewareYaml: String,
        middlewareFile: String,
    ): PluginInstallResponse {
        val request = PluginInstallRequest(
            staticYaml = staticYaml,
            middlewareYaml = middlewareYaml,
            middlewareFile = middlewareFile,
            server = agent().orEmpty(),
        )
        return try {
            apiProvider.apiFor(null).installPlugin(request)
        } catch (exception: HttpException) {
            PluginInstallResponse(
                ok = false,
                error = message(exception) ?: "Could not install the plugin (HTTP ${exception.code()})",
            )
        }
    }

    /** Rewrites one plugin entry. The transform is the host's; the write is wherever it belongs. */
    suspend fun savePlugin(name: String, oldName: String, moduleName: String, version: String) =
        section(
            action = "edit",
            name = name,
            oldName = oldName,
            data = buildJsonObject {
                put("moduleName", JsonPrimitive(moduleName))
                put("version", JsonPrimitive(version))
            },
        )

    suspend fun removePlugin(name: String) =
        section(action = "remove", name = name, oldName = name, data = JsonObject(emptyMap()))

    private suspend fun section(action: String, name: String, oldName: String, data: JsonObject) {
        // An agent's file is not on the host, so the document has to travel with the request.
        val currentRaw = if (agent() != null) read().raw.takeIf { it.isNotBlank() } else null
        val response = try {
            apiProvider.apiFor(null).staticSection(
                StaticSectionRequest(
                    section = "plugins",
                    action = action,
                    name = name,
                    oldName = oldName,
                    data = data,
                    currentRaw = currentRaw,
                ),
            )
        } catch (exception: HttpException) {
            error(message(exception) ?: "Could not change the plugin (HTTP ${exception.code()})")
        }
        if (!response.ok || response.raw.isBlank()) {
            error(response.error ?: "Could not change the plugin")
        }
        write(response.raw)
    }

    private fun message(exception: HttpException): String? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        val parsed = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return listOf("error", "message")
            .firstNotNullOfOrNull { (parsed[it] as? JsonPrimitive)?.content?.takeIf { m -> m.isNotBlank() } }
    }
}

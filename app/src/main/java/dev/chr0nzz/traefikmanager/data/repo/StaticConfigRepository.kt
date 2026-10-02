package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.failHttp
import dev.chr0nzz.traefikmanager.data.model.fail
import dev.chr0nzz.traefikmanager.R
import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.PluginInstallRequest
import dev.chr0nzz.traefikmanager.data.model.PluginInstallResponse
import dev.chr0nzz.traefikmanager.data.model.StaticConfigResponse
import dev.chr0nzz.traefikmanager.data.model.StaticSaveRequest
import dev.chr0nzz.traefikmanager.data.model.StaticSectionRequest
import dev.chr0nzz.traefikmanager.data.model.StaticSectionResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException

@Singleton
class StaticConfigRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serverScope: ServerScope,
) {

    private fun agent(): String? = serverScope.activeAgentId.value

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
        failHttp(message(exception), R.string.static_error_load, exception.code())
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
            failHttp(message(exception), R.string.error_could_not_save_the_static_config, exception.code())
        }
    }

    suspend fun restart() {
        try {
            apiProvider.api().restartTraefik()
        } catch (exception: HttpException) {
            if (exception.code() !in setOf(502, 504)) {
                failHttp(message(exception), R.string.error_the_restart_failed, exception.code())
            }
        }
    }

    suspend fun catalog(): Map<String, String> = runCatching {
        apiProvider.apiFor(null).pluginCatalog().plugins
    }.getOrDefault(emptyMap())

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
                error = message(exception),
            )
        }
    }

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

    suspend fun applySection(
        section: String,
        action: String,
        name: String,
        oldName: String,
        data: JsonObject,
        currentRaw: String,
    ): StaticSectionResponse {
        val response = try {
            apiProvider.apiFor(null).staticSection(
                StaticSectionRequest(
                    section = section,
                    action = action,
                    name = name,
                    oldName = oldName,
                    data = data,
                    currentRaw = currentRaw,
                ),
            )
        } catch (exception: HttpException) {
            failHttp(message(exception), R.string.static_error_apply, exception.code())
        }
        if (!response.ok || response.raw.isBlank()) fail(response.error, R.string.static_error_apply)
        return response
    }

    private suspend fun section(action: String, name: String, oldName: String, data: JsonObject) {
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
            failHttp(message(exception), R.string.error_could_not_change_the_plugin, exception.code())
        }
        if (!response.ok || response.raw.isBlank()) {
            fail(response.error, R.string.error_could_not_change_the_plugin)
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

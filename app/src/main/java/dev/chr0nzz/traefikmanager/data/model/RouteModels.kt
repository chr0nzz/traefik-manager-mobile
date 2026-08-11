package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
data class Route(
    val id: String = "",
    val name: String = "",
    val rule: String = "",
    @SerialName("service_name") val serviceName: String = "",
    val target: String = "",
    val servers: List<String> = emptyList(),
    val middlewares: JsonElement? = null,
    val entryPoints: JsonElement? = null,
    val entrypointMiddlewares: List<String> = emptyList(),
    val protocol: String = "http",
    val tls: JsonElement? = null,
    val enabled: Boolean = true,
    val certResolver: String = "",
    val tlsOptionsProfile: String = "",
    val insecureSkipVerify: Boolean = false,
    val passHostHeader: Boolean? = null,
    val priority: Int? = null,
    val serviceType: String = "loadBalancer",
    val configFile: String = "",
    val provider: String = "file",
) {
    val tlsEnabled: Boolean
        get() = when (val t = tls) {
            null, JsonNull -> false
            is JsonPrimitive -> t.booleanOrNull ?: (t.isString && t.content.isNotEmpty())
            is JsonObject -> true
            else -> false
        }

    val middlewareNames: List<String> get() = middlewares.asStringList()

    val entryPointNames: List<String> get() = entryPoints.asStringList()

    val backendCount: Int get() = if (servers.isNotEmpty()) servers.size else if (target.isNotEmpty() && target != "N/A") 1 else 0

    val hosts: List<String> get() = HOST_REGEX.findAll(rule).map { it.groupValues[1] }.toList()
}

private val HOST_REGEX = Regex("""Host\(`([^`]+)`\)""")

private fun JsonElement?.asStringList(): List<String> = when (this) {
    null, JsonNull -> emptyList()
    is JsonPrimitive -> if (isString && content.isNotEmpty()) listOf(content) else emptyList()
    is kotlinx.serialization.json.JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content }
    else -> emptyList()
}

@Serializable
data class MiddlewareDef(
    val name: String = "",
    val type: String = "http",
    val yaml: String = "",
    val configFile: String = "",
)

@Serializable
data class ConfigError(
    val file: String = "",
    val error: String = "",
)

@Serializable
data class RoutesResponse(
    val apps: List<Route> = emptyList(),
    val middlewares: List<MiddlewareDef> = emptyList(),
    val configErrors: List<ConfigError> = emptyList(),
)

@Serializable
data class ToggleRequest(
    val enable: Boolean,
    @SerialName("agent_id") val agentId: String = "",
)

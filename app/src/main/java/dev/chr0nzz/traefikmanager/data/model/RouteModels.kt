package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
data class CompositeChild(
    val name: String = "",
    val url: String = "",
    val weight: Int = 0,
    val percent: Int = 0,
)

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
    val serviceOwned: Boolean = false,
    val compositeChildren: List<CompositeChild> = emptyList(),
    val configFile: String = "",
    val provider: String = "file",
    val sticky: StickyCookie? = null,
    val stickyEnabled: Boolean = false,
    val healthCheck: HealthCheck? = null,
    val tlsDomains: List<TlsDomain> = emptyList(),
    val streaming: Boolean = false,
    val headersPreset: HeadersPreset? = null,
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

    val isComposite: Boolean get() = serviceType != "loadBalancer"

    val backendCount: Int get() = when {
        servers.isNotEmpty() -> servers.size
        compositeChildren.isNotEmpty() -> compositeChildren.size
        target.isNotEmpty() && target != "N/A" -> 1
        else -> 0
    }

    val hosts: List<String> get() = HOST_REGEX.findAll(rule).map { it.groupValues[1] }.toList()

    val isPlainHostRule: Boolean
        get() = hosts.isNotEmpty() && rule == hosts.joinToString(" || ") { "Host(`$it`)" }

    val tlsPassthrough: Boolean
        get() = (tls as? JsonObject)?.get("passthrough")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true

    val hasWildcard: Boolean get() = tlsDomains.isNotEmpty()
}

@Serializable
data class StickyCookie(
    @Serializable(with = LenientStringSerializer::class) val name: String = "",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    @Serializable(with = LenientStringSerializer::class) val sameSite: String = "",
)

@Serializable
data class HealthCheck(
    @Serializable(with = LenientStringSerializer::class) val path: String = "",
    @Serializable(with = LenientStringSerializer::class) val interval: String = "",
    @Serializable(with = LenientStringSerializer::class) val timeout: String = "",
)

@Serializable
data class TlsDomain(
    val main: String = "",
    val sans: List<String> = emptyList(),
)

@Serializable
data class HeadersPresetToggles(
    val perms: Map<String, String> = emptyMap(),
    val hsts: Boolean = true,
    val nosniff: Boolean = true,
    val frameDeny: Boolean = true,
    val referrer: String = "",
)

@Serializable
data class HeadersPreset(
    val owned: Boolean = false,
    val exists: Boolean = false,
    val state: String = "off",
    val toggles: HeadersPresetToggles = HeadersPresetToggles(),
)

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
data class ServicesByProtocol(
    val http: List<String> = emptyList(),
    val tcp: List<String> = emptyList(),
    val udp: List<String> = emptyList(),
) {
    fun forProtocol(protocol: String): List<String> = when (protocol) {
        "tcp" -> tcp
        "udp" -> udp
        else -> http
    }
}

@Serializable
data class RoutesResponse(
    val apps: List<Route> = emptyList(),
    val middlewares: List<MiddlewareDef> = emptyList(),
    val configErrors: List<ConfigError> = emptyList(),
    val services: ServicesByProtocol = ServicesByProtocol(),
)

@Serializable
data class ToggleRequest(
    val enable: Boolean,
    @SerialName("agent_id") val agentId: String = "",
)

@Serializable
data class RawRoute(
    val raw: String = "",
    val configFile: String = "",
    val proto: String = "",
    val error: String? = null,
)

@Serializable
data class RawRouteSave(
    val content: String,
)

@Serializable
data class PingResult(
    val ok: Boolean = false,
    @SerialName("latency_ms") val latencyMs: Int? = null,
    @SerialName("status_code") val statusCode: Int? = null,
    val self: Boolean = false,
    val error: String? = null,
)

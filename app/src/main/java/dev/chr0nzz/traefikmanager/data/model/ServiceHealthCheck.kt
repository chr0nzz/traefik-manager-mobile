package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
data class HealthCheckPayload(
    val enabled: Boolean,
    val path: String = "",
    val interval: String = "",
    val timeout: String = "",
    val unhealthyInterval: String = "",
    val method: String = "",
    val status: String = "",
    val scheme: String = "",
    val port: String = "",
    val hostname: String = "",
    val mode: String = "",
    val followRedirects: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
)

data class ServiceHealthDraft(
    val enabled: Boolean = false,
    val path: String = "",
    val interval: String = "",
    val timeout: String = "",
    val unhealthyInterval: String = "",
    val method: String = "",
    val status: String = "",
    val scheme: String = "",
    val port: String = "",
    val hostname: String = "",
    val mode: String = "",
    val followRedirects: Boolean = true,
    val headers: List<Pair<String, String>> = emptyList(),
) {
    fun payload(): HealthCheckPayload = HealthCheckPayload(
        enabled = enabled,
        path = path.trim(),
        interval = interval.trim(),
        timeout = timeout.trim(),
        unhealthyInterval = unhealthyInterval.trim(),
        method = method.trim(),
        status = status.trim(),
        scheme = scheme.trim(),
        port = port.trim(),
        hostname = hostname.trim(),
        mode = mode.trim(),
        followRedirects = followRedirects,
        headers = headers.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second.trim() },
    )

    companion object {
        private val DURATION = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?(?:(\d+)ms)?$""")

        fun shortDuration(value: String): String {
            val text = value.trim()
            if (text.isEmpty()) return text
            val match = DURATION.matchEntire(text) ?: return text
            val parts = listOf("h", "m", "s", "ms").mapIndexedNotNull { index, unit ->
                match.groupValues[index + 1].takeIf { it.isNotEmpty() && it.toDouble() != 0.0 }?.let { it + unit }
            }
            return parts.joinToString("").ifEmpty { text }
        }

        fun of(element: JsonElement?): ServiceHealthDraft {
            val hc = element as? JsonObject ?: return ServiceHealthDraft()
            if (hc.isEmpty()) return ServiceHealthDraft()
            fun text(key: String): String =
                (hc[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content.orEmpty()
            return ServiceHealthDraft(
                enabled = true,
                path = text("path"),
                interval = shortDuration(text("interval")),
                timeout = shortDuration(text("timeout")),
                unhealthyInterval = shortDuration(text("unhealthyInterval")),
                method = text("method"),
                status = text("status"),
                scheme = text("scheme"),
                port = text("port"),
                hostname = text("hostname"),
                mode = text("mode"),
                followRedirects = (hc["followRedirects"] as? JsonPrimitive)?.booleanOrNull ?: true,
                headers = (hc["headers"] as? JsonObject)
                    ?.map { (key, value) -> key to (value as? JsonPrimitive)?.content.orEmpty() }
                    .orEmpty(),
            )
        }
    }
}

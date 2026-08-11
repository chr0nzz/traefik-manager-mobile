package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    @SerialName("cert_resolver") val certResolver: String = "",
    val domains: List<String> = emptyList(),
)

@Serializable
data class AgentsResponse(
    val agents: List<Agent> = emptyList(),
)

@Serializable
data class AgentHealth(
    val ok: Boolean = false,
    val version: String? = null,
    @SerialName("latency_ms") val latencyMs: Int? = null,
    val error: String? = null,
)

data class ServerTarget(
    val id: String?,
    val name: String,
) {
    val isHost: Boolean get() = id == null

    companion object {
        val Host = ServerTarget(null, "Host")
    }
}

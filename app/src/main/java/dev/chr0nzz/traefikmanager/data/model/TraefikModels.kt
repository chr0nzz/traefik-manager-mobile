package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OverviewCounts(
    val total: Int? = null,
    val warnings: Int? = null,
    val errors: Int? = null,
)

@Serializable
data class OverviewSection(
    val routers: OverviewCounts? = null,
    val services: OverviewCounts? = null,
    val middlewares: OverviewCounts? = null,
)

@Serializable
data class OverviewFeatures(
    val tracing: String? = null,
    val metrics: String? = null,
    val accessLog: Boolean? = null,
)

@Serializable
data class Overview(
    val http: OverviewSection? = null,
    val tcp: OverviewSection? = null,
    val udp: OverviewSection? = null,
    val providers: List<String>? = null,
    val features: OverviewFeatures? = null,
) {
    val isBlind: Boolean get() = http == null && tcp == null && udp == null
}

@Serializable
data class TraefikObject(
    val name: String = "",
    val provider: String = "",
    val status: String? = null,
    val rule: String? = null,
    val service: String? = null,
    val using: List<String>? = null,
    val entryPoints: List<String>? = null,
    val usedBy: List<String>? = null,
    val serverStatus: Map<String, String>? = null,
    val type: String? = null,
) {
    val shortName: String get() = name.substringBefore('@')
}

@Serializable
data class ProtoEnvelope(
    val http: List<TraefikObject> = emptyList(),
    val tcp: List<TraefikObject> = emptyList(),
    val udp: List<TraefikObject> = emptyList(),
    val reachable: Boolean = true,
) {
    val all: List<TraefikObject> get() = http + tcp + udp
}

@Serializable
data class EntrypointTls(
    val certResolver: String? = null,
    val options: String? = null,
)

@Serializable
data class EntrypointHttp(
    val middlewares: List<String>? = null,
    val tls: EntrypointTls? = null,
)

@Serializable
data class Entrypoint(
    val name: String = "",
    val address: String = "",
    val asDefault: Boolean = false,
    val http: EntrypointHttp? = null,
) {
    val isUdp: Boolean get() = address.endsWith("/udp")
    val isTcpOnly: Boolean get() = address.endsWith("/tcp")
    val hasTls: Boolean get() = http?.tls != null
}

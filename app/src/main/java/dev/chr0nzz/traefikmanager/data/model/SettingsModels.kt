package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerSettings(
    val domains: List<String> = emptyList(),
    @SerialName("cert_resolver") val certResolver: String = "",
) {
    val certResolvers: List<String>
        get() = certResolver.split(',').map(String::trim).filter(String::isNotEmpty)
}

@Serializable
data class CertResolversResponse(
    val resolvers: List<String> = emptyList(),
)

@Serializable
data class StaticConfigResponse(
    val parsed: kotlinx.serialization.json.JsonObject? = null,
    val path: String = "",
)

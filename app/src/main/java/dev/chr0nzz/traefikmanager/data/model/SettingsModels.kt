package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerSettings(
    val domains: List<String> = emptyList(),
    @SerialName("cert_resolver") val certResolver: String = "",
    @SerialName("visible_tabs") val visibleTabs: Map<String, Boolean> = emptyMap(),
    @SerialName("crowdsec_enabled") val crowdsecEnabled: Boolean = false,
    @SerialName("crowdsec_lapi_url") val crowdsecLapiUrl: String = "",
) {
    val certResolvers: List<String>
        get() = certResolver.split(',').map(String::trim).filter(String::isNotEmpty)

    /** Optional server tabs the user has switched on. An unreported tab stays visible. */
    fun tabVisible(tab: String): Boolean = visibleTabs[tab] ?: true
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

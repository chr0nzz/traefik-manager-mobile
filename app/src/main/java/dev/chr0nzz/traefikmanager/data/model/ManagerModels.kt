package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ManagerVersion(
    val version: String = "",
    val repo: String = "",
    @SerialName("static_config_configured") val staticConfigConfigured: Boolean = false,
)

@Serializable
data class ApiKeyStatus(
    val enabled: Boolean = false,
    val count: Int = 0,
)

@Serializable
data class TraefikVersion(
    @SerialName("Version") val version: String? = null,
    @SerialName("Codename") val codename: String? = null,
)

@Serializable
data class OkResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

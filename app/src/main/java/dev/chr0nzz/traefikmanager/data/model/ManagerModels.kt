package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ManagerVersion(
    val version: String? = null,
)

@Serializable
data class ApiKeyStatus(
    val enabled: Boolean = false,
)
